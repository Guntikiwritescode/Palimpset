---
paths: ["pipeline/palimpsest_pipeline/adapters/**"]
---
# adapters/ — normative SDFB mapping rules and the never-invent list

The Phase-0 `sdfb_adapter.py` is the semantic reference. Its rules are
**normative** (ARCHITECTURE §3.1, §17.2). Adapters are pure w.r.t. I/O:
harvesters fetch, adapters transform, the submitter ships.

**Never invent** (HANDOFF §4 rule 2): no date window the source never states, no
confidence for an unscored claim, no entity to satisfy a dangling reference, no
number the source doesn't give. Count anomalies; never patch them over.

**SDFB mapping rules (normative):**
- `odnb_id ∈ {"", "0"}` means **absent** — count `odnb_zero_sentinel`, emit no
  has-external-id claim for it.
- Relationships with an unknown endpoint are **skipped and counted**
  (`relationship_dangling_endpoint_skipped`) — never fabricate the entity (I6).
- Derived bounds that are inverted are **dropped, originals preserved**
  (`valid_time_inverted_dropped_bounds`); the claim becomes undated, not invented.
- Symmetric edges are canonically ordered by numeric id; duplicate pairs are
  **kept as separate claims** (no dedup that loses a source record).
- **Preserve `AF` vs `AF/IN`** (and `BF` vs `BF/IN`) — do not collapse them
  (§17.2 S2). `AF`/`BF` are strict (earliest=year end / latest=year start);
  `AF/IN`/`BF/IN` are "after the beginning of" / "before the end of".
- **No `CA ±5` window** — circa keeps the stated year + an `approximate` marker
  (§17.2 S3). Affects 107 claims.
- Emit **alias name-claims** from `search_names_all`/`aliases` (`has-name`,
  `method_detail.name_kind="alias"`) and index them (§17.2 S6 — 23.4% of people).
- `bad_year_value` counts unparseable years.

**Date-code interpretation is unconfirmed.** 99.7% of intervals rest on `AF/IN`.
A finding that contradicts the `AF/IN`/`BF/IN` reading goes to the architect
**before the WP2 merge** (tripwire). The interpretation table lives in `docs/DATA.md`,
labelled interpretation.

**Every emitted claim self-validates** against the pinned `claim.schema.json`
before submission; a nonzero invalid count aborts the run.
