# QUESTIONS — implementation questions for the owner

One row each (HANDOFF §3). Format: `Q-NNN · date · WP · spec ref · question ·
blocks · options · recommendation · status · answer`.

Because the owner directed a full build this session, each **blocking** question
is resolved by **adopting the handoff's recommended answer** (DEV-003), flagged
here and carried to the checkpoint for ratification. Status `adopted-rec` means
"built to the recommendation, awaiting ratification."

---

### Q-1 · 2026-07-18 · WP4 · D1 (§16 Panel 3 vs §17.2 S4) — the retracted precision figure
- **Q:** Omit the "≈28% lowest-possible precision / 86% n=50" figures from the honesty page, or read the source paper and confirm first?
- **Blocks:** WP4. **Options:** omit / confirm-then-quote.
- **Recommendation & adopted:** **Omit both** until someone reads the paper directly; the honesty page states calibration is Phase-3 work without quoting a precision number. A grep for hard-coded stats fails WP4 regardless.
- **Status:** adopted-rec.

### Q-2 · 2026-07-18 · WP4 · D4 (§5.5, §13.2 vs §13.1, §20 A3) — scope of the unscored toggle
- **Q:** Where does the unscored toggle live, and is the dashed-edge canvas grammar wired?
- **Blocks:** WP4. **Options:** claims-list only + unwired canvas grammar / full canvas control now.
- **Recommendation & adopted:** toggle on the **claims list only**; the dashed-edge grammar is implemented but **unwired**, with a test asserting it renders for synthetic unscored edges. It earns a canvas control at WP2b/WP9.
- **Status:** adopted-rec.

### Q-3 · 2026-07-18 · WP1 · D5 (§5.1 vs §13.2, §20 A2/A7) — the multi-count result line
- **Q:** Does `/entities/{id}/network` return possibly/certainly/undated counts in one response, or does the client make two calls?
- **Blocks:** WP1. **Options:** one response (counts in meta) / two calls.
- **Recommendation & adopted:** **one response — all three counts in `meta.counts`.** The client-side slider already holds the full edge set; a second round trip per drag would defeat the interaction.
- **Status:** adopted-rec (implemented in WP1 network endpoint).

### Q-4 · 2026-07-18 · WP1 · §4 Flow B / WP1 acceptance — fixtures addressed by internal id
- **Q:** Fixtures pin internal id `15429`, but FIX-BACON/FIX-DISAMBIG are SDFB ids `10000473`/`10000475`; internal ids need not survive a rebuild.
- **Blocks:** WP1 acceptance. **Options:** pin internal id / resolve via external id.
- **Recommendation & adopted:** every fixture resolves through `GET /entities/lookup?authority=sdfb&externalId=…`; `15429` survives only as illustrative prose.
- **Status:** adopted-rec (lookup endpoint implemented; tests key off external id).

### Q-5 · 2026-07-18 · WP2 · §20 A5, §14.3, §8 — who builds the synthetic fixture, and when
- **Q:** No WP owns building the synthetic CI fixture (shape + anomaly classes).
- **Blocks:** WP2 and everything downstream. **Options:** stub at WP0 / full fixture at WP2.
- **Recommendation & adopted:** minimal stub at WP0 (CI green); the real synthetic fixture (generator) built in **WP2** alongside the production adapter; extended at WP2b.
- **Status:** adopted-rec.

### Q-6 · 2026-07-18 · WP4/WP0 · §8 vs §20 A5 — the E2E fixture contradiction
- **Q:** §8 wants "29→13 for the Bacon fixture" in the CI E2E; §20 A5 wants the CI fixture synthetic (interaction, not numbers).
- **Blocks:** WP4, CI design at WP0. **Options:** measured numbers in CI / synthetic interaction in CI.
- **Recommendation & adopted:** the CI E2E asserts the **interaction** on synthetic data (edge count strictly decreases across the drag; exact counts defined by the fixture); 29→13 moves to the slow suite against a locally-supplied dump.
- **Status:** adopted-rec.

### Q-7 · 2026-07-18 · WP1 · §3.4, §5.1, §6.3 — which WP owns the projector
- **Q:** WP1 acceptance needs a working `search` (reads `entity_summary`), which only the projector/`rebuild-projections` populates, but WP1 scope doesn't name the projector.
- **Blocks:** WP1. **Options:** projector at WP1 / defer.
- **Recommendation & adopted:** WP1 includes **`rebuild-projections`** and the `entity_summary` + name-search materializers; the outbox-consuming loop follows at WP2 when writes begin.
- **Status:** adopted-rec (implemented in WP1).

---

## Sweep findings (WP0/WP1 scope, per HANDOFF §9 step 3)

Defect log, not redesign. Nothing here reopens a §5 ruling.

### Q-8 · 2026-07-18 · WP1 · §5.1 — pagination sort key for `/search` and list endpoints
- **Q:** Envelope specifies opaque cursor over "a stable sort key" but the key per endpoint is unspecified.
- **Blocks:** nothing (non-blocking). **Adopted:** search sorts by `(similarity desc, entity_id asc)` with the cursor over `entity_id` within a fixed `q`; entity claim lists sort by `(predicate, claim_id)`; `/events` by `(occurred_at desc, id desc)`. Documented in the engine; easiest-to-reverse.
- **Status:** adopted (non-blocking, flagged).

### Q-9 · 2026-07-18 · WP1 · §5.1 `/entities/{id}` — display-name determinism when an entity has 0 name claims
- **Q:** §17.F1 fixes the display-name rule for ≥1 name claim; a synthetic entity could have none.
- **Blocks:** nothing. **Adopted:** an entity with no name claim renders `display_name = "unnamed entity #<id>"` in `entity_summary` and the coverage panel notes it; real SDFB persons always have a display name so this only affects malformed fixtures.
- **Status:** adopted (non-blocking).

### Q-10 · 2026-07-18 · WP2 · §3.1 — `born`/`died` object form
- **Q:** SDFB gives life *years* with no place; is `born` object a literal year or an entity (place)?
- **Blocks:** WP2 adapter. **Adopted:** `born`/`died` are attribute claims with `object.literal {kind:"year"}` and the year carried in `valid_time` (start/end bounds per the four-date model); no place entity is fabricated (I6). Revisited if the Folger tables add places.
- **Status:** adopted (non-blocking).
