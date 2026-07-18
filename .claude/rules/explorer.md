---
paths: ["explorer/**"]
---
# explorer/ — rendering grammars and microcopy are semantic commitments

Restyling a grammar is allowed; re-semanticising one is not (§7.2). The defect
register (HANDOFF §5) governs — read it before building any UI.

**Grammars (§13.3):**
- Confidence chip = number + band label + origin, **never a number alone** (PP2).
  Bands: ≥.90 very strong · .70–.89 strong · .40–.69 moderate · .20–.39 weak ·
  <.20 very weak · unscored. Origin always shown.
- Fuzzy dates: both bounds → `1561–1626`; earliest only → `after 1561`; latest
  only → `before 1626`; year-precise → `1561`; circa → `c. 1561` with an
  **`approximate` marker and NO ±window** (D6); unknown → `date unknown`.
  Life dates carry an **unscored chip + derived-date marker**, never bare (F3).
- Temporal mode: **certainty is a highlight within the possibly graph, not a
  filter** (D2/A2). Result line states possibly · certainly · undated together (D5/A7).
- Unscored toggle: **claims-list only**; dashed-edge canvas grammar implemented
  but **unwired** (Q-2). Search rows show a **scored** tie count only (D8).

**Microcopy (§13.5):** state the limit then the reason; never assert what the
data doesn't ("SDFB records an association between…", not "X knew Y"); use the
historian's vocabulary (attestation, source, evidence), not nodes/edges/scores;
explain uncertainty once, inline.

**Honesty:** the About page derives **every** figure live — a grep for hard-coded
statistics fails WP4 (§16). Omit the retracted precision figure (D1/Q-1). Slider
defaults to **0** (§17.F2); all four controls live in the URL (PP5). Every fact ≤
2 clicks from its source_record (PP1). License-unconfirmed content gets the amber
badge (PP6). Empty states say *why* (PP4).
