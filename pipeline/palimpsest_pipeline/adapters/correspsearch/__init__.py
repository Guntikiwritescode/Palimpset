"""correspSearch adapter (WP2b) — CMIF/TEI letters → interchange claims.

Built after the coverage probe passed (docs/probe-correspsearch.md). Reuses the
WP2 adapter interface wholesale (§20 A1). See adapter.py and cmif.py.
"""
from .adapter import CorrespSearchAdapter
from .cmif import parse_cmif

__all__ = ["CorrespSearchAdapter", "parse_cmif"]
