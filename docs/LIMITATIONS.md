# LIMITATIONS — what this system cannot tell you

**Audience:** P1 (the evaluating reviewer) and P2 (the historian). HANDOFF §7.4
intends this file to be authored once and rendered live as `/about`; in this
build only the *figures* on `/about` are live — its prose is authored separately
(see the honesty caveat in the first section). Created at WP0; filled at WP4 with
the honesty-page copy.

The honesty page derives **every number** live from the corpus — nothing on it is
typed into markup (§16). This file carries the prose; the live figures come from
`/stats/summary`, `/runs`, and `/meta/sources`.

## This build has only ever processed synthetic data

**Read this first.** The corpus loaded in every build, demo, and CI run of this
repository is **entirely synthetic** — invented people and invented ties, authored
to reproduce the *shape* and *anomaly classes* of the real source, never a subset
of it (HANDOFF §5 rule 5; `fixtures/synthetic/README.md`). No real historical
data has ever been loaded here.

The exact size of that synthetic corpus (source: the fixture files themselves):

- **8 person entities** — `fixtures/synthetic/entities.ndjson` (8 lines)
- **49 claims** — `fixtures/synthetic/claims.ndjson` (49 lines)
- **3 synthetic sources** — `synth-fixture`, `correspsearch-synth`, `folger-synth`

**Every figure on `/about` is a fact about this invented data.** Computing each
number live from the loaded corpus (§16) is the correct behaviour — but it means
that while this build runs the synthetic fixture, its "8 people · 49 claims · 3
sources", its confidence histogram, its threshold shares, its gender split, and
its anomaly counters all describe the fixture, not early-modern society and not
the SDFB corpus. They are honestly-derived numbers about honestly-labelled
fiction.

**The SDFB dump was never supplied to any build session** (DEV-001;
`docs/phase0-worklog.md` is itself a placeholder). Consequently the named
fixtures **FIX-CORPUS** (15,882 entities · 261,177 claims · 187,482 source
records), **FIX-ANOMALY** (365 · 14 · 1,575 · 1 · 6), and **FIX-BACON** (SDFB id
10000473 — 29→13 ties windowed to 1600; 2 certainly-active at ≥ 0.60 in 1600) are
**unreproduced in this repository**. Their measured values are carried forward
from a prior Phase-0 session and cited in `docs/ARCHITECTURE.md` §1; they are
asserted only by a slow test suite that runs against a locally-supplied dump
(Q-6), **never in CI or any build here**. Until the dump is supplied, treat every
FIX-\* number as unverified in this codebase.

**The slider defaults to 0 on a finding that has never been re-observed here.**
The default confidence threshold is 0 because 0.60 was measured to leave 7,125 of
15,882 entity pages (44.9 %) blank, and 0.90 to leave 84 % blank (HANDOFF §5 N1,
§17.F2). That measurement was taken on the real 15,882-entity corpus. This
codebase holds only the 8-entity synthetic fixture, so the 44.9 % figure has
**never been re-observed here** — it is inherited from the spec, not reproduced by
this build.

> **One honesty caveat about this file.** HANDOFF §7.4 intends `LIMITATIONS.md`
> to be authored once and rendered live as `/about`. That intent is only
> partially met: `/about` (`explorer/src/routes/About.tsx`) derives its *figures*
> live, but its *prose* is hand-authored JSX independent of this file — so the two
> are maintained separately, and until this section neither reader-facing surface
> stated, in words, that the loaded data is synthetic. This section is that
> disclosure; unifying the two surfaces is tracked as follow-up (see WP-R1
> checkpoint).

## What this system cannot tell you (§16 Panel 9)
- It cannot tell you two people *didn't* know each other — absence of a tie means
  no surviving attested evidence, never no relationship.
- It cannot tell you a relationship's nature beyond what the source recorded —
  SDFB ties are generic associations; there is no typed predicate in this dump.
- It cannot support causal inference.
- It cannot be treated as a sample of early-modern society — only as a sample of
  *what survived and was judged worth recording*.

## Uncalibrated confidence
Confidence is the source's own score, normalized. It has not been calibrated
against ground truth (calibration is Phase-3 work). Treat it as a ranking signal,
not a probability. *(The retracted precision figure is deliberately omitted until
someone reads the source paper directly — D1/Q-1.)*

## Single source in Part 1; survivorship bias; the cliodynamics debate
Filled at WP4 from the live corpus, with this project positioned as the
uncertainty/provenance infrastructure the cliodynamics critiques call for — not
as an endorsement of any theory of history.
