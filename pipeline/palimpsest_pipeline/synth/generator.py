"""Synthetic fixture generator (ARCHITECTURE §14.3, §20 A5; Q-5).

NEVER source-derived. Invented people and ties that reproduce the corpus's SHAPE
and every ANOMALY class, not a subset of SDFB — a subset of license-unconfirmed
data is still license-unconfirmed. This is the license-safe path for CI and
``make demo``.

It reproduces the INTERACTION (edge count strictly decreases as the confidence
threshold rises), NOT the measured numbers (A5 / Q-6) — the FIX-* measured
assertions run in the slow suite against a locally-supplied dump.

Method: build synthetic *source rows* in the documented SDFB CSV shape, then run
them through the real :class:`SDFBAdapter` (with authority ``synth``). This means
(a) the fixtures are guaranteed valid — the adapter self-validates — and (b) the
generator genuinely exercises every anomaly path (the counters increment), so the
fixture is honest about what the adapter does, not a hand-written mock of it.

Anomaly classes exercised:
  - odnb sentinel      — one DISAMBIG twin has odnb_id "0" (no has-external-id);
                         the other has a real id, so the contrast is visible.
  - dangling skipped   — a relationship references an id that is not a person.
  - inverted-dropped   — a relationship with start-year > end-year (undated claim).
  - alias names        — a person with pipe-delimited alternate names.
  - bad year           — a person whose birth_year is unparseable (no born claim).
  - duplicate pair     — the same pair appears twice, kept as separate claims.

FIX-DISAMBIG shape: two people both named "Francis Bacon", distinguishable by life
dates (1561-1626 vs 1600-1663).
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from ..adapters.base import Claim, EntityRecord, to_ndjson_line
from ..adapters.sdfb import SDFBAdapter
from ..quality import AnomalyCounters

SYNTH_AUTHORITY = "synth"
SYNTH_SOURCE_SLUG = "synth-fixture"
SYNTH_SCALE_NAME = "synth_0_100"

# The threshold ladder the interaction is demonstrated on. The generator
# guarantees a STRICTLY decreasing edge count across it by seeding one backbone
# edge in each half-open confidence band [0,20) [20,40) [40,60) [60,80) [80,90)
# [90,100]. Extra scale-driven edges only add to counts, preserving the property.
THRESHOLD_LADDER: tuple[float, ...] = (0.0, 0.2, 0.4, 0.6, 0.8, 0.9)

# One max_certainty per band -> the backbone that guarantees strict monotonicity.
_BACKBONE_CERTAINTIES = (10, 30, 50, 70, 85, 95)


def _person(
    pid: str,
    display_name: str,
    *,
    search_names_all: str = "",
    birth_year: str = "",
    birth_type: str = "IN",
    death_year: str = "",
    death_type: str = "IN",
    gender: str = "",
    description: str = "",
    odnb_id: str = "",
) -> dict[str, Any]:
    return {
        "record_kind": "person",
        "id": pid,
        "display_name": display_name,
        "search_names_all": search_names_all,
        "birth_year": birth_year,
        "birth_type": birth_type,
        "death_year": death_year,
        "death_type": death_type,
        "gender": gender,
        "description": description,
        "odnb_id": odnb_id,
    }


def _rel(
    rid: str,
    p1: str,
    p2: str,
    max_certainty: int,
    *,
    start_year: str = "1590",
    start_type: str = "AF/IN",
    end_year: str = "1626",
    end_type: str = "BF/IN",
) -> dict[str, Any]:
    return {
        "record_kind": "relationship",
        "id": rid,
        "person1_id": p1,
        "person2_id": p2,
        "max_certainty": max_certainty,
        "start_year": start_year,
        "start_type": start_type,
        "end_year": end_year,
        "end_type": end_type,
    }


def build_source_rows(scale: int = 1) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Build synthetic (people, relationships) source rows in SDFB CSV shape.

    ``scale`` (>= 1) adds extra blocks of people + edges; the base set (all anomaly
    classes, the DISAMBIG twins, and the monotonic backbone) is always present.
    """
    if scale < 1:
        raise ValueError("scale must be >= 1")

    people: list[dict[str, Any]] = [
        # --- FIX-DISAMBIG twins: same name, different life dates ----------
        _person("1", "Francis Bacon", birth_year="1561", death_year="1626",
                gender="male", description="Philosopher and statesman.", odnb_id="990"),
        _person("2", "Francis Bacon", birth_year="1600", death_year="1663",
                gender="male", description="Namesake, a generation later.",
                odnb_id="0"),  # odnb sentinel -> no has-external-id claim
        # --- alias names (§17.2 S6) --------------------------------------
        _person("3", "Helen Alexander",
                search_names_all="Helen Alexander|Helen Umpherston|Helen Currie",
                birth_year="1570", death_year="1640", gender="female", odnb_id="1201"),
        _person("4", "Anne Rivers", birth_year="1575", death_year="1630",
                gender="female", odnb_id="1330"),
        _person("5", "Thomas Vale", birth_year="circ", death_year="1610",
                gender="male", odnb_id="1450"),  # bad_year_value on birth
        _person("6", "William Hale", birth_year="1558", death_year="1620",
                gender="male", odnb_id="1502"),
        _person("7", "Margaret Frost", birth_year="1565", death_year="1633",
                gender="female", odnb_id="1610"),
    ]

    relationships: list[dict[str, Any]] = []
    # Backbone: one edge per confidence band, all incident to hub person "1".
    hubs = ["3", "4", "5", "6", "7", "2"]
    for i, cert in enumerate(_BACKBONE_CERTAINTIES):
        relationships.append(_rel(f"r-bb-{i}", "1", hubs[i], cert))

    # Duplicate pair kept as a separate claim (same pair 1-3, different certainty).
    relationships.append(_rel("r-dup", "3", "1", 88))
    # Inverted derived bounds -> dropped, claim becomes undated.
    relationships.append(
        _rel("r-inv", "3", "4", 55, start_year="1650", start_type="AF/IN",
             end_year="1600", end_type="BF/IN")
    )
    # Dangling endpoint -> skipped (person "9999" does not exist).
    relationships.append(_rel("r-dangle", "1", "9999", 77))

    # Scale-driven extra people + edges (spread certainties, all valid).
    next_pid = 8
    for block in range(scale - 1):
        base = next_pid
        for k in range(4):
            pid = str(base + k)
            people.append(
                _person(pid, f"Synthetic Person {pid}",
                        birth_year=str(1560 + (base + k) % 40),
                        death_year=str(1620 + (base + k) % 30),
                        gender="female" if (base + k) % 2 else "male",
                        odnb_id=str(2000 + base + k))
            )
        # Edges fan out from the block, certainties spread across the range.
        for k in range(4):
            src = str(base + k)
            dst = str(base + ((k + 1) % 4))
            cert = 12 + (k * 23 + block * 7) % 84  # spread, distinct-ish
            relationships.append(_rel(f"r-s{block}-{k}", src, dst, cert))
        next_pid = base + 4

    return people, relationships


def generate(scale: int = 1) -> tuple[list[EntityRecord], list[Claim], AnomalyCounters]:
    """Run the synthetic source rows through the real adapter."""
    people, relationships = build_source_rows(scale)
    adapter = SDFBAdapter(
        authority=SYNTH_AUTHORITY,
        source_slug=SYNTH_SOURCE_SLUG,
        certainty_scale=SYNTH_SCALE_NAME,
    )
    entities, claims = adapter.transform(people, relationships)
    return entities, claims, adapter.counters


def edge_counts_by_threshold(
    claims: list[Claim], thresholds: tuple[float, ...] = THRESHOLD_LADDER
) -> list[tuple[float, int]]:
    """Count entity-ranged (``associated-with``) claims whose confidence point
    meets each threshold. Unscored claims never pass a numeric threshold (I5)."""
    edges = [
        c for c in claims
        if c.predicate == "associated-with" and c.confidence.get("kind") == "source_native_scalar"
    ]
    out: list[tuple[float, int]] = []
    for t in thresholds:
        out.append((t, sum(1 for c in edges if c.confidence["point"] >= t)))
    return out


def write_fixture(out_dir: str | Path, scale: int = 1) -> dict[str, Any]:
    """Generate and write ``entities.ndjson`` + ``claims.ndjson`` to ``out_dir``.

    Returns a small summary (counts, anomaly counters, edge-count table).
    """
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    entities, claims, counters = generate(scale)

    (out / "entities.ndjson").write_text(
        "".join(to_ndjson_line(e) + "\n" for e in entities), encoding="utf-8"
    )
    (out / "claims.ndjson").write_text(
        "".join(to_ndjson_line(c) + "\n" for c in claims), encoding="utf-8"
    )

    return {
        "out_dir": str(out),
        "scale": scale,
        "entities": len(entities),
        "claims": len(claims),
        "anomaly_counters": counters.as_dict(),
        "edge_counts": edge_counts_by_threshold(claims),
    }
