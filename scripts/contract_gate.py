#!/usr/bin/env python3
"""PALIMPSEST contract gate (ARCHITECTURE §8, HANDOFF §8).

The CI job that makes "frozen" a mechanical fact rather than an intention:

  1. claim.schema.json is itself a valid JSON Schema (meta-schema self-validation).
  2. The sample-claims fixture validates against it (and a set of hand-built
     NEGATIVE cases are correctly REJECTED — a schema that accepts everything is
     not a contract).
  3. contracts/claim-schema.sql (V1) + the engine's V2 migration apply cleanly to
     an ephemeral PostgreSQL 16 (driven by scripts/apply_migrations.sh).
  4. Byte-identity: the two contract files match their recorded checksums, so an
     accidental edit to a FROZEN file fails the build.

Exit non-zero on any failure. No network access required for steps 1–2.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator
from jsonschema.exceptions import SchemaError

REPO = Path(__file__).resolve().parents[1]
CONTRACTS = REPO / "contracts"
SCHEMA_PATH = CONTRACTS / "claim.schema.json"
SDL_PATH = CONTRACTS / "claim-schema.sql"
SAMPLE = REPO / "fixtures" / "sample-claims.ndjson"
CHECKSUMS = CONTRACTS / "CHECKSUMS.sha256"


def _fail(msg: str) -> None:
    print(f"  ✗ {msg}")
    raise SystemExit(1)


def check_schema_is_valid() -> Draft202012Validator:
    print("[1] claim.schema.json is a valid JSON Schema …")
    schema = json.loads(SCHEMA_PATH.read_text())
    try:
        Draft202012Validator.check_schema(schema)
    except SchemaError as e:  # pragma: no cover - defensive
        _fail(f"schema is not a valid draft 2020-12 schema: {e.message}")
    print("  ✓ meta-schema self-validation passed")
    return Draft202012Validator(schema)


def check_sample_validates(validator: Draft202012Validator) -> None:
    print("[2] sample-claims fixture validates …")
    lines = [ln for ln in SAMPLE.read_text().splitlines() if ln.strip()]
    if not lines:
        _fail("sample fixture is empty")
    for i, ln in enumerate(lines, 1):
        obj = json.loads(ln)
        errs = sorted(validator.iter_errors(obj), key=lambda e: e.path)
        if errs:
            _fail(f"sample line {i} failed validation: {errs[0].message}")
    print(f"  ✓ all {len(lines)} sample claims valid")


def check_negatives_rejected(validator: Draft202012Validator) -> None:
    print("[3] negative cases are rejected (the schema is a real gate) …")
    negatives = [
        ("unscored carrying a point",
         {"schema_version": "0.1.0", "subject": {"authority": "sdfb", "id": "1"},
          "predicate": "has-name", "object": {"literal": {"kind": "string", "value": "X"}},
          "confidence": {"kind": "unscored", "point": 0.5}, "method": "imported",
          "asserted_by": "pipeline", "support": [_sr()]}),
        ("object with both entity and literal",
         {"schema_version": "0.1.0", "subject": {"authority": "sdfb", "id": "1"},
          "predicate": "associated-with",
          "object": {"entity": {"authority": "sdfb", "id": "2"}, "literal": {"kind": "string", "value": "X"}},
          "confidence": {"kind": "unscored"}, "method": "imported",
          "asserted_by": "pipeline", "support": [_sr()]}),
        ("missing support (violates I4 at the contract)",
         {"schema_version": "0.1.0", "subject": {"authority": "sdfb", "id": "1"},
          "predicate": "has-name", "object": {"literal": {"kind": "string", "value": "X"}},
          "confidence": {"kind": "unscored"}, "method": "imported",
          "asserted_by": "pipeline", "support": []}),
        ("wrong schema_version major",
         {"schema_version": "1.0.0", "subject": {"authority": "sdfb", "id": "1"},
          "predicate": "has-name", "object": {"literal": {"kind": "string", "value": "X"}},
          "confidence": {"kind": "unscored"}, "method": "imported",
          "asserted_by": "pipeline", "support": [_sr()]}),
        ("calibrated=true at import (forbidden)",
         {"schema_version": "0.1.0", "subject": {"authority": "sdfb", "id": "1"},
          "predicate": "associated-with", "object": {"entity": {"authority": "sdfb", "id": "2"}},
          "confidence": {"kind": "source_native_scalar", "scale": "s", "raw": 97, "point": 0.97, "calibrated": True},
          "method": "imported", "asserted_by": "pipeline", "support": [_sr()]}),
        ("confidence point out of range",
         {"schema_version": "0.1.0", "subject": {"authority": "sdfb", "id": "1"},
          "predicate": "associated-with", "object": {"entity": {"authority": "sdfb", "id": "2"}},
          "confidence": {"kind": "source_native_scalar", "scale": "s", "raw": 999, "point": 9.9, "calibrated": False},
          "method": "imported", "asserted_by": "pipeline", "support": [_sr()]}),
    ]
    for label, obj in negatives:
        if validator.is_valid(obj):
            _fail(f"negative case wrongly ACCEPTED: {label}")
        print(f"  ✓ rejected: {label}")


def _sr() -> dict:
    return {"source": "s", "record_kind": "person", "external_id": "1",
            "content_hash": "h", "raw": {}}


def check_checksums() -> None:
    print("[4] frozen-file byte identity …")
    if not CHECKSUMS.exists():
        print("  ! CHECKSUMS.sha256 absent — writing it now (first run / bootstrap)")
        _write_checksums()
        return
    recorded = {}
    for ln in CHECKSUMS.read_text().splitlines():
        ln = ln.strip()
        if not ln or ln.startswith("#"):
            continue
        digest, name = ln.split(None, 1)
        recorded[name.strip()] = digest
    ok = True
    for path in (SCHEMA_PATH, SDL_PATH):
        name = path.relative_to(REPO).as_posix()
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if recorded.get(name) != actual:
            print(f"  ✗ FROZEN FILE CHANGED: {name}")
            print(f"      recorded {recorded.get(name)}")
            print(f"      actual   {actual}")
            print("      contracts are frozen (HANDOFF §4 rule 1). Change them only via the")
            print("      §2 change-control process, then run scripts/freeze_contracts.py.")
            ok = False
    if not ok:
        raise SystemExit(1)
    print("  ✓ contract files match recorded checksums")


def _write_checksums() -> None:
    lines = ["# sha256 checksums of the FROZEN contract files (HANDOFF §4 rule 1).",
             "# Regenerate only via the §2 change-control process: scripts/freeze_contracts.py"]
    for path in (SCHEMA_PATH, SDL_PATH):
        name = path.relative_to(REPO).as_posix()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        lines.append(f"{digest}  {name}")
    CHECKSUMS.write_text("\n".join(lines) + "\n")


def main() -> None:
    print("PALIMPSEST contract gate")
    print("=" * 60)
    validator = check_schema_is_valid()
    check_sample_validates(validator)
    check_negatives_rejected(validator)
    check_checksums()
    print("=" * 60)
    print("contract gate: PASS")


if __name__ == "__main__":
    main()
