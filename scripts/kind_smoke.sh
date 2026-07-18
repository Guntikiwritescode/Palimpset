#!/usr/bin/env bash
# =============================================================================
# kind_smoke.sh — the §8 system smoke test (ARCHITECTURE §8, §5.6, WP5).
# =============================================================================
# End to end, in a throwaway kind cluster:
#   1. create the kind cluster
#   2. assert the RENDERED manifests contain NO Ingress/Gateway/HTTPRoute/LB
#   3. deploy the full stack (kubectl apply -k deploy/overlays/local)
#   4. wait for every workload to become Ready
#   5. import a 1,000-claim SYNTHETIC fixture through the engine import path
#   6. run the signature network query via the API and assert edge counts
#   7. assert API p95 latency is *measured* (present in Prometheus)
#   8. assert the projector/outbox lag is ZERO at rest
#
# The fixture is generated at runtime and is 100% synthetic (invented people and
# ties) — never SDFB, never a dump. It reproduces the interaction (edge count
# strictly decreases as the confidence threshold rises), not the measured FIX-*
# numbers (those run in the slow suite against a locally-supplied dump).
#
# CI ONLY: kind/docker are unavailable in the dev container, so this script is
# authored to be correct but is exercised in CI. It is idempotent-ish: re-runs
# reuse an existing cluster unless RECREATE=1.
#
# Metric-name contract (the engine must emit these; dashboards use the same):
#   http_server_requests_seconds_bucket   (Micrometer, API latency histogram)
#   palimpsest_import_claims_total         (import throughput)
#   palimpsest_outbox_pending_rows         (projector/outbox lag == 0 at rest)
#
# Tunables (env):
#   CLUSTER_NAME   (palimpsest-smoke)   RECREATE (0)   KEEP_CLUSTER (0)
#   ENGINE_IMAGE   (palimpsest/engine:local)
#   EXPLORER_IMAGE (palimpsest/explorer:local)
#   ALLOW_PULL     (0)  -> if 1, do not fail when images are absent locally
# =============================================================================
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

CLUSTER_NAME="${CLUSTER_NAME:-palimpsest-smoke}"
OVERLAY="deploy/overlays/local"
ENGINE_IMAGE="${ENGINE_IMAGE:-palimpsest/engine:local}"
EXPLORER_IMAGE="${EXPLORER_IMAGE:-palimpsest/explorer:local}"
RECREATE="${RECREATE:-0}"
KEEP_CLUSTER="${KEEP_CLUSTER:-0}"
ALLOW_PULL="${ALLOW_PULL:-0}"

# Throwaway dev tokens for the smoke run (never real; created imperatively).
SCHOLAR_TOKEN="smoke-scholar-$$"
PIPELINE_TOKEN="smoke-pipeline-$$"

ENGINE_PF_PORT=18080     # local -> svc/palimpsest-engine:8080
PROM_PF_PORT=19090       # local -> svc/prometheus:9090
PF_PIDS=()

# ---- pretty logging ---------------------------------------------------------
log()  { printf '\033[36m[smoke]\033[0m %s\n' "$*"; }
ok()   { printf '\033[32m[ ok ]\033[0m %s\n' "$*"; }
die()  { printf '\033[31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

cleanup() {
  local code=$?
  # Kill any port-forwards we started.
  for pid in "${PF_PIDS[@]:-}"; do kill "$pid" >/dev/null 2>&1 || true; done
  if [[ "$KEEP_CLUSTER" != "1" && "$code" != "0" ]]; then
    log "(leaving cluster '$CLUSTER_NAME' up for debugging; KEEP_CLUSTER=1 to always keep, or 'kind delete cluster --name $CLUSTER_NAME')"
  fi
  if [[ "$KEEP_CLUSTER" != "1" && "$code" == "0" ]]; then
    log "deleting kind cluster '$CLUSTER_NAME'"
    kind delete cluster --name "$CLUSTER_NAME" >/dev/null 2>&1 || true
  fi
  exit "$code"
}
trap cleanup EXIT

need() { command -v "$1" >/dev/null 2>&1 || die "required tool not found: $1"; }

# ---- 0. preflight -----------------------------------------------------------
log "preflight: checking required tooling"
for t in kind kubectl docker curl jq python3; do need "$t"; done
ok "tooling present"

# ---- 1. create the kind cluster --------------------------------------------
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; then
  if [[ "$RECREATE" == "1" ]]; then
    log "RECREATE=1 -> deleting existing cluster '$CLUSTER_NAME'"
    kind delete cluster --name "$CLUSTER_NAME"
    kind create cluster --name "$CLUSTER_NAME" --wait 120s
  else
    log "reusing existing kind cluster '$CLUSTER_NAME'"
  fi
else
  log "creating kind cluster '$CLUSTER_NAME'"
  kind create cluster --name "$CLUSTER_NAME" --wait 120s
fi
kubectl cluster-info --context "kind-${CLUSTER_NAME}" >/dev/null || die "cluster context not reachable"
ok "cluster ready"

# ---- 2. NO-INGRESS assertion on the RENDERED manifests ----------------------
# The gate must hold on what actually gets applied, not just the source files.
log "asserting rendered manifests contain no public exposure"
RENDERED="$(kubectl kustomize "$OVERLAY")"
if grep -Eiq '^[[:space:]]*kind:[[:space:]]*(Ingress|Gateway|HTTPRoute)\b' <<<"$RENDERED"; then
  die "rendered manifests declare an Ingress/Gateway/HTTPRoute — no-Ingress gate VIOLATED"
fi
if grep -Eiq '^[[:space:]]*type:[[:space:]]*LoadBalancer\b' <<<"$RENDERED"; then
  die "rendered manifests declare a Service type LoadBalancer — no-Ingress gate VIOLATED"
fi
# Also run the repo gate over the tree for good measure.
bash scripts/check_no_ingress.sh tree >/dev/null || die "scripts/check_no_ingress.sh failed"
ok "no Ingress / Gateway / HTTPRoute / LoadBalancer present"

# ---- 3a. load local images into the kind node ------------------------------
load_image() {
  local img="$1"
  if docker image inspect "$img" >/dev/null 2>&1; then
    log "loading image into kind: $img"
    kind load docker-image "$img" --name "$CLUSTER_NAME"
  elif [[ "$ALLOW_PULL" == "1" ]]; then
    log "image $img not present locally; ALLOW_PULL=1 -> will rely on registry pull"
  else
    die "image $img not found locally. Build it first (engine: 'make engine-build' + docker build; explorer: build + docker build), or set ALLOW_PULL=1."
  fi
}
load_image "$ENGINE_IMAGE"
load_image "$EXPLORER_IMAGE"

# ---- 3b. dev Secrets (imperative; never committed) --------------------------
# The overlay references Secrets by name; here we create throwaway ones so the
# one-command smoke needs no manual secret editing.
log "creating dev Secrets"
kubectl create namespace palimpsest --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl create namespace observability --dry-run=client -o yaml | kubectl apply -f - >/dev/null

kubectl -n palimpsest create secret generic palimpsest-secrets \
  --from-literal=postgres-superuser-password="smoke-super-$$" \
  --from-literal=engine-db-password="smoke-engine-$$" \
  --from-literal=migrate-db-password="smoke-migrate-$$" \
  --from-literal=analytics-db-password="smoke-analytics-$$" \
  --from-literal=scholar-token="$SCHOLAR_TOKEN" \
  --from-literal=pipeline-token="$PIPELINE_TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

kubectl -n observability create secret generic observability-secrets \
  --from-literal=grafana-admin-password="smoke-grafana-$$" \
  --from-literal=analytics-db-password="smoke-analytics-$$" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
ok "dev Secrets created"

# ---- 3c. deploy -------------------------------------------------------------
log "deploying: kubectl apply -k $OVERLAY"
kubectl apply -k "$OVERLAY"
ok "manifests applied"

# ---- 4. wait for readiness --------------------------------------------------
log "waiting for Postgres StatefulSet"
kubectl -n palimpsest rollout status statefulset/palimpsest-postgres --timeout=180s

log "waiting for Deployments"
for d in palimpsest-engine palimpsest-explorer; do
  kubectl -n palimpsest rollout status deploy/"$d" --timeout=240s
done
for d in otel-collector prometheus grafana postgres-exporter; do
  kubectl -n observability rollout status deploy/"$d" --timeout=180s
done
ok "all workloads Ready"

# ---- 5a. port-forward engine + prometheus ----------------------------------
pf() {  # pf <ns> <svc> <local:remote> -> records the bg pid
  local ns="$1" svc="$2" map="$3"
  kubectl -n "$ns" port-forward "svc/$svc" "$map" >/dev/null 2>&1 &
  PF_PIDS+=("$!")
}
log "starting port-forwards (engine:$ENGINE_PF_PORT, prometheus:$PROM_PF_PORT)"
pf palimpsest palimpsest-engine "${ENGINE_PF_PORT}:8080"
pf observability prometheus "${PROM_PF_PORT}:9090"

ENGINE="http://127.0.0.1:${ENGINE_PF_PORT}"
PROM="http://127.0.0.1:${PROM_PF_PORT}"

# wait for the engine to answer /readyz (DB reachable + migrations current)
log "waiting for engine /readyz"
for i in $(seq 1 60); do
  if curl -fsS "${ENGINE}/readyz" >/dev/null 2>&1; then ok "engine ready"; break; fi
  [[ "$i" == "60" ]] && die "engine did not become ready in time"
  sleep 2
done

# ---- 5b. generate a 1,000-claim synthetic fixture --------------------------
WORK="$(mktemp -d)"
log "generating 1,000-claim synthetic fixture in $WORK"
python3 - "$WORK" >"$WORK/expect.env" <<'PY'
import json, sys, os
out = sys.argv[1]
# Deterministic synthetic corpus. One focus person with a spread of tie
# confidences, plus a background of persons/ties to reach ~1,000 claims.
N_PERSON = 200                 # persons -> 2 attribute claims each = 400 claims
random_edges_target = 560      # background relationship claims
focus_ext = "P0000"

entities, claims = [], []
def person(ext):
    entities.append({"schema_version":"0.1.0","ref":{"authority":"synth","id":ext},
                     "entity_type":"person","external_ids":[{"authority":"synth","id":ext}]})
def name_claim(ext, nm):
    claims.append({"schema_version":"0.1.0","subject":{"authority":"synth","id":ext},
        "predicate":"has-name","object":{"literal":{"kind":"string","value":nm}},
        "confidence":{"kind":"unscored"},"method":"imported","asserted_by":"pipeline",
        "support":[{"source":"synth-smoke","record_kind":"person","external_id":ext,
                    "content_hash":f"h-name-{ext}","raw":{"id":ext,"display_name":nm}}]})
def born_claim(ext, year):
    claims.append({"schema_version":"0.1.0","subject":{"authority":"synth","id":ext},
        "predicate":"born","object":{"literal":{"kind":"year","value":year}},
        "valid_time":{"start_earliest":f"{year}-01-01","start_latest":f"{year}-12-31",
                      "approximate":False,"original":{"type_code":"IN","year":str(year)}},
        "confidence":{"kind":"unscored"},"method":"imported","asserted_by":"pipeline",
        "support":[{"source":"synth-smoke","record_kind":"person","external_id":ext,
                    "content_hash":f"h-born-{ext}","raw":{"id":ext,"birth":str(year)}}]})
def rel_claim(a, b, point):
    claims.append({"schema_version":"0.1.0","subject":{"authority":"synth","id":a},
        "predicate":"associated-with","object":{"entity":{"authority":"synth","id":b}},
        "valid_time":{"start_earliest":"1590-01-01","start_latest":None,"end_earliest":None,
                      "end_latest":"1626-12-31","approximate":False,
                      "original":{"start":{"type_code":"AF/IN","year":"1590"},
                                  "end":{"type_code":"BF/IN","year":"1626"}}},
        "confidence":{"kind":"source_native_scalar","scale":"synth_0_100",
                      "raw":int(round(point*100)),"point":point,"calibrated":False},
        "method":"imported","asserted_by":"pipeline",
        "support":[{"source":"synth-smoke","record_kind":"relationship","external_id":f"{a}-{b}",
                    "content_hash":f"h-rel-{a}-{b}","raw":{"person1":a,"person2":b,
                    "max_certainty":int(round(point*100))}}]})

for i in range(N_PERSON):
    ext = f"P{i:04d}"
    person(ext); name_claim(ext, f"Synth Person {i}"); born_claim(ext, 1550 + (i % 60))

# Focus edges: 40 neighbours with confidence 0.025..1.0 in steps of 0.025.
# Deterministic thresholds: count(>=0.60) and count(>=0.90).
focus_neighbours = 40
c60 = c90 = 0
for k in range(1, focus_neighbours + 1):
    point = round(k * 0.025, 3)           # 0.025 .. 1.000
    if point > 1.0: point = 1.0
    rel_claim(focus_ext, f"P{k:04d}", point)
    if point >= 0.60: c60 += 1
    if point >= 0.90: c90 += 1

# Background relationship claims (deterministic pairing) to reach the target.
made = 0
i = 1
while made < random_edges_target:
    a = f"P{(i % (N_PERSON-1)) + 1:04d}"
    b = f"P{((i * 7 + 3) % (N_PERSON-1)) + 1:04d}"
    if a != b:
        rel_claim(a, b, round(0.10 + (i % 9) * 0.1, 3))
        made += 1
    i += 1

with open(os.path.join(out, "entities.ndjson"), "w") as f:
    for e in entities: f.write(json.dumps(e) + "\n")
with open(os.path.join(out, "claims.ndjson"), "w") as f:
    for c in claims: f.write(json.dumps(c) + "\n")

# Emit expectations for the shell to assert against.
print(f"FOCUS_EXT={focus_ext}")
print(f"EXPECT_ENTITIES={len(entities)}")
print(f"EXPECT_CLAIMS={len(claims)}")
print(f"EXPECT_FOCUS_GE60={c60}")
print(f"EXPECT_FOCUS_GE90={c90}")
PY
# shellcheck disable=SC1091
source "$WORK/expect.env"
log "fixture: $EXPECT_ENTITIES entities, $EXPECT_CLAIMS claims (focus $FOCUS_EXT: ${EXPECT_FOCUS_GE60}@>=0.60, ${EXPECT_FOCUS_GE90}@>=0.90)"
[[ "$EXPECT_CLAIMS" -ge 1000 ]] || die "generator produced fewer than 1,000 claims ($EXPECT_CLAIMS)"

# ---- 5c. import through the engine import path ------------------------------
import_batch() {  # import_batch <kind> <file>
  local kind="$1" file="$2"
  curl -fsS -X POST "${ENGINE}/api/v1/import/batches?kind=${kind}" \
    -H "Content-Type: application/x-ndjson" \
    -H "X-Palimpsest-Run: smoke-$$" \
    -H "X-Palimpsest-Source: synth-smoke" \
    -H "Authorization: Bearer ${PIPELINE_TOKEN}" \
    --data-binary @"$file"
}
log "importing entities"
ent_report="$(import_batch entities "$WORK/entities.ndjson")"
log "importing claims"
clm_report="$(import_batch claims "$WORK/claims.ndjson")"
echo "  entities report: $ent_report"
echo "  claims report:   $clm_report"

# Assert the engine accepted everything (no rejects, expected inserts).
ent_rej="$(jq -r '.rejected | length' <<<"$ent_report")"
clm_rej="$(jq -r '.rejected | length' <<<"$clm_report")"
clm_ins="$(jq -r '.inserted' <<<"$clm_report")"
[[ "$ent_rej" == "0" ]] || die "entity import had $ent_rej rejects"
[[ "$clm_rej" == "0" ]] || die "claim import had $clm_rej rejects"
[[ "$clm_ins" == "$EXPECT_CLAIMS" ]] || die "claim inserted=$clm_ins, expected $EXPECT_CLAIMS"
ok "import clean: inserted=$clm_ins, rejected=0"

# Idempotency check (Flow A.8): re-import claims -> inserted=0, duplicates=all.
log "re-importing claims to verify idempotency"
re_report="$(import_batch claims "$WORK/claims.ndjson")"
re_ins="$(jq -r '.inserted' <<<"$re_report")"
re_dup="$(jq -r '.duplicates' <<<"$re_report")"
[[ "$re_ins" == "0" ]] || die "re-import inserted=$re_ins (expected 0)"
ok "idempotent: re-import inserted=0, duplicates=$re_dup"

# ---- 6. signature network query via the API --------------------------------
log "resolving focus entity internal id"
focus_id="$(curl -fsS "${ENGINE}/api/v1/entities/lookup?authority=synth&externalId=${FOCUS_EXT}" | jq -r '.data.id')"
[[ -n "$focus_id" && "$focus_id" != "null" ]] || die "could not resolve focus entity $FOCUS_EXT"
log "focus entity id = $focus_id"

network_count() {  # network_count <minConfidence>
  curl -fsS "${ENGINE}/api/v1/entities/${focus_id}/network?minConfidence=$1&windowStart=1600-01-01&windowEnd=1600-12-31&temporalMode=possibly&includeUnscored=false&limit=500" \
    | jq -r '.data.edges | length'
}
n60="$(network_count 0.60)"
n90="$(network_count 0.90)"
log "network edges: >=0.60 -> $n60 ; >=0.90 -> $n90"
[[ "$n60" == "$EXPECT_FOCUS_GE60" ]] || die "edges@0.60 = $n60, expected $EXPECT_FOCUS_GE60"
[[ "$n90" == "$EXPECT_FOCUS_GE90" ]] || die "edges@0.90 = $n90, expected $EXPECT_FOCUS_GE90"
[[ "$n90" -lt "$n60" ]] || die "edge count did not strictly decrease as threshold rose ($n60 -> $n90)"
ok "signature query: $n60 -> $n90 (strictly decreasing, matches fixture)"

# ---- 7. assert p95 latency is MEASURED -------------------------------------
# Generate a little load so the histogram has samples, then read p95 from
# Prometheus. We assert a value is *present* (measured); we log the SLO check.
log "generating load on the network route for the latency histogram"
for _ in $(seq 1 40); do network_count 0.60 >/dev/null || true; done
sleep 20   # allow Prometheus (15s scrape) to pick up the histogram

PROMQL='histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri=~"/api/v1/entities/.+/network"}[5m])))'
p95="$(curl -fsS -G "${PROM}/api/v1/query" --data-urlencode "query=${PROMQL}" \
        | jq -r '.data.result[0].value[1] // empty')"
[[ -n "$p95" ]] || die "p95 latency for the network route is NOT measured in Prometheus (no samples)"
# p95 is in seconds. NaN means samples exist but no populated buckets yet.
if [[ "$p95" == "NaN" ]]; then die "p95 query returned NaN — histogram buckets not populated"; fi
ok "p95 network latency measured: ${p95}s (§5.1 SLO reference: < 0.300s)"
awk -v v="$p95" 'BEGIN{ if (v+0 > 0.3) print "  [warn] p95 above the 300ms SLO reference (acceptable on a cold local cluster)"; }'

# ---- 8. assert ZERO projector/outbox lag at rest ---------------------------
log "waiting for the projector to drain the outbox (lag -> 0)"
lag=""
for i in $(seq 1 30); do
  lag="$(curl -fsS -G "${PROM}/api/v1/query" \
          --data-urlencode 'query=max(palimpsest_outbox_pending_rows)' \
          | jq -r '.data.result[0].value[1] // empty')"
  if [[ -n "$lag" && "$lag" != "NaN" ]] && awk -v v="$lag" 'BEGIN{exit !(v+0==0)}'; then
    ok "projector at rest: outbox pending rows = 0"
    break
  fi
  [[ "$i" == "30" ]] && die "outbox lag did not reach 0 (last='${lag:-<none>}')"
  sleep 4
done

log "-------------------------------------------------------------"
ok  "SMOKE PASSED — deploy · import(1k synthetic) · signature query · p95 measured · zero lag · no Ingress"
log "-------------------------------------------------------------"
