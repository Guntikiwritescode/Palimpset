"""The five SDFB data-quality rules, each a SEPARATELY NAMED test.

HANDOFF §4 rule 10: a test states its expected value BEFORE it observes the
output. Every ``expected`` below is written and asserted against a value the test
fixes up front — not copied from a first run. The rows are small SYNTHETIC rows in
the documented SDFB CSV shape; the real dump never enters git or these tests.

Rules (ARCHITECTURE §5.2 "sentinel, repeats, inverted bounds, dangling edge, CA
window"; normative in .claude/rules/pipeline-adapters.md):
  1. odnb sentinel      -> absent, counted, no has-external-id
  2. repeats            -> duplicate pairs kept as separate claims
  3. inverted bounds    -> dropped (original preserved), claim undated, counted
  4. dangling edge      -> skipped, counted, NO fabricated entity
  5. CA window          -> stated year + approximate marker, NO +/-window
"""

from __future__ import annotations

from palimpsest_pipeline.adapters.sdfb import SDFBAdapter


def _person(**over):
    base = {
        "record_kind": "person", "id": "1", "display_name": "Test Person",
        "search_names_all": "", "birth_year": "", "birth_type": "IN",
        "death_year": "", "death_type": "IN", "gender": "", "description": "",
        "odnb_id": "",
    }
    base.update(over)
    return base


def _rel(**over):
    base = {
        "record_kind": "relationship", "id": "r1", "person1_id": "1",
        "person2_id": "2", "max_certainty": 50, "start_year": "1590",
        "start_type": "AF/IN", "end_year": "1626", "end_type": "BF/IN",
    }
    base.update(over)
    return base


# --------------------------------------------------------------------------- #
# Rule 1 — odnb sentinel
# --------------------------------------------------------------------------- #
def test_odnb_zero_sentinel_is_absent_not_a_claim():
    # EXPECTATION (stated before observing):
    #   odnb_id in {"", "0"} means ABSENT. A person with odnb_id "0" and a person
    #   with odnb_id "" each increment odnb_zero_sentinel and emit ZERO
    #   has-external-id claims. A person with a real odnb_id emits exactly one and
    #   does not touch the counter.
    expected_sentinel_count = 2
    expected_external_id_claims_for_sentinels = 0
    expected_external_id_claims_for_real = 1

    a = SDFBAdapter()
    people = [
        _person(id="1", odnb_id="0"),
        _person(id="2", odnb_id=""),
        _person(id="3", odnb_id="990"),
    ]
    a.register_entities(people)
    claims = [c for p in people for c in a.map_to_claims(p)]

    ext_by_subject = {}
    for c in claims:
        if c.predicate == "has-external-id":
            ext_by_subject.setdefault(c.subject["id"], 0)
            ext_by_subject[c.subject["id"]] += 1

    assert a.counters.odnb_zero_sentinel == expected_sentinel_count
    assert ext_by_subject.get("1", 0) == expected_external_id_claims_for_sentinels
    assert ext_by_subject.get("2", 0) == expected_external_id_claims_for_sentinels
    assert ext_by_subject.get("3", 0) == expected_external_id_claims_for_real


# --------------------------------------------------------------------------- #
# Rule 2 — repeats (duplicate pairs kept as separate claims)
# --------------------------------------------------------------------------- #
def test_duplicate_pairs_kept_as_separate_claims():
    # EXPECTATION: the same unordered pair appearing twice yields TWO separate
    # associated-with claims (no dedup that loses a source record), both
    # canonically ordered subject=1 (min id) object=2 (max id) even when the
    # source row lists them 2,1. duplicate_pair_kept increments once.
    expected_associated_claims = 2
    expected_duplicate_pair_kept = 1
    expected_subject = "1"
    expected_object = "2"

    a = SDFBAdapter()
    people = [_person(id="1"), _person(id="2")]
    a.register_entities(people)
    rels = [
        _rel(id="rA", person1_id="1", person2_id="2", max_certainty=90),
        _rel(id="rB", person1_id="2", person2_id="1", max_certainty=40),  # reversed
    ]
    claims = [c for r in rels for c in a.map_to_claims(r)]

    assoc = [c for c in claims if c.predicate == "associated-with"]
    assert len(assoc) == expected_associated_claims
    assert a.counters.duplicate_pair_kept == expected_duplicate_pair_kept
    for c in assoc:
        assert c.subject["id"] == expected_subject
        assert c.obj["entity"]["id"] == expected_object
    # The two claims are distinct source records (different max_certainty raw).
    raws = sorted(c.confidence["raw"] for c in assoc)
    assert raws == [40, 90]


# --------------------------------------------------------------------------- #
# Rule 3 — inverted bounds
# --------------------------------------------------------------------------- #
def test_inverted_derived_bounds_are_dropped_claim_becomes_undated():
    # EXPECTATION: start AF/IN 1650 -> start_earliest 1650-01-01; end BF/IN 1600
    # -> end_latest 1600-12-31. start_earliest (1650) > end_latest (1600) is
    # inverted, so ALL derived bounds are dropped and only `original` survives
    # (the claim becomes undated). valid_time_inverted_dropped_bounds increments.
    # The originals are preserved verbatim, never destroyed.
    expected_inverted_count = 1
    expected_bound_keys_present = {"original"}
    expected_original = {
        "start": {"type_code": "AF/IN", "year": "1650"},
        "end": {"type_code": "BF/IN", "year": "1600"},
    }

    a = SDFBAdapter()
    a.register_entities([_person(id="1"), _person(id="2")])
    claims = a.map_to_claims(
        _rel(start_year="1650", start_type="AF/IN", end_year="1600", end_type="BF/IN")
    )

    assert a.counters.valid_time_inverted_dropped_bounds == expected_inverted_count
    assert len(claims) == 1
    vt = claims[0].valid_time
    assert set(vt.keys()) == expected_bound_keys_present
    assert vt["original"] == expected_original
    # No derived bound leaked through.
    for k in ("start_earliest", "start_latest", "end_earliest", "end_latest"):
        assert k not in vt


# --------------------------------------------------------------------------- #
# Rule 4 — dangling edge
# --------------------------------------------------------------------------- #
def test_dangling_endpoint_is_skipped_and_counted_no_fabricated_entity():
    # EXPECTATION: a relationship whose endpoint id is not a known person is
    # SKIPPED (zero claims) and counted; the missing entity is NEVER fabricated
    # (I6). Only the one real person yields an entity record.
    expected_claims = 0
    expected_dangling_count = 1
    expected_entities = 1

    a = SDFBAdapter()
    people = [_person(id="1")]  # person "9999" deliberately absent
    entities = a.register_entities(people)
    claims = a.map_to_claims(_rel(person1_id="1", person2_id="9999"))

    assert claims == []
    assert len(claims) == expected_claims
    assert a.counters.relationship_dangling_endpoint_skipped == expected_dangling_count
    assert len(entities) == expected_entities
    assert entities[0].ref["id"] == "1"


# --------------------------------------------------------------------------- #
# Rule 5 — CA window (no +/-5)
# --------------------------------------------------------------------------- #
def test_circa_keeps_year_with_approximate_marker_and_no_window():
    # EXPECTATION (§17.2 S3 / D6): CA 1561 on a birth keeps the STATED year
    # bounds [1561-01-01, 1561-12-31] and marks the interval approximate=True.
    # It invents NO window: no 1556 (year-5) and no 1566 (year+5) anywhere.
    expected_start_earliest = "1561-01-01"
    expected_start_latest = "1561-12-31"
    expected_approximate = True
    forbidden_years = ("1556", "1566")

    a = SDFBAdapter()
    people = [_person(id="1", birth_year="1561", birth_type="CA")]
    a.register_entities(people)
    claims = a.map_to_claims(people[0])

    born = [c for c in claims if c.predicate == "born"]
    assert len(born) == 1
    vt = born[0].valid_time
    assert vt["start_earliest"] == expected_start_earliest
    assert vt["start_latest"] == expected_start_latest
    assert vt["approximate"] == expected_approximate
    blob = str(vt)
    for y in forbidden_years:
        assert y not in blob


# --------------------------------------------------------------------------- #
# Bonus — AF vs AF/IN and BF vs BF/IN preserved (§17.2 S2), not a required
# named rule but load-bearing: the distinction the source deliberately encodes.
# --------------------------------------------------------------------------- #
def test_af_strict_differs_from_af_in_on_a_start():
    # EXPECTATION: AF/IN 1590 start -> start_earliest 1590-01-01 (beginning of Y).
    #              AF    1590 start -> start_earliest 1590-12-31 (strictly after Y).
    # The two must NOT be collapsed.
    a = SDFBAdapter()
    a.register_entities([_person(id="1"), _person(id="2")])
    af_in = a.map_to_claims(_rel(id="r1", start_year="1590", start_type="AF/IN"))[0]
    b = SDFBAdapter()
    b.register_entities([_person(id="1"), _person(id="2")])
    af = b.map_to_claims(_rel(id="r2", start_year="1590", start_type="AF"))[0]

    assert af_in.valid_time["start_earliest"] == "1590-01-01"
    assert af.valid_time["start_earliest"] == "1590-12-31"
    assert af_in.valid_time["start_earliest"] != af.valid_time["start_earliest"]
