"""Local-dump reader: CSV/TSV rows, with content-hash verification.

SDFB is a static dump, so the harvester verifies input files against recorded
content hashes before handing rows to the adapter (Flow A step 2). A hash mismatch
aborts the run *before* submission — nothing partial reaches the store (§18: source
schema changed at harvest).
"""

from __future__ import annotations

import csv
import hashlib
from pathlib import Path
from typing import Iterator


def file_content_hash(path: str | Path) -> str:
    """sha256 of a file's bytes, prefixed ``sha256:``."""
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return "sha256:" + h.hexdigest()


def verify_content_hash(path: str | Path, expected: str) -> None:
    """Raise ``ValueError`` unless the file hashes to ``expected``."""
    actual = file_content_hash(path)
    if actual != expected:
        raise ValueError(
            f"content-hash mismatch for {path}: expected {expected}, got {actual}"
        )


def _delimiter_for(path: Path) -> str:
    return "\t" if path.suffix.lower() in {".tsv", ".tab"} else ","


def read_delimited(
    path: str | Path,
    *,
    expected_hash: str | None = None,
    record_kind: str | None = None,
) -> Iterator[dict[str, str]]:
    """Yield rows of a CSV/TSV as dicts.

    Verifies ``expected_hash`` first when supplied. Tags each row with
    ``record_kind`` when supplied (so the adapter can dispatch on it).
    """
    path = Path(path)
    if expected_hash is not None:
        verify_content_hash(path, expected_hash)
    with open(path, newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh, delimiter=_delimiter_for(path))
        for row in reader:
            clean = {k: (v if v is not None else "") for k, v in row.items()}
            if record_kind is not None:
                clean["record_kind"] = record_kind
            yield clean
