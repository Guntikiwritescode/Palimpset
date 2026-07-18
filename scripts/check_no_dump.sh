#!/usr/bin/env bash
# HARD GATE (HANDOFF §4 rule 5, §7.5): the SDFB dump never enters git, whole or
# in part. CI/demo fixtures are synthetic. Runs as a pre-commit hook and in CI.
#
# Heuristics — the dump is the SDFB CSV export. We block:
#   * files that look like SDFB source dumps by name
#   * any *.csv under fixtures/ that is not explicitly marked synthetic
#   * files carrying the real SDFB dump's telltale header columns
# The check operates on staged files (pre-commit) or the whole tree (CI).
set -euo pipefail

MODE="${1:-tree}"   # "staged" | "tree"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

if [[ "$MODE" == "staged" ]]; then
  mapfile -t FILES < <(git diff --cached --name-only --diff-filter=ACM)
else
  mapfile -t FILES < <(git ls-files)
fi

fail=0
# Real SDFB people/relationship export headers (telltales). Synthetic fixtures
# must NOT reproduce the dump; they carry a SYNTHETIC marker instead.
DUMP_NAME_RE='(six[-_ ]?degrees|SDFB).*(dump|export|people|persons|relationships)|sdfb.*20[0-9]{2}-[0-9]{2}-[0-9]{2}'
DUMP_HEADER_RE='max_certainty.*min_certainty|person1_id.*person2_id.*max_certainty'

for f in "${FILES[@]:-}"; do
  [[ -z "$f" ]] && continue
  [[ ! -f "$f" ]] && continue
  # Allow docs and this script itself to mention the dump.
  case "$f" in
    docs/*|scripts/check_no_dump.sh|.claude/*|CLAUDE.md|README.md) continue;;
  esac
  if [[ "$f" =~ \.csv$ ]] && [[ "$f" == fixtures/* ]]; then
    if ! head -c 4096 "$f" | grep -qi 'SYNTHETIC'; then
      echo "  ✗ $f — CSV under fixtures/ without a SYNTHETIC marker (possible dump slice)"; fail=1
    fi
  fi
  if [[ "$(basename "$f")" =~ $DUMP_NAME_RE ]]; then
    echo "  ✗ $f — filename resembles an SDFB dump artifact"; fail=1
  fi
  if head -c 8192 "$f" 2>/dev/null | grep -Eqi "$DUMP_HEADER_RE"; then
    echo "  ✗ $f — contains SDFB dump header columns"; fail=1
  fi
done

if [[ "$fail" -ne 0 ]]; then
  echo "no-dump gate: FAIL — the SDFB dump must never enter git (HANDOFF §4.5)." >&2
  exit 1
fi
echo "no-dump gate: PASS"
