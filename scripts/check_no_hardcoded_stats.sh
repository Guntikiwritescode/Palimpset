#!/usr/bin/env bash
# WP4 gate (ARCHITECTURE §16, tripwire §11): the honesty page derives every
# figure live. A grep for hard-coded corpus statistics in the explorer's About
# code fails the WP. We flag literal occurrences of the measured corpus numbers
# and percent-literals in the /about component tree.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
ABOUT_GLOBS=("$REPO"/explorer/src/**/about/** "$REPO"/explorer/src/**/About*)

# The measured figures that must never be typed into markup.
BANNED_RE='15,?882|261,?177|187,?482|80\.4|19\.6|17\.5|99\.7|1,?575'

shopt -s globstar nullglob
fail=0
found_any=0
for f in $REPO/explorer/src/**/*bout*.{ts,tsx} $REPO/explorer/src/**/about/**/*.{ts,tsx}; do
  [[ -f "$f" ]] || continue
  found_any=1
  if grep -Enq "$BANNED_RE" "$f"; then
    echo "  ✗ hard-coded statistic in $f:"; grep -En "$BANNED_RE" "$f" | sed 's/^/      /'
    fail=1
  fi
done

if [[ "$found_any" -eq 0 ]]; then
  echo "no-hardcoded-stats gate: no About component found yet (skipping until WP4)"
  exit 0
fi
if [[ "$fail" -ne 0 ]]; then
  echo "no-hardcoded-stats gate: FAIL — the honesty page must derive figures live (§16)." >&2
  exit 1
fi
echo "no-hardcoded-stats gate: PASS"
