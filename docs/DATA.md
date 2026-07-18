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

### The SDFB date-code interpretation table  *(interpretation — unconfirmed)*
Filled at WP2. 99.7% of relationship intervals rest on the single code `AF/IN`.
Corroborated by an independent RDF converter, not confirmed against SDFB's own
source. A contradicting finding goes to the architect **before** the WP2 merge.
