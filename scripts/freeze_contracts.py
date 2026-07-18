#!/usr/bin/env python3
"""Record sha256 checksums of the frozen contract files.

Run this ONLY after an owner-approved §2 change-control migration of a contract.
Normal development never runs it; the contract gate treats an unrecorded change
to a frozen file as a build failure (HANDOFF §4 rule 1).
"""
from __future__ import annotations

import hashlib
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
FROZEN = [REPO / "contracts" / "claim.schema.json", REPO / "contracts" / "claim-schema.sql"]
OUT = REPO / "contracts" / "CHECKSUMS.sha256"


def main() -> None:
    lines = [
        "# sha256 checksums of the FROZEN contract files (HANDOFF §4 rule 1).",
        "# Regenerate only via the §2 change-control process: scripts/freeze_contracts.py",
    ]
    for path in FROZEN:
        name = path.relative_to(REPO).as_posix()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        lines.append(f"{digest}  {name}")
        print(f"{digest}  {name}")
    OUT.write_text("\n".join(lines) + "\n")
    print(f"\nwrote {OUT.relative_to(REPO)}")


if __name__ == "__main__":
    main()
