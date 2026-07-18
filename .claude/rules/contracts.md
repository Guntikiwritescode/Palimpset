---
paths: ["contracts/**"]
---
# contracts/ is FROZEN

Do not edit `claim.schema.json` or `claim-schema.sql`. They are the frozen
contracts (ARCHITECTURE §2, HANDOFF §4 rule 1), enforced by
`scripts/contract_gate.py` (checksums) — an unrecorded change fails the build.

- Flyway **V1** = `claim-schema.sql` **verbatim** (`services/engine/.../V1__initial_contract.sql`).
- **V2** is the single additive migration (§6.3) and nothing more.
- Any further schema change requires the §2 change-control process with owner
  sign-off, then `scripts/freeze_contracts.py` to re-record checksums.

A schema generated from the spec's prose "would look right and have silently
forked from the artifact real claims were validated against." If a contract must
change, propose a deviation record in `docs/DECISIONS.md` first.
