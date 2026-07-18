"""Quality accounting — anomaly counters aggregated into the run report.

The pipeline never invents data (HANDOFF §4 rule 2): sentinels, dangling
references, inverted ranges and unparseable years are **counted and surfaced**,
never patched over. These counters feed the run manifest and, downstream, the
honesty page's anomaly panel.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass
class AnomalyCounters:
    """The pipeline's anomaly counters (ARCHITECTURE Flow A step 3, §19 FIX-ANOMALY).

    - ``odnb_zero_sentinel`` — ``odnb_id`` in {"", "0"} treated as absent; no
      has-external-id claim emitted.
    - ``relationship_dangling_endpoint_skipped`` — a relationship touching an
      unknown entity id; skipped, never fabricated (I6).
    - ``valid_time_inverted_dropped_bounds`` — derived bounds that invert; dropped
      (original preserved), claim becomes undated.
    - ``bad_year_value`` — a year present but unparseable.
    - ``duplicate_pair_kept`` — a symmetric pair seen more than once; kept as a
      separate claim (no dedup that loses a source record).
    """

    odnb_zero_sentinel: int = 0
    relationship_dangling_endpoint_skipped: int = 0
    valid_time_inverted_dropped_bounds: int = 0
    bad_year_value: int = 0
    duplicate_pair_kept: int = 0

    def as_dict(self) -> dict[str, int]:
        return {
            "odnb_zero_sentinel": self.odnb_zero_sentinel,
            "relationship_dangling_endpoint_skipped": self.relationship_dangling_endpoint_skipped,
            "valid_time_inverted_dropped_bounds": self.valid_time_inverted_dropped_bounds,
            "bad_year_value": self.bad_year_value,
            "duplicate_pair_kept": self.duplicate_pair_kept,
        }

    def total(self) -> int:
        return sum(self.as_dict().values())
