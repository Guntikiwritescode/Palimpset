"""Schema-module tests: the pinned contract loads, and the entity validator is
built correctly (not root + $ref, which would double-apply)."""

from __future__ import annotations

from palimpsest_pipeline.schema import (
    claim_schema,
    contract_path,
    entity_schema,
    validate_claim,
    validate_entity,
)


def test_contract_loaded_from_repo_root_not_a_copy():
    p = contract_path()
    # It lives under the repo-root contracts/ dir, not inside the pipeline package.
    assert p.parent.name == "contracts"
    assert "pipeline" not in p.parts
    assert claim_schema()["title"] == "PALIMPSEST interchange claim"


def test_entity_schema_is_standalone_ref_not_combined_root():
    s = entity_schema()
    # Standalone: only $schema, $defs, $ref — no root claim `required`/`properties`.
    assert set(s.keys()) == {"$schema", "$defs", "$ref"}
    assert s["$ref"] == "#/$defs/entityRecord"
    assert "required" not in s  # root claim constraints are NOT applied


def test_valid_entity_record_passes():
    entity = {
        "schema_version": "0.1.0",
        "ref": {"authority": "sdfb", "id": "1"},
        "entity_type": "person",
        "external_ids": [{"authority": "sdfb", "id": "1"}],
    }
    assert validate_entity(entity) == []


def test_claim_shaped_object_is_rejected_by_entity_validator():
    # A claim is NOT an entity record; the entity validator must reject it.
    claim = {
        "schema_version": "0.1.0",
        "subject": {"authority": "sdfb", "id": "1"},
        "predicate": "has-name",
        "object": {"literal": {"kind": "string", "value": "X"}},
        "confidence": {"kind": "unscored"},
        "method": "imported",
        "asserted_by": "pipeline",
        "support": [{"source": "s", "record_kind": "person", "external_id": "1",
                     "content_hash": "h", "raw": {}}],
    }
    assert validate_entity(claim) != []
    assert validate_claim(claim) == []


def test_unscored_must_not_carry_a_point():
    # I5: unscored is absence, never a number. A point on unscored is invalid.
    bad = {
        "schema_version": "0.1.0",
        "subject": {"authority": "sdfb", "id": "1"},
        "predicate": "has-name",
        "object": {"literal": {"kind": "string", "value": "X"}},
        "confidence": {"kind": "unscored", "point": 0.0},
        "method": "imported",
        "asserted_by": "pipeline",
        "support": [{"source": "s", "record_kind": "person", "external_id": "1",
                     "content_hash": "h", "raw": {}}],
    }
    assert validate_claim(bad) != []
