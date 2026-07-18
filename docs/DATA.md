# DATA — per-source provenance and data-quality findings

**Audience:** P2 (the working historian) and P3 (the data steward). Filled at
WP2 (SDFB) and extended at WP2b (correspSearch). This skeleton is created at WP0
with its audience stated (HANDOFF §7.4).

For each source: what it is, what was verified and when, license status, what was
mapped to which predicates, and **every** data-quality finding with its handling.

## Sources

### Six Degrees of Francis Bacon (SDFB), 2017-10-13 dump
- **What:** ~15,000 early-modern figures, ~170,000 relationships, NLP-inferred
  from the ODNB (NER + Poisson graphical lasso → a confidence matrix).
- **License:** UNCONFIRMED. A 2015 project blog post states data is shared free of
  charge for non-commercial research, without warranty, asking for a citation.
  That is a statement of intent, not a located license file. `license_confirmed=false`.
- **Confidence semantics:** the score is a **bootstrap selection frequency** (how
  often an edge survived resampling, 0–100), NOT a probability the tie existed
  (§17.2 S4). Rendered as an uncalibrated ranking signal, never as a probability.
- **Mapping:** filled at WP2 (predicate table, the AF/IN date-code interpretation
  table labelled *interpretation, unconfirmed*, the alias-name handling).
- **Data-quality findings (FIX-ANOMALY):** filled at WP2 with counts + handling.

### The SDFB date-code interpretation table  *(interpretation — UNCONFIRMED)*

99.7% of relationship intervals rest on the single start code `AF/IN` and 99.8%
on the end code `BF/IN`. This reading is corroborated by an independent RDF
converter processing the same CSV exports (`jiemakel/anything2rdf`, §17.2 S1) but
is **not** confirmed against SDFB's own Rails source. **A finding that contradicts
it goes to the architect *before* the WP2 merge** (tripwire, HANDOFF §11). The
code-side authority is `pipeline/palimpsest_pipeline/adapters/sdfb/dates.py`.

The interpretation is endpoint-independent: a code+year yields an `(earliest,
latest)` pair for whichever endpoint (start or end) it annotates. `approximate`
is a property of the whole interval (true if either endpoint code is `CA`).

| code | gloss | earliest | latest | approximate |
|---|---|---|---|---|
| `IN` | in year Y | Y-01-01 | Y-12-31 | no |
| `CA` | circa year Y | Y-01-01 | Y-12-31 | **YES** — §17.2 S3: NO ±window (D6) |
| `AF/IN` | after the beginning of Y | Y-01-01 | *(null)* | no |
| `BF/IN` | before the end of Y | *(null)* | Y-12-31 | no |
| `AF` | strictly after Y (year end) | Y-12-31 | *(null)* | no — §17.2 S2: **not** ≡ `AF/IN` |
| `BF` | strictly before Y (year top) | *(null)* | Y-01-01 | no — §17.2 S2: **not** ≡ `BF/IN` |

Worked consequences, per the endpoint the code annotates:

- `AF/IN` on a start → `start_earliest = Y-01-01`, `start_latest = null`
- `BF/IN` on an end → `end_latest = Y-12-31`, `end_earliest = null`
- `AF` on a start → `start_earliest = Y-12-31`
- `BF` on an end → `end_latest = Y-01-01`; `BF` on a start → `start_latest = Y-01-01`
- `IN` on a start → `start_earliest = Y-01-01`, `start_latest = Y-12-31`
- `CA` on either → same bounds as `IN`, plus `approximate=true`, **no ±window**

`AF` vs `AF/IN` and `BF` vs `BF/IN` are preserved distinctly and never collapsed
(§17.2 S2). `CA` keeps the stated year with an `approximate` marker and invents no
window (§17.2 S3 / D6 — the `CA = ±5` figure the source never states, which the
project's own no-fabricated-numbers rule caught, affecting 107 claims).

This interpretation drives `possibly_active`/`certainly_active` (contracts DDL):
because the dominant `AF/IN` start gives only an *earliest* start and `BF/IN` end
only a *latest* end, `certainly_active` (which needs an upper bound on the start
and a lower bound on the end) is satisfiable for only ~0.18% of claims — which is
why the UI **highlights** certainty rather than filtering on it (§20 A2, D2).

### Anomaly counters (FIX-ANOMALY) — handling

The adapter counts, never patches, five anomaly classes. Against the **real dump**
the measured values are 365 / 14 / 1,575 / 1 / 6 (§1 ground truth) and are asserted
only by the slow suite (`PALIMPSEST_SDFB_DUMP`), never by CI; the synthetic fixture
reproduces one of each class (not the counts). Handling:

| counter | meaning | handling |
|---|---|---|
| `odnb_zero_sentinel` | `odnb_id ∈ {"","0"}` | absent — no has-external-id claim emitted |
| `relationship_dangling_endpoint_skipped` | relationship endpoint not a known person | claim skipped; **no entity fabricated** (I6) |
| `valid_time_inverted_dropped_bounds` | derived start > end | bounds dropped (original preserved); claim becomes undated |
| `bad_year_value` | year present but unparseable | year treated as unknown; counted |
| `duplicate_pair_kept` | same pair asserted more than once | kept as **separate** claims (no silent merge, PP3) |
