"""correspSearch adapter tests (WP2b, A2).

Golden + rule tests on SYNTHETIC CMIF (invented people, never a real edition).
Each test states its expected value before observing it (HANDOFF §4 rule 10).
The CMIF shape mirrors the live v1.1 API verified in docs/probe-correspsearch.md.
"""
from __future__ import annotations

from palimpsest_pipeline.adapters.correspsearch import CorrespSearchAdapter, parse_cmif
from palimpsest_pipeline.schema import validate_claim, validate_entity

# Two invented letters. L1: both endpoints have a GND + a date → a tie.
# L2: the receiver has no GND ref → the tie must be skipped and counted (I6).
SYNTHETIC_CMIF = """<?xml version="1.0" encoding="UTF-8"?>
<TEI xmlns="http://www.tei-c.org/ns/1.0">
  <teiHeader><fileDesc><publicationStmt><availability>
    <licence target="https://creativecommons.org/licenses/by/4.0/">CC-BY 4.0</licence>
  </availability></publicationStmt></fileDesc></teiHeader>
  <text><body><list>
    <correspDesc source="#synth-edition" ref="https://example.test/letter/1" key="L1">
      <correspAction type="sent">
        <persName ref="http://d-nb.info/gnd/900000001">Ada Synth</persName>
        <placeName ref="http://www.geonames.org/1">Somewhere</placeName>
        <date when="1600-05-01">1 May 1600</date>
      </correspAction>
      <correspAction type="received">
        <persName ref="http://d-nb.info/gnd/900000002">Bram Synth</persName>
      </correspAction>
    </correspDesc>
    <correspDesc source="#synth-edition" ref="https://example.test/letter/2" key="L2">
      <correspAction type="sent">
        <persName ref="http://d-nb.info/gnd/900000003">Cleo Synth</persName>
        <date when="1601">1601</date>
      </correspAction>
      <correspAction type="received">
        <persName>Unknown Hand</persName>
      </correspAction>
    </correspDesc>
  </list></body></text>
</TEI>"""


def _letters():
    return parse_cmif(SYNTHETIC_CMIF)


def test_parse_cmif_extracts_letters_and_gnd():
    letters = _letters()
    assert len(letters) == 2                       # expected: two correspDesc
    l1 = letters[0]
    assert l1["key"] == "L1"
    assert l1["edition"] == "synth-edition"
    assert l1["sent"]["gnd"] == "900000001"        # GND extracted from the URI
    assert l1["sent"]["when"] == "1600-05-01"
    assert l1["received"]["gnd"] == "900000002"
    assert _letters()[1]["received"].get("gnd") is None  # no ref → no GND


def test_corresponded_with_is_unscored_and_dated():
    adapter = CorrespSearchAdapter()
    claims = adapter.map_to_claims(_letters()[0])
    tie = [c for c in claims if c.predicate == "corresponded-with"]
    assert len(tie) == 1                            # exactly one tie for L1
    c = tie[0]
    assert c.confidence == {"kind": "unscored"}     # a letter attests; no invented number (I5)
    assert c.subject == {"authority": "gnd", "id": "900000001"}
    assert c.obj == {"entity": {"authority": "gnd", "id": "900000002"}}
    # A dated letter is an instant → certainly-active in its year.
    assert c.valid_time["start_earliest"] == "1600-05-01"
    assert c.valid_time["end_latest"] == "1600-05-01"
    assert c.valid_time["original"] == {"when": "1600-05-01"}


def test_missing_gnd_endpoint_is_skipped_and_counted():
    adapter = CorrespSearchAdapter()
    claims = adapter.map_to_claims(_letters()[1])   # L2: receiver has no GND
    ties = [c for c in claims if c.predicate == "corresponded-with"]
    assert ties == []                               # no tie emitted (no fabrication, I6)
    assert adapter.counters["letter_missing_receiver_gnd"] == 1


def test_document_entity_and_names_emitted():
    adapter = CorrespSearchAdapter()
    l1 = _letters()[0]
    entities = adapter.map_to_entities(l1)
    types = sorted(e.entity_type for e in entities)
    assert types == ["document", "person", "person"]   # sender, receiver, the letter
    doc = [e for e in entities if e.entity_type == "document"][0]
    assert doc.ref == {"authority": "correspsearch", "id": "L1"}
    names = [c for c in adapter.map_to_claims(l1) if c.predicate == "has-name"]
    assert {c.subject["authority"] for c in names} == {"gnd", "correspsearch"}


def test_every_emitted_line_validates_against_the_frozen_schema():
    adapter = CorrespSearchAdapter()
    for letter in _letters():
        for e in adapter.map_to_entities(letter):
            assert validate_entity(e.to_dict()) == []
        for c in adapter.map_to_claims(letter):
            assert validate_claim(c.to_dict()) == []
