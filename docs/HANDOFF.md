# PALIMPSEST — Implementation Handoff

**Architect → Claude Code · 2026-07-18 · Companion to `PALIMPSEST-System-Architecture-v1.md` (v1.4)**

---

## 0. What this document is

The architecture specification says **what to build**. This document says **how we work, what already exists, what has already been decided, and what to do when the spec is silent, stale, or self-contradicting.**

The spec was written across four versions and pressure-tested in four rounds. Later rounds corrected earlier text, and not every correction was propagated back into every paragraph. A reader working front-to-back will therefore encounter statements that have been overturned — including, in one case, an instruction to publish an unverified statistic on the page whose entire purpose is not doing that. §5 lists every such case found, with a ruling. **Read §5 before implementing any UI.**

**Precedence.** This extends the spec's §0 list. For matters of **design**, the order is unchanged: frozen contracts → architecture spec → product spec → Phase 0 worklog. For matters of **process, supersession, repository shape, and preflight**, this handoff sits directly below the frozen contracts and above the architecture spec. Where §5 records a supersession, the supersession governs.

**Reading order.**

1. This document, §§0–5. The operating agreement, the standing rules, and the defect register.
2. Architecture spec §§0, 1, 2, 3, 9, 10, 11 — orientation, contracts, components, work packages, review protocol, open items.
3. Architecture spec §§4–8 and §§12–21 — read what a work package depends on, when that work package starts. Do not read all 15,000 words before writing the first line of a scaffold.
4. `PALIMPSEST-Part-1-Core-Platform.md`, `-Part-2-Expansion.md`, `-Context-and-Decision-Record.md` — background. Authoritative for *why*, not for *what*.

---

## 1. Release status — complete this before WP0 begins

The spec's header reads v1.4 and states that implementation begins on release. Its closing line still reads *"End of specification v1.1 … in owner review, not released to implementation"* — stale text carried from v1.1 and never updated. **The closing line is void.** Release is governed by the block below.

### Ratification block — owner completes

| Decision | Substance | Status per spec §0 | Owner |
|---|---|---|---|
| ADR-001 | PostgreSQL is the system of record; the graph is a projection. No graph database exists in Part 1. | Ratified by conduct | ☐ ratify ☐ veto ☐ discuss |
| ADR-002 | Contract-first; Python pipeline; Java 21 engine from the first commit; no throwaway services. | Ratified by conduct | ☐ ratify ☐ veto ☐ discuss |
| ADR-003 | Local `kind` now; standing cluster deferred. | Ratified by conduct | ☐ ratify ☐ veto ☐ discuss |
| correspSearch amendment | correspSearch is the Part 1 correspondence layer in place of EMLO, which prohibits bulk use without permission. | Ratified by conduct | ☐ ratify ☐ veto ☐ discuss |
| ADR-004 | The engine owns **all** writes, including bulk import. The Phase 0 direct-DB loader is demoted to `tools/`. | Architect-decided, vetoable | ☐ ratify ☐ veto ☐ discuss |
| ADR-005 | Single-scholar identity: one human token, distinct pipeline/model tokens, real attribution in the event log. | Architect-decided, vetoable | ☐ ratify ☐ veto ☐ discuss |
| ADR-006 | Transactional-outbox projections, in-engine first; the Go recompute operator consumes the same outbox in Phase 3. | Architect-decided, vetoable | ☐ ratify ☐ veto ☐ discuss |

**Release line.** *"Architecture spec v1.4 and this handoff are released to implementation as of ____________. Work begins at WP0."* — signed: ____________

A veto on any line means the affected sections are revised before the dependent work package starts, not worked around.

---

## 2. Preflight

### 2.1 Artifacts the owner must supply — these cannot be regenerated

WP0's acceptance criterion is that the contracts are **byte-identical to the Phase 0 artifacts**. That criterion is unsatisfiable if the artifacts are not in hand. They were produced in a prior working session; this handoff cannot verify that they still exist.

| File | Destination | If missing |
|---|---|---|
| `claim.schema.json` (v0.1.0) | `contracts/` | **STOP.** Escalate. Do not reconstruct. |
| `claim-schema.sql` (executed clean on PG 16.14) | `contracts/` | **STOP.** Escalate. Do not reconstruct. |
| `sdfb_adapter.py` (Phase 0 prototype) | `tools/`, marked NON-PRODUCTION | Escalate — WP2's golden tests lose their semantic reference |
| Phase 0 loader script | `tools/`, marked NON-PRODUCTION | Escalate — WP1 acceptance loads Phase 0 data via tools |
| `phase0-worklog.md` | `docs/` | Escalate — measured facts lose their provenance |
| `PALIMPSEST-ADRs-001-003.md` | `docs/` | Escalate — seeds `DECISIONS.md` |
| `PALIMPSEST-System-Architecture-v1.md` | `docs/ARCHITECTURE.md` | Blocking |
| This handoff | `docs/HANDOFF.md` | Blocking |
| Part 1 / Part 2 / Context records | `docs/` | Non-blocking; background |
| The SDFB 2017-10-13 dump | **Not in the repository.** A local path outside the working tree, referenced by config, covered by `.gitignore`. | Blocks WP1/WP2 acceptance only |

**The reconstruction prohibition is load-bearing.** The architecture spec *describes* the contracts; it is not the contracts. A schema generated from the description would look right, pass its own tests, and have silently forked from the artifact that 261,177 real claims were validated against — invalidating the Phase 0 validation run as evidence. A missing contract file is an owner problem, not an implementation problem.

### 2.2 Toolchain

Verify and report versions before starting; do not work around a missing tool. Required: JDK 21, Python 3.12, Node (current LTS), Docker (Testcontainers and `kind` both need it), `kind`, `kubectl`, `kustomize`, a PostgreSQL 16 client, `git`.

### 2.3 Repository decisions the owner owns

- **Visibility.** §20 A5 establishes that the repository must not contain the dump under any subsetting. It does not settle public versus private. Recommended: private until the SDFB licence answer arrives — trivially reversible in one direction and not the other.
- **Licence for the project's own source code.** Not addressed anywhere in the spec. Owner decides; the file is created at WP0 or its absence is deliberate and stated.
- **Remote and default branch name.** Owner supplies.
- **Committed generated output.** The SDK drift gate fails on *uncommitted* drift, so the generated TypeScript client is committed. `.gitignore` covers the dump, `.env`, `CLAUDE.local.md`, local database volumes, and build output.

### 2.4 Owner action queue — start these now, they have lead time

| Action | Blocks | Lead time |
|---|---|---|
| Ask the SDFB project for written licence confirmation, citing their 2015 public statement (free of charge, non-commercial, no warranty, citation requested). §17.2 reduced this from "find the licence" to "confirm this statement still holds" — a short email. | Ingress, publication, the demo recording's "real data" claim | Weeks; may never arrive |
| Request the remaining SDFB tables from the Folger: relationship types with their own confidence, dates, justification and citation; categories; notes; sub-year date precision; **an alternate birth/death year per person** | Typed predicates; sub-year precision; **source-native competing life dates** | Weeks |
| Send the drafted EMLO permission request | Nothing in Part 1; an EMLO adapter only if granted | Weeks |
| Recruit one early-modern historian for a 45-minute session at WP4 exit (§20 A6) | The WP4 → WP6 gate; findings are binding on WP6 | Weeks to schedule |

The Folger request is the highest-value of these. An alternate birth/death year per person is a **source-native competing claim** — precisely what §20 A1 found the Part 1 corpus to be missing, and the thing without which the project's central thesis has nothing to demonstrate.

---

## 3. The working agreement

**Roles.** The owner is architect, reviewer, and sole committer of record. Claude Code implements. Claude Code does not decide architecture, does not resolve open items, and does not declare a work package done — it presents evidence and waits.

**One work package = one PR train.** Each WP ends at a **review checkpoint**: stop, post the report in §10's form, wait. Three checkpoints are mandatory architect sign-offs per spec §10 — end of **WP2** (the import path is the riskiest surface), end of **WP5** (Phase 1 exit), end of **WP7** (the ER operating point is a judgment call). *Added here:* **WP0 is a fourth**, because it fixes repository conventions, CI shape, and the contents of `contracts/` — all cheap to change at WP0 and expensive afterwards.

**Every PR description states** which §§ of the spec it implements, the acceptance evidence, and any deviation.

**Acceptance evidence means the artifact, not a description of it.** The command run and the output it produced, pasted. Measured numbers with the conditions they were measured under. Screenshots for UI work. Test counts with failures shown rather than summarised. "Works on my machine" is not evidence.

**If an acceptance criterion cannot be met, stop and say so.** Do not redefine it, substitute a weaker one, or mark it partially met and continue. Named fixtures exist so that "done" means the same thing on every run.

**Deviations are proposed, not implemented.** A deviation record — context, proposed change, why, blast radius — is appended to `docs/DECISIONS.md`, and the work is blocked until the architect approves. The test for whether something is a deviation: *would the architect be surprised to find this when reading the diff?*

**Questions are logged, not guessed.** The spec's own standard: *if Claude Code must guess at anything material, that is a defect in the document — file it as a question, not a guess.* Questions live in `docs/QUESTIONS.md`, one row each: `Q-NNN · date · WP · spec reference · question · what it blocks · options considered · recommendation · status · answer`.

- **Blocking** — the work cannot proceed correctly without an answer. Stop, post, wait.
- **Non-blocking** — record it, take the option that is **easiest to reverse**, flag the choice in the PR, carry the question to the checkpoint.

**Scope discipline.** Nothing from a later work package enters an earlier one, including "while I was in there." An improvement a later WP would enable is a note in the checkpoint report, not a commit.

---

## 4. Standing rules

Ten rules. None negotiable inside a work package; all visible in a diff.

1. **The contracts are frozen.** No edits to `contracts/`. Flyway V1 is `claim-schema.sql` verbatim; V2 is the additive migration in §6.3 and nothing more. Further schema change requires §2's change-control process with owner sign-off.
2. **Never invent a number.** If the spec does not state a value, ask. This project has already caught its own author on exactly this: `CA = ±5 years` was a figure the source never states, affecting 107 claims, caught by the project's own no-fabricated-numbers rule. Every fixture assertion names where its number comes from.
3. **Unscored is absence, not zero** (I5). Never given a numeric value, never ranked among scored claims, never passes a numeric threshold filter.
4. **The licence gate is mechanical, not advisory.** No Ingress exists. `/export/claims` refuses `license_confirmed=false` sources with an actionable 403 naming them. Every response carrying source-derived content carries the flag so the UI can badge it (I8).
5. **The dump never enters git**, in whole or in part. CI and demo fixtures are **synthetic** — invented people and ties reproducing the corpus's *shape* and *anomaly classes*, not a subset of SDFB (§20 A5).
6. **No fabricated entities** (I6): an unresolvable reference rejects its claim with a per-line error; the engine never creates an entity as a side effect of a relation. **No silent merges** (PP3). **Absence renders as absence** (PP4): an empty state states *why*.
7. **Claims are immutable** (I1); every write is transactional with its event and outbox rows (I2); the status fold is derived, never independently authoritative (I3).
8. **Commit attribution.** Commits, PR descriptions, and repository metadata carry the owner's authorship only. No `Co-Authored-By:` trailers naming Claude or any AI tool, no "Generated with…" footers, no reference to AI assistance in commit messages or PR titles. This is the owner's standing rule for this repository. How the owner describes tooling in the README or an application is the owner's decision and outside the implementer's scope.
9. **No secrets in images, manifests, or git history.** Bearer tokens and the database password are Kubernetes `Secret` objects, referenced not embedded. Postgres roles are `engine_rw`, `analytics_ro`, `migrate` — least privilege enforced at the database, not by convention.
10. **Tests state their expectation before they observe it.** A test whose expected value was copied from the first run's output proves only that the code is deterministic.

---

## 5. Defect and supersession register

Eleven findings from a verification pass over v1.4. **The ruling column governs.** Two need owner rulings and reappear in §6.

Items marked *stale text* have already been decided elsewhere in the spec and simply were not propagated; Claude Code should propose the corrections as a **single documentation PR at WP0** rather than silently building to one side of each contradiction. Items marked *gap* were never decided.

| # | Kind | Where | The problem | Ruling |
|---|---|---|---|---|
| **D1** | **Critical — stale text** | §16 Panel 3 vs §17.2 S4 | The honesty page is instructed to state *"a lowest-possible precision around 28% across its 10–100 band"* as "the source's own reported figure." §17.2 explicitly retracts it: recorded in Phase 0, **could not be re-verified**, and *"must not be quoted until someone reads the paper directly."* As written the spec requires publishing an unverified statistic on the one screen whose purpose is not doing that. The same passage flags that the 86% / 40–100 / n=50 figure is quoted from a search excerpt and needs confirming against the original. | **Omit both figures** until someone reads the paper. State that calibration is Phase 3 work without quoting a precision number. **Owner may override — see Q-1.** |
| **D2** | Stale text | §5.5 vs §13.2(b), §20 A2 | ConfidenceControls in §5.5 still lists a `possibly \| certainly` **toggle** mapping to the two SQL predicates; §13.2(b) was corrected and §5.5 was not. | Certainty is **not a filter mode**. Certainly-active edges are **highlighted within** the possibly-active graph; the certainly-active count stays permanently in the result line. Only 313 of 171,600 relationship claims (0.18%) can ever be certainly-active — a toggle blanks the canvas and reads as broken. |
| **D3** | Stale text | §13.3 "Temporal mode" vs §20 A2 | Still describes a toggle the user switches into, and illustrates it with *"13 → 4 ties."* The **4 matches no measured fixture**: FIX-BACON records **2** certainly-active at ≥ 0.60 in 1600. | The paragraph's **semantics** stand — *permits* versus *requires* is the distinction the UI must not blur. Its **toggle mechanics** are void per D2. Do not hard-code 13 → 4 anywhere. |
| **D4** | Stale text | §5.5, §13.2(b)(c) vs §13.1, §20 A3 | The unscored toggle still sits inside ConfidenceControls with canvas semantics ("dashed edges"). Every relationship claim in this corpus is scored, so it has no canvas effect. §13.1 was updated; these were not. | Scope the toggle to the **claims list**, where unscored attribute claims actually live. Implement the dashed-edge grammar but leave it unwired; it earns a canvas control when WP2b or WP9 introduces genuinely unscored relationship claims. **Residual ambiguity — Q-2.** |
| **D5** | **Gap** | §5.1 endpoint table vs §13.2, §20 A2, §20 A7 | The result line must state possibly-, certainly-, and undated-counts **simultaneously** (*"29 ties not ruled out in 1600 · 2 are required by the evidence · 3 carry no dates at all"*). But `GET /entities/{id}/network` takes a single `temporalMode` value. Nothing specifies whether the endpoint returns all three counts, the explorer issues two requests, or the client derives them from returned bounds. | **Needs a ruling before WP1's network endpoint is final — Q-3.** |
| **D6** | Stale text | §13.3 "Fuzzy dates", §16 Panel 5 vs §17.2, WP2 acceptance | Circa dates still render as *"c. 1561 (±5) with the window policy stated"*, and the honesty page still lists the ±5 policy as a stated choice — contradicting WP2's own acceptance criterion, *"no claim contains a ±5 window."* | Render `c. 1561` with an **`approximate` marker** and no window. Affects 107 claims (75 start, 32 end). |
| **D7** | Stale text | §9 table row order vs §9 dependency note, §20 | The WP table lists WP2b physically after WP7, and WP8 no longer exists. | Execution order is **WP0 → WP1 → WP2 → WP2b → WP3 → WP4 → user session → WP5 → WP6 → WP7 → WP9.** The second source precedes the explorer. **WP5 is the *portfolio-viable* milestone, WP7 the *portfolio-strong* milestone** — each an explicit decision point about whether to keep building or ship what exists. |
| **D8** | Stale text | §13.1 search rows | Earlier drafts advertised an unscored tie count alongside the scored one. | Search rows show a **scored** tie count only. A counter that always reads zero teaches the user the interface is decorative. |
| **D9** | Stale text | Closing line of the spec | "End of specification v1.1 … in owner review, not released to implementation." | Void. Release status is §1 of this handoff. |
| **N1** | **Not a contradiction — do not "fix" it** | §13.2(b) vs §14.1 | The slider default is **0**; the demo storyboard opens at 0.60. | Correct as written. The default is 0 because 0.6 renders 7,125 of 15,882 entity pages (44.9%) empty, and 0.9 renders 84% empty. The demo's 0.6 comes from a **pre-set URL** — which is also why §13.4 requires all four controls to live in the URL. |
| **N2** | **Not a contradiction — do not "fix" it** | §9 WP1 acceptance vs ADR-004 | WP1 loads Phase 0 data "via tools" while ADR-004 says the engine owns all writes. | Correct as written. The `tools/` loader is how data gets in *before* the engine's import path exists at WP2. It is marked NON-PRODUCTION, is never invoked by the engine, pipeline, or CI, and is retired from the loading role the moment WP2 lands. |

**The standing caveat that touches the most surface area.** 99.7% of relationship intervals derive from the single date code `AF/IN`, whose semantics remain an **interpretation** — independently corroborated by an external converter processing the same CSVs, but not confirmed against SDFB's own code. Every date rendered at size carries a *derived* marker linking to the method note; life dates are unscored claims and render with an *unscored* chip, not as bare fact. A correction would require re-deriving roughly 171,000 claims' bounds — cheap, because originals are preserved verbatim, but it must happen before any temporal finding is published.

---

## 6. Open questions for the owner

Seven items the spec does not settle. Each carries a recommendation so none of them stalls WP0; each needs a ruling before the work package that depends on it. **These are questions, not decisions.**

**Q-1 · The retracted precision figure (D1).** Omit it from the honesty page, or read the source paper and confirm it first? *Absent a ruling: omitted.* **Blocks WP4.**

**Q-2 · Scope of the unscored toggle (D4).** *Recommendation:* WP4 ships the toggle on the claims list only; the dashed-edge grammar is implemented but unwired, with a test asserting it renders correctly when given synthetic unscored edges. **Blocks WP4.**

**Q-3 · The multi-count result line (D5).** Does `/entities/{id}/network` return possibly / certainly / undated counts in one response, or does the explorer make two calls and derive the third? *Recommendation:* the endpoint returns all three counts in `meta` — the client-side slider (§13.4) already holds the full edge set, so a second round trip on every window change would defeat the interaction's whole point. **Blocks WP1.**

**Q-4 · Fixtures addressed by internal id.** §4 Flow B and WP1's acceptance criterion pin entity **`15429`**, an internal primary key — but FIX-BACON and FIX-DISAMBIG are defined by **SDFB ids** `10000473` and `10000475`. Internal ids are assigned at load time and need not survive a rebuild, nor the WP1 (`tools/` loader) → WP2 (engine import) transition. *Recommendation:* every fixture resolves through `GET /entities/lookup?authority=sdfb&externalId=…`; `15429` survives only as an illustrative example in prose. **Blocks WP1 acceptance.**

**Q-5 · Who builds the synthetic fixture, and when.** §20 A5 and §14.3 require a synthetic fixture for CI and `make demo`; §8 requires a 1,000-claim fixture for the kind smoke. **No work package owns building it**, and it must reproduce the corpus's shape *and its anomaly classes* — which is adapter knowledge. *Recommendation:* a minimal stub at WP0 (enough for CI to be green), the real synthetic fixture built in **WP2** alongside the production adapter, extended at WP2b. **Blocks WP2 and everything downstream.**

**Q-6 · The E2E fixture contradiction.** §8 requires "one Playwright-style E2E … slider 0.6→0.9 shows **29→13** for the Bacon fixture," while §20 A5 states the CI fixture is synthetic and reproduces "the *interaction*, not the numbers," with measured FIX-\* assertions moved to "the slow suite against a locally-supplied dump." These cannot both hold. *Recommendation:* the CI E2E asserts the **interaction** against synthetic data — edge count strictly decreases across the drag, exact counts defined by the fixture itself — and 29→13 moves to the slow suite. **Blocks WP4, and the CI design at WP0.**

**Q-7 · Which work package owns the projector.** §3.4 and §5.1 define it and §6.3 requires a `rebuild-projections` command, but WP1's scope names only migrations, domain model, read endpoints, problem+json, pagination, OTel and Testcontainers — while WP1's *acceptance* requires a working `search`, which reads `entity_summary`, which only the projector or `rebuild-projections` populates. *Recommendation:* WP1 includes `rebuild-projections` and the `entity_summary` materializer; the outbox-consuming loop follows at WP2 when writes begin. **Blocks WP1.**

Also outstanding, and owner-only: **repository visibility**, **the source-code licence** (§2.3), and **the four lead-time actions** in §2.4.

---

## 7. Repository shape, delegation, and agent configuration

### 7.1 Delegated to Claude Code — no deviation record needed

Java build tool · Python packaging and dependency manager · JavaScript package manager · the specific OpenAPI-to-TypeScript generator (within "typed, fetch-based, tree-shakeable") · the graph rendering library (within: handles 500 edges interactively, supports enter/exit animation, node and edge click handlers) · internal module and file naming · test helper design · CI platform specifics. Chosen versions are pinned and recorded in the WP0 report.

### 7.2 Not delegated

Anything that changes an interface shape, an invariant (I1–I8), a contract, a product promise (PP1–PP6), a stated performance bound, or a rendering grammar's *meaning*. Restyling a grammar is allowed; re-semanticising one is not.

### 7.3 Top-level layout

Assembled from the per-service trees in spec §5. Claude Code fills in the internals; this is the shape the owner reviews at WP0.

```
palimpsest/
  contracts/          claim.schema.json · claim-schema.sql            [FROZEN]
  services/engine/    Java 21 — api · domain · store · importer · projector · actions · config
  pipeline/           Python — adapters · harvest · submit · quality · schema · cli
  analytics/          Python                                          [WP7+]
  sdk/typescript/     generated in CI; committed; never hand-edited
  explorer/           React + TypeScript
  deploy/             Kustomize bases + overlays (local · cloud-stub); README with runbook
  tools/              Phase 0 scripts, clearly marked NON-PRODUCTION
  fixtures/           synthetic only — never source-derived
  docs/               ARCHITECTURE.md · HANDOFF.md · DECISIONS.md · QUESTIONS.md
                      DATA.md · LIMITATIONS.md · OPERATIONS.md · phase0-worklog.md
  CLAUDE.md           short pointer file — see 7.5
  .claude/rules/      path-scoped rules
  README.md           thesis · slider screenshot · quickstart · the licence gate, stated plainly
```

### 7.4 Conventions

**Branches:** `wp0/…`, `wp1/…`, one per PR in the train. **Commits:** conventional prefix, imperative mood, WP in the scope — `feat(wp1): add effective-confidence resolution per I7`. No AI attribution anywhere (rule 8). **PR title:** `WP<n> · <short scope>`. **PR body:** sections implemented, acceptance evidence, deviations, plus any non-blocking question IDs carried forward.

`docs/DECISIONS.md` opens as a copy of `PALIMPSEST-ADRs-001-003.md`, extended with ADR-004/005/006 as ratified in §1, and receives every deviation record thereafter. `docs/DATA.md` and `docs/LIMITATIONS.md` are created at WP0 with their audiences stated and filled at WP2 and WP4. LIMITATIONS.md is authored once and rendered live as `/about` — the same content, not written twice.

### 7.5 Agent configuration

Anthropic's Claude Code documentation states that CLAUDE.md files should **target under 200 lines**, because longer files consume more context and reduce adherence, and recommends `.claude/rules/` with `paths` YAML frontmatter for instructions that should load only when Claude works with matching files (https://code.claude.com/docs/en/memory).

So **`CLAUDE.md` is a pointer file, not a copy of this document.** It carries: the licence gate in one line, the current work package, the guess rule, the commit-attribution rule, and paths to the specification and this handoff. Path-scoped rules carry the rest — one scoped to `contracts/**` stating the files are frozen; one to `explorer/**` carrying the rendering grammars and microcopy rules; one to `pipeline/adapters/**` carrying the normative SDFB mapping rules and the never-invent list; one to `deploy/**` carrying the no-Ingress rule.

The same documentation is explicit that these files are **context, not enforced configuration** — to block an action regardless of what the model decides, use a hook. So the two hard gates — **no dump in the repository, no Ingress in any manifest** — belong in a pre-commit hook and a CI check, not only in prose.

---

## 8. WP0 in detail

**Spec scope:** monorepo layout per the §5 trees; `contracts/` (the two frozen files, verbatim); `tools/` (Phase 0 scripts, marked non-production); `docs/`; CI running the contract gate and linters. **Spec acceptance:** CI green on a trivial change; contracts byte-identical to the Phase 0 artifacts; README states the licence gate.

Added here, and small:

- `docs/QUESTIONS.md` and `docs/DECISIONS.md`, seeded — DECISIONS with ADRs 001–006; QUESTIONS with Q-1…Q-7 above plus whatever the sweep in §9 finds.
- The **documentation-correction PR** for D2, D3, D4, D6, D7, D8, D9 — substance already decided, owner approves the edits.
- `.gitignore`; `CLAUDE.md` and `.claude/rules/` per §7.5; the no-dump and no-Ingress hooks.
- A **preflight report** (§2): artifacts present or missing, tool versions, repository decisions.
- A minimal synthetic fixture stub sufficient for CI to be green, pending the Q-5 ruling.

**The contract gate is the CI job that matters:** JSON Schema self-validation, the sample-claims fixture validating against it, V1+V2 applying cleanly to an ephemeral PostgreSQL 16, and a byte-identity check against the Phase 0 files — so that "frozen" is enforced by the pipeline rather than by good intentions.

---

## 9. Session one, in order

1. **Confirm the release block (§1) is signed.** If it is not, stop and say so.
2. **Preflight (§2).** Report artifacts, tool versions, repository decisions. If a contract file is missing, stop here.
3. **Scoped spec-defect sweep.** Read the spec's §§0–11 and §§12–16, plus this document, with one question in mind: *what would I have to guess at to implement WP0 and WP1?* Scope it to **WP0 and WP1 only** — later work packages get their sweep when they start. Write findings to `docs/QUESTIONS.md` as new `Q-` rows.
   Two constraints. This is a **defect log, not a redesign**: do not propose alternative architectures and do not reopen anything §5 has ruled on. And per §20 A10 the specification is not the deliverable — remaining detail is produced *inside* work packages as implementation questions arise, not in advance. **Time-box by output:** if the sweep starts producing design proposals, it has failed; cut it short.
4. **Post the question list and the WP0 plan together, and stop.**
5. **On the owner's answers, build WP0**, then post the §10 report and stop.

---

## 10. The checkpoint report

At the end of every work package, post this and wait. No prose summary substitutes for it.

```
WP<n> — checkpoint

SCOPE       sections of ARCHITECTURE.md implemented
EVIDENCE    per acceptance criterion: the measured result and how it was measured
            (command + output, counts, p95 figures, screenshots for UI)
FIXTURES    which named fixtures ran, and their results
DEVIATIONS  deviation record IDs, with approval status
QUESTIONS   open Q- IDs, blocking and non-blocking, carried forward
NOT DONE    anything in scope not completed, and why
NEXT        the next work package, and what it needs from the architect first
```

---

## 11. Tripwires

Reaching any of these means stopping and escalating, not improvising a way through.

- **Any move toward public exposure** — an Ingress, a public URL, a published dataset, a hosted demo — while `license_confirmed=false`. The gate is mechanical, but the gate does not know your intentions.
- **A finding that contradicts the `AF/IN` / `BF/IN` interpretation.** The desk check of SDFB's own source belongs to WP2, and its findings go to the architect **before the WP2 merge**, not after.
- **The measured figures failing to reproduce.** FIX-CORPUS is 15,882 entities / 261,177 claims / 187,482 source records. FIX-ANOMALY is 365 / 14 / 1,575 / 1 / 6. Drift means the adapter changed behaviour — investigate and report. Never adjust the expected values to match.
- **Any temptation to fabricate**: an entity to satisfy a dangling reference, a date window the source never states, a confidence number for an unscored claim, a citation, a statistic on the honesty page. The honesty page derives every figure live — *a grep for hard-coded statistics fails WP4*.
- **A schema change that is not V2.** Contracts are frozen; §2's change-control process is the only route.
- **Ambiguity a coin-flip would resolve.** That is the definition of a blocking question.

---

## 12. What "good" looks like at the end of Part 1

Not a checklist — the shape of the thing, so intermediate decisions can be made against it. A reviewer opens the demo recording and watches someone search a historical figure, find two people with the same name and tell them apart in one interaction, drag a slider and see weakly-attested relationships fall away, click an edge and land on the verbatim source row two clicks later, and finish on a page where the system states its own limits in live numbers. Behind that: an immutable claim store where two sources that disagree produce two claims; an engine that is the sole write authority and enforces its invariants in one place; a pipeline whose every run traces to the claims it produced; a Kubernetes deployment with real dashboards and an exercised restore drill; and a repository whose `LIMITATIONS.md` is as carefully written as its `ARCHITECTURE.md`.

The strongest evidence that this design is sound is already in the record: across four rounds of pressure-testing, every correction landed in an adapter, a projector rule, or a UI grammar. **Not one finding required changing the engine, the store, or the contracts.** Keep it that way — and when something finally does force a change to one of those three, that is not a nuisance to route around. It is the most important signal the project will produce.

---

*This handoff governs process. `PALIMPSEST-System-Architecture-v1.md` governs design. The frozen contracts govern both. If following this document requires a design decision the specification does not make, that is a specification defect — file it, and do not resolve it by building.*
