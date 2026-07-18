"""The production SDFB adapter (ARCHITECTURE §3.1, §17.2; rules normative in
``.claude/rules/pipeline-adapters.md``).

Six Degrees of Francis Bacon, 2017-10-13 dump. ~15,000 early-modern figures and
~170,000 NLP-inferred relationships. This is a *re-implementation* of the Phase 0
``sdfb_adapter.py`` as a tested package — not an import of the prototype.

CSV shape (documented assumption; the golden tests exercise small SYNTHETIC rows
in this shape, never the real dump):

  people rows:
    id, display_name, search_names_all, birth_year, birth_type,
    death_year, death_type, gender, description, odnb_id
      - search_names_all: pipe-delimited alternate names (may repeat the display
        name); non-display entries become alias name-claims (§17.2 S6).
      - birth_type / death_type: a date code (IN/CA/AF/BF/AF/IN/BF/IN) annotating
        the corresponding year. Absent code defaults to IN.
      - odnb_id: an ODNB identifier, or the sentinel "" / "0" meaning absent.

  relationship rows:
    id, person1_id, person2_id, max_certainty, start_year, start_type,
    end_year, end_type
      - max_certainty: SDFB's 0-100 bootstrap selection frequency (§17.2 S4) — a
        ranking signal, NOT a probability; carried verbatim, calibrated=false.
      - start_type / end_type: date codes interpreted per ``dates`` (§17.2 S1-S3).

Normative behaviours implemented here:
  - odnb sentinel -> absent (count ``odnb_zero_sentinel``); no has-external-id.
  - relationship with an unknown endpoint -> skipped + counted
    (``relationship_dangling_endpoint_skipped``); the entity is NEVER fabricated.
  - inverted derived bounds -> dropped, original preserved, claim becomes undated
    (count ``valid_time_inverted_dropped_bounds``).
  - symmetric edges canonically ordered by numeric id; duplicate pairs kept as
    SEPARATE claims (count ``duplicate_pair_kept``; no dedup that loses a record).
  - AF vs AF/IN and BF vs BF/IN preserved (§17.2 S2); CA keeps its year with an
    ``approximate`` marker and no window (§17.2 S3 / D6).
  - alias name-claims from search_names_all (``method_detail.name_kind="alias"``);
    primary is ``name_kind="primary"`` (§17.2 S6).
  - ``bad_year_value`` counts unparseable years.
  - person attribute claims are all ``unscored`` (SDFB scores no attribute);
    relationship claims carry ``source_native_scalar`` from max_certainty/100.
  - SDFB has no typed predicate, so relationships use the generic ``associated-with``.

The adapter is pure w.r.t. I/O. It holds one piece of in-memory state — the set of
known entity ids, populated from the person pass — which relationship mapping needs
to detect dangling endpoints. Populate it via :meth:`register_entities` (or the
one-shot :meth:`transform`) before mapping relationships.
"""

from __future__ import annotations

import hashlib
import json
from typing import Any, Iterable

from ..base import (
    SCHEMA_VERSION,
    Adapter,
    Authority,
    Claim,
    EntityRecord,
    RelationType,
    entity_ref,
    literal,
    source_native_scalar,
    unscored,
)
from ...quality import AnomalyCounters
from . import dates

ODNB_SENTINELS = {"", "0"}


def _content_hash(raw: dict[str, Any]) -> str:
    """A stable content hash of a verbatim source record.

    Canonical JSON (sorted keys) -> sha256; identical bytes hash identically, so
    the engine's ``(source, record_kind, external_id)`` upsert can tell a duplicate
    (same hash, no-op) from a supersession (different hash) — Flow A step 6c.
    """
    canonical = json.dumps(raw, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return "sha256:" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()


class SDFBAdapter(Adapter):
    name = "sdfb"
    version = "0.1.0"

    def __init__(
        self,
        *,
        authority: str = "sdfb",
        source_slug: str = "sdfb-2017-10-13",
        certainty_scale: str = "sdfb_max_certainty_0_100",
    ) -> None:
        self.authority = authority
        self.source_slug = source_slug
        self.certainty_scale = certainty_scale
        self.counters = AnomalyCounters()
        self._known_ids: set[str] = set()
        self._seen_pairs: set[tuple[str, str]] = set()

    # ------------------------------------------------------------------ #
    # Interface registration
    # ------------------------------------------------------------------ #
    def register_types(self) -> list[RelationType]:
        return [
            RelationType("associated-with", "associated with", range="entity", symmetric=True),
            RelationType("has-name", "has name", range="literal"),
            RelationType("born", "born", range="literal"),
            RelationType("died", "died", range="literal"),
            RelationType("has-gender", "has gender", range="literal"),
            RelationType("has-description", "has description", range="literal"),
            RelationType("has-external-id", "has external id", range="literal"),
        ]

    def register_authorities(self) -> list[Authority]:
        return [
            Authority(self.authority, "Six Degrees of Francis Bacon", identity_bearing=True),
            # ODNB is a has-external-id target, NOT an identity anchor (schema note).
            Authority("odnb", "Oxford Dictionary of National Biography", identity_bearing=False),
        ]

    def normalize_names(self, raw_name: str) -> str:
        return " ".join(str(raw_name).split()).strip()

    def map_uncertainty(self, native: dict[str, Any]) -> Any:
        """Map a native uncertainty encoding.

        ``{"kind": "certainty", "value": <0-100 int>}`` -> a confidence object.
        ``{"kind": "interval", "start": (code, year)|None, "end": (code, year)|None}``
        -> ``(valid_time_dict, inverted: bool)``.
        """
        kind = native.get("kind")
        if kind == "certainty":
            return source_native_scalar(int(native["value"]), self.certainty_scale)
        if kind == "interval":
            return self._build_interval(native.get("start"), native.get("end"))
        raise ValueError(f"unknown native uncertainty: {native!r}")

    # ------------------------------------------------------------------ #
    # Orchestration
    # ------------------------------------------------------------------ #
    def register_entities(self, people: Iterable[dict[str, Any]]) -> list[EntityRecord]:
        """First pass: mint one entity per person row and register its id.

        Registration must precede relationship mapping so dangling endpoints are
        detectable.
        """
        entities: list[EntityRecord] = []
        for row in people:
            for rec in self.map_to_entities(row):
                entities.append(rec)
                self._known_ids.add(rec.ref["id"])
        return entities

    def transform(
        self,
        people: Iterable[dict[str, Any]],
        relationships: Iterable[dict[str, Any]],
    ) -> tuple[list[EntityRecord], list[Claim]]:
        """One-shot: entities + all claims, with counters accumulated on ``self``."""
        people = list(people)
        entities = self.register_entities(people)
        claims: list[Claim] = []
        for row in people:
            claims.extend(self._person_claims(row))
        for row in relationships:
            claims.extend(self._relationship_claims(row))
        return entities, claims

    # ------------------------------------------------------------------ #
    # Interface: map_to_entities / map_to_claims
    # ------------------------------------------------------------------ #
    def map_to_entities(self, source_record: dict[str, Any]) -> list[EntityRecord]:
        if source_record.get("record_kind") == "relationship":
            return []
        pid = str(source_record["id"]).strip()
        ref = entity_ref(self.authority, pid)
        return [EntityRecord(ref=ref, entity_type="person", external_ids=[dict(ref)])]

    def map_to_claims(self, source_record: dict[str, Any]) -> list[Claim]:
        kind = source_record.get("record_kind")
        if kind == "relationship":
            return self._relationship_claims(source_record)
        if kind == "person" or "id" in source_record:
            return self._person_claims(source_record)
        raise ValueError(f"cannot classify source record: {source_record!r}")

    # ------------------------------------------------------------------ #
    # Person mapping
    # ------------------------------------------------------------------ #
    def _person_support(self, row: dict[str, Any]) -> list[dict[str, Any]]:
        pid = str(row["id"]).strip()
        raw = {k: v for k, v in row.items() if k != "record_kind"}
        return [
            {
                "source": self.source_slug,
                "record_kind": "person",
                "external_id": pid,
                "content_hash": _content_hash(raw),
                "raw": raw,
            }
        ]

    def _person_claims(self, row: dict[str, Any]) -> list[Claim]:
        pid = str(row["id"]).strip()
        subject = entity_ref(self.authority, pid)
        support = self._person_support(row)
        claims: list[Claim] = []

        # --- names: primary + aliases (§17.2 S6) --------------------------
        primary = self.normalize_names(row.get("display_name", ""))
        if primary:
            claims.append(
                Claim(
                    subject=subject,
                    predicate="has-name",
                    obj={"literal": literal("string", primary)},
                    confidence=unscored(),
                    method="imported",
                    asserted_by="pipeline",
                    support=support,
                    method_detail={"name_kind": "primary"},
                )
            )
        for alias in self._aliases(row.get("search_names_all", ""), primary):
            claims.append(
                Claim(
                    subject=subject,
                    predicate="has-name",
                    obj={"literal": literal("string", alias)},
                    confidence=unscored(),
                    method="imported",
                    asserted_by="pipeline",
                    support=support,
                    method_detail={"name_kind": "alias"},
                )
            )

        # --- born / died: attribute claims, year literal, no place (Q-10) -
        for predicate, year_field, type_field in (
            ("born", "birth_year", "birth_type"),
            ("died", "death_year", "death_type"),
        ):
            claim = self._life_date_claim(
                subject, support, predicate, row.get(year_field), row.get(type_field)
            )
            if claim is not None:
                claims.append(claim)

        # --- gender ------------------------------------------------------
        gender = str(row.get("gender", "") or "").strip()
        if gender:
            claims.append(
                Claim(
                    subject=subject,
                    predicate="has-gender",
                    obj={"literal": literal("gender", gender)},
                    confidence=unscored(),
                    method="imported",
                    asserted_by="pipeline",
                    support=support,
                )
            )

        # --- description -------------------------------------------------
        description = str(row.get("description", "") or "").strip()
        if description:
            claims.append(
                Claim(
                    subject=subject,
                    predicate="has-description",
                    obj={"literal": literal("string", description)},
                    confidence=unscored(),
                    method="imported",
                    asserted_by="pipeline",
                    support=support,
                )
            )

        # --- ODNB external id, honouring the sentinel --------------------
        odnb = str(row.get("odnb_id", "") or "").strip()
        if odnb in ODNB_SENTINELS:
            self.counters.odnb_zero_sentinel += 1
        else:
            claims.append(
                Claim(
                    subject=subject,
                    predicate="has-external-id",
                    obj={"literal": literal("external-id", odnb, authority="odnb")},
                    confidence=unscored(),
                    method="imported",
                    asserted_by="pipeline",
                    support=support,
                )
            )

        return claims

    def _aliases(self, search_names_all: str, primary: str) -> list[str]:
        seen: set[str] = set()
        out: list[str] = []
        for part in str(search_names_all or "").split("|"):
            name = self.normalize_names(part)
            if not name or name == primary or name in seen:
                continue
            seen.add(name)
            out.append(name)
        return out

    def _life_date_claim(
        self,
        subject: dict[str, str],
        support: list[dict[str, Any]],
        predicate: str,
        year_raw: Any,
        type_raw: Any,
    ) -> Claim | None:
        try:
            year = dates.parse_year(year_raw)
        except dates.BadYear:
            self.counters.bad_year_value += 1
            return None
        if year is None:
            return None
        code = str(type_raw or "IN").strip() or "IN"
        earliest, latest, approx = dates.interpret_endpoint(code, year)
        valid_time: dict[str, Any] = {
            "start_earliest": earliest,
            "start_latest": latest,
            "approximate": approx,
            "original": {"type_code": code, "year": str(year)},
        }
        return Claim(
            subject=subject,
            predicate=predicate,
            obj={"literal": literal("year", year)},
            valid_time=valid_time,
            confidence=unscored(),
            method="imported",
            asserted_by="pipeline",
            support=support,
        )

    # ------------------------------------------------------------------ #
    # Relationship mapping
    # ------------------------------------------------------------------ #
    def _relationship_claims(self, row: dict[str, Any]) -> list[Claim]:
        p1 = str(row["person1_id"]).strip()
        p2 = str(row["person2_id"]).strip()

        # Dangling endpoint -> skip + count; NEVER fabricate the entity (I6).
        if p1 not in self._known_ids or p2 not in self._known_ids:
            self.counters.relationship_dangling_endpoint_skipped += 1
            return []

        # Canonical order by numeric id; duplicate pairs kept as separate claims.
        a, b = self._canonical_pair(p1, p2)
        if (a, b) in self._seen_pairs:
            self.counters.duplicate_pair_kept += 1
        else:
            self._seen_pairs.add((a, b))

        subject = entity_ref(self.authority, a)
        obj_ref = entity_ref(self.authority, b)

        # Confidence from max_certainty (verbatim; uncalibrated ranking signal).
        confidence = source_native_scalar(int(row["max_certainty"]), self.certainty_scale)

        start = self._endpoint_spec(row.get("start_year"), row.get("start_type"))
        end = self._endpoint_spec(row.get("end_year"), row.get("end_type"))
        valid_time = self._build_interval(start, end)

        raw = {k: v for k, v in row.items() if k != "record_kind"}
        rel_id = str(row.get("id") or f"{p1}-{p2}").strip()
        support = [
            {
                "source": self.source_slug,
                "record_kind": "relationship",
                "external_id": rel_id,
                "content_hash": _content_hash(raw),
                "raw": raw,
            }
        ]
        return [
            Claim(
                subject=subject,
                predicate="associated-with",
                obj={"entity": obj_ref},
                valid_time=valid_time,
                confidence=confidence,
                method="imported",
                asserted_by="pipeline",
                support=support,
            )
        ]

    @staticmethod
    def _canonical_pair(p1: str, p2: str) -> tuple[str, str]:
        try:
            return (p1, p2) if int(p1) <= int(p2) else (p2, p1)
        except ValueError:
            # Non-numeric ids: fall back to lexicographic, still deterministic.
            return (p1, p2) if p1 <= p2 else (p2, p1)

    def _endpoint_spec(self, year_raw: Any, type_raw: Any) -> tuple[str, int] | None:
        """Parse one relationship endpoint into ``(code, year)`` or ``None``."""
        try:
            year = dates.parse_year(year_raw)
        except dates.BadYear:
            self.counters.bad_year_value += 1
            return None
        if year is None:
            return None
        code = str(type_raw or "IN").strip() or "IN"
        return (code, year)

    def _build_interval(
        self, start: tuple[str, int] | None, end: tuple[str, int] | None
    ) -> dict[str, Any]:
        """Assemble the four-date fuzzy interval, dropping inverted derived bounds.

        Returns a ``valid_time`` dict. If the derived bounds invert, they are
        dropped (only ``original`` survives) and ``valid_time_inverted_dropped_bounds``
        is incremented — the claim becomes undated, never invented.
        """
        original: dict[str, Any] = {}
        se = sl = ee = el = None
        approx = False
        if start is not None:
            s_code, s_year = start
            se, sl, s_approx = dates.interpret_endpoint(s_code, s_year)
            approx = approx or s_approx
            original["start"] = {"type_code": s_code, "year": str(s_year)}
        if end is not None:
            e_code, e_year = end
            ee, el, e_approx = dates.interpret_endpoint(e_code, e_year)
            approx = approx or e_approx
            original["end"] = {"type_code": e_code, "year": str(e_year)}

        if self._inverted(se, sl, ee, el):
            self.counters.valid_time_inverted_dropped_bounds += 1
            return {"original": original}

        return {
            "start_earliest": se,
            "start_latest": sl,
            "end_earliest": ee,
            "end_latest": el,
            "approximate": approx,
            "original": original,
        }

    @staticmethod
    def _inverted(
        se: str | None, sl: str | None, ee: str | None, el: str | None
    ) -> bool:
        """A lower bound strictly after an upper bound it must not exceed."""
        k = dates.date_key
        checks = (
            (se, sl),  # start_earliest <= start_latest
            (ee, el),  # end_earliest   <= end_latest
            (se, el),  # start_earliest <= end_latest (feasibility: start <= end)
        )
        for lower, upper in checks:
            if lower is not None and upper is not None and k(lower) > k(upper):
                return True
        return False
