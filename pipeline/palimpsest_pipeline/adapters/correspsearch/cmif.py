"""CMIF/TEI → per-letter records (pure transform, no I/O).

CMIF (Correspondence Metadata Interchange Format) is the TEI-based format the
correspSearch v1.1 API returns (see docs/probe-correspsearch.md). Each
``<correspDesc>`` is one letter: a ``sent`` and a ``received`` ``correspAction``,
persons identified by GND URIs, an optional place (GeoNames), and a date.

This parser turns the XML into plain dicts the adapter maps to claims — it never
fetches anything (the harvester does that) and never invents a field.
"""
from __future__ import annotations

import xml.etree.ElementTree as ET
from typing import Any

TEI = "{http://www.tei-c.org/ns/1.0}"


def _gnd(ref: str | None) -> str | None:
    """Extract the bare GND id from a ``http://d-nb.info/gnd/<id>`` URI."""
    if ref and "gnd/" in ref:
        return ref.rsplit("gnd/", 1)[1].strip() or None
    return None


def _action(ca: ET.Element) -> dict[str, Any]:
    info: dict[str, Any] = {}
    pers = ca.find(TEI + "persName")
    if pers is not None:
        info["gnd"] = _gnd(pers.get("ref"))
        info["name"] = (pers.text or "").strip() or None
    place = ca.find(TEI + "placeName")
    if place is not None:
        info["place_ref"] = place.get("ref")
        info["place_name"] = (place.text or "").strip() or None
    date = ca.find(TEI + "date")
    if date is not None:
        for k in ("when", "notBefore", "notAfter", "from", "to"):
            v = date.get(k)
            if v:
                info[k] = v
    return info


def parse_cmif(xml_text: str) -> list[dict[str, Any]]:
    """Parse a CMIF/TEI document into one dict per ``correspDesc`` (letter)."""
    root = ET.fromstring(xml_text)
    letters: list[dict[str, Any]] = []
    for cd in root.iter(TEI + "correspDesc"):
        letter: dict[str, Any] = {
            "record_kind": "letter",
            "key": cd.get("key"),
            "ref": cd.get("ref"),
            "edition": (cd.get("source") or "").lstrip("#") or None,
        }
        for ca in cd.findall(TEI + "correspAction"):
            role = ca.get("type")  # "sent" | "received"
            if role in ("sent", "received"):
                letter[role] = _action(ca)
        letters.append(letter)
    return letters
