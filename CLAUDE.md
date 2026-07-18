# PALIMPSEST — agent pointer file

Short by design (Anthropic guidance: keep CLAUDE.md < 200 lines; path-scoped
rules in `.claude/rules/` load only with matching files). This is a pointer, not
a copy of the spec.

## The one gate that matters
**License gate.** `source.license_confirmed=false` ⇒ internal-use-only. **No
Ingress, no public URL, no published dataset, no hosted demo** while unconfirmed.
`/export/claims` refuses such sources with a 403. Enforced mechanically by a
pre-commit hook + CI (`scripts/check_no_ingress.sh`, `scripts/check_no_dump.sh`),
not by good intentions.

## Current work package
Part 1 build. Execution order (HANDOFF D7): **WP0 → WP1 → WP2 → WP2b → WP3 → WP4
→ user session → WP5 → WP6 → WP7 → WP9**. See `docs/HANDOFF.md` §9 for the plan.

## Standing rules (full list: HANDOFF §4)
1. **Contracts are frozen.** No edits to `contracts/`. Flyway V1 = `claim-schema.sql`
   verbatim; V2 is additive (§6.3) and nothing more. Change only via §2 process.
2. **Never invent a number.** If the spec doesn't state a value, ask — file a
   question in `docs/QUESTIONS.md`, don't guess. Every fixture assertion names its source.
3. **Unscored is absence, not zero** (I5). Never numeric, never ranked, never passes a threshold.
4. **The dump never enters git**, whole or in part. CI/demo fixtures are synthetic.
5. **No fabricated entities** (I6); no silent merges (PP3); absence renders as absence (PP4).
6. **Commit attribution: owner only.** No `Co-Authored-By` naming any AI tool, no
   "Generated with…" footers, no AI reference in commit messages or PR titles.

## Where things live
- Design authority: `docs/ARCHITECTURE.md` (v1.4). Process: `docs/HANDOFF.md`.
- Pinned wire interfaces every component shares: `docs/BUILD-CONTRACT.md`.
- Decisions + deviations: `docs/DECISIONS.md`. Open questions: `docs/QUESTIONS.md`.
- Contracts (frozen): `contracts/claim.schema.json`, `contracts/claim-schema.sql`.

## Guess rule
If you must guess at anything material, that is a spec defect — file it as a
question, take the easiest-to-reverse option for non-blocking items and flag it;
stop for blocking ones.
