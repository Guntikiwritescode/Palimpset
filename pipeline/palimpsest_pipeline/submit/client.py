"""Engine import client — streams interchange NDJSON to the engine.

``POST /api/v1/import/batches?kind=entities|claims`` with body
``application/x-ndjson`` in batches of <= 5000 lines, headers
``X-Palimpsest-Run``, ``X-Palimpsest-Source``, ``Authorization: Bearer <token>``.
Parses the ``202`` report ``{received, inserted, duplicates, superseded,
rejected:[{line, reason}]}`` and aggregates. Any reject > 0 -> the caller exits
nonzero for operator review (Flow A step 7).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Iterable, Iterator

import requests

from ..adapters.base import Claim, EntityRecord, to_ndjson_line

MAX_BATCH_LINES = 5000
IMPORT_PATH = "/api/v1/import/batches"


def iter_batches(lines: list[str], size: int = MAX_BATCH_LINES) -> Iterator[list[str]]:
    for i in range(0, len(lines), size):
        yield lines[i : i + size]


@dataclass
class BatchReport:
    received: int = 0
    inserted: int = 0
    duplicates: int = 0
    superseded: int = 0
    rejected: list[dict[str, Any]] = field(default_factory=list)

    @classmethod
    def from_response(cls, payload: dict[str, Any]) -> "BatchReport":
        return cls(
            received=payload.get("received", 0),
            inserted=payload.get("inserted", 0),
            duplicates=payload.get("duplicates", 0),
            superseded=payload.get("superseded", 0),
            rejected=list(payload.get("rejected", [])),
        )


@dataclass
class SubmissionResult:
    """Aggregate of every batch report across one kind (entities or claims)."""

    kind: str
    received: int = 0
    inserted: int = 0
    duplicates: int = 0
    superseded: int = 0
    rejected: list[dict[str, Any]] = field(default_factory=list)
    batches: int = 0

    def add(self, report: BatchReport) -> None:
        self.received += report.received
        self.inserted += report.inserted
        self.duplicates += report.duplicates
        self.superseded += report.superseded
        self.rejected.extend(report.rejected)
        self.batches += 1

    @property
    def ok(self) -> bool:
        return not self.rejected

    def as_dict(self) -> dict[str, Any]:
        return {
            "kind": self.kind,
            "batches": self.batches,
            "received": self.received,
            "inserted": self.inserted,
            "duplicates": self.duplicates,
            "superseded": self.superseded,
            "rejected": self.rejected,
        }


class ImportClient:
    def __init__(
        self,
        engine_url: str,
        token: str,
        run_id: str,
        source_slug: str,
        *,
        session: requests.Session | None = None,
        timeout: float = 120.0,
    ) -> None:
        self.engine_url = engine_url.rstrip("/")
        self.token = token
        self.run_id = run_id
        self.source_slug = source_slug
        self.session = session or requests.Session()
        self.timeout = timeout

    def _headers(self) -> dict[str, str]:
        return {
            "Content-Type": "application/x-ndjson",
            "X-Palimpsest-Run": self.run_id,
            "X-Palimpsest-Source": self.source_slug,
            "Authorization": f"Bearer {self.token}",
        }

    def _post_batch(self, kind: str, batch_lines: list[str]) -> BatchReport:
        body = "\n".join(batch_lines) + "\n"
        resp = self.session.post(
            f"{self.engine_url}{IMPORT_PATH}",
            params={"kind": kind},
            data=body.encode("utf-8"),
            headers=self._headers(),
            timeout=self.timeout,
        )
        if resp.status_code != 202:
            raise RuntimeError(
                f"import batch failed: HTTP {resp.status_code}: {resp.text[:500]}"
            )
        return BatchReport.from_response(resp.json())

    def submit_entities(self, entities: Iterable[EntityRecord]) -> SubmissionResult:
        return self._submit("entities", [to_ndjson_line(e) for e in entities])

    def submit_claims(self, claims: Iterable[Claim]) -> SubmissionResult:
        return self._submit("claims", [to_ndjson_line(c) for c in claims])

    def _submit(self, kind: str, lines: list[str]) -> SubmissionResult:
        result = SubmissionResult(kind=kind)
        for batch in iter_batches(lines):
            if not batch:
                continue
            result.add(self._post_batch(kind, batch))
        return result
