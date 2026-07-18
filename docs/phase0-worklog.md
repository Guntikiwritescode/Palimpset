# Phase 0 worklog — PLACEHOLDER

The original `phase0-worklog.md` was **not supplied** to this build session
(see `docs/PREFLIGHT.md` §2.1). The measured Phase-0 facts it would contain are
cited authoritatively in `docs/ARCHITECTURE.md` §1 ("Ground truth carried
forward") and reused as the FIX-* named fixtures (§19):

- FIX-CORPUS — 15,882 person entities · 261,177 claims · 187,482 source records.
- FIX-ANOMALY — 365 odnb_id="0" sentinels · 14 repeated ODNB ids · 1,575
  temporally inverted relationship bounds · 1 dangling endpoint · 6 unparseable years.
- FIX-BACON — SDFB id 10000473: 288 total ties; 34 at ≥0.60 / 15 at ≥0.90 (all
  time); 29 / 13 windowed to 1600 (possibly); 2 certainly-active at ≥0.60 in 1600.
- Signature query measured at 1–5 ms on PG 16.14 over 261k claims.

These numbers are **facts about a locally-supplied dump**, not data in the repo.
They are asserted only by the slow test suite against that dump (Q-6), never by CI.

If the real worklog surfaces, replace this file with it.
