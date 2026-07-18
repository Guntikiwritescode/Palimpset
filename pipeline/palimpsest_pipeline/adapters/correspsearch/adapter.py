"""correspSearch adapter — CMIF/TEI letters → interchange claims (WP2b).

Reuses WP2's machinery wholesale (the adapter-interface proof, §20 A1). Each
letter (``correspDesc``) becomes:

  - a **document** entity (authority ``correspsearch``, id = the letter ``key``);
  - person entities for the sender and receiver (authority ``gnd``);
  - a **``corresponded-with``** claim, directed sender→receiver, dated from the
    letter's ``date`` (four-date model), and **unscored** — a letter attests the
    tie, but correspSearch provides no confidence score and we invent none (I5).
    correspSearch is therefore the project's first source of genuinely unscored
    *relationship* claims (D4/Q-2).
  - ``has-name`` claims (unscored) for each named person and the document.

Normative rules (mirroring the SDFB adapter's discipline):
  - persons are identified by GND; a ``correspAction`` endpoint with no GND is
    **skipped and counted** — the corresponded-with claim for that letter is not
    emitted (no fabricated entity, I6).
  - a letter with no parseable date yields an **undated** claim (counted), never
    an invented window.
  - support carries the verbatim letter record (CMIF-derived) for lineage; source
    slug ``correspsearch`` (CC-BY 4.0, license_confirmed=true).
"""
from __future__ import annotations

import hashlib
import json
from collections import Counter
from typing import Any

from ..base import (
    Adapter,
    Authority,
    Claim,
    EntityRecord,
    RelationType,
    entity_ref,
    literal,
    unscored,
)

GND = "gnd"
CS = "correspsearch"


def _content_hash(raw: dict[str, Any]) -> str:
    return hashlib.sha256(
        json.dumps(raw, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()[:16]


def _day(value: str, end: bool) -> str | None:
    """Normalize a partial date to YYYY-MM-DD; widen partials to a bound.

    YYYY        -> YYYY-01-01 (start) / YYYY-12-31 (end)
    YYYY-MM     -> YYYY-MM-01 (start) / YYYY-MM-28 (end; 28 is the safe month floor)
    YYYY-MM-DD  -> as given
    Anything else -> None (unparseable).
    """
    if not value:
        return None
    v = value.strip()
    parts = v.split("-")
    try:
        if len(parts) == 1 and len(parts[0]) == 4:
            return f"{int(parts[0]):04d}-{'12-31' if end else '01-01'}"
        if len(parts) == 2:
            y, m = int(parts[0]), int(parts[1])
            return f"{y:04d}-{m:02d}-{'28' if end else '01'}"
        if len(parts) == 3:
            y, m, d = int(parts[0]), int(parts[1]), int(parts[2])
            return f"{y:04d}-{m:02d}-{d:02d}"
    except ValueError:
        return None
    return None


class CorrespSearchAdapter(Adapter):
    name = "correspsearch"
    version = "1.1.0"

    def __init__(self, source_slug: str = "correspsearch") -> None:
        self.source_slug = source_slug
        self.counters: Counter[str] = Counter()

    # ---- registrations -----------------------------------------------------
    def register_authorities(self) -> list[Authority]:
        return [
            Authority(GND, "Gemeinsame Normdatei (GND)", identity_bearing=True),
            Authority(CS, "correspSearch letter id", identity_bearing=True),
        ]

    def register_types(self) -> list[RelationType]:
        return [RelationType("corresponded-with", "corresponded with", range="entity", symmetric=False)]

    def normalize_names(self, raw_name: str) -> str:
        return " ".join((raw_name or "").split())

    def map_uncertainty(self, native: dict[str, Any]) -> dict[str, Any] | None:
        """Native correspAction date → the four-date fuzzy interval (a letter is an instant)."""
        if not native:
            return None
        original = {k: native[k] for k in ("when", "notBefore", "notAfter", "from", "to") if k in native}
        when = native.get("when")
        if when:
            se, sl = _day(when, end=False), _day(when, end=True)
            if se is None:
                self.counters["letter_bad_date"] += 1
                return None
            return {"start_earliest": se, "start_latest": sl,
                    "end_earliest": se, "end_latest": sl,
                    "approximate": False, "original": original}
        nb = native.get("notBefore") or native.get("from")
        na = native.get("notAfter") or native.get("to")
        if nb or na:
            se = _day(nb, end=False) if nb else None
            el = _day(na, end=True) if na else None
            return {"start_earliest": se, "start_latest": el if el else se,
                    "end_earliest": se if se else el, "end_latest": el,
                    "approximate": False, "original": original}
        return None  # undated

    # ---- entities ----------------------------------------------------------
    def map_to_entities(self, source_record: dict[str, Any]) -> list[EntityRecord]:
        out: list[EntityRecord] = []
        for role in ("sent", "received"):
            gnd = (source_record.get(role) or {}).get("gnd")
            if gnd:
                out.append(EntityRecord(entity_ref(GND, gnd), "person", [entity_ref(GND, gnd)]))
        key = source_record.get("key")
        if key:
            out.append(EntityRecord(entity_ref(CS, key), "document", [entity_ref(CS, key)]))
        return out

    # ---- claims ------------------------------------------------------------
    def map_to_claims(self, source_record: dict[str, Any]) -> list[Claim]:
        letter = source_record
        support = [{
            "source": self.source_slug,
            "record_kind": "letter",
            "external_id": str(letter.get("key") or letter.get("ref") or "unknown"),
            "content_hash": _content_hash(letter),
            "raw": letter,
        }]
        claims: list[Claim] = []

        sent = letter.get("sent") or {}
        recv = letter.get("received") or {}
        s_gnd, r_gnd = sent.get("gnd"), recv.get("gnd")

        # Name each identified person (unscored) so search and the header resolve.
        for who in (sent, recv):
            gnd = who.get("gnd")
            name = self.normalize_names(who.get("name") or "")
            if gnd and name:
                claims.append(Claim(
                    subject=entity_ref(GND, gnd), predicate="has-name",
                    obj={"literal": literal("string", name)}, confidence=unscored(),
                    method="imported", asserted_by="pipeline", support=support))

        # Name the document, so a letter is a navigable node.
        key = letter.get("key")
        if key:
            label = self._doc_label(sent, recv)
            claims.append(Claim(
                subject=entity_ref(CS, key), predicate="has-name",
                obj={"literal": literal("string", label)}, confidence=unscored(),
                method="imported", asserted_by="pipeline", support=support))

        # The corresponded-with tie — only when both endpoints resolve (I6).
        if not s_gnd:
            self.counters["letter_missing_sender_gnd"] += 1
        if not r_gnd:
            self.counters["letter_missing_receiver_gnd"] += 1
        if s_gnd and r_gnd:
            valid = self.map_uncertainty(sent) or self.map_uncertainty(recv)
            if valid is None:
                self.counters["letter_undated"] += 1
            claims.append(Claim(
                subject=entity_ref(GND, s_gnd), predicate="corresponded-with",
                obj={"entity": entity_ref(GND, r_gnd)}, valid_time=valid,
                confidence=unscored(), method="imported", asserted_by="pipeline",
                method_detail={"direction": "sent", "letter": key}, support=support))
        return claims

    def _doc_label(self, sent: dict[str, Any], recv: dict[str, Any]) -> str:
        s = self.normalize_names(sent.get("name") or "?")
        r = self.normalize_names(recv.get("name") or "?")
        when = sent.get("when") or sent.get("notBefore") or recv.get("when") or ""
        return f"Letter: {s} → {r}" + (f" ({when})" if when else "")
