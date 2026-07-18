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

## Regenerating

```
palimpsest-pipeline synth --out fixtures/synthetic [--scale N]
```

The generator builds synthetic **source rows** in the documented SDFB CSV shape and
runs them through the real `SDFBAdapter` (authority `synth`), so the fixture is
guaranteed valid (the adapter self-validates) and genuinely exercises every anomaly
path. What it reproduces:

- **FIX-DISAMBIG** — two people both named "Francis Bacon", distinguishable by
  life dates (1561–1626 vs 1600–1663).
- **odnb sentinel** — one twin has `odnb_id="0"` (no has-external-id claim).
- **dangling skipped** — a relationship references an id that is not a person.
- **inverted-dropped** — a relationship whose derived bounds invert (undated claim,
  original preserved).
- **alias names** — "Helen Alexander" also known as "Helen Umpherston" / "Helen
  Currie" (§17.2 S6).
- **bad year** — a person whose birth year is unparseable (no born claim).
- **duplicate pair** — the same pair appears twice, kept as separate claims.

The edge set demonstrates the signature interaction: raising `minConfidence` across
`[0.0, 0.2, 0.4, 0.6, 0.8, 0.9]` strictly decreases the edge count.
