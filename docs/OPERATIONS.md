# OPERATIONS — deploy, ingest, adjudicate, back up, restore

**Audience:** P3 (the data steward). Filled at WP5. Created at WP0 with audience
stated (HANDOFF §7.4).

Covers: one-command local deploy on kind; running an ingest and reading the run
report; the adjudication queue; reading the Grafana dashboards; the `pg_dump`
backup CronJob and the **exercised restore drill**; responding to SLO breaches
(projector/outbox lag, API latency). **No Ingress** — access is port-forward
only, stated next to the reason.

---

## Restore-drill record (exercised)

`scripts/restore_drill.sh` proves the backup/restore path: dump → the disaster
(`DROP DATABASE`) → recovery (`pg_restore`) → per-table rowcount verify. This
section records **actual runs with measured numbers**, per HANDOFF §3
("acceptance evidence is the artifact, not a description of it"). Before WP-R1
the script existed and was documented but had **no evidence of ever being run**.

### 2026-07-18 · WP-R1 · local PostgreSQL 16.13, synthetic corpus

**What was drilled.** A real PostgreSQL 16.13 server with the frozen V1+V2 schema
and the synthetic corpus (8 entities / 49 claims) loaded through the engine's
import path — **391 rows across 20 tables**. Run via the WP-R1 `LOCAL=1` mode of
`restore_drill.sh` (there is no kind cluster in this build's environment —
DEV-002), which exercises the identical `pg_dump -Fc → DROP → pg_restore →
rowcount-compare` sequence as the superuser over the unix socket.

Command (both variants run):

```
RESTORE_DB=palimpsest_verify LOCAL=1 bash scripts/restore_drill.sh   # safe: scratch DB
FORCE=1 LOCAL=1 bash scripts/restore_drill.sh                        # destructive: in-place
```

Measured result:

| Variant | Dump (72 KB, `-Fc`) | Drop+create | `pg_restore` | Total | Verify |
|---|---|---|---|---|---|
| Safe (scratch `palimpsest_verify`; primary untouched) | 0.21 s | 0.30 s | 0.62 s | 1.15 s | 391/391 rows, 20/20 tables match ✅ |
| Destructive in-place (`DROP DATABASE palimpsest`) | 0.15 s | 0.36 s | 0.50 s | 1.02 s | 391/391 rows, 20/20 tables match ✅ |

Per-table rowcounts before == after (both runs): `agent`=3, `claim`=49,
`claim_confidence_current`=49, `claim_event`=49, `claim_status_current`=49,
`claim_support`=49, `entity`=8, `entity_external_id`=8, `entity_name_search`=11,
`entity_summary`=8, `flyway_schema_history`=2, `import_run`=2, `outbox`=74,
`relation_type`=9, `source`=4, `source_record`=17 (plus empty
`calibration_run`, `claim_calibration`, `entity_canonical`, `er_candidate`).
`pg_restore --exit-on-error` completed clean (no errors) both times; post-drill
the primary answered `claims=49, entities=8`.

**What did NOT run first time / what remains unexercised.** Nothing failed — both
variants passed on the first attempt. But two parts of the *cluster* drill were
**not** exercised here, and are flagged NOT DONE:

- The **k8s wrapper** — `kubectl exec` into the `palimpsest-postgres` pod and the
  **engine scale-to-0 quiesce** — cannot run without a cluster (no
  `kind`/`kubectl` in this environment). That is the default (non-`LOCAL`) path of
  the script; it is authored but unrun here and should be exercised inside the
  kind smoke environment on a Docker-capable runner.
- Scale: the corpus is the 391-row synthetic fixture, not a real SDFB-scale dump.
  Timings at real scale are unmeasured until the dump is supplied.
