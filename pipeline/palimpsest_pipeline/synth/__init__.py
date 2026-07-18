"""Synthetic fixture generator."""

from .generator import (
    THRESHOLD_LADDER,
    build_source_rows,
    edge_counts_by_threshold,
    generate,
    write_fixture,
)

__all__ = [
    "THRESHOLD_LADDER",
    "build_source_rows",
    "edge_counts_by_threshold",
    "generate",
    "write_fixture",
]
