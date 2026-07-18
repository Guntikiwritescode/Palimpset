# PALIMPSEST

**An uncertainty-aware, bitemporal, provenance-complete knowledge graph over
early-modern Europe (1500–1700).** Every relationship and attribute is a
time-scoped, source-backed, confidence-weighted **claim**; two sources that
disagree yield two coexisting claims, never a silent merge; the archive's
survivorship bias is modeled explicitly, not hidden.

> *A palimpsest is a manuscript overwritten many times, with traces of earlier
> text still showing through — an incomplete, layered, contested record. That is
> what this system models, and how it models it.*

## The one interaction that carries the thesis

Search a historical figure → see their evidence-backed network → **drag a
confidence slider and watch weakly-attested relationships fall away** → click any
edge and land on the verbatim source record two clicks later → finish on a page
where the system states its own limits in live numbers.

![The confidence slider on Francis Bacon's ego network, windowed to 1600. The
result line states all three counts at once: ties not ruled out, ties required by
the evidence, and ties with no dates.](docs/img/slider-0.6.png)

*Above: the entity view at confidence ≥ 0.60. The honesty page (`docs/img/honesty-page.png`)
derives every figure live from the corpus. Screenshots are of the **synthetic**
fixture — the license gate forbids showing the real dump.*

## ⚠ License gate — read this first

One of the data sources (Six Degrees of Francis Bacon) has an **unconfirmed
license**. While that holds, PALIMPSEST is **internal-use only**:

- **No public deployment.** No Ingress, no public URL, no hosted demo, no
  published dataset. Access is `kubectl port-forward` only. This is enforced
  mechanically (a pre-commit hook + CI: `scripts/check_no_ingress.sh`), not by
  intention.
- **The dump never enters git**, whole or in part. CI and demo fixtures are
  **synthetic** — invented people and ties that reproduce the corpus's *shape*
  and *anomaly classes*, never a subset of the source (`scripts/check_no_dump.sh`).
- The data export API **refuses** license-unconfirmed sources with a 403 that
  names them; every response carrying source-derived content is badged in the UI.

The gate is not advisory. It is a designed property of the system.

## Architecture in one paragraph

PostgreSQL is the system of record; the graph is a projection (ADR-001). A Java 21
engine is the **sole write authority** and the only API anyone calls; it enforces
the invariants (I1–I8) in one place, emits a transactional outbox, and runs an
in-process projector. A Python pipeline turns external sources into interchange
claims and submits them through the engine. A generated TypeScript SDK is the
explorer's only path to the API. Deployed on Kubernetes (`kind`) with real
observability. Full design: `docs/ARCHITECTURE.md`.

## Repository layout

```
contracts/   claim.schema.json · claim-schema.sql            [FROZEN]
services/engine/  Java 21 — api · domain · store · importer · projector · actions · config
pipeline/    Python — adapters · harvest · submit · quality · schema · cli
sdk/typescript/   generated in CI; committed; never hand-edited
explorer/    React + TypeScript
deploy/      Kustomize bases + overlays (local · cloud-stub)
tools/       Phase-0 scripts, clearly marked NON-PRODUCTION
fixtures/    synthetic only — never source-derived
docs/        ARCHITECTURE · HANDOFF · DECISIONS · QUESTIONS · DATA · LIMITATIONS · OPERATIONS
analytics/   Python (WP7+)
```

## Quickstart (local, no public exposure)

```bash
# 0. one PostgreSQL 16 server on 127.0.0.1:5432
make dev-db            # create roles + the palimpsest database
make hooks             # install the no-dump / no-Ingress pre-commit gates

# 1. gates
make contracts         # schema self-validates, sample validates, checksums match
make migrations        # V1 (frozen) + V2 apply cleanly to an ephemeral PG 16

# 2. engine
make engine-run        # http://127.0.0.1:8080 ; OpenAPI at /v3/api-docs

# 3. load a synthetic fixture through the engine's import path
make fixture           # generate fixtures/synthetic/
python -m palimpsest_pipeline.cli ingest synthetic --data-dir fixtures/synthetic --run-id demo

# 4. explorer
make explorer-build
```

Real SDFB data is loaded only from a **locally-supplied dump** outside the tree
(never committed), and only for internal use while the license is unconfirmed.

## Status

Part 1 (the core platform) is under construction. Execution order:
**WP0 → WP1 → WP2 → WP2b → WP3 → WP4 → user session → WP5 → WP6 → WP7 → WP9.**
WP5 is the *portfolio-viable* milestone; WP7 the *portfolio-strong* milestone.
See `docs/HANDOFF.md` for the working agreement and `docs/QUESTIONS.md` for open items.
