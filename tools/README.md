# tools/ — NON-PRODUCTION

Phase-0 scripts live here, clearly marked non-production (ADR-004). They are
**never invoked by the engine, the pipeline, or CI**, and are retired from any
loading role the moment the engine's import path (WP2) lands.

The Phase-0 `sdfb_adapter.py` and direct-DB loader were **not supplied** to this
build (see `docs/PREFLIGHT.md`). The production SDFB adapter (in
`pipeline/palimpsest_pipeline/adapters/sdfb/`) is written from the normative
mapping rules (ARCHITECTURE §3.1, §17.2), not by importing any prototype. If the
Phase-0 prototype surfaces, place it here as the semantic reference for the
golden tests — do not wire it into any runtime path.
