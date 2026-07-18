#!/usr/bin/env bash
# =============================================================================
# demo.sh — `make demo` (ARCHITECTURE §14.3): the one-command local bring-up.
# =============================================================================
#   kind up -> deploy -> migrate (engine runs Flyway at startup) -> ingest the
#   SYNTHETIC fixture -> port-forward -> print the URL.
#
# The fixture is ALWAYS the committed synthetic one (fixtures/synthetic/*.ndjson)
# — invented people and ties, never a dump. A subset of license-unconfirmed data
# is still license-unconfirmed (§20 A5), so the demo never touches SDFB.
#
# Access is port-forward only (no Ingress, §3.8). This script leaves the
# port-forwards running in the foreground and prints the URLs; Ctrl-C tears them
# down. Set KEEP_CLUSTER=1 to keep the cluster after Ctrl-C.
#
# CI ONLY for the cluster steps: kind/docker are unavailable in the dev
# container. Authored to be correct and run locally / in CI.
#
# Tunables (env): CLUSTER_NAME (palimpsest-demo), ENGINE_IMAGE, EXPLORER_IMAGE,
#   ALLOW_PULL (0), KEEP_CLUSTER (0).
# =============================================================================
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

CLUSTER_NAME="${CLUSTER_NAME:-palimpsest-demo}"
OVERLAY="deploy/overlays/local"
ENGINE_IMAGE="${ENGINE_IMAGE:-palimpsest/engine:local}"
EXPLORER_IMAGE="${EXPLORER_IMAGE:-palimpsest/explorer:local}"
ALLOW_PULL="${ALLOW_PULL:-0}"
KEEP_CLUSTER="${KEEP_CLUSTER:-0}"

FIXTURE_DIR="fixtures/synthetic"
ENGINE_PF_PORT=8080      # engine API
EXPLORER_PF_PORT=8081    # explorer UI (CORS is pinned to http://localhost:8081)
GRAFANA_PF_PORT=3000     # dashboards

PIPELINE_TOKEN="demo-pipeline-token"
SCHOLAR_TOKEN="demo-scholar-token"
PF_PIDS=()

log()  { printf '\033[36m[demo]\033[0m %s\n' "$*"; }
ok()   { printf '\033[32m[ ok ]\033[0m %s\n' "$*"; }
die()  { printf '\033[31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "required tool not found: $1"; }

cleanup() {
  for pid in "${PF_PIDS[@]:-}"; do kill "$pid" >/dev/null 2>&1 || true; done
  if [[ "$KEEP_CLUSTER" != "1" ]]; then
    log "deleting kind cluster '$CLUSTER_NAME' (KEEP_CLUSTER=1 to keep)"
    kind delete cluster --name "$CLUSTER_NAME" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# ---- preflight --------------------------------------------------------------
for t in kind kubectl docker curl jq; do need "$t"; done
[[ -f "$FIXTURE_DIR/entities.ndjson" && -f "$FIXTURE_DIR/claims.ndjson" ]] \
  || die "synthetic fixture missing under $FIXTURE_DIR"

# ---- 1. kind up -------------------------------------------------------------
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; then
  log "reusing kind cluster '$CLUSTER_NAME'"
else
  log "creating kind cluster '$CLUSTER_NAME'"
  kind create cluster --name "$CLUSTER_NAME" --wait 120s
fi

# ---- 2. load images ---------------------------------------------------------
for img in "$ENGINE_IMAGE" "$EXPLORER_IMAGE"; do
  if docker image inspect "$img" >/dev/null 2>&1; then
    log "loading image into kind: $img"; kind load docker-image "$img" --name "$CLUSTER_NAME"
  elif [[ "$ALLOW_PULL" == "1" ]]; then
    log "image $img not present; ALLOW_PULL=1 -> relying on registry pull"
  else
    die "image $img not found locally; build it or set ALLOW_PULL=1"
  fi
done

# ---- 3. dev Secrets ---------------------------------------------------------
log "creating dev Secrets"
kubectl create namespace palimpsest --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl create namespace observability --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl -n palimpsest create secret generic palimpsest-secrets \
  --from-literal=postgres-superuser-password="demo-super" \
  --from-literal=engine-db-password="demo-engine" \
  --from-literal=migrate-db-password="demo-migrate" \
  --from-literal=analytics-db-password="demo-analytics" \
  --from-literal=scholar-token="$SCHOLAR_TOKEN" \
  --from-literal=pipeline-token="$PIPELINE_TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl -n observability create secret generic observability-secrets \
  --from-literal=grafana-admin-password="demo-grafana" \
  --from-literal=analytics-db-password="demo-analytics" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

# ---- 4. deploy + migrate (Flyway runs at engine startup) -------------------
log "deploying: kubectl apply -k $OVERLAY"
kubectl apply -k "$OVERLAY"
log "waiting for Postgres + engine (engine applies Flyway migrations at startup)"
kubectl -n palimpsest rollout status statefulset/palimpsest-postgres --timeout=180s
kubectl -n palimpsest rollout status deploy/palimpsest-engine --timeout=240s
kubectl -n palimpsest rollout status deploy/palimpsest-explorer --timeout=180s
kubectl -n observability rollout status deploy/grafana --timeout=180s
ok "stack up, migrations applied"

# ---- 5. port-forwards -------------------------------------------------------
pf() { kubectl -n "$1" port-forward "svc/$2" "$3" >/dev/null 2>&1 & PF_PIDS+=("$!"); }
log "starting port-forwards"
pf palimpsest palimpsest-engine "${ENGINE_PF_PORT}:8080"
pf palimpsest palimpsest-explorer "${EXPLORER_PF_PORT}:80"
pf observability grafana "${GRAFANA_PF_PORT}:3000"

ENGINE="http://127.0.0.1:${ENGINE_PF_PORT}"
log "waiting for engine /readyz"
for i in $(seq 1 60); do
  curl -fsS "${ENGINE}/readyz" >/dev/null 2>&1 && { ok "engine ready"; break; }
  [[ "$i" == "60" ]] && die "engine not ready"
  sleep 2
done

# ---- 6. ingest the synthetic fixture ---------------------------------------
import_batch() {
  curl -fsS -X POST "${ENGINE}/api/v1/import/batches?kind=$1" \
    -H "Content-Type: application/x-ndjson" \
    -H "X-Palimpsest-Run: demo" -H "X-Palimpsest-Source: synth-fixture" \
    -H "Authorization: Bearer ${PIPELINE_TOKEN}" \
    --data-binary @"$2"
}
log "ingesting synthetic fixture (entities, then claims)"
import_batch entities "$FIXTURE_DIR/entities.ndjson" | jq -c '{received,inserted,rejected:(.rejected|length)}'
import_batch claims   "$FIXTURE_DIR/claims.ndjson"   | jq -c '{received,inserted,rejected:(.rejected|length)}'
ok "fixture ingested"

# ---- 7. print URLs and hold ------------------------------------------------
cat <<EOF

  =========================================================================
   PALIMPSEST demo is up (kind cluster: ${CLUSTER_NAME})
  -------------------------------------------------------------------------
   Explorer   ->  http://localhost:${EXPLORER_PF_PORT}
   Engine API ->  http://localhost:${ENGINE_PF_PORT}/api/v1   (try /stats/summary)
   Grafana    ->  http://localhost:${GRAFANA_PF_PORT}         (anonymous viewer)
  -------------------------------------------------------------------------
   Access is port-forward only — there is NO Ingress (SDFB license gate, §3.8).
   Ctrl-C to tear down the port-forwards (and the cluster unless KEEP_CLUSTER=1).
  =========================================================================

EOF

log "holding port-forwards open (Ctrl-C to stop)"
wait
