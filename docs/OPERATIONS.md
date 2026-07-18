# OPERATIONS — deploy, ingest, adjudicate, back up, restore

**Audience:** P3 (the data steward). Filled at WP5. Created at WP0 with audience
stated (HANDOFF §7.4).

Covers: one-command local deploy on kind; running an ingest and reading the run
report; the adjudication queue; reading the Grafana dashboards; the `pg_dump`
backup CronJob and the **exercised restore drill**; responding to SLO breaches
(projector/outbox lag, API latency). **No Ingress** — access is port-forward
only, stated next to the reason.
