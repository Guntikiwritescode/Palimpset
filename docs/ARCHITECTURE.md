# PALIMPSEST — System Architecture Specification

**Version 1.4 · 2026-07-18 · Status: In owner review — final specification pass. Pressure-tested in three rounds (§17) and adversarially reviewed (§20). Awaiting owner ratification; implementation begins at WP0 on release.**

---

## 0. How to read this document

**Audience.** The implementer is Claude Code, working in a repository with no memory of the design conversations. This document must therefore be self-contained: it specifies the system front to back — every component, every interface, every request path, hop by hop — precisely enough to implement without guessing. The human owner architect-reviews; Claude Code implements; nothing in this document is code, by direction. Interface shapes, endpoint tables, and numbered flows are specification, not implementation.

**Changelog.** v1.1 added the Product & UX layer: personas and end-to-end user journeys (§12), the full screen-level explorer specification with rendering grammars and microcopy rules (§13), the demo storyboard and user-documentation plan (§14), and the feature-triage register (§15). Supporting deltas: six new read/export endpoints (§5.1), the `import_run` table (§6.3), Flow H (§4), expanded WP4/WP6 (§9). **v1.2 added no features at all — by design.** It deepens what exists: the honesty page specified in full (§16), a pressure-test of the journeys against the real loaded corpus that found and fixed two product-breaking defects (§17), failure modes and the degradation ladder (§18), and a verifiable Part 1 definition of done with named fixtures (§19). Corrections applied from §17 touch §3.4, §11, §13.2, §13.3, and §15. **v1.3** adds two further pressure-test rounds: an external desk check of the SDFB source that corroborated the critical date-code interpretation and corrected two mapping decisions (§17.2), and a write-path review that found nine defects in the action model (§17.3). Corrections touch §5.1, §6.3, §11, §13.2, and WP2/WP6/WP7. **v1.4** adds the adversarial review of the plan itself (§20): ten attacks, of which one is critical — the Part 1 corpus contains no competing claims, so the thesis has nothing to demonstrate until a second source lands — plus the resulting resequencing, two milestone names, a synthetic fixture, and a scheduled user session. Per §20 A10 this is the **final full specification pass**; further detail is produced inside work packages.

**Document precedence.** Where documents disagree, the order of authority is:

1. `contracts/claim.schema.json` (interchange) and `contracts/claim-schema.sql` (store) — the frozen contracts, already executed and validated against real data.
2. This document.
3. `PALIMPSEST-Part-1-Core-Platform.md` (the product spec) and `PALIMPSEST-ADRs-001-003.md`.
4. The Phase 0 worklog and adapter spec (background and measured facts).

**Governing assumptions.** ADR-001 (PostgreSQL as system of record, graph as projection), ADR-002 (contract-first; Python pipeline; Java 21 engine from first commit; no throwaway services), ADR-003 (local kind now, standing cluster deferred), and the correspSearch-for-EMLO amendment are treated as **ratified by conduct** — the owner directed implementation to begin without objecting to them. If the owner vetoes any, this document is revised before the affected work package starts. Three further decisions are made in this document by the architect and are similarly vetoable: **ADR-004** (the engine owns *all* writes, including bulk import — §3.3), **ADR-005** (single-scholar identity model for Part 1 — §7), **ADR-006** (transactional-outbox projections, in-engine first, external operator later — §3.4).

**Ground truth carried forward.** All measured figures cited here come from the Phase 0 validation run of 2026-07-17 against the real SDFB 2017-10-13 dump and a real PostgreSQL 16.14 instance: 15,882 person entities; 261,177 claims (all validating against the interchange schema with 0 errors); 187,482 source records; the signature query returning 29 ties at confidence ≥ 0.60 and 13 at ≥ 0.90 for Francis Bacon (entity of SDFB id 10000473), possibly active in 1600, in 1–5 ms. Data-quality findings: 365 `odnb_id="0"` sentinels, 14 legitimately repeated ODNB ids, 1,575 temporally inverted relationship bounds, 1 dangling relationship endpoint, 6 unparseable years. These numbers are reused below as acceptance-test fixtures — they are measured facts, not aspirations.

**Explicitly unresolved items** (listed fully in §11) must not be "resolved" by implementation improvisation. The two most important: the SDFB **license is unconfirmed** (development may proceed; *publication and any public exposure may not*), and the SDFB **date-code semantics are an interpretation**, not yet confirmed against the SDFB source code.

---

## 1. System overview

PALIMPSEST is an uncertainty-aware, bitemporal, provenance-complete knowledge graph over early-modern Europe (1500–1700), built as a claim store: every relationship and attribute is a time-scoped, source-backed, confidence-weighted Claim; contradictory sources yield coexisting claims; all change flows through an attributed, append-only event log. The runtime is small and deliberate — one database, one engine, one static frontend, a batch pipeline, and a thin projection loop — deployed on Kubernetes with real observability.

```
                       ┌─────────────────────────────────────────────┐
                       │              EXPLORER (React/TS)            │
                       │  search · entity view · ego-network canvas  │
                       │  confidence slider · evidence panel · about │
                       └──────────────────┬──────────────────────────┘
                                          │ generated TypeScript SDK (from OpenAPI)
                                          ▼
   ┌──────────────┐   NDJSON    ┌─────────────────────────────────────┐
   │   PIPELINE   │  batches    │        ONTOLOGY ENGINE (Java 21)    │
   │   (Python)   ├────────────▶│  REST API v1 · import · actions     │
   │ harvesters + │  POST       │  invariants · outbox · projector    │
   │ adapters +   │  /import    └───────┬──────────────────┬──────────┘
   │ submitter    │                     │ JDBC             │ outbox poll
   └──────┬───────┘                     ▼                  ▼
          │ reads               ┌──────────────────────────────────────┐
          ▼                     │      CLAIM STORE (PostgreSQL 16)     │
   external sources             │ claim · claim_event · source_record  │
   (SDFB dump, correspSearch    │ entity · materializations · outbox   │
    API, WHG API, ITRDB…)       └──────────────────┬───────────────────┘
                                                   │ read-only role
                                          ┌────────▼─────────┐
                                          │ ANALYTICS WORKERS│  (Python; C++ kernel
                                          │ ER · calibration │   and Go recompute
                                          │ MC · bias models │   operator in Phase 3)
                                          └──────────────────┘
   Cross-cutting: OpenTelemetry → collector → Prometheus/Grafana · Kustomize manifests · kind
```

Seven planes, one sentence each. **Contracts** define the claim shape twice — interchange (JSON Schema) and storage (DDL) — and everything programs against them. The **pipeline** turns external sources into interchange claims and submits them. The **store** holds immutable claims, the event log, verbatim source records, and derived materializations. The **engine** is the sole write authority and the only API anyone calls. The **projector** keeps derived views fresh from a transactional outbox. **Analytics** read the store (read-only) and contribute conclusions back *as claims* through the engine. The **explorer** renders evidence-transparent, confidence-filtered views for a non-expert.

Runtime inventory:

| Process | Language | Replicas (P1) | State | Talks to |
|---|---|---|---|---|
| `palimpsest-engine` | Java 21 | 1 | stateless (DB-backed) | PostgreSQL |
| `palimpsest-explorer` | static TS/React via nginx | 1 | none | engine (browser-side) |
| `pipeline` CLI / Jobs | Python 3.12 | on-demand | run manifests on disk | sources, engine |
| `analytics` Jobs | Python 3.12 | on-demand (P2+) | none | PostgreSQL (RO), engine |
| PostgreSQL 16 | — | 1 (StatefulSet) | PVC | — |
| otel-collector / Prometheus / Grafana | — | 1 each | Prom TSDB PVC | all of the above |

---

## 2. The contracts

**`contracts/claim.schema.json` (v0.1.0)** governs every claim that crosses a process boundary: adapter → engine import, action requests that create claims, analytics write-back. Entity references are authority-qualified external ids (`{"authority":"sdfb","id":"10000473"}`); the engine resolves them. Confidence is either `source_native_scalar` (with `scale`, `raw`, `point`∈[0,1], `calibrated:false`) or `unscored` (no `point` permitted — no invented numbers). Fuzzy valid time is the four-date model with the source's native encoding preserved verbatim under `original`.

**`contracts/claim-schema.sql`** governs storage; it executed clean on PostgreSQL 16.14 and holds all Phase 0 data. Its non-negotiable properties: `claim` rows are immutable; `claim_event` is append-only and is the system-time history; `claim_status_current` is a materialized fold of events, never independently authoritative; every claim has ≥ 1 `claim_support` row to a `source_record` carrying the verbatim upstream payload; `source.license_confirmed` defaults false and gates publication; `entity_external_id` holds only identity-bearing 1:1 authority anchors (for SDFB data, only the `sdfb` authority) — many-to-one cross-references such as ODNB are `has-external-id` *claims*.

**Change control.** Contracts change only by explicit owner-approved migration: JSON Schema by semver (adapters pin the version they emit; the engine validates and rejects unknown majors), DDL by numbered Flyway migrations (V1 = the existing file, verbatim). This architecture requires one additive migration, **V2**, specified in §6.3; anything further requires a deviation record (§10). CI enforces the contracts on every commit (§8).

**Derived contracts.** The engine's OpenAPI description (generated from its annotations) is the API contract; the TypeScript SDK is generated from it in CI and is never hand-edited. Direction of truth: DDL/JSON Schema → engine → OpenAPI → SDK → explorer.

---

## 3. Component catalog

### 3.1 Pipeline (Python 3.12)

**Responsibility.** Everything between "a source exists in the world" and "valid interchange claims are submitted to the engine": harvesting (fetching with retries/caching), adaptation (source schema → claims, per the adapter interface), quality accounting (anomaly counters), and submission (batched NDJSON to the engine with a run manifest). It owns no database access — per ADR-004 it writes only through the engine — and it never invents data: sentinels, dangling references, inverted ranges and the like are counted and surfaced, never patched over.

**The adapter interface** (Part 2 §5.3, adopted now — this is deliberately the same interface the Roman expansion will use later): each adapter implements `map_to_claims(source_record) → [Claim]`, `register_types() → [RelationType]`, `register_authorities() → [Authority]`, `normalize_names(...)`, and `map_uncertainty(...)` (native doubt → fuzzy dates / confidence objects). Adapters are pure with respect to I/O: harvesters fetch, adapters transform, the submitter ships. The Phase 0 `sdfb_adapter.py` is the semantic reference for the SDFB mapping (its rules are normative: `odnb_id` ∈ {"", "0"} means absent; relationships with unknown endpoints are skipped and counted; inverted derived bounds are dropped with originals preserved; symmetric edges are canonically ordered by numeric id; duplicate pairs are kept as separate claims); the production adapter re-implements it as a tested package, not by importing the prototype.

**Run manifest.** Every pipeline run writes a manifest (run id, adapter+version, source slug+version, input content hashes, counts emitted, anomaly counters, submission receipt) so any claim in the store can be traced to the exact run that produced it. The engine stores the run id it was given on each import batch.

### 3.2 Claim store (PostgreSQL 16)

**Responsibility.** Durable truth: claims, events, entities, sources, source records, support links — plus derived materializations (status fold, canonical map, entity summaries, confidence-current, calibration tables, outbox) that are always reconstructible from the base tables. Writers: the engine only (plus Flyway at deploy). Readers: engine (RW role), analytics (RO role), nobody else. Full table inventory and the V2 migration are in §6.

### 3.3 Ontology engine (Java 21) — the sole write authority

**Responsibility.** The one process that writes the store, so the invariants live in exactly one codebase. It serves the read API (entity views, claim detail, evidence, history, network/slider queries, search, metadata, stats), the bulk import path, and (Phase 2) the governed actions. It emits outbox rows transactionally with every write and runs the in-process projector (ADR-006). **ADR-004 rationale:** the Phase 0 direct-DB loader proved the schema but duplicated invariant logic outside the engine; in production that guarantees drift. Bulk import therefore becomes an engine endpoint that validates against the JSON Schema server-side and performs the same transactional writes (claim + support + assert event + status row + outbox) the interactive path uses, with COPY-based batching internally for throughput. The Phase 0 loader is retired to a `tools/` directory as a validation utility, clearly marked non-production.

**Engine invariants** (enforced in one place; every one is testable):

| # | Invariant |
|---|---|
| I1 | `claim` rows are never updated or deleted. |
| I2 | Every write to any base table happens in a transaction that also appends the corresponding `claim_event`(s) and outbox row(s). |
| I3 | `claim_status_current` is exactly the fold of `claim_event` for each claim; the engine recomputes the affected row in the same transaction as the event. |
| I4 | Every claim has ≥ 1 support link at creation; imports lacking resolvable support are rejected, not defaulted. |
| I5 | Unscored confidence is represented as absence (`confidence_point NULL`), never as 0, and never passes a numeric threshold filter. |
| I6 | Entity references that cannot be resolved (unknown authority id) reject the containing claim with a per-line error; the engine never fabricates entities as a side effect of relations. (Entities are created only by explicit entity records in an import, or by explicit action.) |
| I7 | Effective confidence resolution order is: manual `adjust_confidence` (latest event) → active calibration run value → source-native point → unscored. (§6.2.) |
| I8 | Responses that include source-derived content carry the source's `license_confirmed` flag so the UI can badge unpublishable material. |

### 3.4 Projector (in-engine, Phase 1) → recompute operator (Go, Phase 3)

**Responsibility.** Consume the transactional outbox in order (batched `SELECT … FOR UPDATE SKIP LOCKED`), and maintain: `entity_summary` (display name by the deterministic rule in §17.F1 — *not* "highest confidence," which is undefined when every name claim is unscored; life-date bounds from `born`/`died`; degree counts split scored/unscored), the trigram search index over summaries, and cache invalidation for any memoized network responses. Lag (outbox rows unprocessed, age of oldest) is a first-class metric with an SLO. **ADR-006 rationale:** an outbox is transactional and replayable, requires no extra infrastructure, and maps directly onto the spec's change-data-capture indexer; Postgres logical decoding and the external Go operator (which additionally triggers heavy recompute jobs — analytics artifacts, snapshot rebuilds) are Phase 3 evolutions that consume the same outbox, so nothing is thrown away.

### 3.5 Analytics workers (Python; C++ kernel in Phase 3)

**Responsibility.** Batch jobs reading the store with a read-only role and contributing conclusions back **as claims** via the engine (`method=inferred`, with `method_detail` identifying algorithm and parameters) or as versioned artifacts (calibration runs, snapshot files). Part 1 scope: entity resolution (blocking → Fellegi–Sunter baseline → collective features; emits `same-as` claims plus an adjudication queue), calibration (reliability-diagram-driven mapping from source-native scores to calibrated probabilities, written as calibration runs — §6.2), Monte Carlo propagation and survivorship-bias-aware inference (Phase 3, per the product spec §10). Hard validation rules restated as architecture: temporal forward-chaining and spatial-block validation only — naive random splits are forbidden on this data; every model's evaluation protocol is written down next to its results.

### 3.6 SDK (generated TypeScript)

**Responsibility.** A typed client generated in CI from the engine's OpenAPI document; the explorer's only path to the API. Hand-editing is prohibited; a CI job regenerates and fails on drift. Versioned in lockstep with the API (`v1`).

### 3.7 Explorer (React + TypeScript)

**Responsibility.** The operator-grade surface for a non-expert: search → entity view → ego network with the confidence slider, time window, and possibly/certainly mode → evidence panel down to the verbatim source record → (Phase 2) dispute/assert actions and history. It renders uncertainty honestly: unscored claims are a visually distinct class, never silently mixed into scored filtering; competing claims are shown side by side; every displayed fact is one click from its evidence. URL query parameters are the canonical UI state (`?c=0.7&y0=1600&y1=1600&mode=possibly&unscored=0`) so any view is shareable and reproducible. Full view specs in §5.5.

### 3.8 Infrastructure & observability

**Responsibility.** kind cluster, Kustomize manifests, Flyway-migrated Postgres StatefulSet with scheduled `pg_dump` backups, OTel instrumentation in engine and pipeline exported through a collector to Prometheus/Grafana dashboards, defined SLOs, CI/CD from lint to a kind end-to-end smoke test. Details in §5.6 and §8. Until the SDFB license is resolved, **no Ingress is created**; access is `kubectl port-forward` only — the license gate is enforced at the network layer, not by good intentions.

---

## 4. End-to-end flows, hop by hop

These numbered walkthroughs are the "line by line" of the system: each step names the component, the exact interface crossed, and the data shape in flight.

### Flow A — Ingestion: a source row becomes queryable claims

1. Operator (or CronJob, later) runs `pipeline ingest sdfb --data-dir … --run-id R`.
2. **Harvester** verifies input files against recorded content hashes (SDFB is a static dump; API sources fetch with ETag/backoff) and hands raw records to the adapter.
3. **Adapter** (`map_to_claims`) transforms each row into interchange objects: entity records (`{ref, entity_type, external_ids}`) and claims conforming to `claim.schema.json` v0.1.0, applying the normative SDFB rules (§3.1) and incrementing anomaly counters (`odnb_zero_sentinel`, `valid_time_inverted_dropped_bounds`, `relationship_dangling_endpoint_skipped`, `bad_year_value`).
4. **Adapter self-validation**: every emitted claim is validated against the pinned JSON Schema locally; a nonzero invalid count aborts the run before anything is submitted.
5. **Submitter** streams NDJSON to the engine: first `POST /api/v1/import/batches` with `kind=entities`, then `kind=claims`, in batches of 5,000 lines, headers `Content-Type: application/x-ndjson`, `X-Palimpsest-Run: R`, `X-Palimpsest-Source: sdfb-2017-10-13`, `Authorization: Bearer <scholar token>`.
6. **Engine importer**, per batch, in one DB transaction: (a) re-validates each line against the schema (server trusts no client); (b) resolves entity refs via `entity_external_id`; (c) upserts `source_record` by `(source_id, record_kind, external_id)` — an identical `content_hash` marks the line **duplicate/no-op**, a differing hash marks it **superseding** (new record row; V2 adds `supersedes_record_id`); (d) inserts claims (COPY internally), support links, one `assert` `claim_event` per claim, `claim_status_current='asserted'`, and outbox rows; (e) records per-line rejects (unresolvable ref, schema failure) without aborting the batch.
7. **Engine importer** responds `202` with a machine-readable report: `{run, batch, received, inserted, duplicates, superseded, rejected:[{line, reason}]}`. The submitter aggregates reports into the run manifest; any reject > 0 exits nonzero for operator review.
8. **Idempotency guarantee:** resubmitting the same input produces `inserted=0, duplicates=N` — verified in acceptance tests by running the full SDFB import twice.
9. **Projector** drains the outbox: recomputes `entity_summary` rows for touched entities, refreshes the search index entries, bumps the ingestion-freshness metric.
10. The data is now queryable through every read endpoint; Flow B works immediately.

### Flow B — Read: the signature confidence-slider interaction

1. User is on `/entity/15429` (Francis Bacon, the philosopher) and drags the confidence slider from 0.60 to 0.90.
2. **Explorer** debounces 250 ms, updates the URL to `?c=0.9&y0=1600&y1=1600&mode=possibly&unscored=0`, and calls the SDK.
3. **SDK** issues `GET /api/v1/entities/15429/network?minConfidence=0.9&windowStart=1600-01-01&windowEnd=1600-12-31&temporalMode=possibly&includeUnscored=false&limit=500`.
4. **Engine** validates parameters (dates parse, 0 ≤ minConfidence ≤ 1, mode ∈ {possibly, certainly}), then executes the network query pattern proven in Phase 0: asserted claims whose relation type is entity-ranged, touching entity 15429, with **effective confidence** (I7) ≥ 0.9, and `possibly_active(bounds, window)`; joined to counterpart `entity_summary` rows.
5. **Engine** responds with the envelope: `{"data": {"focus": <entity summary>, "edges": [{"claimId", "counterpart": <entity summary>, "predicate", "confidence": {"effective": 0.97, "origin": "source_native", "raw": 97, "calibrated": false}, "validTime": {...}, "scored": true}, …]}, "meta": {"requestId", "count": 13, "truncated": false}}`.
6. **Explorer** diffs the edge set against the previous render and animates exits — the visible "weakly attested links disappear" beat: 29 edges at 0.60 become 13 at 0.90 (measured fixture).
7. User clicks an edge → Flow C.

### Flow C — Evidence drill-down: from rendered edge to verbatim source bytes

1. Explorer opens the evidence panel and calls `GET /api/v1/claims/{claimId}/evidence`.
2. Engine returns the claim in full (predicate, subject/object summaries, fuzzy valid time **with `valid_time_original`**, confidence object with provenance chain and `calibrated` flag, method + `method_detail`) plus each support link expanded: source metadata (slug, title, version, retrieval URI, **`license`, `license_confirmed`**) and the `source_record` (`record_kind`, `external_id`, `content_hash`, `raw` verbatim JSON).
3. Explorer renders: the human-readable claim; a provenance strip ("imported from sdfb-2017-10-13 · SDFB max_certainty 97 · uncalibrated"); the raw record in a collapsible viewer; and a **license badge** — `license_confirmed=false` renders "source license unconfirmed — internal use only" (I8).
4. Nothing in this panel is synthesized: every field is stored data; absence renders as absence.

### Flow D — Write-back: a scholar disputes a claim (Phase 2)

1. In the evidence panel the scholar clicks **Dispute**, enters a reason, and the explorer calls `POST /api/v1/actions/dispute` with `{"claimId": 90871, "reason": "ODNB entry does not support this tie"}` (actor identity from the bearer token — ADR-005).
2. Engine validates: claim exists; current status permits dispute (asserted → disputed; disputing a superseded claim is a 409).
3. In one transaction: append `claim_event(dispute, actor, {reason})`; recompute `claim_status_current` for that claim (I3); write outbox row.
4. Response `200` with the new status and the event id; the explorer flips the claim's badge to *disputed* and shows it struck-through-but-visible in networks (disputed ≠ deleted — the UI default excludes disputed edges from the canvas but lists them in the side panel; a "show disputed" toggle exists).
5. `GET /api/v1/claims/90871/history` now returns the ordered event list — the audit trail: assert (import run R, timestamp) → dispute (scholar, reason, timestamp).
6. Supersede follows the same skeleton with a twist: the request carries a full replacement claim in interchange shape; the engine creates the new claim (Flow A steps 6c–6d semantics, `method=manual`), then appends `supersede` to the old one with `{superseded_by: <new id>}`, all in one transaction.

### Flow E — Bitemporal "as-of" read (Phase 2)

1. Any read endpoint accepts `asOfSystem=<timestamp>`.
2. Engine switches from the current-tables path to the log path: claims restricted to `recorded_at ≤ T`; per-claim status computed as the latest `claim_event` with `occurred_at ≤ T` (claims with no event by T do not exist yet); effective confidence resolved from adjustments/calibration runs active at T.
3. Responses carry `meta.asOfSystem=T` so the UI can banner "viewing the database as it stood on T". Current-state materializations are never consulted on this path — they answer only "now".

### Flow F — Entity resolution batch (Phase 2)

1. `analytics er-run --config …` reads (RO role) candidate features: names/aliases from claims and `source_record.raw`, life-date bounds, shared correspondents/places.
2. Blocking generates candidate pairs; the Fellegi–Sunter baseline scores them; scores map to match probabilities on a held-out, hand-labelled gold set (gold-set tooling is part of this WP: sampling UI/CSV, two-way labels, stored under version control).
3. High-confidence matches are submitted through the engine as `same-as` claims (`method=inferred`, confidence = model probability, support = a `source_record` describing the ER run inputs). Mid-band pairs go to the **adjudication queue**: `GET /api/v1/er/queue` lists them; the explorer's adjudication view shows both entities' evidence side by side.
4. A scholar accepts → `POST /api/v1/actions/merge-entities {"memberEntityIds":[a,b], "rationale": …}`: the engine records an accepted `same-as` (manual) and recomputes `entity_canonical` for the affected connected component (canonical id = lowest entity id, deterministic).
5. Undoing a merge = disputing the `same-as` claim; the canonical recompute separates the component. (A true split of one over-merged *source* entity uses the supersede-and-recreate pattern — specified, deferred beyond Part 1 unless the data demands it.)
6. Reads may request `resolution=canonical` to fold same-as components into one node (edges re-attributed, membership listed); default is `resolution=raw`. Precision/recall at the chosen operating point is reported on the honesty page.

### Flow G — Incremental recompute (Phase 3, Go operator)

1. The operator consumes the same outbox beyond the engine's light projections: claim/entity changes mark derived artifacts (network snapshots, centrality distributions, calibration inputs) dirty via dependency tags.
2. It debounces, then launches Kubernetes Jobs that recompute only dirty artifacts; job status, queue depth, and staleness are exported metrics with an SLO ("derived views ≤ X minutes stale after write").
3. Until Phase 3, the same guarantee is met trivially: analytics jobs are run manually after imports, and the projector's lag metric covers the light views.

### Flow H — Find connection: bounded path search (Phase 2)

1. On an entity page the user clicks **Find connection…**, picks the target via the same entity search, and the explorer navigates to `/paths/:from/:to` carrying the current confidence/window/mode state.
2. **SDK** issues `GET /api/v1/paths?from=A&to=B&minConfidence=0.7&windowStart=…&windowEnd=…&temporalMode=possibly&maxDepth=4&maxPaths=3`.
3. **Engine** runs a bounded bidirectional breadth-first search over asserted, entity-ranged claims that pass the effective-confidence threshold and the temporal predicate — implemented as batched indexed neighbor queries per frontier (not one giant recursive query), so the 2-second budget and node cap are enforceable mid-search. Edge cost is hop count in v1; confidence-weighted cost is reserved as a parameter.
4. **Engine** responds with up to `maxPaths` shortest paths, each an alternating chain `[entity, claimId, entity, …]` with entity summaries, per-edge effective confidence, and the path's **weakest link** (its minimum edge confidence) — plus `truncated`/`budgetExceeded` flags.
5. **Explorer** renders each path as a chain with the weakest link visually marked; the confidence slider stays live, and raising it re-queries — the second demo beat: *watch the connection break as the evidentiary bar rises*.
6. Empty result renders honestly: "No path at ≥ 0.7 within 4 hops in this window — lower the threshold, widen the window, or accept that the surviving record doesn't connect them."

---

## 5. Service specifications

### 5.1 Engine (`services/engine/`) — Java 21

Recommended frame: current stable Spring Boot with springdoc for OpenAPI generation, Flyway for migrations, JDBC (plain or JDBI-style) rather than heavyweight ORM — claims are immutable rows and the SQL patterns are explicit; Testcontainers for integration tests. Deviations here are implementation freedom *within* the constraint that OpenAPI generation, migrations-as-code, and container-based integration tests exist.

Module layout (responsibilities, not files-as-code):

```
services/engine/src/main/java/dev/palimpsest/engine/
  api/         controllers + DTOs + problem-json error mapper + pagination
  domain/      Claim, EntityRef, FuzzyInterval, Confidence (effective-resolution I7),
               ActionType, status fold logic — pure, framework-free, unit-tested
  store/       repositories per aggregate; SQL lives here; Flyway resources (V1 = contracts DDL verbatim, V2 = §6.3)
  importer/    NDJSON streaming reader, schema validation (pinned contract),
               ref resolution, batched COPY writes, per-line reject report
  projector/   outbox consumer loop; entity_summary + search materializers
  actions/     Phase 2 handlers (dispute/supersede/merge/…): validate → events → folds → outbox
  config/      auth filter (bearer token), CORS for explorer origin, OTel wiring
```

**API v1.** All under `/api/v1`; JSON; envelope `{"data": …, "meta": {"requestId", "asOfSystem"?, "page"?: {"cursor", "limit", "nextCursor"?}}}`; errors are `application/problem+json` with `type/title/status/detail/instance/requestId`; cursor pagination (opaque cursor over stable sort key, default limit 50, max 500); all list endpoints accept `asOfSystem` from Phase 2.

| Method & path | Purpose | Key parameters / body | Notes |
|---|---|---|---|
| GET `/entities/{id}` | Entity view | `resolution=raw\|canonical` | summary + attribute claims grouped by predicate, each with confidence + support counts; per-source contribution counts and scored/unscored tallies (feeds the coverage panel, §13.2) |
| GET `/entities/lookup` | Resolve external ref | `authority`, `externalId` | 404 if unknown; used by pipeline tools and deep links |
| GET `/entities/{id}/claims` | Claims touching an entity | `predicate?`, `role=subject\|object\|any`, `status=asserted\|disputed\|superseded\|any` (default asserted), `minConfidence?`, `includeUnscored=bool`, `windowStart/windowEnd?`, `temporalMode=possibly\|certainly`, cursor/limit | the general filtered listing |
| GET `/entities/{id}/network` | Ego network (the slider) | as above minus role; `predicates?` (default: all entity-ranged); `limit` ≤ 500 | returns focus + edges with counterpart summaries; `meta.truncated` when capped; depth fixed at 1 in v1 (`depth` reserved) |
| GET `/claims/{id}` | Claim detail | — | full claim incl. `valid_time_original`, confidence provenance chain |
| GET `/claims/{id}/evidence` | Support expansion | — | Flow C payload incl. source license flags and `source_record.raw` |
| GET `/claims/{id}/history` | Event audit trail | — | ordered `claim_event`s with actors |
| GET `/search/entities` | Name search | `q` (≥ 2 chars), `type?`, cursor/limit | trigram over `entity_summary.display_name` + alias index |
| GET `/meta/relation-types` · `/meta/sources` · `/meta/agents` | Registries | — | sources include `license`, `license_confirmed` |
| GET `/stats/summary` | Corpus stats | — | entity/claim/source-record counts, confidence histogram, anomaly counters — feeds the honesty page |
| POST `/import/batches` | Bulk import | NDJSON body; `kind=entities\|claims` (query or header); run headers | Flow A semantics; 202 + per-batch report; auth required |
| POST `/actions/assert-claim` | Manual claim (P2) | interchange claim body | `method=manual`; same invariant path as import |
| POST `/actions/dispute` / `/actions/undispute` | Status change (P2) | `{claimId, reason}` | Flow D; 409 on illegal transitions |
| POST `/actions/supersede` | Replace (P2) | `{claimId, replacement: <interchange claim>, reason}` | creates new claim + supersede event atomically |
| POST `/actions/adjust-confidence` | Manual override (P2) | `{claimId, confidence: <confidence object>, reason}` | event + `claim_confidence_current`; never touches the claim row |
| POST `/actions/merge-entities` | Accept ER match (P2) | `{memberEntityIds:[…], rationale}` | Flow F step 4 |
| GET `/er/queue` | Adjudication queue (P2) | cursor/limit | mid-band `same-as` candidates with both entities' evidence |
| GET `/entities/{a}/relations/{b}` | Pair dossier | `status=asserted\|any` (default any, flagged) | every claim linking a and b, any predicate/direction; competing and disputed claims grouped and labelled — the evidentiary record of one relationship |
| GET `/entities/random` | Featured / explore | `type?`, `minScoredDegree?` | uniform over qualifying `entity_summary` rows; powers "Explore someone" |
| GET `/paths` | Find connection (P2) | `from`, `to`, `minConfidence`, `windowStart/windowEnd?`, `temporalMode`, `maxDepth` ≤ 4, `maxPaths` ≤ 5 | bounded bidirectional BFS per Flow H; 2 s budget, node cap, explicit `budgetExceeded` |
| GET `/runs` · `/runs/{id}` | Import-run history | cursor/limit | from `import_run` (§6.3); feeds the honesty page's provenance strip |
| GET `/events` | Audit feed (P2) | `since?`, cursor/limit | ordered `claim_event`s across the corpus, newest first, with actors |
| GET `/export/claims` | Data export (P2) | same filters as `/claims`; `format=ndjson\|csv` | **refuses** content from sources with `license_confirmed=false`, returning 403 that lists the offending sources — the license gate, machine-enforced |
| GET `/healthz` · `/readyz` | Probes | — | ready = DB reachable + migrations current |

Non-functional bounds the engine enforces: request body ≤ 32 MB per import batch; network responses capped at `limit` with explicit truncation flag; path search runs under a hard 2-second budget and frontier node cap, returning partial results with `budgetExceeded=true` rather than timing out opaquely; every request carries/creates a request id propagated into traces and problem responses.

### 5.2 Pipeline (`pipeline/`) — Python 3.12

```
pipeline/
  palimpsest_pipeline/
    adapters/base.py          # the adapter interface (§3.1) — the same one Part 2 reuses
    adapters/sdfb/            # production port of the Phase 0 mapping; rules normative
    adapters/correspsearch/   # WP2b; built after the coverage probe passes (§11)
    harvest/                  # fetchers: local-dump reader; HTTP with ETag/backoff/rate limits
    submit/                   # engine client; NDJSON batcher; run-manifest writer
    quality/                  # anomaly counters → run report + Prometheus pushgateway (or logged metrics)
    schema/                   # loads pinned contracts from repo-root contracts/ (no copies)
    cli.py                    # `pipeline ingest <adapter> …`, `pipeline validate <dir>`
  tests/
    golden/                   # known input rows → expected interchange claims (byte-stable)
    test_sdfb_rules.py        # sentinel, repeats, inverted bounds, dangling edge, CA window — each a named test
```

Golden tests pin the five data-quality behaviors with the measured counts as fixtures on a full-dump run (marked slow) and hand-picked rows for the fast suite. The CLI exits nonzero on any schema-invalid claim or engine reject, printing the per-line report — the pipeline's contract with operators is "silent success or loud, specific failure."

### 5.3 Analytics (`analytics/`) — Python 3.12 (+C++ in Phase 3)

Jobs are containerized CLIs: `er-run`, `er-goldset`, `calibrate`, later `mc-propagate`, `bias-model`. Each writes a run record (inputs, parameters, code version, metrics) before any store write-back; write-backs go through the engine exclusively; evaluation protocols (forward-chaining / spatial blocking) are part of each job's config and echoed into its run record so the honesty page can display them. The C++ kernel, when it lands, is a Python-bound library used by these jobs — not a service.

### 5.4 SDK (`sdk/typescript/`)

CI job: start engine (Testcontainers or compose) → fetch `/v3/api-docs` (OpenAPI JSON) → generate client (an established OpenAPI-to-TS generator; choice is Claude Code's within "typed, fetch-based, tree-shakeable") → typecheck → publish as a workspace package consumed by the explorer. A second job regenerates on every PR and fails on uncommitted drift.

### 5.5 Explorer (`explorer/`) — React + TypeScript

Routes and views (inventory only — the full screen-level specification, states, and grammars are §13):

| Route | View | Contents |
|---|---|---|
| `/` | Search | search box (SDK `/search/entities`, debounced), result list with life-date bounds and type badges; "Explore someone" (random); corpus stats footer from `/stats/summary` |
| `/entity/:id` | Entity | header (display name, fuzzy life dates rendered as ranges, external-id claims with authority badges); **ConfidenceControls**; **NetworkCanvas**; decade strip; coverage panel; attribute claims list; competing claims shown side-by-side whenever >1 claim shares a predicate |
| `/pair/:a/:b` | Pair dossier | the complete evidentiary record between two people: every claim in either direction, grouped by predicate, competing/disputed labelled; entry point from any edge |
| `/paths/:from/:to` | Find connection (P2) | Flow H results: path chains with weakest-link marking, live threshold |
| `/claim/:id` | Claim | full detail + evidence panel + (P2) history timeline + action buttons |
| `/adjudicate` | ER queue (P2) | side-by-side entity evidence, accept/reject → actions API |
| `/about` | Honesty page | what the numbers mean: uncalibrated-confidence explanation, calibration status, ER operating point when live, anomaly counters, import-run history, license status per source, the survivorship-bias caveat — sourced from `/stats/summary`, `/runs`, and `/meta/sources`, not hard-coded |

**ConfidenceControls semantics** (the heart of the product): slider sets `minConfidence` (0–1, step 0.05); year-window control sets `windowStart/windowEnd` (single year expands to Jan 1–Dec 31); mode toggle `possibly | certainly` maps to the two SQL predicates; **unscored toggle** — when on, unscored claims appear as a visually distinct class (dashed edges, separate list section, "unscored" badge) and are *never* ranked among scored edges; when off they are absent. All four controls live in the URL; back/forward and link-sharing reproduce exact views. Interaction mechanics — including the dual-mode (client/server) slider that makes the signature beat instantaneous at typical ego sizes — are specified in §13.4.

**NetworkCanvas:** force-directed ego network, canvas/WebGL-capable library (implementer's choice within: handles 500 edges interactively, supports enter/exit animation for the slider beat, node click navigates, edge click opens evidence). Above 500 edges the engine truncates by effective confidence and the UI shows "showing top 500 — tighten filters," never silently.

Accessibility and honesty floors: controls keyboard-operable; confidence communicated by number and badge, not color alone; every rendered edge/attribute reachable to its evidence in ≤ 2 clicks; disputed claims struck-through-but-visible where shown; empty states state *why* ("no scored ties ≥ 0.9 possibly active in 1600 — 16 unscored ties hidden").

### 5.6 Infrastructure (`deploy/`)

kind cluster, two namespaces (`palimpsest`, `observability`). Kustomize bases + overlays (`local` now; `cloud` overlay is a stub until the ADR-003 checkpoint). Workloads: Postgres StatefulSet (PVC 10 Gi, resource limits, `pg_dump` CronJob to a backup PVC, restore runbook in `deploy/README`); engine Deployment (readiness = `/readyz`; Flyway runs at startup; config via ConfigMap, secrets — DB password, scholar bearer token — via Secret objects); explorer (nginx serving the static build; CORS pinned to its origin in engine config); otel-collector DaemonSet-or-Deployment receiving OTLP from engine and pipeline jobs, exporting to Prometheus; Grafana with provisioned dashboards (API latency by route, import throughput + rejects, projector/outbox lag, DB size + table rowcounts, anomaly counters over time). **No Ingress until the SDFB license is confirmed** — port-forward only, stated in the runbook next to the reason.

SLOs (initial, revisited at Phase 1 exit): p95 `/entities/{id}/network` < 300 ms and p95 read endpoints < 500 ms at Phase 1 scale (justified: the raw SQL measured 1–5 ms at 261 k claims; the budget covers engine overhead ×50); outbox lag < 60 s p99 during steady state; import throughput ≥ 5 k claims/s sustained (Phase 0 direct COPY achieved ~8.6 k/s end-to-end including validation).

---

## 6. Data architecture

### 6.1 Base tables (migration V1 — the existing contract, verbatim)

`agent`, `source`, `source_record`, `entity`, `entity_external_id`, `relation_type`, `claim`, `claim_support`, `claim_event`, `claim_status_current`, `entity_canonical`, functions `possibly_active`/`certainly_active`, view `v_asserted_claim` — semantics as in §2 and the DDL comments. Writer/reader matrix: engine RW; Flyway DDL; analytics RO; nothing else has credentials.

### 6.2 Effective confidence (resolution I7, and why)

Claims are immutable, but belief about them changes on three timescales: bulk recalibration (Phase 3 writes a *calibration run* mapping source-native scores to calibrated probabilities for thousands of claims at once), scholarly overrides (rare, per-claim, audited), and the source-native default. Modeling recalibration as 261 k `adjust_confidence` events per run would bury the audit log in machine noise; modeling manual overrides as calibration rows would hide them from audit. So both exist, with a strict resolution order — manual event (latest) → active calibration run value → source-native `confidence_point` → unscored — implemented in one place in the engine (`domain/Confidence`) and surfaced to clients as a provenance chain (`{"effective":0.83,"origin":"calibration","calibrationRun":"cal-2026-09-r1","raw":97,"manualOverride":null}`), so the UI can always answer "why is this number what it is."

### 6.3 Migration V2 (additive; the only schema change this architecture requires)

| Table | Columns (shape) | Purpose |
|---|---|---|
| `outbox` | `event_seq` bigserial PK, `aggregate` ('claim'\|'entity'\|'source'), `aggregate_id`, `kind`, `payload` jsonb, `created_at`, `processed_at` null | transactional change feed (ADR-006) |
| `entity_summary` | `entity_id` PK→entity, `display_name`, `name_claim_id`, `born_earliest/born_latest/died_earliest/died_latest` dates, `entity_type`, `degree_scored`, `degree_unscored`, `updated_at` | projector-maintained read model for search/results/network counterparts |
| `claim_confidence_current` | `claim_id` PK→claim, `confidence` jsonb, `confidence_point` real null, `origin` ('manual'\|'calibration'\|'source'), `calibration_run_id` null, `updated_at` | materialized I7 result; recomputed on adjust events and calibration activation |
| `calibration_run` | `run_id` PK, `created_at`, `method`, `params` jsonb, `metrics` jsonb, `active` bool (≤ 1 active) | Phase 3 calibration versioning |
| `claim_calibration` | (`run_id`, `claim_id`) PK, `point` real, `distribution` jsonb null | per-claim calibrated values per run |
| `source_record.supersedes_record_id` | nullable self-FK (column addition) | upstream record changed between harvests (Flow A 6c) |
| `er_candidate` | `pair_id` PK, `entity_a`, `entity_b`, `score`, `features` jsonb, `state` ('queued'\|'accepted'\|'rejected'), `same_as_claim_id` null, timestamps | the adjudication queue backing store (P2) |
| `import_run` | `run_id` text PK, `source_slug`, `started_at`, `finished_at`, `batches`, `received`, `inserted`, `duplicates`, `superseded`, `rejected`, `manifest` jsonb | engine-recorded ingest history (written during Flow A); powers `/runs` and the honesty page's provenance strip |
| index additions | trigram (pg_trgm) on `entity_summary.display_name`; `claim_event(claim_id, occurred_at)` if not present; partial indexes supporting the network query | performance for search, as-of, slider |

Rules: V2 contains no destructive change; `claim_status_current` and every V2 materialization must be rebuildable from base tables by a documented engine maintenance command (`rebuild-projections`), which is also the disaster-recovery path and the projector's correctness oracle in tests.

### 6.4 Scale envelope

Design point: 10⁵–10⁷ claims (Phase 1 measured: 261,177). All listed queries are index-served neighborhood scans or key lookups. ADR-001 revisit triggers restated: claims approaching 10⁸, deep-traversal product needs, or SLO breach on neighborhood queries → evaluate graph projection store; until then, no graph database exists in this system.

---

## 7. Security & identity (ADR-005)

Part 1 is a single-scholar system with real attribution, not a multi-tenant product: one bearer token (Kubernetes Secret) authenticates the owner; the engine maps it to a persistent `agent` row (kind=human) and stamps every action event with it; pipeline and analytics authenticate with distinct tokens mapped to their pipeline/model agents — so the audit trail distinguishes human from machine assertions even with one human. No anonymous writes exist; reads require the token too while the license gate holds (defense in depth with the no-Ingress rule). CORS locked to the explorer origin; secrets never in images or manifests (Secret objects only); Postgres roles: `engine_rw`, `analytics_ro`, `migrate` — least privilege enforced at the database, not by convention. Multi-user roles, review workflows, and per-source visibility are explicitly out of scope for Part 1; the event log's actor model is the hook they will attach to.

---

## 8. Testing strategy & CI gates

| Layer | Tests | Gate |
|---|---|---|
| Contracts | JSON Schema self-validation; sample-claims fixture validates; DDL V1+V2 apply cleanly to ephemeral PG 16 | every PR |
| Engine | domain unit tests (status fold, effective confidence, fuzzy-time predicates — property-based where cheap); Testcontainers integration: import → read → action → history round-trips; projector correctness vs `rebuild-projections`; as-of vs log replay | every PR |
| Pipeline | golden-file adapter tests; the five SDFB data-quality rules as named tests; full-dump run (slow suite) asserting the measured counts: 15,882 / 261,177 / 187,482 and anomaly counters 365 / 14 / 1,575 / 1 / 6 | PR (fast) + nightly (slow) |
| SDK | generation succeeds; typecheck; drift check (regenerate == committed) | every PR |
| Explorer | component tests for ConfidenceControls semantics (unscored never numerically ranked; URL round-trip; client/server mode parity — client-filtered result set must equal a server re-query at the same threshold); rendering-grammar conformance against the §13.3 tables; every state in the §13.6 catalog has a test; automated accessibility scan (axe-style) on core routes; one Playwright-style E2E against a seeded engine: search → entity → slider 0.6→0.9 shows 29→13 for the Bacon fixture | PR (components) + E2E in the kind smoke |
| System | kind smoke: deploy full stack → import 1 k-claim fixture → signature query via UI path → assert counts, p95 latency, zero projector lag at rest; chaos-lite: kill engine mid-import batch → verify atomicity (no partial batch) | main branch |

Data-science validation rules (restated as gates for any WP9 model work): forward-chaining temporal splits and spatial blocking only; calibration judged by reliability diagrams on held-out labels; every reported metric ships with its protocol.

---

## 9. Work packages for Claude Code

Sequential unless noted; each ends at a **review checkpoint** — Claude Code stops, posts the acceptance evidence, and waits for architect review before the next WP. Acceptance criteria are the definition of done; "works on my machine" is not.

| WP | Scope | Key acceptance criteria |
|---|---|---|
| **WP0** — Repo & CI scaffold | Monorepo layout as in §5 trees + `contracts/` (the two frozen files, verbatim), `tools/` (Phase 0 scripts, marked non-production), `docs/` (this spec, product spec, ADRs, worklog); CI running the contract gate + linters | CI green on a trivial change; contracts byte-identical to the Phase 0 artifacts; README states the license gate |
| **WP1** — Engine read core | V1+V2 migrations; domain model; read endpoints (`entities`, `claims`, `network`, `search`, `meta`, `stats`, probes); problem+json; pagination; OTel; Testcontainers suite | With Phase 0 data loaded via tools, `network` for entity 15429 returns 29 edges at 0.60 / 13 at 0.90 (1600, possibly); p95 < 300 ms locally; OpenAPI served |
| **WP2** — Import path & pipeline port | `/import/batches` per Flow A; production SDFB adapter + submitter + run manifests + golden tests. **Adapter corrections from §17.2:** preserve the `AF` vs `AF/IN` (and `BF` vs `BF/IN`) distinction rather than collapsing it; drop the invented `CA` ±5 window in favour of year bounds plus an `approximate` marker; emit alias name-claims from `search_names_all`/`aliases` | Full SDFB ingest through the engine reproduces exactly 15,882 / 261,177 / 187,482 with the five anomaly counters at 365 / 14 / 1,575 / 1 / 6; immediate re-ingest: inserted=0; reject report demonstrably actionable (seeded bad fixture); the 229 bare-`AF`/`BF` claims carry bounds distinct from their `/IN` counterparts; no claim contains a `±5` window; searching "Umpherston" finds Helen Alexander (alias round-trip) |
| **WP3** — SDK | OpenAPI → TS generation in CI; drift gate | Explorer-ready typed client; drift check fails on manual edit (demonstrated) |
| **WP4** — Explorer MVP (read-only) | Routes `/`, `/entity/:id`, `/pair/:a/:b`, `/claim/:id`, `/about` (per §16); ConfidenceControls with dual-mode slider (§13.4) **defaulting to 0** (§17.F2); NetworkCanvas; EvidencePanel; decade strip; coverage panel; guided first-run tour; citation-copy; "Explore someone"; license badges; URL-as-state; rendering grammars + microcopy per §13.3/§13.5; state catalog per §13.6; a11y + mobile floors per §13.7. **Ends with the §17 journey walk against the then-current corpus.** *Stretch:* compare drawer for disambiguation (J2) | E2E: Bacon slider beat via UI, instant in client mode; pair dossier shows every claim between two chosen figures with competing claims grouped; the two Francis Bacons distinguishable within one search interaction; tour completes and never reappears uninvited; grammar conformance spot-checked against the §13.3 tables; **honesty page derives every figure live — a grep for hard-coded statistics fails the WP** (§16); FIX-BACON/FIX-DISAMBIG/FIX-COVERAGE assertions pass (§19); journey-walk findings written up; automated a11y pass clean on core routes; usable at 700 px |
| **WP5** — Deploy & observability | §5.6 in full on kind; dashboards; SLO measurement; backup/restore runbook exercised; kind smoke in CI | One-command local deploy; dashboards show real traffic; restore drill documented with evidence; **no Ingress** — *this completes the product spec's Phase 1* |
| **WP6** — Write-back, bitemporality & connection-finding | Actions (assert/dispute/undispute/supersede/adjust-confidence) **with typed dispute grounds and the §17.3 write-path corrections W1–W4, W8**, history endpoint, as-of reads with the time-travel banner, explorer action UI + history timeline + disputed rendering; **path search** (Flow H) + `/paths/:from/:to` view; `/events` audit feed on the honesty page; license-gated `/export/claims` | Flow D, E, and H round-trips pass; illegal transitions 409; as-of result provably equals log replay in tests; path search honors its 2 s budget and reports `budgetExceeded` rather than hanging; export **refuses** unconfirmed-license sources with an actionable 403 (tested); an `identity`-ground dispute routes to the ER queue rather than flagging the claim; actions submitted from a time-travel context are rejected 409; every aggregate declares its status filter and disputed claims are excluded by default (tested against a seeded dispute); **ends with the §17.3 write-path walk** |
| **WP7** — Entity resolution v1 | Gold-set tooling; blocking + Fellegi–Sunter; `same-as` write-back; `er_candidate` queue + `/adjudicate` view; merge action + canonical recompute; `resolution=canonical` reads. **§17.3 corrections W5–W7, W9:** non-overlapping-lifespan merge warning; evidence snapshot stored in the merge rationale; canonical entities list their member records with a split affordance; inferred claims render their features, score, and run parameters in the evidence panel. Duplicate names are a source-stated false-positive hazard (§17.2 S4) — treat as a prior, not a discovery | Precision/recall reported on the gold set with a stated operating point; the two Francis Bacons remain distinct; a seeded duplicate pair is merged end-to-end through the UI and un-merged by dispute; attempting to merge the two Francis Bacons raises the lifespan warning; an inferred `same-as` shows its features and score, not just the word "inferred"; **ends with the §17.3 write-path walk** |
| **WP2b** — correspSearch adapter *(promoted from WP8 by §20 A1)* | *Gated on the coverage probe, now the project's next external check*: harvest CMIF/TEI, map letters to `corresponded-with` claims + `document` entities, dated directed links; second source proves the adapter interface | Probe report first (letters in 1500–1700 window, license CC-BY 4.0 confirmed in-band); after ingest: cross-source entities carry claims from two sources, visible in the evidence panel; **at least one genuine competing claim exists and renders side by side** (without which PP3 is unfalsifiable); the pair dossier shows more than one claim for at least one pair |
| **WP9** — Phase 3 differentiators | Calibration runs + effective-confidence UI; Monte Carlo distributions over network metrics; survivorship-bias-aware inference; C++ kernel; Go recompute operator; WHG places + map/timeline; ITRDB environment layer | Per the product spec §10/§13; each sub-item lands with its validation protocol; scoped individually at the WP7 review |

**Dependency notes (revised by §20 A1/A4).** The critical path is **WP0 → WP1 → WP2 → WP2b → WP3 → WP4 → user session → WP5 → WP6 → WP7 → WP9.** WP8 is **promoted to WP2b**, immediately after WP2: with a single source there are no competing claims, no cross-source entity resolution, and nothing for the pair dossier or the competing-claims UI to show, so the second source is a prerequisite for the product's thesis rather than a later enrichment. WP3 needs WP1's OpenAPI; WP4 needs WP3; WP5 can proceed in parallel from WP2 onward; WP6–WP7 are post-WP5; nothing in WP9 starts before the WP5 review passes. **WP5 is the portfolio-viable milestone and WP7 the portfolio-strong milestone** — each is an explicit decision point about whether to continue building or to ship what exists.

---

## 10. Review protocol (architect ↔ Claude Code)

One WP = one PR train against `main`. Every PR description must state: which §§ of this document it implements; the acceptance evidence (test output, measured numbers, screenshots for UI); and any deviation. **Deviations from this spec or the contracts are not silently implemented** — they are proposed as a short *deviation record* (context, proposed change, why, blast radius) appended to the ADR file and are blocked until the architect approves; trivial implementation choices this document explicitly delegates (library picks within stated constraints, file naming, internal helpers) need no record. Contract files change only via §2's process with owner sign-off. Mandatory stop-and-review points: end of WP2 (the write path is the riskiest surface), end of WP5 (Phase 1 exit — SLOs, runbook, smoke evidence reviewed against §5.6/§8), and end of WP7 (ER operating point is a judgment call the architect signs). The architect reviews for: invariant preservation (I1–I8), contract conformance, honesty behaviors (unscored handling, license badges, anomaly surfacing), test sufficiency against §8, and scope discipline (nothing from a later WP smuggled in).

---

## 11. Open items — inputs the implementation must not improvise

| Item | State | Blocks | Owner |
|---|---|---|---|
| SDFB dataset license | **Partial evidence found (§17.2), still unconfirmed.** A 2015 post on the project's own Tumblr states SDFB shares data free of charge for **non-commercial** use by other researchers, without warranty, requesting at minimum a citation of their website. That is a public statement of intent, not a located licence file, and it is eleven years old. `license_confirmed` stays **false**. | Any public exposure (Ingress, published datasets, public demo). Not development. | Human owner — ask the project directly, citing the Tumblr statement; a one-line written confirmation closes this |
| SDFB date-code semantics (IN/BF/AF/CA) | **Independently corroborated (§17.2), not yet authoritatively confirmed.** An external RDF converter processing the same CSVs maps `AF/IN` and `BF/IN` identically to this project, and glosses the codes as "after the beginning of" / "before the end of" — which covers 99.7% of intervals. Two divergences found and corrected. Still unconfirmed against SDFB's own code or documentation. Formerly stated as **critical:** measurement shows **99.7% of all relationship claims carry the single code `AF/IN`** (§17.F6), so the entire temporal layer — time filter, decade strip, possibly/certainly distinction — rests on this one interpretation. | Treating any date bound as fact; a correction would invalidate and require re-deriving ~171k claims' bounds (originals are preserved, so re-derivation is cheap — but it must happen before any temporal finding is published) | Owner or Claude Code (desk check of the SDFB Rails repo) — **findings to architect before WP2 merge**, not after |
| Remaining SDFB tables | Not yet obtained. **§17.2 confirmed a richer export exists** than the dump in hand: relationship types with their own confidence, dates, justification and citation; relationship and group categories; person and group notes; and — in that export — day/month date precision and an *alternate* birth/death year per person (a source-native competing claim this project would want). | Typed relationship predicates (currently `associated-with` only); sub-year date precision; source-native competing life dates | Human owner (Folger record download / project request) |
| correspSearch early-modern coverage | API + CC-BY 4.0 verified; volume in 1500–1700 unknown. **Now the project's next external check** (§20 A1): with one source there are no competing claims, so this gates the thesis, not just an enrichment | WP2b start (probe is WP2b step 1) | Claude Code (probe) + architect (go/no-go) |
| EMLO permission | Bulk use prohibited without permission; request drafted | Nothing (correspSearch is the P1 layer); EMLO adapter only if granted | Human owner sends |
| ITRDB/NOAA access details | Not re-verified this session | WP9 environment layer design detail | Verify at WP9 scoping |
| Seshat license (non-commercial) | Known constraint | Any future Seshat overlay must respect it | Architect at WP9 scoping |
| ADR-004/005/006 ratification | Architect-decided herein, vetoable | Respective components if vetoed | Human owner (silence past WP0 = consent) |

---

## 12. Users, journeys, and what the system is *for*

Everything above specifies a correct system. This section specifies a *useful* one. The distinction matters because the failure mode of an uncertainty-first knowledge graph is not incorrectness — it is being technically impeccable and practically inert: a beautiful claim store nobody can get an answer out of.

### 12.1 The three users

**P1 — The Evaluating Reviewer** (a Palantir engineer or hiring manager, five minutes, no domain knowledge, no patience for setup). Wants to know within ninety seconds whether this person can build a real system and reason about hard data. Never reads the README first. Judges: does the thesis land visibly, is the engineering real, does the author know what they don't know. **Design consequence:** the landing experience must be self-explaining and immediately manipulable; the honesty page must be a *feature*, not an apology; nothing may require setup to appreciate.

**P2 — The Working Historian** (an early-modernist, curious but sceptical of "digital" claims, will try to break it in the first two minutes). Tests it by looking up a figure they know intimately and checking whether the system's account matches their expertise. Will immediately spot flattened nuance, will ask "says who?" of every edge, and will abandon a tool that asserts more than it can support. **Design consequence:** every displayed assertion must be one click from its verbatim source; competing claims must be visible as competing; the vocabulary must be theirs (attestation, source, editorial doubt), not ours (nodes, edges, scores). Their killer question — *"where does this come from?"* — must always be answerable, and their second — *"what's missing?"* — must be answerable too.

**P3 — The Data Steward** (the owner, operating the system: ingesting, curating, adjudicating). Needs to know what got loaded, what got rejected and why, what the anomaly counters say, which merges are pending, and what changed since last week. **Design consequence:** run history, reject reports, the adjudication queue, and the audit feed are first-class product surfaces, not log files — the operational layer of the Ontology made visible.

A fourth party is worth naming to exclude: there is **no anonymous public user** in Part 1. The license gate (§11) forbids public exposure, so the system is built for these three and honest about it.

### 12.2 The five journeys, end to end

Each journey names the user, the trigger, the click path, the moment of value, and the specific failure it must not commit.

**J1 — "Show me what this thing does" (P1, 90 seconds).** Lands on `/` → a first-run tour (§13.8) offers one sentence and a button: *"Meet Francis Bacon."* → `/entity/{bacon}` pre-set to a sensible window with the slider at 0.6 → the network canvas renders 29 ties → the tour's second step points at the slider: *"Drag me."* → dragging to 0.90 drops the graph to 13 ties, live and instantly → the third step points at a surviving edge: *"Every line has evidence behind it. Click one."* → the evidence panel opens on the verbatim SDFB row. **Value:** the thesis is understood without a word of documentation. **Must not:** require reading; animate so slowly the beat is lost; or show a spinner between slider positions (hence the dual-mode slider, §13.4).

**J2 — "Is this the Francis Bacon I mean?" (P2, disambiguation).** Searches "Francis Bacon" → results list shows **two** entries with life-date ranges (1561–1626 · philosopher; 1600–1663 · politician) and a scored-tie count each → picks the philosopher → header confirms the identity with its evidence. **Value:** the historian sees the system take identity seriously in the first ten seconds. **Must not:** merge them; present one arbitrarily; or show bare names with no distinguishing context. *(This is why search results carry life dates and descriptions, not just names — the single highest-value detail in the search view.)*

**J3 — "Says who?" (P2, the credibility test).** On an entity page, clicks any edge → evidence panel: the claim in plain language, the provenance strip (*imported from sdfb-2017-10-13 · SDFB max_certainty 97 · uncalibrated*), the verbatim source record, and — critically — the **uncalibrated warning** explaining that 0.97 means "SDFB's own score, normalized," not "97% likely true" → clicks through to the pair dossier (`/pair/:a/:b`) to see *every* claim linking these two people, including any that contradict → optionally copies a citation. **Value:** the historian's scepticism is satisfied by transparency rather than argued away. **Must not:** present a confidence number without its origin; hide contradictions; or make the raw record inaccessible.

**J4 — "What's actually here?" (P2/P1, the coverage question).** From the entity page's coverage panel or the honesty page: which sources contributed to this figure, how many claims are scored vs unscored, what the corpus contains overall, what the anomaly counters found, what the confidence distribution looks like, and what is *known to be missing*. **Value:** the archive's silences are made legible — the project's intellectual core, rendered as UI. **Must not:** imply the graph is complete; bury caveats in prose nobody reads; or state coverage figures that aren't computed from live data.

**J5 — "I disagree" (P2/P3, Phase 2).** Reading evidence, the historian judges a tie unsupported → **Dispute**, with a reason → the claim is struck-through-but-visible, not deleted → the history timeline shows assert (import run, timestamp) → dispute (them, reason, timestamp) → the audit feed on the honesty page records it corpus-wide. **Value:** the graph is an operational system a scholar edits, not a static dataset — the "kinetic" layer made real. **Must not:** delete anything; lose attribution; or let a dispute silently rewrite history rather than appending to it.

### 12.3 The product promises (and what would falsify each)

Six commitments, each testable — an acceptance criterion in §9 exists for every one:

| # | Promise | Falsified by |
|---|---|---|
| PP1 | Every displayed assertion reaches its verbatim source in ≤ 2 clicks | any rendered fact with no path to a `source_record` |
| PP2 | No number appears without its origin and calibration status | a bare "0.97" anywhere in the UI |
| PP3 | Contradictions are shown as contradictions | any silent selection between competing claims |
| PP4 | Absence is rendered as absence | an unscored claim ranked among scored ones; an empty state that doesn't say *why* it's empty |
| PP5 | Any view is a shareable URL that reproduces exactly | a filter state that survives only in component memory |
| PP6 | Unpublishable material is visibly and mechanically gated | license-unconfirmed content exported or exposed without a badge |

---

## 13. The explorer, screen by screen

This section is the UI's specification: what each screen contains, what every visual encoding *means*, what every state looks like, and what the words say. It exists so implementation makes no aesthetic decisions that are secretly epistemic ones.

### 13.1 Search (`/`)

**Purpose:** get to a person in one interaction, and make identity ambiguity visible at the point of choice.

Contents: a single search field (autofocus, debounced 250 ms, minimum 2 characters, results as you type); a result list; an "Explore someone" button (`/entities/random`, `minScoredDegree` set so the result is never a dead end); a corpus footer from `/stats/summary` reading *"15,882 people · 261,177 claims · 1 source · confidence uncalibrated"*; and a link to the honesty page.

Each result row shows, in this order of visual weight: **display name** · life-date range rendered per the fuzzy-date grammar (§13.3) · the description claim, truncated · a **scored tie-count** (*34 ties*). The count lets the user judge which of two same-named figures is the one they mean before clicking. It deliberately does **not** advertise an unscored tie count: in the Part 1 corpus every relationship claim is scored, and a counter that always reads zero teaches the user that the interface is decorative (§20 A3). The unscored/scored distinction lives in the claims list, where unscored attribute claims actually exist.

States: empty (prompt + "Explore someone"); no matches (*"No one matching 'X'. The corpus is early-modern Europe, c. 1500–1700, and currently covers 15,882 people from one source."* — the empty state teaches the corpus's scope); loading (skeleton rows, never a full-page spinner); error (problem-json `detail` rendered plainly with a retry).

### 13.2 Entity (`/entity/:id`) — the primary surface

Five regions, in DOM and visual order:

**(a) Identity header.** Display name; life dates in fuzzy-date grammar; entity-type badge; external-id chips (ODNB etc.) with a tooltip stating these are *claims*, many-to-one and contradictable, not identity keys; a "why this name?" affordance opening the `has-name` claim's evidence (names are claims here, and the header quietly proves it).

**(b) ConfidenceControls.** The slider (**default 0 — see §17.F2; the demo's 0.6 comes from a pre-set URL, not the default**; range 0–1, step 0.05, current value shown numerically and as a plain-language band label — see §13.3), the year-window control, the possibly/certainly toggle, and the unscored toggle. Directly beneath, a **live result line** that states both what is shown and what the mode means: *"Showing 29 ties not ruled out in 1600 · 2 are required by the evidence (highlighted) · 3 carry no dates at all."* Certainty is a **highlight within** the graph, not a filter mode that empties it — only 0.18% of claims can ever be certainly-active, so a certainty toggle would blank the canvas and read as broken (§20 A2). Undated claims are counted separately because `possibly_active` is trivially true for them (§20 A7). This line is the honest summary of exactly what the user is looking at, and it changes as they drag.

**(c) NetworkCanvas.** Force-directed ego network, focus node visually distinct, edge thickness and opacity encoding effective confidence (§13.3), unscored edges dashed when shown. Node click navigates; edge click opens the evidence panel; hover shows a tooltip with counterpart name, predicate, confidence and origin. Truncation above 500 edges is stated, never silent. Below the canvas, a **decade strip**: a small bar chart of how many ties are possibly-active per decade across 1500–1700, click-to-set the window — cheap to compute from bounds already loaded, and it turns the abstract time filter into something a historian can read at a glance.

**(d) Coverage panel.** Per-source contribution counts for *this* entity (*"SDFB: 34 relationship claims, 6 attribute claims"*), the scored/unscored split, and the calibration status. This is J4 at entity scale — the "what's actually here?" question answered locally.

**(e) Claims list.** All attribute claims grouped by predicate. Where a predicate has more than one claim, they render **side by side under a "competing claims" heading** with their evidence and confidence — never collapsed to a winner. Each row: value, confidence chip, source chip, evidence link.

### 13.3 Rendering grammars — visual encodings and their meanings

Every encoding below is a semantic commitment. Implementation may restyle but may not change meaning.

**Confidence.** Effective confidence renders as a chip carrying *number + band label + origin*, never a number alone (PP2). Bands: ≥ 0.90 *very strong* · 0.70–0.89 *strong* · 0.40–0.69 *moderate* · 0.20–0.39 *weak* · < 0.20 *very weak* · unscored *unscored*. Origin is always present: *source-native (uncalibrated)* · *calibrated (run id)* · *manual override* · *inferred (algorithm)*. On the canvas, confidence maps to edge opacity and thickness monotonically; color alone never carries it (accessibility floor, §13.7). The uncalibrated warning appears wherever a source-native number is shown at size — one sentence: *"This is the source's own score, normalized. It has not been calibrated against ground truth, so treat it as a ranking signal, not a probability."*

**Fuzzy dates.** The four-date model renders in a fixed grammar: both bounds known → *1561–1626*; earliest only → *after 1561*; latest only → *before 1626*; year-precise (`IN`) → *1561*; circa (`CA`) → *c. 1561 (±5)* with the window policy stated; unknown → *date unknown* (never a blank, never an invented range). Hovering any date reveals the source's native encoding verbatim (`{"type_code":"AF/IN","year":"1561"}`) — the original notation is never destroyed and never hidden. **Bounds are derived, not stated:** 99.7% of relationship intervals come from one source code whose semantics are still unconfirmed (§17.F6), so any date rendered at size carries a *derived* marker linking to the method note, and life dates — which are unscored claims (§17.F3) — render with an *unscored* chip like any other unscored assertion rather than as bare fact.

**Temporal mode.** *Possibly active* (default) = nothing rules this tie out during the window; *certainly active* = the evidence bounds force it through the window. The toggle carries this as a one-line explanation, because the distinction is the difference between "the record permits" and "the record requires" — a historian's distinction the UI must not blur. Certainly-active is strictly a subset, and switching modes shows the count change (*"13 → 4 ties"*), which teaches the semantics faster than any tooltip.

**Status.** Asserted → normal. Disputed → struck-through, dimmed, with the disputer and reason on hover; excluded from the canvas by default but always listed in the side panel with a "show disputed" toggle. Superseded → dimmed with a link to the superseding claim. Nothing is ever hidden by deletion, because nothing is ever deleted.

**Provenance strip.** A one-line summary under any claim: *method · source · source's own score · calibration status* — e.g. *imported · sdfb-2017-10-13 · max_certainty 97 · uncalibrated*.

**License badge.** Any content from a source with `license_confirmed=false` carries an amber badge: *"Source license unconfirmed — internal use only."* On the honesty page this expands to the full explanation and what it blocks (§11).

### 13.4 The slider, mechanically (the signature interaction)

The beat only works if it is *instant*. Specification: on entity load, the explorer fetches the ego network **once** at `minConfidence=0` including unscored, up to the 500-edge cap, and holds it in memory. Slider drags then filter **client-side** — no network round trip, no spinner, sub-frame response, smooth enter/exit animation. The URL updates (debounced 250 ms) so the view stays shareable. When the initial fetch was truncated at the cap, or the user changes the window or temporal mode (which alter the server-side predicate), the explorer **re-queries the server** and shows a subtle inline loading indicator on the result line, never a canvas blank-out. A test asserts client-filtered results equal a server re-query at the same threshold (§8) — the optimization must never change the answer.

Two details that make it feel designed rather than assembled: the slider shows tick marks at the band boundaries (0.20/0.40/0.70/0.90) so the user feels the semantic bands rather than arbitrary decimals; and exiting edges animate out over ~200 ms rather than vanishing, because *watching* weak evidence fall away is the entire rhetorical point.

### 13.5 Microcopy rules

The system's honesty lives or dies in its sentences. Four rules, and the vocabulary they enforce:

1. **State the limit, then the reason.** *"No scored ties ≥ 0.90 possibly active in 1600. 16 unscored ties are hidden — the surviving evidence here is thin, not absent."*
2. **Never assert what the data doesn't.** "Bacon knew X" is forbidden; *"SDFB records an association between Bacon and X"* is correct. The UI reports what sources say, always attributed.
3. **Use the historian's vocabulary.** Attestation, source, evidence, editorial doubt, competing readings — not nodes, edges, scores, records. (One exception: the honesty page may name the underlying mechanics for the P1 reviewer, clearly marked as the technical view.)
4. **Explain uncertainty once, well, where it appears** — inline, at the point of confusion, not in a documentation page the user will never open.

### 13.6 State catalog

Every view implements all applicable states; each gets a test (§8). Loading — skeletons matching final layout, never full-page spinners. Empty — always states *why*, per rule 1. Truncated — *"Showing the 500 strongest ties of 1,240. Raise the confidence threshold or narrow the window to see fewer, better-attested ones."* Error — problem-json `detail` in plain language, with a retry, plus the request id in small type (it appears in the traces, so a user report is debuggable). Budget-exceeded (path search) — *"Search stopped at its 2-second limit; these are the paths found so far."* Stale — if projector lag exceeds its SLO, a quiet banner: *"Some summaries may be up to N minutes behind recent changes."* Unconfirmed-license — the amber badge, everywhere applicable. Time-travel (Phase 2) — a persistent banner: *"Viewing the database as it stood on 2026-08-01."*

### 13.7 Accessibility, responsiveness, performance floors

Keyboard operability for every control including the slider (arrow keys step by 0.05, Home/End jump to bounds) and canvas navigation (tab through edges in confidence order, Enter opens evidence). Confidence and status communicated by number, label, and shape — never color alone. Screen-reader summaries for the canvas: the result line and an ordered text list of ties are the accessible equivalent of the graph, kept in sync. Focus management: opening the evidence panel moves focus into it; closing returns focus to the edge. Layout usable from 700 px (a reviewer may open it on a laptop half-screen); below that, the canvas collapses to the ranked tie list, which is the honest fallback — a list of attested ties with confidences is the same information without the spatial metaphor. Performance floors: interaction-to-paint under 100 ms for slider drags (client-mode), first meaningful paint under 2 s on the entity route, and a canvas that stays interactive at the 500-edge cap.

### 13.8 First-run tour and citation

**Tour.** A four-step, dismissible overlay on first visit (persisted in `localStorage`; a "replay tour" link lives on the honesty page): (1) *"This is a historical knowledge graph where every link is a claim backed by a source."* (2) *"Drag this slider to filter by how strongly a link is attested."* (3) *"Click any link to see the evidence behind it — down to the original record."* (4) *"Nothing here is presented as certain. The About page explains what the numbers mean."* It must never reappear uninvited, must be skippable in one click, and must not block interaction — a reviewer who ignores it entirely still gets J1 from the pre-set view.

**Citation.** Any claim, pair dossier, or filtered view offers **copy citation**, producing a text block with the claim's assertion, its source (title, version, retrieval URI), the confidence and its calibration status, the accessed date, and the reproducing URL. This is the single cheapest feature that signals the system was built for historians: it makes the tool usable in someone's actual footnotes, and it propagates the uncertainty caveat into whatever they write.

---

## 14. Demo, packaging, and user documentation

### 14.1 The five-minute demo storyboard

The product spec requires a demo recording; here it is as a shot list, timed. **0:00–0:30** — the problem, over the search screen: historical evidence is fragmentary and contradictory; most systems flatten that into false certainty; this one refuses to. **0:30–1:30** — J2: search "Francis Bacon," two figures appear, disambiguate by life dates, land on the philosopher. **1:30–2:30** — the signature beat: the network at 0.60 (29 ties), drag to 0.90 (13 ties), watch weak evidence fall away; then the certainly/possibly toggle to show the semantic distinction. **2:30–3:30** — J3: click an edge, open the evidence, drill to the verbatim SDFB row, read the uncalibrated warning aloud — *"this number is the source's own score, not a probability, and the system says so."* **3:30–4:15** — J4: the coverage panel and honesty page — anomaly counters (365 sentinels, 14 shared ODNB ids, 1,575 inverted date ranges, 1 dangling edge), the license gate, the survivorship-bias caveat. **4:15–5:00** — the architecture in thirty seconds (claim store, immutable claims, append-only event log, engine as sole write authority) and the one-line thesis. The recording ends on the honesty page, deliberately: *the last thing the reviewer sees is the system stating its own limits.*

### 14.2 Repository-facing documentation

Six documents, each with a stated audience and a defined "done": **README** (P1, 5 minutes) — what this is, the one-line thesis, a screenshot of the slider beat, the demo link, quickstart, and the license gate stated plainly. **ARCHITECTURE.md** — this document. **DECISIONS.md** — the ADRs plus deviation records. **DATA.md** (P2/P3) — per source: what it is, what was verified and when, license status, what was mapped, and *every* data-quality finding with its handling; the SDFB date-code interpretation table lives here, explicitly labelled as interpretation. **OPERATIONS.md** (P3) — deploy, ingest, adjudicate, back up, restore, read the dashboards, respond to SLO breaches. **LIMITATIONS.md** (P1/P2) — the honest limits: uncalibrated confidence, single source in Part 1, survivorship bias, the cliodynamics debate and this project's position in it, what the system cannot tell you. The honesty page (`/about`) is LIMITATIONS.md rendered live with real numbers — the same content, one authored once.

### 14.3 The reviewer's ninety seconds

Given the license gate forbids public hosting, packaging must survive "reviewer will not run anything": the README's slider-beat screenshot and the demo recording carry the experience, and a single `make demo` (kind up → migrate → ingest a fixture → port-forward → open) is the path for anyone who *will* run it. The fixture is **synthetic** — invented people and ties reproducing the corpus's shape and anomaly classes, not a subset of SDFB, because a subset of license-unconfirmed data is still license-unconfirmed (§20 A5). It reproduces the *interaction*, not the measured numbers; the FIX-* assertions run in the slow suite against a locally-supplied dump. Ordering for a reviewer, per the product spec: demo → architecture → the ER and uncertainty engine → infra → the scaling analysis.

---

## 15. Feature triage register

What was considered during this expansion, and what happened to it. The register exists so that "we didn't think of it" and "we thought about it and said no" are distinguishable later — and because a Palantir reviewer reads *what you cut and why* as the clearest evidence of judgment.

**In Part 1 (specified above):** pair dossier · decade strip · coverage panel · dual-mode slider · first-run tour · citation copy · "Explore someone" · search-result life dates and tie counts · rendering grammars · state catalog · path search with weakest-link (WP6) · audit feed (WP6) · license-gated export (WP6) · run history (WP6) · accessibility and mobile floors.

**Staged to Phase 3 / Part 2, with the reason:**

| Feature | Why not now |
|---|---|
| Map view (WHG places) | Needs the places layer; the product spec already schedules both together (WP9). Premature without gazetteer reconciliation. |
| Full timeline scrubber | The decade strip delivers most of the value at a fraction of the cost; a true scrubber earns its complexity only once events exist as a populated layer. |
| Monte Carlo distributions in the UI | Requires the Phase 3 machinery. When it lands, the design commitment is that metrics render as distributions with intervals, never as point estimates. |
| Saved views / annotations / a scholar's notebook | Genuinely valuable for P2, but it is a second product (personal state, storage, and a sharing model). Revisit after WP7 with real usage. |
| Multi-user roles, review queues, per-source visibility | ADR-005 scopes Part 1 to a single scholar; the event log's actor model is the hook these attach to later. |
| Cross-source diff view ("where do SDFB and correspSearch disagree?") | Needs a genuine second source — becomes compelling immediately after WP2b, and the pair dossier is deliberately built to accommodate it. |

**Considered and rejected outright:**

| Rejected | Reason |
|---|---|
| A single "trust score" per person | Aggregating incommensurable evidence into one number is exactly the false precision this project exists to refuse. |
| Auto-merging same-named entities | The two Francis Bacons are the counterexample; ER decisions are claims requiring adjudication (Flow F), never conveniences. |
| Hiding low-confidence claims by default | The default must show the record's messiness; filtering is the *user's* deliberate act. Measurement settled the exact default: a 0.6 default blanks 44.9% of entity pages, so the slider defaults to **0** and the result line states what filtering *would* exclude (§17.F2). |
| "Suggested facts" / generative summaries of a person's life | Any synthesized prose would be unattributable to a source, breaking PP1 and PP2 at the root. If it ever appears, it must be a claim with a method and a confidence, like everything else. |
| Public read-only deployment before license confirmation | The gate exists for a reason and is enforced architecturally (no Ingress), not by intention. |

---

## 16. The honesty page, in full (`/about`)

For P1 this may be the highest-signal screen in the product: it is where a reviewer learns whether the author knows what they don't know. For P2 it is the credibility check. It therefore gets a real specification rather than "renders LIMITATIONS.md."

**Governing rule: every number on this page is computed live from the corpus.** Nothing is typed into markup. If a statistic cannot be computed, it is *absent*, not estimated — and the panels below list only statistics verified computable against the loaded Phase 0 corpus (measurements in §17). A hard-coded figure on this page is a defect of the same class as a fabricated citation.

**Panel 1 — What this is.** Two sentences and the thesis. Then the single most important sentence on the site: *"Nothing here is presented as established fact. Every statement is a claim, attributed to a source, with an explicit measure of how well that source supports it."*

**Panel 2 — What's in the corpus.** Live: entities by type; claims by predicate; sources with versions and retrieval dates; the temporal span **as measured, not as advertised** — the slice is described as 1500–1700, but 16% of people carry birth years before 1500 (earliest 1389), and the page says so rather than rounding the corpus to its billing.

**Panel 3 — What the numbers mean.** The confidence histogram (live) with the band labels of §13.3, plus three facts stated plainly: only **1.8%** of relationship claims sit at ≥ 0.90 and **11.4%** at ≥ 0.60, so a filtered view is a small, deliberately-chosen slice of the record; the scores are the source's own, normalized and **uncalibrated**; and SDFB's published validation reported a lowest-possible precision around 28% across its 10–100 band, which is *the source's own reported figure* and the reason calibration is Phase 3 work rather than a footnote.

**Panel 4 — What's missing, and how the record is biased.** The intellectual core, as live statistics rather than throat-clearing: **80.4% of people in the corpus are recorded male, 19.6% female**; **17.5% have no recorded relationship at all** in this source; the people layer derives from a national biographical dictionary, so "who is in the graph" reflects who was judged notable by a particular editorial tradition, not who existed; and the surviving record over-represents the literate, propertied, and institutionally connected. Then the general statement: an absent tie means *no surviving attested evidence in these sources*, never *no relationship*.

**Panel 5 — Method notes.** Each interpretive decision with its status: the date-code table flagged **interpretation, unconfirmed**, alongside the figure that makes it load-bearing (99.7% of relationship intervals rest on the single code `AF/IN`); the `CA` = ±5 years policy as a stated choice; the possibly/certainly semantics with the worked example (Bacon in 1600: 29 not ruled out, **2** required); and, when they exist, the ER operating point with precision/recall and the calibration run's reliability diagram. Every method note carries who decided it and when.

**Panel 6 — Data-quality findings.** The anomaly counters as a live table with a one-line explanation each — 365 ODNB sentinels, 14 shared ODNB ids, 1,575 inverted date ranges, 1 dangling edge, 6 unparseable years — framed as *found and handled*, with the handling stated. This panel is a strength, not a confession: it demonstrates that the pipeline inspects its inputs.

**Panel 7 — Provenance and change.** Import-run history from `/runs` (when, which source version, counts, rejects) and the recent audit feed from `/events` (Phase 2). A reviewer can see the system's own history, which is the bitemporal thesis made concrete.

**Panel 8 — License and access.** Per source: license, confirmation status, and what is blocked while unconfirmed. States the gate plainly: this system is not publicly deployed because one source's terms are unresolved, and export refuses that source's content (§5.1).

**Panel 9 — What this system cannot tell you.** Four explicit non-claims: it cannot tell you two people *didn't* know each other; it cannot tell you a relationship's nature beyond what the source recorded (SDFB's ties are generic associations — there is no typed predicate in this dump); it cannot support causal inference; and it cannot be treated as a sample of early-modern society, only as a sample of *what survived and was judged worth recording*.

**Panel 10 — The technical view.** For P1: architecture in a diagram, the claim model, invariants I1–I8, links to the repository and this document. Clearly marked as the technical section so P2 can skip it.

---

## 17. Pressure-test: three rounds

The product layer was written from reasoning. This section tests it — against the loaded corpus (Round 1), against the upstream source's own code and statements (Round 2), and against the write path's semantics (Round 3). **Every figure is measured or cited, not estimated.** Twenty findings; six were defects in this document's own earlier sections, and the corrections are applied above.

### 17.1 Round 1 — the read path, against the loaded corpus

**F1 — The display-name rule was undefined.** §3.4 specified display name as the "highest-effective-confidence `has-name` claim." Measurement: **all 15,882 name claims are unscored** (SDFB provides no score for names), so there is no highest — the rule silently reduces to arbitrary row order, and the moment WP2b adds a second source's names, the entity header could flip between renders. **Correction (applied):** a deterministic rule — prefer scored over unscored; then by source priority (an explicit ordered list in configuration, not implicit); then the lowest `claim_id`. And where an entity has more than one name claim, the header carries a *known as* affordance listing the alternatives with their sources, because in this system a name is a claim like any other, and picking one silently is exactly the flattening the project refuses.

**F2 — The default confidence threshold was product-breaking.** §13.2 defaulted the slider to 0.6. Measurement: at 0.6, **7,125 of 15,882 entity pages (44.9%) render an empty graph**; at 0.9, 84% do. A user exploring a randomly chosen figure would have had a coin-flip chance of a blank canvas that reads as *broken* rather than *honest*. The unfiltered view, meanwhile, is not the hairball I assumed: **median ego size is 15 ties, p90 is 54**, and only **20 entities of 15,882 exceed the 500-edge canvas cap**. **Correction (applied):** default the slider to **0**. The typical page opens legible and messy — which is the thesis — and the demo's 0.6 comes from a pre-set URL. This also resolves the contradiction in §15, which had rejected "hiding low-confidence claims by default" while specifying a default that hid 88.6% of ties. The demo arc improves too: Bacon goes **288 ties → 34 at ≥0.60 → 15 at ≥0.90** across all time (29 → 13 windowed to 1600).

**F3 — Life dates were about to be rendered as bare fact.** Birth/death claims are unscored by design (SDFB gives no score), yet §13.2 put "1561–1626" in the entity header with no confidence marking — which violates PP2 in the most visible place on the page. **Correction (applied):** life dates render with an *unscored* chip and the derived-date marker, like any other unscored claim.

**F4 — The possibly/certainly toggle is far more consequential than it appeared.** Measurement on Bacon at ≥0.60 in 1600: **29 ties are "not ruled out," but only 2 are "required by the evidence."** A 14× collapse. Written as a minor refinement in v1.1, it is in fact the sharpest honesty instrument in the UI — and correspondingly the most dangerous default, because a user who reads "29 ties in 1600" as "29 attested relationships in 1600" has been misled by the interface, not by the data. **Correction (applied):** the result line always states both numbers and both meanings (§13.2), and the toggle's explanation is permanent, not a hover.

**F5 — The decade strip is viable; my worry was unfounded.** I expected open-ended intervals to make per-decade counts uniform and useless. Measurement: **169,729 of 171,600 relationship claims (98.9%) carry both bounds**; only 31 are start-only open-ended. The strip is meaningful — provided it is labelled in possibly/certainly terms (these intervals are largely biographical envelopes, i.e. *when a tie was possible*, not *when it was attested*), which F4's correction already requires.

**F6 — The temporal layer rests on a single interpretation.** Measurement: **99.7% of relationship claims (171,033 of 171,600) carry the one start code `AF/IN`**, and 99.8% carry `BF/IN` as the end code. Every time filter, the decade strip, and the possibly/certainly distinction therefore inherit one reading of two codes. Round 2 was commissioned specifically to attack this finding.

**Two assumptions that held.** Multi-edge rendering needs no special handling for Part 1: **zero pairs carry more than one relationship claim** in SDFB (it becomes live at WP2b, which is why the pair dossier exists — and §20 A1 is the reason that work package moved earlier). And "Explore someone" is safe with a `minScoredDegree` filter: 10,930 entities have five or more scored ties.

### 17.2 Round 2 — desk check against the upstream source

F6 made the date-code interpretation the highest-value open item in the project, so it was checked directly. SDFB's own Rails source could not be read (GitHub's subdirectory listings are robots-disallowed to automated fetching), but three external sources were consulted: an independent RDF converter that processes the same SDFB CSV exports, the project's published methods paper, and the project's own blog.

**S1 — The critical interpretation is corroborated.** An independent converter (`jiemakel/anything2rdf`, `SDFBCSV2RDF.scala`) maps the same codes into the same four-date model this project uses, and glosses them explicitly: `AF/IN` → *"after the beginning of"*, `BF/IN` → *"before the end of"*, `AF` → *"after"*, `BF` → *"before"*. For `AF/IN` and `BF/IN` — **99.7% and 99.8% of the corpus** — its bounds are identical to ours. Independent corroboration is not authoritative confirmation, but it moves the risk from *"one unchecked reading"* to *"two independent readings that agree,"* which is a materially different exposure. The item stays open; it is no longer critical.

**S2 — We collapsed a distinction the source deliberately encodes.** The corroborating implementation treats bare `AF` as *strictly after* the year (earliest bound = year end) and bare `BF` as *strictly before* it (latest bound = year start), whereas our adapter mapped `AF`≡`AF/IN` and `BF`≡`BF/IN`. If the two codes meant the same thing, SDFB would not have both. Our version is wider, so it never overstated certainty — but it erased information. Affected: **229 claims** (start: 106 `BF`, 43 `AF`; end: 55 `AF`, 25 `BF`). **Correction (applied to WP2):** preserve the distinction.

**S3 — Our `CA` policy violated the project's own honesty rule.** The adapter widened *circa* dates by an invented ±5 years. The corroborating implementation instead keeps the stated year and flags the assertion as approximate. Ours manufactures a specific numeric interval the source never states — the date-layer equivalent of inventing a confidence number, which §2 forbids for confidence and §12.3 (PP2) forbids generally. That the invention is *conservative* does not make it sourced. Affected: **107 claims** (75 start, 32 end). **Correction (applied to WP2):** year bounds plus an `approximate` marker carried into the claim; no invented window.

**S4 — The confidence scores are a bootstrap selection frequency, which sharpens the calibration story.** SDFB's methods paper describes fitting the Poisson graphical lasso <cite index="24-1">on random subsets of the data 100 times and summing the resulting adjacency matrices into a confidence matrix, giving a value from 0 (never inferred) to 100 (always inferred)</cite>. So the number is *how often an edge survived resampling* — a stability statistic, not a probability that the relationship existed. This is precisely why "uncalibrated ranking signal" is the honest framing, and it upgrades the honesty page's explanation from a caveat to a mechanism. The same paper reports that its false positives clustered around <cite index="24-1">group biographies, duplicate names, and unusually high co-mention rates within related biographies</cite>, and that excluding four such people left <cite index="24-1">fifty relationships with an 86.00% precision rate in the 40–100 confidence interval</cite>. Two consequences: the honesty page must quote precision figures *with* the subset they were measured on, and — usefully — the source itself names duplicate names as a reliability hazard, which is a source-stated prior for the entity-resolution work in WP7.
> *Correction to an earlier session's note:* a figure of "≈28% lowest-possible precision over the 10–100 band" was recorded during Phase 0. It could not be re-verified in this session (the journal is robots-disallowed to automated fetching), so it must not be quoted until someone reads the paper directly. The 86%/40–100/n=50 figure above is quoted from a search-result excerpt of that paper and should likewise be confirmed against the original before it appears in the product.

**S5 — The dataset licence has public evidence, but not enough.** The project's own blog states that SDFB shares data <cite index="25-1">free of charge for other researchers' non-commercial purposes, without warranty, asking at minimum that its website be cited</cite>. That is a genuine statement of intent from the project — but it is a 2015 blog post, not a licence file, and "non-commercial" is a term a portfolio author should get in writing rather than infer. `license_confirmed` stays **false**, and the no-Ingress rule stands. What changes is the ask: instead of "find the licence," it is now "confirm this still holds," which is a much easier email to send and a much easier one to answer.

**S6 — Search would fail the historian's first instinct.** Not a date finding, but the check surfaced it: **3,710 people (23.4%) carry alternate names** in `search_names_all` that are not substrings of their display name — including married and maiden names ("Helen Alexander" also appears as "Helen Umpherston" and "Helen Currie"). The adapter preserves these in the raw record but emits no claims for them, so J2 — the historian looking up someone they know — silently fails for a quarter of the corpus, and fails hardest for women, whose names change. **Correction (applied to WP2):** emit alias name-claims (`has-name`, `name_kind=alias`) and index them for search.

### 17.3 Round 3 — the write path

Flows D and F had never been walked. Nine findings; the write path is where an interface can most easily launder a scholar's judgment into something the data does not support.

**W1 — "Dispute" collapses five different scholarly acts into one flag.** A historian rejecting *"Bacon associated-with X"* may mean: these people never had a connection; they did but not in this period; the confidence is wrong; the source has been misread; or **this is the wrong Bacon**. Only the first is really "dispute the claim." The last is an *identity* error whose correct remedy is a split, not a dispute — and the current UI would happily let a scholar file it as a dispute, permanently recording the wrong diagnosis in the audit log and leaving the identity error uncorrected. **Correction:** disputes carry a required typed ground — `existence` · `dating` · `identity` · `confidence` · `source-reading` — and the `identity` ground routes to the ER queue rather than flagging the claim. A typed ground also makes disputes analyzable later ("what kind of thing do scholars most often reject?"), which a free-text field never would.

**W2 — A dispute is itself a claim, but the model treats it as a status.** In a system whose thesis is that competing assertions coexist, marking the *original* claim "disputed" quietly privileges the disputer. Two scholars disagreeing *about the dispute* has no representation. For Part 1 this is acceptable — ADR-005 scopes the system to a single scholar, and the event log does record actor, ground, and reason — but it is a real architectural inconsistency and must be named rather than discovered later. **Correction:** documented as a known limitation with its migration path (disputes become claims-about-claims when the system becomes multi-user); the UI never renders a disputed claim as *refuted*, only as *contested by [actor], on [ground]*.

**W3 — Disputed claims were silently included in every aggregate.** Unspecified anywhere: whether `/stats/summary`, the coverage panel, the decade strip, degree counts, and search tie-counts exclude disputed claims. If a scholar disputes fifty ties and the honesty page still counts them, the page lies — and it lies on the screen whose entire purpose is not lying. **Correction:** every aggregate declares its status filter in its response `meta`, the default excludes disputed and superseded, and the honesty page shows both figures where they differ.

**W4 — Writing while viewing a merged entity was ambiguous.** Under `resolution=canonical`, several raw entities render as one node. A scholar asserting a new claim there has no defined target — the engine cannot know which underlying record the assertion belongs to, and guessing would corrupt provenance. **Correction:** writes always target a raw entity; in canonical view the action UI must require the scholar to pick which record the claim attaches to, showing each record's source. (Reads stay folded; only writes disambiguate.)

**W5 — Nothing warned against an obviously wrong merge.** The system's own showcase disambiguation — Bacon 1561–1626 versus Bacon 1600–1663 — is exactly the merge a tired adjudicator might rubber-stamp, and nothing in the design objected. **Correction:** a merge-time check computed from existing claim bounds warns (never blocks) when candidates' life spans do not overlap, or when their name claims share no token. Cheap, uses data already loaded, and directly defends FIX-DISAMBIG.

**W6 — The adjudication queue recorded decisions without recording their basis.** "Accepted" in an audit log, with no record of what the adjudicator was shown, is an unfalsifiable decision — the same defect the project criticizes in flattened historical data. **Correction:** the merge rationale stores an evidence snapshot (the claim ids displayed at decision time, and the ER features and score), so a later reader can reconstruct *why*, not merely *that*.

**W7 — Merged entities hid their own components.** After a merge, the canonical node is what the scholar sees; the constituent records become invisible, making a reversible operation feel permanent and discouraging correction. **Correction:** canonical entities always list their member records with their sources and a visible split affordance.

**W8 — Time-travel plus write is nonsense and was not forbidden.** `asOfSystem` sets a historical view; nothing said what happens if an action is submitted from one. Writing into the past is either meaningless or a silent rewrite of history — both unacceptable in an append-only bitemporal store. **Correction:** actions are rejected (409) when submitted from a time-travel context, and the UI disables action controls behind the time-travel banner.

**W9 — Inferred claims arrived as oracles.** ER writes `same-as` claims with `method=inferred`, but the evidence panel was specified to show *support* — and an inferred claim's support is a run record, not a document. Presenting "inferred" with no visible reasoning is precisely the black box a historian should reject. **Correction:** for `method=inferred`, the evidence panel renders the features and score that produced the claim, plus the run's parameters and evaluation protocol — the same transparency standard applied to imported claims, adapted to the fact that the "source" is a computation.

**One thing the write path got right.** Because claims are immutable and canonical identity is a *derived* projection rather than a destructive merge, un-merging is a recompute rather than a repair — the reversibility promised in Flow F is real, not aspirational. W7's correction is about making that visible, not about making it true.

### 17.4 What the three rounds cost, and the standing rule

Round 1: twelve queries against the existing corpus. Round 2: five external sources, one of which corroborated the load-bearing interpretation and two of which corrected our own mapping. Round 3: no tooling at all — a careful walk through two flows. Between them: **two product-breaking defects, one honesty violation in the most visible UI element, two source-mapping errors, one silent search failure affecting a quarter of the corpus (and disproportionately women), nine write-path defects, and one open item de-escalated and one made answerable.** None of this required implementation.

**Standing rule (extends §19):** the read-path walk repeats at WP4 and WP2b; the **write-path walk repeats at WP6 and WP7**, when actions and adjudication first become real; and any adapter's source mapping gets a Round-2-style external check **before** its work package merges, not after.

---

## 18. Failure modes and degradation

What the system does when things go wrong. No new features — this specifies the behavior of components already defined, so that failure is designed rather than discovered.

| Failure | Detection | System behavior | User-visible behavior |
|---|---|---|---|
| Postgres unreachable | `/readyz` fails; connection errors | Engine returns 503 problem-json; no retry storm (circuit-break with backoff); pods stay up so recovery is automatic | Explorer shows a service-unavailable state with retry, not a blank page |
| Migration pending or failed at deploy | Flyway check at startup | Engine refuses readiness rather than serving against an unknown schema | Deployment does not roll; previous version keeps serving |
| Import batch fails mid-stream | Per-batch transaction | Batch rolls back atomically; prior batches stand; run manifest records the failure point | Operator sees exactly which batch and line failed; resubmission is idempotent (Flow A.8) |
| Import contains unresolvable refs | Per-line validation (I6) | Line rejected with reason; batch continues; **no fabricated entities** | Reject report lists line, reason, and the offending ref |
| Projector falls behind or crashes | Outbox lag metric + SLO | Reads still work — base tables are authoritative; only summaries lag. Restart resumes from the unprocessed row (at-least-once, idempotent materialization) | Stale banner appears once lag exceeds the SLO (§13.6) |
| Projector produces wrong summaries | `rebuild-projections` vs live comparison (a test, and an ops command) | Rebuild from base tables is always available and is the recovery path | None if caught by the nightly comparison |
| Network query exceeds the edge cap | Count vs cap | Truncate by effective confidence, set `truncated` | Explicit message with actionable advice, never silent (PP4) |
| Path search exceeds budget | 2 s / node cap | Return partial paths with `budgetExceeded` | "Search stopped at its limit; these are the paths found so far" |
| Two writers race on the same claim | Status transitions validated against current state in-transaction | Second writer gets 409 with the current status | "This claim was changed by another action — reload to see the current state" |
| Disk exhaustion on the DB PVC | Free-space metric + alert | Alert before writes fail; imports are the only large writer and are operator-initiated | Operator halts imports; runbook covers expansion |
| Backup restore needed | Manual | Restore drill is documented and exercised in WP5 acceptance | — |
| Source URL dead / schema changed at harvest | Harvester content-hash check + fetch failure | Run aborts *before* submission; nothing partial reaches the store | Operator sees which source and what changed |

**Degradation ladder.** The system degrades in a deliberate order, from fully operational down to: summaries stale (reads fine, banner shown) → writes rejected but reads served (DB read-only) → read-only cached frontend (engine down) → hard down. Each rung is a state in §13.6, so none of them renders as an unexplained blank.

---

## 19. Part 1: definition of done

The product spec's §19 defines "core complete" as the gate for Part 2. This section makes each of those seven criteria *verifiable in this system*, with named fixtures. Claude Code does not declare Part 1 done; it presents this table with evidence, and the architect signs.

| Product-spec criterion | Verified in this architecture by | Evidence required |
|---|---|---|
| 1. Claim engine built (support, method, confidence, bitemporal, competing claims) | §2 contracts + §3.3 invariants I1–I8 | Engine test suite green, incl. immutability, status-fold, and effective-confidence tests |
| 2. People + correspondence + one environment layer ingested with full lineage | WP2 (SDFB) + WP2b (correspSearch) + WP9 (ITRDB) | Import-run records for each; every claim traces to a `source_record` (lineage completeness test) |
| 3. Cross-source ER runs and is evaluated on a gold set | WP7 | Precision/recall at a stated operating point; the two Francis Bacons remain distinct |
| 4. Governed write-back (assert/dispute/merge/split) with audit history | WP6 + WP7 | Flow D and F round-trips; history endpoint shows the full chain |
| 5. Explorer deployed and usable by a non-expert, with slider and evidence transparency | WP4 + WP5 | The demo recording (§14.1); PP1–PP6 acceptance tests; a11y pass |
| 6. Infrastructure real: K8s, IaC, observability, an SLO, incremental recompute | WP5 (+WP9 operator) | kind smoke green; dashboards showing real traffic; restore drill evidence; SLO measured |
| 7. Calibration and survivorship-bias-aware inference working and validated | WP9 | Reliability diagrams; the model's validation protocol stated with results |

**Named acceptance fixtures** (stable, referenced by tests so "done" means the same thing every time):

- **FIX-BACON** — entity for SDFB id `10000473`. Canonical assertions: 288 total ties; 34 at ≥ 0.60 and 15 at ≥ 0.90 (all time); 29 and 13 respectively when windowed to 1600 with `possibly`; **2** with `certainly` at ≥ 0.60 in 1600.
- **FIX-DISAMBIG** — SDFB ids `10000473` and `10000475`, both named "Francis Bacon." Must remain distinct entities through import, ER, and canonical resolution; must be distinguishable in one search interaction (J2).
- **FIX-ANOMALY** — the five data-quality counters at their measured values: 365 / 14 / 1,575 / 1 / 6. Any drift means the adapter changed behavior, and the test says so.
- **FIX-CORPUS** — 15,882 entities · 261,177 claims · 187,482 source records.
- **FIX-COVERAGE** — the honesty page's live statistics: 80.4/19.6% gender split; 17.5% with no recorded tie; 1.8% of ties ≥ 0.90; 99.7% of intervals from `AF/IN`. These are *computed* assertions — the test verifies the page derives them, not that they are hard-coded.

**Standing gate (from §17.4).** At WP4 and WP2b the read-path journeys J1–J5 are walked against the *then-current* corpus; at WP6 and WP7 the write path is walked; and every new adapter gets an external source check before its work package merges. Findings are written up as §17 was. New sources change the corpus's shape, and shape changes invalidate UI assumptions — as F2 demonstrated, a defensible default became a product-breaking one purely because of how the data was distributed.

**What "done" explicitly does not require:** public deployment (blocked by the license gate), a second historical period (that is Part 2), typed relationship predicates (blocked on the remaining Folger tables), or calibrated confidence (Phase 3 — until then the UI says uncalibrated everywhere, which is itself the deliverable).

---

## 20. Adversarial review

The three rounds in §17 tested the design against data and sources. This section attacks the **plan** — its premise, sequencing, scope, and whether it achieves its purpose. Ten attacks, ordered by severity. Where an attack lands, the correction is stated; where the plan survives, that is stated too, because a review that finds only faults is as untrustworthy as one that finds none.

**A1 (critical) — The Part 1 corpus cannot exercise the Part 1 thesis.**
Measured across the loaded corpus: **zero** relationship claims are unscored; **zero** entity pairs carry more than one relationship claim; and for every attribute predicate (`has-name`, `born`, `died`, `has-gender`, `has-description`, `has-external-id`) **no subject carries more than one claim**. There are therefore **no competing claims of any kind in Part 1's data.** The thesis — *two sources that disagree produce two claims, never silently merged* — has no instances to demonstrate. The competing-claims display never fires. The pair dossier, built specifically to show contradiction, shows exactly one claim per pair. PP3 ("contradictions are shown as contradictions") is not merely untested but *unfalsifiable*: you cannot verify that a system surfaces disagreement in a corpus containing none. And core-complete criterion 3 — *cross-source* entity resolution — is unsatisfiable with a single source, yet **WP7 (entity resolution) is sequenced before WP8 (the second source)**. The plan builds a reconciliation engine and then waits five work packages before giving it anything to reconcile.
**Correction:** promote the second source to immediately after WP2, renaming it **WP2b**, with the correspSearch coverage probe as the project's next external check rather than a late one. Consequences, all favourable: the pair dossier and competing-claims UI have real content the first time they render; ER at WP7 becomes genuinely cross-source and can satisfy criterion 3; the demo can show the capability Palantir actually screens for; and if the probe fails, that is discovered at WP2 rather than after the explorer has been built around an assumption. The cost is one pipeline work package before the first browsable UI — acceptable, because WP2b reuses WP2's machinery wholesale, which is itself the adapter-interface proof.

**A2 (severe) — The "certainly active" mode empties the screen 99.8% of the time.**
Measured: only **313 of 171,600 relationship claims (0.18%)** carry the bounds `certainly_active` requires. This is structural, not incidental: `AF/IN` on start yields only an *earliest* start, `BF/IN` on end yields only a *latest* end, and certainty needs the opposite pair. So for 99.8% of the corpus the answer is permanently "not certain," and a user switching modes sees a blank canvas that reads as a broken feature. §17.1 F4 called this toggle "the sharpest honesty instrument in the UI" — that judgment was right about its *meaning* and wrong about its *mechanism*.
**Correction:** stop treating certainty as a filter mode. The certainly-active count stays permanently in the result line (already required by F4), and certainly-active edges are **highlighted within** the possibly-active graph rather than isolated by a toggle. The user then sees the whole permitted network with its tiny evidentially-required core emphasized — which conveys "the record almost never pins a tie to a period" far better than an empty screen, and is the more honest rendering of a genuinely devastating fact about the source.

**A3 (severe) — The unscored toggle is a control that does nothing.**
Because every relationship claim is scored (A1), the unscored toggle has **no effect on the network canvas at all**; it can only affect attribute claims, which do not render as edges. §13.1's search rows advertise "34 scored · 12 unscored ties" — a display of a quantity that is always zero. A visible control with no effect, and a counter that always reads nil, teach the user that the interface is decorative.
**Correction:** drop the unscored tie-count from search rows; scope the unscored toggle to the claims list where unscored attribute claims actually live, and label it accordingly. Retain the *concept* — WP2b and WP9 will introduce genuinely unscored relationship claims, at which point the control earns its place in the canvas.

**A4 (severe) — Scope and purpose are mismatched.**
"Core complete" per the product spec requires calibration and survivorship-bias-aware inference — WP9 work. But the artifact's *purpose* is a portfolio demonstration, and its value to that purpose peaks far earlier: a deployed, evidence-transparent explorer over two real sources with governed write-back and honest uncertainty (roughly WP5–WP7). Everything past that is intellectually excellent and adds progressively less to the hiring decision, while adding a great deal of calendar time. The roles also carry graduation-year gates, so an implicit deadline exists even though the owner has set no explicit one. A plan that produces nothing demonstrable for a year can miss the window entirely.
**Correction:** name **WP5 the "portfolio-viable" milestone** (deployed, demoable, honest) and **WP7 the "portfolio-strong" milestone** (write-back + cross-source ER = the full FDE story), and state plainly that WP9 is intellectual completion rather than application readiness. This changes no engineering; it changes what "behind schedule" means, and it protects against the failure mode where the best version is never shown to anyone.

**A5 (moderate) — The license gate contradicts the fixture plan.**
§14.3 proposes shipping "a small, license-safe subset with the Bacon neighborhood intact." A subset of license-unconfirmed data is still license-unconfirmed data; the phrase quietly assumes away the exact problem the gate exists for. The same question applies to the demo recording, which necessarily displays SDFB content, and to the repository, which must not contain the dump.
**Correction:** the CI and demo fixture becomes **synthetic** — invented people and ties with the same shape and the same anomaly classes, sized to reproduce the interaction (not the numbers) — so tests and `make demo` run with no license exposure. The measured FIX-* assertions remain, but they are *facts about* the data rather than the data, and they run only in the slow suite against a locally-supplied dump. The demo recording stays gated on the owner's licence answer; if it does not arrive, the recording uses the synthetic fixture and says so, which costs the "real data" claim but keeps the project publishable.

**A6 (moderate) — No historian has ever seen this.**
P2 is a designed persona, and every claim about what a historian would want is inference. One 45-minute session with a real early-modernist — watching them search someone they know, ask "says who?", and try to break it — would validate or destroy more of §§12–13 than all three pressure-test rounds combined, because it tests the one thing internal review structurally cannot: whether the vocabulary, the confidence framing, and the evidence path match how a domain expert actually reasons. It is also, not incidentally, the single most FDE-authentic activity in the whole project: sitting with a user and finding out that the thing you built answers the wrong question.
**Correction:** one user session scheduled at WP4 exit, written up like a §17 round, with findings binding on WP6. Recruiting one early-modernist willing to spend an hour is a low bar and the write-up is itself a portfolio artifact.

**A7 (moderate) — 1,650 undated claims silently appear in every time window.**
Measured: 1,650 relationship claims (0.96%) carry no temporal bounds — including the 1,575 whose inverted bounds were correctly dropped. `possibly_active` is trivially true for all of them, so they appear in *every* window without distinction. A user filtering to 1600 receives ties that are not dated at all, presented identically to ties whose evidence genuinely permits 1600.
**Correction:** undated claims render with an explicit *undated* marker and are counted separately in the result line ("29 ties not ruled out in 1600, of which 3 carry no dates at all"). This is the temporal analogue of the unscored/scored distinction, and the same honesty rule applies: absence must look like absence.

**A8 (moderate) — The demo's showcase entity is atypical, and its first impression is a hairball.**
With the default threshold correctly at 0 (F2), Francis Bacon opens at **288 ties** — against a corpus median of 15 and p90 of 54. He is a p99+ outlier. A reviewer arriving via a direct link rather than the tour meets an unreadable tangle, and a reviewer who assumes Bacon is representative will form a wrong impression of the corpus in both directions.
**Correction:** the tour's pre-set view remains the intended entry (0.6, windowed), the honesty page states Bacon's atypicality alongside the median, and the demo storyboard adds one ordinary figure — the median case — so the reviewer sees both what a hub looks like and what the corpus mostly looks like.

**A9 (worth revisiting, not overturning) — ADR-002 delays the only artifact that matters.**
"JVM engine from commit 1, no throwaway services" eliminates a rewrite, but it places the Java engine, OpenAPI generation, SDK generation, and a React app on the critical path *before anything is visible*. Under A4's reframing, time-to-first-demo is a real cost, and the rewrite it avoids is perhaps a week's work on a project this size. The counter-argument still holds — a Python-then-Java port is the classic promise that never gets kept, and the JVM engine is itself part of the signal for these roles — so the decision stands. But it should stand *knowingly*: **the risk is time-to-first-demo, and the mitigation is that WP1's read API is deliberately thin.** If WP1 exceeds a few weeks, that is the trigger to reconsider, and the trigger should be written down rather than discovered.

**A10 (the meta-attack) — specification is becoming the deliverable.**
This document is now roughly 15,000 words across four versions, and the repository contains no product. The specification work has been genuinely productive — it caught two product-breaking defects, two source-mapping errors, a silent search failure, and nine write-path defects, all before any code existed — but the marginal return is now falling, and there is a recognizable failure mode where planning substitutes for shipping and the portfolio contains an architecture document instead of a system.
**Correction:** this is the last full specification pass. Remaining detail is produced *inside* work packages as implementation questions arise, not in advance. The standing gates (§17.4, §19) are the mechanism for continued rigor; they are triggered by implementation milestones rather than by available thinking time.

### What survived the attack

Four load-bearing decisions were attacked and held. **The claim model** absorbed every data pathology thrown at it — sentinels, shared authority ids, inverted ranges, dangling references, unscored assertions — without a schema change, and the one migration required (V2) is purely additive. **Immutability plus derived canonical identity** makes un-merging a recompute rather than a repair, so Flow F's reversibility is real rather than promised. **The honesty rules proved falsifiable**: PP2 caught the bare life-dates violation, and the "no invented numbers" rule caught the `CA ±5` invention — rules that catch their author are working rules. And **the layered architecture** meant that every correction in this review lands in an adapter, a projector rule, or a UI grammar; **not one finding across four rounds required changing the engine, the store, or the contracts.** That is the strongest evidence available that the core design is sound.

### The revised critical path

Reflecting A1 and A4: **WP0 → WP1 → WP2 (SDFB) → WP2b (second source) → WP3 → WP4 → user session → WP5 [portfolio-viable] → WP6 → WP7 [portfolio-strong] → WP9.** The two changes from §9 are the promotion of the second source ahead of the explorer, and the naming of two milestones as decision points about whether to continue or to ship what exists.

---

## 21. Glossary

**Claim** — the atomic assertion: subject, predicate, object (entity or literal), fuzzy valid time, support, method, confidence, immutable once written. **Effective confidence** — the I7 resolution of manual override → calibration → source-native → unscored. **Unscored** — confidence honestly absent; never zero, never ranked. **Possibly/certainly active** — the two temporal predicates over four-date fuzzy intervals; "the record permits" vs "the record requires." **Interchange** — the JSON claim shape crossing process boundaries. **Outbox** — the transactional change feed driving projections. **Run manifest** — the pipeline's per-run provenance record. **Adjudication queue** — mid-confidence ER candidates awaiting a human. **License gate** — `license_confirmed=false` ⇒ internal-only, enforced by the no-Ingress rule, UI badges, and export refusal. **Signature query** — one figure's network, windowed in time, filtered by confidence: the product in a sentence. **Signature beat** — the demo moment where raising the threshold visibly drops the network from 29 ties to 13. **Pair dossier** — every claim linking two entities, competing claims included. **Coverage panel** — per-entity source contributions and scored/unscored split. **Decade strip** — per-decade tie counts doubling as a window selector. **Rendering grammar** — the fixed mapping from data state to visual encoding (§13.3); restylable, not re-semanticizable. **Product promises (PP1–PP6)** — the six falsifiable honesty commitments (§12.3). **Weakest link** — a path's minimum edge confidence, the honest summary of how much a connection can bear. **Journey walk** — the standing review that replays J1–J5 against the current corpus and reports what broke (§17, §19). **Degradation ladder** — the deliberate order in which the system loses capability, each rung a designed, explained state (§18). **Named fixture** — a stable, measured assertion (FIX-BACON, FIX-ANOMALY, …) that makes "done" mean the same thing on every run (§19). **Dispute ground** — the required type of a dispute (existence · dating · identity · confidence · source-reading); `identity` routes to entity resolution, not to the claim log (§17.3 W1). **Evidence snapshot** — the record of what an adjudicator was shown at decision time, stored with the decision (§17.3 W6). **Source-native selection frequency** — what an SDFB confidence number actually is: how often an edge survived bootstrap resampling, not a probability that the tie existed (§17.2 S4).

---

*End of specification v1.1. **This document is in owner review, not released to implementation** — §§12–15 are new and the decisions in them (six new endpoints, the `import_run` table, expanded WP4/WP6 scope, and every entry in the §15 triage register) are proposals awaiting your judgment. When it is released, the first reader test is the implementation itself: if Claude Code must guess at anything material, that is a defect in this document — file it as a question, not a guess.*
