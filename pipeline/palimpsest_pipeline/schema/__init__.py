"""Schema loading + validation against the frozen contract.

Loads the pinned ``contracts/claim.schema.json`` from the repo root (NO copy —
HANDOFF §4 rule 1, the contract is frozen and there is exactly one). Claims
validate against the root; entity-import records validate against
``#/$defs/entityRecord``.

The entity validator is built as a standalone schema
``{"$schema": ..., "$defs": schema["$defs"], "$ref": "#/$defs/entityRecord"}`` —
NOT the root schema combined with a ``$ref``, which would double-apply the root's
claim constraints to an entity record.
"""

from __future__ import annotations

import json
import os
from functools import lru_cache
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

CONTRACT_FILENAME = "claim.schema.json"


def _find_contract() -> Path:
    """Locate the frozen contract without ever copying it.

    Honours ``PALIMPSEST_CONTRACTS_DIR`` if set, else walks up from this file
    until a ``contracts/claim.schema.json`` is found (works for an editable
    install, whose ``__file__`` stays inside the repo).
    """
    override = os.environ.get("PALIMPSEST_CONTRACTS_DIR")
    if override:
        candidate = Path(override) / CONTRACT_FILENAME
        if candidate.is_file():
            return candidate
        raise FileNotFoundError(f"PALIMPSEST_CONTRACTS_DIR set but {candidate} missing")

    for parent in Path(__file__).resolve().parents:
        candidate = parent / "contracts" / CONTRACT_FILENAME
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        f"could not locate contracts/{CONTRACT_FILENAME} above {__file__}; "
        "set PALIMPSEST_CONTRACTS_DIR"
    )


@lru_cache(maxsize=1)
def contract_path() -> Path:
    return _find_contract()


@lru_cache(maxsize=1)
def claim_schema() -> dict[str, Any]:
    return json.loads(contract_path().read_text(encoding="utf-8"))


@lru_cache(maxsize=1)
def entity_schema() -> dict[str, Any]:
    root = claim_schema()
    # Standalone entity-record schema — do NOT combine root + $ref (double-apply).
    return {
        "$schema": root["$schema"],
        "$defs": root["$defs"],
        "$ref": "#/$defs/entityRecord",
    }


@lru_cache(maxsize=1)
def claim_validator() -> Draft202012Validator:
    return Draft202012Validator(claim_schema())


@lru_cache(maxsize=1)
def entity_validator() -> Draft202012Validator:
    return Draft202012Validator(entity_schema())


def validate_claim(claim: dict[str, Any]) -> list[str]:
    """Return a list of human-readable validation errors (empty == valid)."""
    return [
        f"{list(e.path)}: {e.message}"
        for e in claim_validator().iter_errors(claim)
    ]


def validate_entity(entity: dict[str, Any]) -> list[str]:
    return [
        f"{list(e.path)}: {e.message}"
        for e in entity_validator().iter_errors(entity)
    ]
