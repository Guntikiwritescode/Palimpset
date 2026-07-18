# PALIMPSEST — deploy & observability runbook (WP5)

The Kubernetes deployment and observability layer for Part 1. Implements
`ARCHITECTURE.md` §3.8 and §5.6 on a local **kind** cluster, with a Grafana/
Prometheus/OpenTelemetry observability stack and an exercised backup/restore
drill.

> ## There is no Ingress. On purpose.
> The SDFB dataset license is **unconfirmed** (`source.license_confirmed=false`,
> `ARCHITECTURE.md` §11). Until it is confirmed, PALIMPSEST **must not be
> publicly exposed** — so there is **no Ingress, no Gateway/HTTPRoute, and no
> Service of type `LoadBalancer`** anywhere in these manifests. Every service is
> `ClusterIP`, and the only way to reach anything is `kubectl port-forward`. The
> gate is enforced at the network layer, not by good intentions:
> `scripts/check_no_ingress.sh` runs in pre-commit and CI and fails the build if
> a public-exposure kind ever appears. See `.claude/rules/deploy.md`.

---

## Layout

```
deploy/
  base/                         # the full stack (two namespaces, two planes)
    namespaces.yaml             #   palimpsest + observability
    secret.example.yaml         #   Secret TEMPLATE (placeholders; not applied)
    palimpsest/                 #   application plane
      postgres-*.yaml           #     StatefulSet (PVC 10Gi) + init roles + headless svc
      postgres-backup.yaml      #     pg_dump CronJob -> backup PVC
      engine-*.yaml             #     Deployment (/readyz,/healthz) + ConfigMap + svc
      explorer-*.yaml           #     nginx static build + ConfigMap + svc
    observability/              #   observability plane
      otel-collector.yaml       #     OTLP in (4317/4318) -> Prometheus exporter (8889)
      prometheus.yaml           #     scrape + TSDB PVC + svc
      postgres-exporter.yaml    #     DB size + rowcounts
      grafana.yaml              #     provisioned datasource + dashboards
      dashboards/*.json         #     the dashboard models (ConfigMap-generated)
  overlays/
    local/                      # the kind overlay (ClusterIP + port-forward only)
    cloud-stub/                 # deferred to the ADR-003 checkpoint (still NO Ingress)
```

Pinned images: `postgres:16.4`, `grafana/grafana:11.1.4`,
`prom/prometheus:v2.53.1`, `otel/opentelemetry-collector-contrib:0.104.0`,
`quay.io/prometheuscommunity/postgres-exporter:v0.15.0`, `nginx` (explorer base),
and the app placeholders `palimpsest/engine:local` / `palimpsest/explorer:local`.

---

## 1. One-command local deploy

```bash
make demo          # kind up -> deploy -> migrate -> ingest synthetic fixture -> port-forward -> print URLs
```

`make demo` runs `scripts/demo.sh`. It brings up a kind cluster, loads the local
engine/explorer images, creates throwaway dev Secrets, applies
`deploy/overlays/local`, waits for readiness (the engine runs Flyway migrations
at startup), ingests the **synthetic** fixture, and holds port-forwards open. It
prints:

```
Explorer   ->  http://localhost:8081
Engine API ->  http://localhost:8080/api/v1
Grafana    ->  http://localhost:3000
```

Prerequisites: `kind`, `kubectl`, `docker`, `curl`, `jq`, plus the engine and
explorer images built and available locally (`palimpsest/engine:local`,
`palimpsest/explorer:local`). Set `ALLOW_PULL=1` to skip the local-image
requirement, `KEEP_CLUSTER=1` to keep the cluster after Ctrl-C.

### Manual deploy (without the script)

```bash
kind create cluster --name palimpsest
kind load docker-image palimpsest/engine:local palimpsest/explorer:local --name palimpsest

# Secrets: copy the template, fill REAL values, apply it (never commit it).
cp deploy/base/secret.example.yaml /tmp/secrets.yaml
$EDITOR /tmp/secrets.yaml            # replace every CHANGE_ME_dev_only
kubectl apply -f /tmp/secrets.yaml && rm /tmp/secrets.yaml

kubectl apply -k deploy/overlays/local
kubectl -n palimpsest rollout status deploy/palimpsest-engine --timeout=240s
```

The Secrets are **referenced by name** from the workloads
(`palimpsest-secrets` in `palimpsest`, `observability-secrets` in
`observability`) — never embedded in a manifest or image (`ARCHITECTURE.md` §7).
`secret.example.yaml` carries only obvious `CHANGE_ME_dev_only` placeholders and
is deliberately **not** listed in any kustomization, so `kubectl apply -k` can
never push placeholders into a cluster.

Postgres roles are least-privilege (§7), created by the StatefulSet's init hook:
`migrate` (owns DDL / Flyway), `engine_rw` (DML — the engine's runtime role),
`analytics_ro` (SELECT — analytics + postgres_exporter).

---

## 2. Port-forwarding (the only access path)

```bash
# Explorer UI  (CORS is pinned engine-side to http://localhost:8081 — use 8081)
kubectl -n palimpsest port-forward svc/palimpsest-explorer 8081:80

# Engine API
kubectl -n palimpsest port-forward svc/palimpsest-engine 8080:8080

# Grafana dashboards
kubectl -n observability port-forward svc/grafana 3000:3000

# Prometheus (optional, for ad-hoc PromQL)
kubectl -n observability port-forward svc/prometheus 9090:9090
```

The browser loads the explorer from `http://localhost:8081` and calls the engine
at `http://localhost:8080`; the engine's CORS allow-list
(`PALIMPSEST_CORS_ALLOWED_ORIGINS`) is pinned to `http://localhost:8081`, so keep
those ports. There is intentionally no reverse proxy and no Ingress.

---

## 3. Running an ingest

The engine is the sole write authority; imports go through
`POST /api/v1/import/batches` (`ARCHITECTURE.md` Flow A). The fixture is always
**synthetic** — `make demo` ingests `fixtures/synthetic/`. To ingest manually
against a running port-forward:

```bash
TOKEN=<pipeline bearer token>            # the pipeline-token from your Secret

curl -sS -X POST "http://localhost:8080/api/v1/import/batches?kind=entities" \
  -H "Content-Type: application/x-ndjson" \
  -H "X-Palimpsest-Run: manual-$(date +%s)" \
  -H "X-Palimpsest-Source: synth-fixture" \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @fixtures/synthetic/entities.ndjson

curl -sS -X POST "http://localhost:8080/api/v1/import/batches?kind=claims" \
  -H "Content-Type: application/x-ndjson" \
  -H "X-Palimpsest-Run: manual-$(date +%s)" \
  -H "X-Palimpsest-Source: synth-fixture" \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @fixtures/synthetic/claims.ndjson
```

Each call returns a `202` report `{run,batch,received,inserted,duplicates,
superseded,rejected:[...]}`. `rejected` should be empty; a re-import of the same
data returns `inserted=0, duplicates=N` (idempotency, Flow A.8). The full
`kind create → apply → import(1k) → query` path is exercised in CI by
`scripts/kind_smoke.sh`.

---

## 4. Backups and the exercised restore drill

### 4a. Backups (automated)

`palimpsest-pg-backup` is a `CronJob` (`postgres:16.4`) that runs daily at 02:00,
writing a custom-format `pg_dump` to the `palimpsest-backup` PVC and retaining
the 7 most recent dumps. Run one on demand:

```bash
kubectl -n palimpsest create job --from=cronjob/palimpsest-pg-backup backup-now
kubectl -n palimpsest logs job/backup-now -f
```

### 4b. The restore drill (exercised)

`scripts/restore_drill.sh` proves the restore path works: it quiesces writes,
records rowcounts, dumps, **drops the database**, restores, and asserts every
table's rowcount matches.

```bash
# Destructive in-place drill (default; disposable kind cluster).
FORCE=1 bash scripts/restore_drill.sh

# Non-destructive variant: restore into a scratch DB and compare, leaving the
# primary untouched.
RESTORE_DB=palimpsest_verify bash scripts/restore_drill.sh
```

Step by step, what it does (and what to do by hand if you ever need to):

1. **Quiesce.** `kubectl -n palimpsest scale deploy/palimpsest-engine --replicas=0`
   so no new claims land mid-drill (connections are released).
2. **Record truth.** Exact `count(*)` per table in `palimpsest`.
3. **Dump.** Inside the postgres pod:
   `pg_dump -Fc -h 127.0.0.1 -U postgres -d palimpsest -f /tmp/palimpsest-drill.dump`.
4. **Drop (the disaster).** Terminate backends, then
   `DROP DATABASE palimpsest; CREATE DATABASE palimpsest OWNER migrate;`
   (run from the `postgres` maintenance DB).
5. **Restore (the recovery).**
   `pg_restore -h 127.0.0.1 -U postgres -d palimpsest --exit-on-error /tmp/palimpsest-drill.dump`.
6. **Verify.** Re-count every table; assert it equals step 2. Any mismatch exits
   nonzero.
7. **Resume.** Scale the engine back to its original replica count.

To restore from a **CronJob backup** on the backup PVC instead of a fresh dump,
run a one-off pod that mounts `palimpsest-backup`, pick the newest
`palimpsest-*.dump`, and `pg_restore` it into the DB with the same drop/create/
restore/verify sequence above (point `pg_restore` at
`palimpsest-postgres.palimpsest.svc.cluster.local`). The `rebuild-projections`
engine command (§6.3) rebuilds every materialization from the restored base
tables and is the projector's correctness oracle after a restore.

---

## 5. Reading the dashboards

Grafana provisions a Prometheus datasource and two dashboards (folder
**PALIMPSEST**) from ConfigMaps — no click-ops. Anonymous viewing is enabled on
the local overlay, so `http://localhost:3000` opens straight into read-only
dashboards.

**PALIMPSEST — API & SLOs**
- *API latency p95 / p50 by route* — from the Micrometer
  `http_server_requests_seconds` histogram. Reference lines at the §5.1 SLOs
  (p95 network < 300 ms, p95 reads < 500 ms).
- *p95 network query* stat — the signature `/entities/{id}/network` route; turns
  red above 300 ms.
- *Request rate* and *5xx error rate* by route.

**PALIMPSEST — Ingest, Projector & Data**
- *Import throughput* (`palimpsest_import_claims_total`) and *import outcomes*
  (inserted / duplicates / superseded / rejected). Rejected > 0 is
  operator-actionable.
- *Projector/outbox lag*: `palimpsest_outbox_pending_rows` (0 = caught up) and
  `palimpsest_outbox_oldest_age_seconds` vs the 60 s SLO (§3.4).
- *DB size + table rowcounts* from postgres_exporter (`pg_database_size_bytes`,
  `pg_stat_user_tables_n_live_tup`).
- *Anomaly counters over time* (`palimpsest_anomaly_total{class=...}`): the five
  data-quality classes (§4 / §19 FIX-ANOMALY), framed as *found and handled*.

> Metric-name contract: these panels (and `scripts/kind_smoke.sh`) assume the
> engine emits `http_server_requests_seconds*`, `palimpsest_import_*`,
> `palimpsest_outbox_*`, and `palimpsest_anomaly_total`, exported via OTLP to the
> collector and/or `/actuator/prometheus`. That instrumentation is the engine's
> side of WP5; the dashboards are the read side.

Data path: engine → OTLP → otel-collector (`:8889` Prometheus exporter) →
Prometheus scrape → Grafana. Prometheus also scrapes the engine's
`/actuator/prometheus` directly for the HTTP histogram, and postgres_exporter for
DB stats.

---

## 6. Responding to SLO breaches

| Symptom (dashboard) | Likely cause | First response |
|---|---|---|
| p95 network > 300 ms (API dashboard red) | cold cache / missing index / DB pressure | check *DB rowcounts* + Postgres CPU; confirm the network partial indexes (V2) exist; warm the cache; if sustained at Phase-1 scale, capture a query plan and open a perf finding (§6.4 ADR-001 revisit trigger) |
| p95 reads > 500 ms | same, broader | as above across routes |
| Outbox pending rows > 0 and rising | projector crashed or slow | `kubectl -n palimpsest logs deploy/palimpsest-engine`; reads still work (base tables are authoritative, §18); the explorer shows the *stale* banner once lag > SLO; restart resumes from the unprocessed row (at-least-once, idempotent) |
| Oldest outbox age > 60 s | projector backpressure | as above; if wrong summaries are suspected, run `rebuild-projections` (the recovery path + test oracle) |
| Import *rejected* > 0 | unresolvable refs / schema failures | read the per-line reject report from the run manifest (Flow A.7); reject is actionable, not fatal — the batch continues, no fabricated entities (I6) |
| DB size approaching PVC capacity | ingest growth | alert fires before writes fail (§18); halt imports (the only large writer, operator-initiated); expand the PVC |
| `/readyz` failing / pod not Ready | DB unreachable or migration pending/failed | engine refuses readiness rather than serving against an unknown schema (§18); the previous version keeps serving; fix the DB/migration, the pod rolls when ready |

Degradation ladder (§18), worst-case in order: summaries stale (reads fine,
banner shown) → writes rejected but reads served (DB read-only) → read-only
frontend (engine down) → hard down. Each rung is a designed, explained state.

---

## 7. cloud-stub

`deploy/overlays/cloud-stub` is a **deferred** placeholder (ADR-003). It renders
(it just references `base`) so the seam is visible and the no-Ingress gate is
checked against it too, but it is not a deployable cloud target — see its own
`README.md` for what a real cloud overlay must decide. The no-Ingress rule
survives into cloud unchanged while the license is unconfirmed.

---

## 8. CI

`scripts/kind_smoke.sh` is the §8 system smoke (runs on `main`): create kind →
assert rendered manifests contain no Ingress → `kubectl apply -k
deploy/overlays/local` → wait ready → import a 1,000-claim synthetic fixture →
run the signature network query → assert edge counts, that p95 latency is
measured, and that projector lag is zero at rest.
