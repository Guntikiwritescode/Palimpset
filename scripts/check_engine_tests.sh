#!/usr/bin/env bash
# =============================================================================
# check_engine_tests.sh — CI assertion for finding F5.
# =============================================================================
# The engine's only integration test used to carry
# @Testcontainers(disabledWithoutDocker = true), which SKIPS (does not fail)
# without Docker — a green build that tested nothing. This gate makes that
# impossible to reintroduce silently. After `mvn verify`, it asserts:
#   1. surefire reports exist (the tests ran at all);
#   2. NO test was skipped (a skip that isn't a failure hides a vanished test);
#   3. at least one *IntegrationTest class executed a non-zero number of tests;
#   4. the total engine test count holds a floor (catches a suite that shrinks).
#
# Usage: bash scripts/check_engine_tests.sh [reports-dir]
#   FLOOR (env, default 20) — minimum total engine tests.
# =============================================================================
set -euo pipefail

REPORTS="${1:-services/engine/target/surefire-reports}"
FLOOR="${FLOOR:-20}"

if [[ ! -d "$REPORTS" ]]; then
  echo "FAIL: no surefire reports at '$REPORTS' — engine tests did not run." >&2
  exit 1
fi

python3 - "$REPORTS" "$FLOOR" <<'PY'
import sys, glob, os, xml.etree.ElementTree as ET

reports_dir, floor = sys.argv[1], int(sys.argv[2])
files = sorted(glob.glob(os.path.join(reports_dir, "TEST-*.xml")))
if not files:
    print(f"FAIL: no TEST-*.xml in {reports_dir}", file=sys.stderr)
    sys.exit(1)

total = skipped = failures = errors = it_tests = 0
for f in files:
    ts = ET.parse(f).getroot()
    t  = int(ts.get("tests", 0));    s  = int(ts.get("skipped", 0))
    fl = int(ts.get("failures", 0)); er = int(ts.get("errors", 0))
    name = ts.get("name", "")
    total += t; skipped += s; failures += fl; errors += er
    if name.endswith("IntegrationTest"):
        it_tests += t
    print(f"  {name}: tests={t} skipped={s} failures={fl} errors={er}")

ok = True
if failures or errors:
    print(f"FAIL: {failures} failures, {errors} errors", file=sys.stderr); ok = False
if skipped:
    print(f"FAIL: {skipped} test(s) SKIPPED — a silent skip hides a test that did not run (F5). "
          f"Use `-P no-it` to exclude integration tests explicitly instead.", file=sys.stderr); ok = False
if it_tests <= 0:
    print("FAIL: zero *IntegrationTest tests executed — the integration suite vanished (F5).", file=sys.stderr); ok = False
if total < floor:
    print(f"FAIL: total engine tests {total} < floor {floor}.", file=sys.stderr); ok = False

if ok:
    print(f"OK: {total} engine tests executed ({it_tests} integration), 0 skipped, floor {floor} held.")
sys.exit(0 if ok else 1)
PY
