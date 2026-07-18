# QUESTIONS — implementation questions for the owner

One row each (HANDOFF §3). Format: `Q-NNN · date · WP · spec ref · question ·
blocks · options · recommendation · status · answer`.

Because the owner directed a full build this session, each **blocking** question
was resolved by **adopting the handoff's recommended answer** (DEV-003). **The
owner RATIFIED these on 2026-07-18** — Q-1…Q-7 answers stand as the adopted
recommendations; Q-8…Q-10 (non-blocking) are accepted. Status `ratified` marks
each below.

---

### Q-1 · 2026-07-18 · WP4 · D1 (§16 Panel 3 vs §17.2 S4) — the retracted precision figure
- **Q:** Omit the "≈28% lowest-possible precision / 86% n=50" figures from the honesty page, or read the source paper and confirm first?
- **Blocks:** WP4. **Options:** omit / confirm-then-quote.
- **Recommendation & adopted:** **Omit both** until someone reads the paper directly; the honesty page states calibration is Phase-3 work without quoting a precision number. A grep for hard-coded stats fails WP4 regardless.
- **Status:** RATIFIED (owner 2026-07-18).

### Q-2 · 2026-07-18 · WP4 · D4 (§5.5, §13.2 vs §13.1, §20 A3) — scope of the unscored toggle
- **Q:** Where does the unscored toggle live, and is the dashed-edge canvas grammar wired?
- **Blocks:** WP4. **Options:** claims-list only + unwired canvas grammar / full canvas control now.
- **Recommendation & adopted:** toggle on the **claims list only**; the dashed-edge grammar is implemented but **unwired**, with a test asserting it renders for synthetic unscored edges. It earns a canvas control at WP2b/WP9.
- **Status:** RATIFIED (owner 2026-07-18).

### Q-3 · 2026-07-18 · WP1 · D5 (§5.1 vs §13.2, §20 A2/A7) — the multi-count result line
- **Q:** Does `/entities/{id}/network` return possibly/certainly/undated counts in one response, or does the client make two calls?
- **Blocks:** WP1. **Options:** one response (counts in meta) / two calls.
- **Recommendation & adopted:** **one response — all three counts in `meta.counts`.** The client-side slider already holds the full edge set; a second round trip per drag would defeat the interaction.
- **Status:** RATIFIED (owner 2026-07-18) (implemented in WP1 network endpoint).

### Q-4 · 2026-07-18 · WP1 · §4 Flow B / WP1 acceptance — fixtures addressed by internal id
- **Q:** Fixtures pin internal id `15429`, but FIX-BACON/FIX-DISAMBIG are SDFB ids `10000473`/`10000475`; internal ids need not survive a rebuild.
- **Blocks:** WP1 acceptance. **Options:** pin internal id / resolve via external id.
- **Recommendation & adopted:** every fixture resolves through `GET /entities/lookup?authority=sdfb&externalId=…`; `15429` survives only as illustrative prose.
- **Status:** RATIFIED (owner 2026-07-18) (lookup endpoint implemented; tests key off external id).

### Q-5 · 2026-07-18 · WP2 · §20 A5, §14.3, §8 — who builds the synthetic fixture, and when
- **Q:** No WP owns building the synthetic CI fixture (shape + anomaly classes).
- **Blocks:** WP2 and everything downstream. **Options:** stub at WP0 / full fixture at WP2.
- **Recommendation & adopted:** minimal stub at WP0 (CI green); the real synthetic fixture (generator) built in **WP2** alongside the production adapter; extended at WP2b.
- **Status:** RATIFIED (owner 2026-07-18).

### Q-6 · 2026-07-18 · WP4/WP0 · §8 vs §20 A5 — the E2E fixture contradiction
- **Q:** §8 wants "29→13 for the Bacon fixture" in the CI E2E; §20 A5 wants the CI fixture synthetic (interaction, not numbers).
- **Blocks:** WP4, CI design at WP0. **Options:** measured numbers in CI / synthetic interaction in CI.
- **Recommendation & adopted:** the CI E2E asserts the **interaction** on synthetic data (edge count strictly decreases across the drag; exact counts defined by the fixture); 29→13 moves to the slow suite against a locally-supplied dump.
- **Status:** RATIFIED (owner 2026-07-18).

### Q-7 · 2026-07-18 · WP1 · §3.4, §5.1, §6.3 — which WP owns the projector
- **Q:** WP1 acceptance needs a working `search` (reads `entity_summary`), which only the projector/`rebuild-projections` populates, but WP1 scope doesn't name the projector.
- **Blocks:** WP1. **Options:** projector at WP1 / defer.
- **Recommendation & adopted:** WP1 includes **`rebuild-projections`** and the `entity_summary` + name-search materializers; the outbox-consuming loop follows at WP2 when writes begin.
- **Status:** RATIFIED (owner 2026-07-18) (implemented in WP1).

---

## Sweep findings (WP0/WP1 scope, per HANDOFF §9 step 3)

Defect log, not redesign. Nothing here reopens a §5 ruling.

### Q-8 · 2026-07-18 · WP1 · §5.1 — pagination sort key for `/search` and list endpoints
- **Q:** Envelope specifies opaque cursor over "a stable sort key" but the key per endpoint is unspecified.
- **Blocks:** nothing (non-blocking). **Adopted:** search sorts by `(similarity desc, entity_id asc)` with the cursor over `entity_id` within a fixed `q`; entity claim lists sort by `(predicate, claim_id)`; `/events` by `(occurred_at desc, id desc)`. Documented in the engine; easiest-to-reverse.
- **Status:** RATIFIED (owner 2026-07-18) — accepted, non-blocking.

### Q-9 · 2026-07-18 · WP1 · §5.1 `/entities/{id}` — display-name determinism when an entity has 0 name claims
- **Q:** §17.F1 fixes the display-name rule for ≥1 name claim; a synthetic entity could have none.
- **Blocks:** nothing. **Adopted:** an entity with no name claim renders `display_name = "unnamed entity #<id>"` in `entity_summary` and the coverage panel notes it; real SDFB persons always have a display name so this only affects malformed fixtures.
- **Status:** RATIFIED (owner 2026-07-18) — accepted, non-blocking.

### Q-10 · 2026-07-18 · WP2 · §3.1 — `born`/`died` object form
- **Q:** SDFB gives life *years* with no place; is `born` object a literal year or an entity (place)?
- **Blocks:** WP2 adapter. **Adopted:** `born`/`died` are attribute claims with `object.literal {kind:"year"}` and the year carried in `valid_time` (start/end bounds per the four-date model); no place entity is fabricated (I6). Revisited if the Folger tables add places.
- **Status:** RATIFIED (owner 2026-07-18) — accepted, non-blocking.

---

## WP-R1 findings (evidence remediation session)

### Q-11 · 2026-07-18 · WP-R1/WP4 · §7.4 vs reality — `/about` does not render `LIMITATIONS.md`
- **Q:** HANDOFF §7.4 (and `LIMITATIONS.md`'s own header) claim the file is "authored once and rendered live as `/about` — the same content, not written twice." It is not: `/about` (`explorer/src/routes/About.tsx`) derives its *figures* live but its *prose* is hand-authored JSX, independent of the markdown. The two are maintained separately, and until WP-R1 **neither** stated in words that the loaded corpus is synthetic.
- **Blocks:** nothing (non-blocking; the figures are live and correct). **Options:** (a) render `LIMITATIONS.md` into `/about` (one source); (b) generate the markdown from the JSX; (c) accept two surfaces, keep in sync by review.
- **WP-R1 action:** the synthetic-data disclosure was added to `LIMITATIONS.md` (F6) and the false "authored once" claim there corrected to describe reality. Unifying the surfaces is product work, out of WP-R1 scope.
- **Recommendation:** (a). **Status:** OPEN — carried to the WP-R1 checkpoint.

### Q-12 · 2026-07-18 · WP-R1/WP5 · §8 — can a GitHub-hosted runner host the full kind smoke?
- **Q:** The kind smoke is wired as a `main`-gated CI job, but a standard `ubuntu-latest` runner (2 vCPU / 7 GB) must host a kind cluster running Postgres + engine + explorer + otel-collector + prometheus + grafana + postgres-exporter, build two images, deploy, import 1k claims, and query Prometheus within job limits. Whether that fits is unverified (this environment has no Docker/kind — DEV-002 — so it could not be exercised; and a branch pass is not evidence for `main`).
- **Blocks:** the WP5 "kind smoke green on `main`" acceptance only. **Options:** (a) run as-is on `ubuntu-latest`; (b) a larger runner; (c) a CI-only overlay trimming Grafana (keeping Prometheus, which steps 7–8 require) to fit memory.
- **Smallest honest alternative if it OOMs:** (c) — trim Grafana only; do **not** weaken the assertions (p95 measured, outbox lag 0). A smoke that passes by not testing is worse than none (the F5 pattern).
- **Recommendation:** (a) first, fall back to (c). **Status:** OPEN — carried to the WP-R1 checkpoint.
