"""Golden tests: hand-picked SYNTHETIC rows -> expected interchange lines,
byte-stable.

Each expected value is a pinned canonical NDJSON line (``to_ndjson_line`` uses
sorted keys + tight separators, so output is deterministic). If the adapter's
mapping changes, these lines change and the diff is explicit — that is the point.
Rows are synthetic; the real SDFB dump never enters git.
"""

from __future__ import annotations

from palimpsest_pipeline.adapters.base import to_ndjson_line
from palimpsest_pipeline.adapters.sdfb import SDFBAdapter

# A single golden person row exercising primary name, alias, born, died, gender,
# and a real ODNB id.
GOLDEN_PERSON = {
    "record_kind": "person",
    "id": "10000473",
    "display_name": "Francis Bacon",
    "search_names_all": "Francis Bacon|Lord Verulam",
    "birth_year": "1561", "birth_type": "IN",
    "death_year": "1626", "death_type": "IN",
    "gender": "male", "description": "", "odnb_id": "990",
}

# Two minimal persons + one relationship row; person1/person2 are listed
# out of numeric order to exercise canonical ordering (subject = min id).
GOLDEN_REL_PEOPLE = [
    {"record_kind": "person", "id": "10000473", "display_name": "Francis Bacon",
     "search_names_all": "", "birth_year": "", "birth_type": "", "death_year": "",
     "death_type": "", "gender": "", "description": "", "odnb_id": ""},
    {"record_kind": "person", "id": "10000475", "display_name": "Francis Bacon",
     "search_names_all": "", "birth_year": "", "birth_type": "", "death_year": "",
     "death_type": "", "gender": "", "description": "", "odnb_id": ""},
]
GOLDEN_REL = {
    "record_kind": "relationship", "id": "rel-42",
    "person1_id": "10000475", "person2_id": "10000473", "max_certainty": 97,
    "start_year": "1590", "start_type": "AF/IN",
    "end_year": "1626", "end_type": "BF/IN",
}

EXPECTED_ENTITY = (
    '{"entity_type":"person","external_ids":[{"authority":"sdfb","id":"10000473"}],'
    '"ref":{"authority":"sdfb","id":"10000473"},"schema_version":"0.1.0"}'
)

_PERSON_SUPPORT = (
    '"support":[{"content_hash":'
    '"sha256:7e3609d71630b35f34d5b548cc233022ef0736fc7a40829de6da3d58cac73569",'
    '"external_id":"10000473","raw":{"birth_type":"IN","birth_year":"1561",'
    '"death_type":"IN","death_year":"1626","description":"","display_name":'
    '"Francis Bacon","gender":"male","id":"10000473","odnb_id":"990",'
    '"search_names_all":"Francis Bacon|Lord Verulam"},"record_kind":"person",'
    '"source":"sdfb-2017-10-13"}]'
)

EXPECTED_PERSON_CLAIMS = [
    # has-name primary
    '{"asserted_by":"pipeline","confidence":{"kind":"unscored"},"method":"imported",'
    '"method_detail":{"name_kind":"primary"},"object":{"literal":{"kind":"string",'
    '"value":"Francis Bacon"}},"predicate":"has-name","schema_version":"0.1.0",'
    '"subject":{"authority":"sdfb","id":"10000473"},' + _PERSON_SUPPORT + '}',
    # has-name alias
    '{"asserted_by":"pipeline","confidence":{"kind":"unscored"},"method":"imported",'
    '"method_detail":{"name_kind":"alias"},"object":{"literal":{"kind":"string",'
    '"value":"Lord Verulam"}},"predicate":"has-name","schema_version":"0.1.0",'
    '"subject":{"authority":"sdfb","id":"10000473"},' + _PERSON_SUPPORT + '}',
    # born
    '{"asserted_by":"pipeline","confidence":{"kind":"unscored"},"method":"imported",'
    '"object":{"literal":{"kind":"year","value":1561}},"predicate":"born",'
    '"schema_version":"0.1.0","subject":{"authority":"sdfb","id":"10000473"},'
    + _PERSON_SUPPORT +
    ',"valid_time":{"approximate":false,"original":{"type_code":"IN","year":"1561"},'
    '"start_earliest":"1561-01-01","start_latest":"1561-12-31"}}',
    # died
    '{"asserted_by":"pipeline","confidence":{"kind":"unscored"},"method":"imported",'
    '"object":{"literal":{"kind":"year","value":1626}},"predicate":"died",'
    '"schema_version":"0.1.0","subject":{"authority":"sdfb","id":"10000473"},'
    + _PERSON_SUPPORT +
    ',"valid_time":{"approximate":false,"original":{"type_code":"IN","year":"1626"},'
    '"start_earliest":"1626-01-01","start_latest":"1626-12-31"}}',
    # has-gender
    '{"asserted_by":"pipeline","confidence":{"kind":"unscored"},"method":"imported",'
    '"object":{"literal":{"kind":"gender","value":"male"}},"predicate":"has-gender",'
    '"schema_version":"0.1.0","subject":{"authority":"sdfb","id":"10000473"},'
    + _PERSON_SUPPORT + '}',
    # has-external-id
    '{"asserted_by":"pipeline","confidence":{"kind":"unscored"},"method":"imported",'
    '"object":{"literal":{"authority":"odnb","kind":"external-id","value":"990"}},'
    '"predicate":"has-external-id","schema_version":"0.1.0",'
    '"subject":{"authority":"sdfb","id":"10000473"},' + _PERSON_SUPPORT + '}',
]

EXPECTED_REL_CLAIM = (
    '{"asserted_by":"pipeline","confidence":{"calibrated":false,'
    '"kind":"source_native_scalar","point":0.97,"raw":97,'
    '"scale":"sdfb_max_certainty_0_100"},"method":"imported","object":'
    '{"entity":{"authority":"sdfb","id":"10000475"}},"predicate":"associated-with",'
    '"schema_version":"0.1.0","subject":{"authority":"sdfb","id":"10000473"},'
    '"support":[{"content_hash":'
    '"sha256:11ecb8d3a21324b8e94d03be110c6df988c7f949fbce7dcbb06965da173f948f",'
    '"external_id":"rel-42","raw":{"end_type":"BF/IN","end_year":"1626","id":"rel-42",'
    '"max_certainty":97,"person1_id":"10000475","person2_id":"10000473",'
    '"start_type":"AF/IN","start_year":"1590"},"record_kind":"relationship",'
    '"source":"sdfb-2017-10-13"}],"valid_time":{"approximate":false,'
    '"end_earliest":null,"end_latest":"1626-12-31","original":{"end":{"type_code":'
    '"BF/IN","year":"1626"},"start":{"type_code":"AF/IN","year":"1590"}},'
    '"start_earliest":"1590-01-01","start_latest":null}}'
)


def test_golden_person_entity_line():
    a = SDFBAdapter()
    entities = a.map_to_entities(GOLDEN_PERSON)
    assert len(entities) == 1
    assert to_ndjson_line(entities[0]) == EXPECTED_ENTITY


def test_golden_person_claim_lines():
    a = SDFBAdapter()
    a.register_entities([GOLDEN_PERSON])
    claims = a.map_to_claims(GOLDEN_PERSON)
    actual = [to_ndjson_line(c) for c in claims]
    assert actual == EXPECTED_PERSON_CLAIMS


def test_golden_relationship_claim_line_canonical_order():
    a = SDFBAdapter()
    a.register_entities(GOLDEN_REL_PEOPLE)
    claims = a.map_to_claims(GOLDEN_REL)
    assert len(claims) == 1
    assert to_ndjson_line(claims[0]) == EXPECTED_REL_CLAIM
