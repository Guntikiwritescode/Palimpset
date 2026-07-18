#!/usr/bin/env bash
# Create the local dev database + least-privilege roles (ARCHITECTURE §7).
# Idempotent. For local development only — real credentials are k8s Secrets.
set -euo pipefail
PG_SUPERUSER="${PG_SUPERUSER:-postgres}"

sudo -u "$PG_SUPERUSER" psql -v ON_ERROR_STOP=1 <<'SQL'
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='migrate')      THEN CREATE ROLE migrate      LOGIN PASSWORD 'migrate_pw';   END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='engine_rw')    THEN CREATE ROLE engine_rw    LOGIN PASSWORD 'engine_pw';    END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='analytics_ro') THEN CREATE ROLE analytics_ro LOGIN PASSWORD 'analytics_pw'; END IF;
END $$;
SQL

if ! sudo -u "$PG_SUPERUSER" psql -tAc "SELECT 1 FROM pg_database WHERE datname='palimpsest'" | grep -q 1; then
  sudo -u "$PG_SUPERUSER" createdb -O migrate palimpsest
fi

sudo -u "$PG_SUPERUSER" psql -d palimpsest -v ON_ERROR_STOP=1 <<'SQL'
GRANT ALL   ON SCHEMA public TO migrate;
GRANT USAGE ON SCHEMA public TO engine_rw, analytics_ro;
-- engine_rw gets DML on all tables created by migrate; analytics_ro gets SELECT.
ALTER DEFAULT PRIVILEGES FOR ROLE migrate IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO engine_rw;
ALTER DEFAULT PRIVILEGES FOR ROLE migrate IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO engine_rw;
ALTER DEFAULT PRIVILEGES FOR ROLE migrate IN SCHEMA public GRANT SELECT ON TABLES TO analytics_ro;
SQL
echo "dev-db ready: palimpsest @ 127.0.0.1:5432 (roles migrate/engine_rw/analytics_ro)"
