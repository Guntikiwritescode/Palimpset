#!/usr/bin/env bash
# Apply V1 (frozen contract) + V2 (additive) to an ephemeral PostgreSQL 16
# database, in Flyway order, as the `migrate` role. Part of the contract gate
# (ARCHITECTURE §8). Also asserts V1 == the frozen contract file byte-for-byte.
#
# Env (with sensible local defaults):
#   PGHOST (127.0.0.1) PGPORT (5432) PG_SUPERUSER (postgres)
#   MIGRATE_USER (migrate) MIGRATE_PW (migrate_pw) GATE_DB (contract_gate)
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
MIG="$REPO/services/engine/src/main/resources/db/migration"
FROZEN="$REPO/contracts/claim-schema.sql"

PGHOST="${PGHOST:-127.0.0.1}"; PGPORT="${PGPORT:-5432}"
PG_SUPERUSER="${PG_SUPERUSER:-postgres}"
MIGRATE_USER="${MIGRATE_USER:-migrate}"; MIGRATE_PW="${MIGRATE_PW:-migrate_pw}"
GATE_DB="${GATE_DB:-contract_gate}"

echo "[gate] V1 migration is byte-identical to the frozen contract …"
if ! diff -q "$FROZEN" "$MIG/V1__initial_contract.sql" >/dev/null; then
  echo "  ✗ V1__initial_contract.sql has diverged from contracts/claim-schema.sql" >&2
  exit 1
fi
echo "  ✓ V1 == frozen contract"

echo "[gate] apply V1+V2 to an ephemeral PostgreSQL 16 …"
sudo -u "$PG_SUPERUSER" dropdb --if-exists "$GATE_DB" >/dev/null 2>&1 || true
sudo -u "$PG_SUPERUSER" createdb -O "$MIGRATE_USER" "$GATE_DB"
sudo -u "$PG_SUPERUSER" psql -d "$GATE_DB" -qc "GRANT ALL ON SCHEMA public TO $MIGRATE_USER;" >/dev/null

for f in "$MIG"/V*.sql; do
  echo "  applying $(basename "$f")"
  PGPASSWORD="$MIGRATE_PW" psql -h "$PGHOST" -p "$PGPORT" -U "$MIGRATE_USER" -d "$GATE_DB" \
    -v ON_ERROR_STOP=1 -q -f "$f"
done

tables=$(PGPASSWORD="$MIGRATE_PW" psql -h "$PGHOST" -p "$PGPORT" -U "$MIGRATE_USER" -d "$GATE_DB" -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';")
echo "  ✓ V1+V2 applied cleanly — $tables base tables"

sudo -u "$PG_SUPERUSER" dropdb "$GATE_DB"
echo "[gate] migrations: PASS"
