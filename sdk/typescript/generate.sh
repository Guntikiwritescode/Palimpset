#!/usr/bin/env bash
# Generate the typed TS client from the engine's OpenAPI document and fail on
# drift (ARCHITECTURE §3.6, §5.4). Direction of truth: engine → OpenAPI → SDK.
# The generated src/schema.d.ts is COMMITTED and never hand-edited.
#
#   generate.sh          — regenerate openapi.json (if an engine is up) + types
#   generate.sh --check  — regenerate into a temp file and diff against committed
set -euo pipefail
cd "$(dirname "$0")"

ENGINE="${PALIMPSEST_ENGINE:-http://127.0.0.1:8080}"
CHECK=0
[[ "${1:-}" == "--check" ]] && CHECK=1

# Refresh the OpenAPI snapshot from a running engine when one is reachable;
# otherwise fall back to the committed openapi.json (CI starts an engine).
if curl -sf "$ENGINE/v3/api-docs" -o /tmp/openapi.fresh.json 2>/dev/null; then
  if [[ "$CHECK" -eq 1 ]]; then
    if ! diff -q openapi.json /tmp/openapi.fresh.json >/dev/null; then
      echo "✗ committed openapi.json is stale vs the running engine" >&2
      diff openapi.json /tmp/openapi.fresh.json | head -40 >&2 || true
      exit 1
    fi
  else
    cp /tmp/openapi.fresh.json openapi.json
  fi
fi

if [[ "$CHECK" -eq 1 ]]; then
  npx --yes openapi-typescript openapi.json -o /tmp/schema.fresh.d.ts >/dev/null
  if ! diff -q src/schema.d.ts /tmp/schema.fresh.d.ts >/dev/null; then
    echo "✗ SDK drift: src/schema.d.ts differs from a fresh generation." >&2
    echo "  Run 'bash sdk/typescript/generate.sh' and commit the result." >&2
    diff src/schema.d.ts /tmp/schema.fresh.d.ts | head -40 >&2 || true
    exit 1
  fi
  echo "✓ SDK in sync with the engine OpenAPI"
else
  npx --yes openapi-typescript openapi.json -o src/schema.d.ts
  echo "✓ regenerated src/schema.d.ts"
fi
