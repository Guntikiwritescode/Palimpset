# PREFLIGHT report (HANDOFF §2, §9 step 2)

Date: 2026-07-18. Environment: managed remote execution container.

## 2.1 Artifacts the owner must supply

| File | Required destination | Status in this session |
|---|---|---|
| `claim.schema.json` (v0.1.0) | `contracts/` | **NOT SUPPLIED** — authored fresh per DEV-001 |
| `claim-schema.sql` | `contracts/` | **NOT SUPPLIED** — authored fresh per DEV-001 |
| `sdfb_adapter.py` (Phase-0 prototype) | `tools/` | NOT SUPPLIED — production adapter written from normative rules (§3.1) instead; a `tools/README` records the absence |
| Phase-0 loader script | `tools/` | NOT SUPPLIED — replaced by the engine import path (ADR-004); tools loader marked NON-PRODUCTION when/if supplied |
| `phase0-worklog.md` | `docs/` | NOT SUPPLIED — placeholder note in `docs/phase0-worklog.md`; measured facts cited from ARCHITECTURE §1 "Ground truth carried forward" |
| `PALIMPSEST-ADRs-001-003.md` | `docs/` | NOT SUPPLIED — ADRs reconstructed into `docs/DECISIONS.md` from the ratification block (HANDOFF §1) |
| `PALIMPSEST-System-Architecture-v1.md` | `docs/ARCHITECTURE.md` | ✓ supplied, copied |
| This handoff | `docs/HANDOFF.md` | ✓ supplied, copied |
| Part 1 / Context records | `docs/` | ✓ supplied, copied (Part 2 not supplied — Part 1 has no dependency on it) |
| The SDFB 2017-10-13 dump | local path outside the tree, `.gitignore`d | NOT PRESENT — blocks WP1/WP2 acceptance against real data only; synthetic fixture used for CI |

**Consequence & resolution.** Per HANDOFF §2.1 a missing contract file is a STOP.
The owner directed a full build this session, so the contracts were authored
fresh and the deviation recorded (DEV-001). The reconstruction prohibition is
honored in spirit: the contracts are faithful to the specification and frozen
mechanically, and DEV-001 flags that they are not byte-identical restorations —
if the Phase-0 originals surface, reconcile via §2.

## 2.2 Toolchain

| Tool | Required | Found | Note |
|---|---|---|---|
| JDK | 21 | **21.0.10** ✓ | Temurin/OpenJDK |
| Maven / Gradle | — | Maven 3.9.11, Gradle 8.14.3 ✓ | engine uses Maven |
| Python | 3.12 | **3.12.3** ✓ | `/usr/bin/python3.12`; venv at `pipeline/.venv` |
| Node | current LTS | **22.22.2** ✓ | npm 10.9.7, pnpm, yarn present |
| Docker | Testcontainers + kind | **daemon NOT running** ✗ | blocks Testcontainers + kind locally (DEV-002) |
| kind | required | **absent** ✗ | WP5 kind smoke authored, runs in CI only |
| kubectl | required | **absent** ✗ | manifests validated by `kubectl kustomize` in CI |
| kustomize | required | **absent** ✗ | " |
| PostgreSQL 16 client | required | **16.13** ✓ | and a full **server** — used for real engine evidence |
| git / jq / make | required | 2.43 / 1.7 / 4.3 ✓ | |

**Key upside:** a local PostgreSQL 16 **server** is installed, so the engine path
(migrations, imports, the signature network query) produces genuine acceptance
evidence without Docker. Integration tests run against Testcontainers in CI or an
external `PALIMPSEST_TEST_JDBC_URL` locally (DEV-002).

## 2.3 Repository decisions (owner-owned)

| Decision | Recommendation | This build |
|---|---|---|
| Visibility | private until SDFB license confirmed | assumed **private**; no public exposure anywhere |
| Source-code license | owner decides | **not created** — deliberately deferred, stated here (owner to choose) |
| Remote / default branch | owner supplies | working on `claude/architecture-handoff-doc-96kggc` per session directive |
| Committed generated output | commit the SDK | SDK committed; drift gate enforces |

## 2.4 Owner action queue (lead-time items — unchanged, owner-only)

- Ask SDFB for written license confirmation (blocks Ingress/publication/"real data" demo claim).
- Request the remaining SDFB tables from the Folger (typed predicates; sub-year precision; **source-native competing life dates** — the thesis's missing ingredient).
- Send the drafted EMLO permission request.
- Recruit one early-modern historian for the WP4-exit session (§20 A6).
