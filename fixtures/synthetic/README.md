# Synthetic fixture — NEVER source-derived (ARCHITECTURE §14.3, §20 A5)

Invented people and ties that reproduce the corpus's **shape** and its **anomaly
classes**, not a subset of SDFB. A subset of license-unconfirmed data is still
license-unconfirmed; this is the license-safe path for CI and `make demo`.

It reproduces the *interaction* (edge count strictly decreases as the confidence
threshold rises), NOT the measured numbers — the FIX-* measured assertions run in
the slow suite against a locally-supplied dump (Q-6).

- `entities.ndjson` — kind=entities lines (validate against
  `claim.schema.json#/$defs/entityRecord`).
- `claims.ndjson` — kind=claims lines (validate against the root schema).

This directory is a **WP0 stub** sufficient for CI to be green. The full
generator (reproducing every anomaly class, and FIX-DISAMBIG's two same-named
people) lands in WP2: `python -m palimpsest_pipeline.cli synth`.
