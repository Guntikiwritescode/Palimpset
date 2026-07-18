#!/usr/bin/env bash
# =============================================================================
# restore_drill.sh — the exercised backup/restore drill (ARCHITECTURE §18, WP5).
# =============================================================================
# Proves the restore path actually works, end to end, against the running
# cluster's Postgres:
#
#   1. quiesce writes  (scale the engine to 0 so no new claims land mid-drill)
#   2. record per-table rowcounts  (the "truth" we must reproduce)
#   3. pg_dump -Fc     (custom/restorable format)
#   4. DROP DATABASE   (the disaster)
#   5. CREATE + pg_restore  (the recovery)
#   6. record per-table rowcounts again
#   7. assert every table's rowcount matches; scale the engine back up
#
# By default the drill is IN-PLACE and DESTRUCTIVE (it drops and restores the
# real `palimpsest` database) — that is the point of a drill, and the local
# cluster is disposable. Set RESTORE_DB=palimpsest_verify to run the safe,
# non-destructive variant instead: restore the dump into a scratch database and
# compare, leaving the primary untouched.
#
# CI/local ONLY: needs a reachable cluster (kind). kubectl is required; all
# psql/pg_dump/pg_restore run *inside* the postgres pod, so no local Postgres
# client is needed. The postgres superuser password is read from the pod's own
# env (never pulled to the client).
#
# Tunables (env):
#   NAMESPACE (palimpsest)  DB (palimpsest)  RESTORE_DB (unset = in-place)
#   DUMP_PATH (/tmp/palimpsest-drill.dump, inside the pod)
#   FORCE (0)  -> required to run the destructive in-place drill non-interactively
# =============================================================================
set -euo pipefail

NAMESPACE="${NAMESPACE:-palimpsest}"
DB="${DB:-palimpsest}"
RESTORE_DB="${RESTORE_DB:-}"            # empty => in-place (destructive)
DUMP_PATH="${DUMP_PATH:-/tmp/palimpsest-drill.dump}"
FORCE="${FORCE:-0}"
ENGINE_DEPLOY="palimpsest-engine"

log()  { printf '\033[36m[drill]\033[0m %s\n' "$*"; }
ok()   { printf '\033[32m[ ok ]\033[0m %s\n' "$*"; }
die()  { printf '\033[31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "required tool not found: $1"; }

need kubectl

# Resolve the Postgres pod.
POD="$(kubectl -n "$NAMESPACE" get pod \
        -l app.kubernetes.io/name=postgres -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
[[ -n "$POD" ]] || die "could not find the postgres pod in namespace '$NAMESPACE'"
log "target: namespace=$NAMESPACE pod=$POD db=$DB"

# Run a bash snippet inside the pod. Client-side args are passed positionally
# ($1, $2, ...); the pod's own $POSTGRES_PASSWORD stays in the pod.
kx() { local script="$1"; shift; kubectl -n "$NAMESPACE" exec -i "$POD" -- bash -c "$script" _ "$@"; }

# psql-as-superuser helper string (used inside kx snippets).
PSQL='PGPASSWORD="$POSTGRES_PASSWORD" psql -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres'

# Print sorted "table|count" lines for a database (exact counts, not estimates).
count_rows() {  # count_rows <db>
  kx '
    set -euo pipefail
    db="$1"
    '"$PSQL"' -Atd "$db" -c "SELECT tablename FROM pg_tables WHERE schemaname='"'"'public'"'"' ORDER BY 1" \
    | while read -r t; do
        [ -z "$t" ] && continue
        n=$('"$PSQL"' -Atd "$db" -c "SELECT count(*) FROM public.\"$t\"")
        echo "$t|$n"
      done
  ' "$1"
}

if [[ -z "$RESTORE_DB" ]]; then
  log "MODE: IN-PLACE (destructive) — will DROP and restore database '$DB'"
  if [[ -t 0 && "$FORCE" != "1" ]]; then
    read -r -p "  This DROPs database '$DB'. Type 'drill' to proceed: " ans
    [[ "$ans" == "drill" ]] || die "aborted"
  elif [[ "$FORCE" != "1" && ! -t 0 ]]; then
    die "refusing destructive in-place drill non-interactively without FORCE=1 (or set RESTORE_DB for the safe variant)"
  fi
else
  log "MODE: SAFE — restore into scratch database '$RESTORE_DB'; primary '$DB' untouched"
fi

# ---- 1. quiesce writes ------------------------------------------------------
ORIG_REPLICAS="$(kubectl -n "$NAMESPACE" get deploy "$ENGINE_DEPLOY" -o jsonpath='{.spec.replicas}' 2>/dev/null || echo 1)"
if kubectl -n "$NAMESPACE" get deploy "$ENGINE_DEPLOY" >/dev/null 2>&1; then
  log "scaling $ENGINE_DEPLOY to 0 (was $ORIG_REPLICAS) to quiesce writes"
  kubectl -n "$NAMESPACE" scale deploy "$ENGINE_DEPLOY" --replicas=0
  kubectl -n "$NAMESPACE" rollout status deploy "$ENGINE_DEPLOY" --timeout=60s || true
fi
restore_engine() {
  if kubectl -n "$NAMESPACE" get deploy "$ENGINE_DEPLOY" >/dev/null 2>&1; then
    log "scaling $ENGINE_DEPLOY back to $ORIG_REPLICAS"
    kubectl -n "$NAMESPACE" scale deploy "$ENGINE_DEPLOY" --replicas="$ORIG_REPLICAS" || true
  fi
}
trap restore_engine EXIT

# ---- 2. record BEFORE counts ------------------------------------------------
log "recording BEFORE rowcounts"
BEFORE="$(count_rows "$DB")"
[[ -n "$BEFORE" ]] || die "no tables found in '$DB' — nothing to drill (is the schema migrated + loaded?)"
before_total="$(awk -F'|' '{s+=$2} END{print s+0}' <<<"$BEFORE")"
echo "$BEFORE" | sed 's/^/    /'
log "BEFORE total rows across $(wc -l <<<"$BEFORE") tables: $before_total"

# ---- 3. pg_dump -------------------------------------------------------------
log "pg_dump -Fc '$DB' -> $DUMP_PATH (inside pod)"
kx '
  set -euo pipefail
  '"$PSQL"' -d "$1" -Atc "SELECT 1" >/dev/null
  PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -Fc -h 127.0.0.1 -U postgres -d "$1" -f "$2"
  echo "dump size: $(du -h "$2" | cut -f1)"
' "$DB" "$DUMP_PATH"
ok "dump written"

# ---- 4 + 5. drop & restore --------------------------------------------------
if [[ -z "$RESTORE_DB" ]]; then
  TARGET_DB="$DB"
  log "DROP DATABASE '$DB' then CREATE + pg_restore (in-place)"
  kx '
    set -euo pipefail
    db="$1"
    # Terminate remaining backends, then drop and recreate the database.
    '"$PSQL"' -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='"'"'"'$db'"'"'"' AND pid<>pg_backend_pid();" >/dev/null || true
    '"$PSQL"' -d postgres -c "DROP DATABASE IF EXISTS \"$db\";"
    '"$PSQL"' -d postgres -c "CREATE DATABASE \"$db\" OWNER migrate;"
  ' "$DB"
else
  TARGET_DB="$RESTORE_DB"
  log "CREATE scratch database '$RESTORE_DB' for restore"
  kx '
    set -euo pipefail
    '"$PSQL"' -d postgres -c "DROP DATABASE IF EXISTS \"$1\";"
    '"$PSQL"' -d postgres -c "CREATE DATABASE \"$1\" OWNER migrate;"
  ' "$RESTORE_DB"
fi

log "pg_restore -> '$TARGET_DB'"
kx '
  set -euo pipefail
  PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -h 127.0.0.1 -U postgres -d "$1" --no-privileges=false --exit-on-error "$2"
' "$TARGET_DB" "$DUMP_PATH"
ok "restore complete into '$TARGET_DB'"

# ---- 6. record AFTER counts -------------------------------------------------
log "recording AFTER rowcounts"
AFTER="$(count_rows "$TARGET_DB")"
after_total="$(awk -F'|' '{s+=$2} END{print s+0}' <<<"$AFTER")"
echo "$AFTER" | sed 's/^/    /'
log "AFTER total rows: $after_total"

# ---- 7. verify --------------------------------------------------------------
if [[ "$BEFORE" == "$AFTER" ]]; then
  ok "VERIFIED — every table's rowcount matches after restore ($after_total rows)"
else
  echo "----- BEFORE vs AFTER diff -----" >&2
  diff <(echo "$BEFORE") <(echo "$AFTER") >&2 || true
  die "rowcount MISMATCH after restore — the drill FAILED"
fi

# Clean up scratch db in safe mode.
if [[ -n "$RESTORE_DB" ]]; then
  log "dropping scratch database '$RESTORE_DB'"
  kx ''"$PSQL"' -d postgres -c "DROP DATABASE IF EXISTS \"$1\";"' "$RESTORE_DB"
fi

log "-------------------------------------------------------------"
ok  "RESTORE DRILL PASSED — dump · drop · restore · rowcounts match"
log "-------------------------------------------------------------"
