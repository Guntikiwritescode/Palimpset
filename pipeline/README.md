# palimpsest_pipeline

The PALIMPSEST ingestion pipeline (ARCHITECTURE §3.1, §5.2). Everything between
"a source exists in the world" and "valid interchange claims are submitted to the
engine": harvest → adapt → quality-account → submit.

- **harvest/** — fetchers (local dump reader with content-hash verification; HTTP
  with ETag + backoff for API sources).
- **adapters/** — pure source→interchange transforms. `adapters/sdfb/` is the
  production SDFB adapter; its mapping rules are normative
  (`.claude/rules/pipeline-adapters.md`).
- **schema/** — loads the pinned `contracts/claim.schema.json` from the repo root
  (never a copy) and validates every emitted claim / entity record.
- **submit/** — engine client (batched NDJSON import) + run-manifest writer.
- **quality/** — anomaly counters aggregated into the run report.
- **synth/** — synthetic fixture generator (license-safe; reproduces the corpus
  shape and every anomaly class, never a subset of SDFB — §20 A5).

The pipeline owns no database access (ADR-004: it writes only through the engine)
and it never invents data: sentinels, dangling references, inverted ranges and the
like are counted and surfaced, never patched over.

## Usage

```
palimpsest-pipeline ingest sdfb --data-dir DIR --run-id R [--engine URL --token T]
palimpsest-pipeline validate DIR
palimpsest-pipeline synth --out fixtures/synthetic [--scale N]
```

The CLI exits nonzero on any schema-invalid claim or engine reject — "silent
success or loud, specific failure".
