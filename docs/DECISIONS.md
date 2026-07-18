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
