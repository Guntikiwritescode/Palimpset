"""Run manifest — every pipeline run traces to the claims it produced.

The manifest records run id, adapter + version, source slug + version, input
content hashes, counts emitted, anomaly counters, and the submission receipt
(ARCHITECTURE §3.1). Any claim in the store can then be traced to the exact run
that produced it; the engine stores the run id it was given on each import batch.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


@dataclass
class RunManifest:
    run_id: str
    adapter: str
    adapter_version: str
    source_slug: str
    source_version: str
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    input_content_hashes: dict[str, str] = field(default_factory=dict)
    counts: dict[str, int] = field(default_factory=dict)
    anomaly_counters: dict[str, int] = field(default_factory=dict)
    submission: dict[str, Any] | None = None
    validation: dict[str, Any] | None = None

    def as_dict(self) -> dict[str, Any]:
        return {
            "run_id": self.run_id,
            "created_at": self.created_at,
            "adapter": self.adapter,
            "adapter_version": self.adapter_version,
            "source_slug": self.source_slug,
            "source_version": self.source_version,
            "input_content_hashes": self.input_content_hashes,
            "counts": self.counts,
            "anomaly_counters": self.anomaly_counters,
            "validation": self.validation,
            "submission": self.submission,
        }

    def write(self, path: str | Path) -> Path:
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.as_dict(), indent=2, sort_keys=True), encoding="utf-8")
        return path
