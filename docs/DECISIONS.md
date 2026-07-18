# DECISIONS — ADRs and deviation records

Opens as the ADR record (ADR-001…003 ratified by conduct; ADR-004…006
architect-decided, vetoable — HANDOFF §1) and receives every deviation record
thereafter (HANDOFF §3). One WP = one PR train; deviations are proposed, not
silently implemented.

> **RATIFICATION — owner, 2026-07-18.** The owner ratified the Part 1 build:
> ADR-004/005/006 (vetoable → not vetoed), **DEV-001** (contracts authored
> fresh), and **DEV-003** (Q-1…Q-7 adopted recommendations) are **ratified**.
> DEV-002/DEV-004 are informational and acknowledged. The frozen contracts of
> record are the fresh-authored files (DEV-001); change only via the §2 process.

---

## ADR-001 — PostgreSQL is the system of record; the graph is a projection
**Status:** ratified by conduct. No graph database exists in Part 1. Revisit
triggers (§6.4): claims approaching 10⁸, deep-traversal product needs, or an SLO
breach on neighborhood queries.

## ADR-002 — Contract-first; Python pipeline; Java 21 engine from the first commit; no throwaway services
**Status:** ratified by conduct. Direction of truth: DDL/JSON Schema → engine →
OpenAPI → SDK → explorer.

## ADR-003 — Local `kind` now; standing cluster deferred
**Status:** ratified by conduct. The `cloud` overlay is a stub until the checkpoint.

## correspSearch amendment — correspSearch is the Part 1 correspondence layer in place of EMLO
**Status:** ratified by conduct. EMLO prohibits bulk use without permission.

## ADR-004 — The engine owns all writes, including bulk import
**Status:** ratified by the owner 2026-07-18 (not vetoed). The Phase-0 direct-DB
loader is demoted to `tools/`, marked non-production, never invoked by engine/pipeline/CI.

## ADR-005 — Single-scholar identity model for Part 1
**Status:** ratified by the owner 2026-07-18 (not vetoed). One human token; distinct
pipeline/model tokens; real attribution in the event log.

## ADR-006 — Transactional-outbox projections, in-engine first
**Status:** ratified by the owner 2026-07-18 (not vetoed). The Go recompute operator
consumes the same outbox in Phase 3; nothing is thrown away.

---

# Deviation records

## DEV-001 — Contracts authored fresh (Phase-0 artifacts unavailable)
**Context.** HANDOFF §2.1 requires the two frozen contract files
(`claim.schema.json`, `claim-schema.sql`) to be supplied by the owner and used
byte-identically; if missing, "STOP, escalate, do not reconstruct." Neither file
was present in the repository (empty, no commits) or in the handoff package
delivered to this session.

**Proposed change / what was done.** The owner directed a full Part-1 build in
this session. With no Phase-0 artifact to restore, the two contracts were
**authored fresh**, faithful to ARCHITECTURE §2 (interchange + storage
properties), §3.3 (invariants I1–I8), §6.1/§6.3 (table inventory + V2), and
§13.3 (fuzzy-date grammar). They are the contract of record for this build.

**Why.** WP0 cannot proceed without contracts; the alternative (stop) conflicts
with the owner's explicit instruction. Authoring to the specification is the
faithful reconstruction the spec's own text describes, done transparently.

**Blast radius.** These are NOT byte-identical to any prior Phase-0 file. If the
original surfaces, reconcile via the §2 change-control process; a diff may
require re-freezing (`scripts/freeze_contracts.py`) and, if the shape differs,
re-validating Phase-0 evidence. All downstream code programs against these files,
so a later swap is a controlled migration, not a rewrite. Frozen-ness is enforced
mechanically from here on (`scripts/contract_gate.py`, checksums).

**Status:** **RATIFIED by the owner 2026-07-18.** The fresh-authored contracts are
the contract of record for this build; further change only via the §2 process.

## DEV-002 — Runtime evidence uses a local PostgreSQL server; Docker/kind unavailable
**Context.** The session environment has no Docker daemon and no
`kind`/`kubectl`/`kustomize`. ARCHITECTURE §8 specifies Testcontainers
integration tests and a kind end-to-end smoke.

**What was done.** A real PostgreSQL 16 server (installed in the environment) is
used to produce genuine engine acceptance evidence (migrations, imports, the
signature network query). Integration tests are written to run against
Testcontainers in CI **or** an externally-supplied JDBC URL
(`PALIMPSEST_TEST_JDBC_URL`) so they also run here against the local cluster.
Kustomize manifests (WP5) are authored and validated by `kustomize build` in CI;
the kind smoke is scripted but cannot be executed in this session.

**Blast radius.** No production behavior changes. WP5's "kind smoke green" and
Testcontainers-in-CI acceptance are authored but unverified in this session and
are flagged NOT DONE where they could not be executed.

**Status:** informational — environment constraint, not a design change.

## DEV-003 — Open questions Q-1…Q-7 resolved by the handoff's recommendations
**Context.** The owner is not in-loop to rule on the blocking questions; the
session directive is to build Part 1.

**What was done.** Each open question in `docs/QUESTIONS.md` was resolved by
adopting the handoff's own recommended answer (all author-endorsed and
easiest-to-reverse). Each is flagged in the relevant PR and carried to the
checkpoint for ratification.

**Status:** **RATIFIED by the owner 2026-07-18** — the adopted Q-1…Q-7 defaults stand.

## DEV-004 — WP7 entity resolution: partials in this build
**Context.** WP7's full scope (gold-set tooling, blocking + Fellegi–Sunter scorer,
precision/recall on a labelled set, the `/adjudicate` view, `resolution=canonical`
read-folding) is a portfolio-strong milestone gated on a real second source (WP2b).

**What is built.** The write-side of Flow F: `merge-entities` records the canonical
mapping in `entity_canonical` with the non-overlapping-lifespan warning (W5), and an
`identity`-ground dispute routes to the `er_candidate` queue (W1). `GET /er/queue`
and the analytics ER pipeline (blocking → Fellegi–Sunter → gold set) are **not built**
in this session.

**What is partial.** `resolution=canonical` is accepted on `/entities/{id}` but reads
do not yet fold same-as components on the canvas (raw resolution only). The Go
recompute operator, calibration runs, Monte Carlo, WHG/ITRDB layers (WP9) are unbuilt.

**Status:** informational — scoped honestly; carried to the WP7 checkpoint. The
engine, store, and contracts did not change to accommodate any of it (the design
held), which is the signal §12/§20 asks us to preserve.

## DEV-005 — Governance correction: WP2 & WP5 sign-offs ratified without evidence; WP-R1 restored it

**Context.** An external review compared the repository against ARCHITECTURE §8 and
the WP acceptance criteria. The build is real and substantial, but several things
signed off as done had acceptance criteria that **could not be demonstrated** — the
evidence did not exist. Two of the affected checkpoints (WP2 the import path, WP5
the portfolio-viable Phase-1 exit) are **mandatory architect sign-offs** per
HANDOFF §3. Ratifying them without their evidence is the defect this record names.
Session WP-R1 closes the gap; this entry is the register the correction lives in
(ARCHITECTURE §15 — "we signed off without evidence, we found it, here is the fix").

**Which criteria were signed off without evidence, and what was missing.** Six
findings, each independently reproduced at the start of WP-R1 (Step 0):

| # | Signed-off claim | What was actually missing |
|---|---|---|
| F1 | WP5 dashboards / WP2 import observability work | The engine registered **no custom meters**. `palimpsest_import_claims_total`, `palimpsest_outbox_pending_rows` (and 5 sibling series) were queried by the Grafana dashboards and `kind_smoke.sh` but **never emitted**. |
| F2 | WP5 "the stack deploys" | **Zero Dockerfiles.** The manifests reference `palimpsest/engine:local` / `palimpsest/explorer:local` — images nothing built. |
| F3 | WP5 kind smoke | The §8 system smoke **never ran in CI**. The `manifests` job renders kustomize only; the smoke was commented-out pending Dockerfiles. |
| F4 | §8 "import → read → action" | Engine integration coverage was **one test** (import→read); the **action path had zero integration coverage**. |
| F5 | Engine test suite | The only integration test carried `@Testcontainers(disabledWithoutDocker = true)` — a **silent skip** without Docker; nothing asserted it ran. |
| F6 | `LIMITATIONS.md` "as carefully written as ARCHITECTURE.md" | The reader-facing honesty doc **never said the corpus is synthetic**; the WP5 restore drill had **no evidence of ever being run**. |

(The review's supporting detail was imprecise in two places, corrected in Step 0:
the engine main tree is **3,450** lines not 4,193; DomainTest names **I3/I5/I7**,
not I1. Neither changes the finding.)

**What WP-R1 restored (all evidence executed against a real PostgreSQL 16; see the
WP-R1 checkpoint):**
- **F1** — registered the meter family in `metrics/EngineMetrics`
  (`palimpsest_import_claims_total` = claims *inserted*, plus `_duplicates_/_superseded_/_rejected_total`
  and the outbox gauges `palimpsest_outbox_pending_rows` / `_oldest_age_seconds`).
  Verified live at `/actuator/prometheus`: counter 0→49 on import, gauge 0 at rest.
- **F2** — multi-stage engine (JDK→JRE, non-root, actuator healthcheck) and explorer
  (Vite→nginx) Dockerfiles, pinned base images, `make images` + `kind-load`. Build
  stages validated (`mvn package`, `pnpm build`); image assembly deferred (no Docker
  daemon here — DEV-002).
- **F3** — `kind_smoke.sh` restored as a `main`-gated CI job with the image build
  preceding it, failing loudly (unchanged script).
- **F4/F5** — engine tests **8 → 23**, all executed, **0 skipped**: claim lifecycle
  (assert→dispute→supersede, folds read from the projection), ER merge (PP3 — no
  silent merge), identity-dispute→ER queue, the licence gate 403, I6 dangling-ref
  (entity count identical), fuzzy-time predicates, and property tests
  (effective-confidence determinism/order-independence; slider monotonicity).
  `disabledWithoutDocker` removed; tests now run against Testcontainers **or**
  `PALIMPSEST_TEST_JDBC_URL`, fail loudly if neither, with a named `-P no-it`
  opt-out and a CI floor/no-skip assertion (`scripts/check_engine_tests.sh`).
- **F6** — `LIMITATIONS.md` leads with the synthetic-corpus disclosure (8 entities /
  49 claims / 3 sources); the restore drill is now **exercised** with measured
  timings recorded in `OPERATIONS.md`.

**Incidental defects WP-R1 surfaced and fixed (flagged for ratification — each
easiest-to-reverse; the WP5 smoke could never have passed with them present):**
- **Probe path mismatch.** The engine answered probes only under `/api/v1/` while
  the k8s deployment and `kind_smoke.sh` hit `/healthz` / `/readyz` at the **root**
  → pods would never become Ready. Fixed additively: `ProbeController` now answers
  at **both** root and `/api/v1` (rebuild endpoint kept at `/api/v1`); `Filters`
  exempts both; SDK regenerated (drift gate green). *Interface addition (HANDOFF
  §7.2) — flagged.*
- **p95 histogram.** `http_server_requests_seconds` published only a summary (no
  `_bucket`), so the smoke's `histogram_quantile(...)` (step 7) would return no data.
  Enabled percentile histograms in `application.yaml`; buckets now present (local p95
  ≈ 14 ms).
- **Meter scope.** Step 2 named two meters; the same dashboard queried seven. WP-R1
  registered the whole import/outbox family so every engine-emitted panel is live,
  not just the two the smoke asserts. The anomaly counter (dashboard panel 31)
  remains **unwired** — its taxonomy is adapter-domain; wiring it engine-side would
  risk a fabricated classification, so it is deferred, not invented.

**What remains UNPROVABLE until the SDFB dump is supplied** (carried, not closed):
- **FIX-CORPUS** (15,882 / 261,177 / 187,482), **FIX-ANOMALY** (365 / 14 / 1,575 / 1
  / 6), **FIX-BACON** (29→13; 2 certainly-active) — measured in a prior Phase-0
  session, cited in ARCHITECTURE §1, asserted only by the slow suite against a
  locally-supplied dump; **no build or CI in this repo reproduces them**.
- The **§5.1 p95 SLO (< 300 ms) at real corpus scale** — the histogram is now
  measured, but only over the 391-row synthetic fixture; the SLO at 261k-claim scale
  is unmeasured.
- The **kind smoke green on `main`** — wired, but its execution needs a Docker-capable
  runner with kind capacity (see Q-12). "Passed on the branch" would not be evidence
  for `main` regardless (HANDOFF §3).

**Status:** proposed — awaiting owner ratification at the WP-R1 checkpoint. The
engine, store, and frozen contracts did **not** change to close any of this (the
design held; §12/§20 signal preserved).

## Build notes — implementer judgment calls (flagged, easiest-to-reverse)
- **Explorer unscored-toggle default ON.** Every attribute claim in this corpus is
  unscored; defaulting OFF would empty the primary claims list and hide the messy
  record the product exists to show (F2). ON renders their absence *with the reason*
  (PP4). Within D4's latitude; owner may flip.
- **Explorer possibly/certainly is a pure client-side highlight** (no re-query), so
  the canvas never empties (D2/A2); only window changes re-query the server.
- **Playwright pinned to 1.56.0** to match the preinstalled Chromium revision (1194).
- **Manual `scholar-note` source defaults to license_confirmed=false**, so the export
  gate errs closed on hand-asserted claims until the owner confirms that source. Safe
  by default; the owner confirms trusted sources via `source.license_confirmed`.
- **Bitemporal `asOfSystem` reads** (Flow E) are specified and the write-side guard
  (W8, 409) is enforced, but as-of *read* replay across all endpoints is not wired in
  this session — carried to the WP6 checkpoint.
