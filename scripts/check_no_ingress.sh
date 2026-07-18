#!/usr/bin/env bash
# HARD GATE (HANDOFF §4, §11; ARCHITECTURE §3.8/§5.6): no Ingress (or equivalent
# public exposure) in any manifest while the SDFB license is unconfirmed. Runs as
# a pre-commit hook and in CI.
set -euo pipefail

MODE="${1:-tree}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

if [[ "$MODE" == "staged" ]]; then
  mapfile -t FILES < <(git diff --cached --name-only --diff-filter=ACM | grep -E '\.(ya?ml)$' || true)
else
  mapfile -t FILES < <(git ls-files '*.yaml' '*.yml' || true)
fi

fail=0
for f in "${FILES[@]:-}"; do
  [[ -z "$f" ]] && continue
  [[ ! -f "$f" ]] && continue
  case "$f" in .github/*) continue;; esac   # CI workflows are not cluster manifests
  # Block Kubernetes public-exposure kinds. Comments in a file explaining the
  # rule are fine; an actual `kind:` declaration is not.
  if grep -Eq '^\s*kind:\s*(Ingress|Gateway|HTTPRoute)\b' "$f"; then
    echo "  ✗ $f — declares a public-exposure kind (Ingress/Gateway/HTTPRoute)"; fail=1
  fi
  if grep -Eq '^\s*type:\s*LoadBalancer\b' "$f"; then
    echo "  ✗ $f — Service type LoadBalancer exposes the cluster publicly"; fail=1
  fi
done

if [[ "$fail" -ne 0 ]]; then
  echo "no-Ingress gate: FAIL — license_confirmed=false forbids public exposure (HANDOFF §11)." >&2
  echo "Access is 'kubectl port-forward' only until the SDFB license is confirmed." >&2
  exit 1
fi
echo "no-Ingress gate: PASS"
