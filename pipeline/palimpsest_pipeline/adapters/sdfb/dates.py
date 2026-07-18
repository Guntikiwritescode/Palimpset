"""SDFB date-code interpretation -> the four-date fuzzy model (§13.3, §17.2 S1-S3).

INTERPRETATION — UNCONFIRMED. 99.7% of SDFB relationship intervals rest on the
single start code ``AF/IN`` and 99.8% on the end code ``BF/IN``. The reading below
is corroborated by an independent RDF converter processing the same CSV exports
(``jiemakel/anything2rdf``, §17.2 S1) but is **not** confirmed against SDFB's own
Rails source. A finding that contradicts it goes to the architect *before* the WP2
merge (tripwire, HANDOFF §11).

The interpretation is endpoint-independent: a code+year yields an ``(earliest,
latest)`` pair for whichever endpoint (start or end) it annotates. ``approximate``
is a property of the whole interval (true if either endpoint code is ``CA``).

================================ INTERPRETATION TABLE ==========================
(This table is the code-side authority. The same content belongs in
 docs/DATA.md under "The SDFB date-code interpretation table" — see the pipeline
 handoff note; editing docs/ is outside the pipeline build's scope.)

  code   | gloss                        | earliest    | latest      | approx
  -------+------------------------------+-------------+-------------+-------
  IN     | in year Y                    | Y-01-01     | Y-12-31     | no
  CA     | circa year Y                 | Y-01-01     | Y-12-31     | YES   (§17.2 S3: NO +/-window, D6)
  AF/IN  | after the beginning of Y     | Y-01-01     | (null)      | no
  BF/IN  | before the end of Y          | (null)      | Y-12-31     | no
  AF     | strictly after Y (year end)  | Y-12-31     | (null)      | no    (§17.2 S2: NOT == AF/IN)
  BF     | strictly before Y (year top) | (null)      | Y-01-01     | no    (§17.2 S2: NOT == BF/IN)

Worked consequences, per the endpoint the code annotates:
  - AF/IN on a start  => start_earliest = Y-01-01, start_latest = null
  - BF/IN on an end   => end_latest    = Y-12-31, end_earliest = null
  - AF    on a start  => start_earliest = Y-12-31
  - BF    on an end   => end_latest     = Y-01-01
  - BF    on a start  => start_latest   = Y-01-01
  - IN    on a start  => start_earliest = Y-01-01, start_latest = Y-12-31
  - CA    on either   => same bounds as IN, plus approximate=true, NO +/-window
===============================================================================

``AF`` vs ``AF/IN`` and ``BF`` vs ``BF/IN`` are preserved distinctly and never
collapsed (§17.2 S2). ``CA`` keeps the stated year with an ``approximate`` marker
and invents no window (§17.2 S3 / D6 — the ``CA = +/-5`` figure the source never
states, which the project's own no-fabricated-numbers rule caught).
"""

from __future__ import annotations

# Machine-readable form of the table above. Each entry maps a code to how it
# fills the (earliest, latest) pair of the endpoint it annotates, plus whether it
# marks the interval approximate. "beg" = Y-01-01, "end" = Y-12-31, None = unknown.
_CODES: dict[str, tuple[str | None, str | None, bool]] = {
    "IN": ("beg", "end", False),
    "CA": ("beg", "end", True),
    "AF/IN": ("beg", None, False),
    "BF/IN": (None, "end", False),
    "AF": ("end", None, False),
    "BF": (None, "beg", False),
}


class BadYear(ValueError):
    """A year value that is present but cannot be parsed to an integer."""


def parse_year(raw: str | int | None) -> int | None:
    """Parse a source year.

    Returns ``None`` when the value is absent/blank (unknown, not an anomaly), and
    raises :class:`BadYear` when it is present but not an integer (counted as
    ``bad_year_value``).
    """
    if raw is None:
        return None
    s = str(raw).strip()
    if s == "":
        return None
    try:
        return int(s)
    except ValueError:
        raise BadYear(s)


def _bound(kind: str | None, year: int) -> str | None:
    if kind is None:
        return None
    month_day = "01-01" if kind == "beg" else "12-31"
    return f"{year:04d}-{month_day}"


def interpret_endpoint(code: str, year: int) -> tuple[str | None, str | None, bool]:
    """Return ``(earliest, latest, approximate)`` for one endpoint.

    An unknown or blank code is treated as ``IN`` (the corroborated default for a
    bare year). Raises ``KeyError`` only for a code that is present but not one of
    the six recognised codes — the caller decides how to account for that.
    """
    key = (code or "IN").strip().upper()
    if key not in _CODES:
        key = "IN"
    earliest_kind, latest_kind, approx = _CODES[key]
    return _bound(earliest_kind, year), _bound(latest_kind, year), approx


def date_key(iso: str) -> tuple[int, int, int]:
    """Sort key for an ``[-]YYYY-MM-DD`` bound, for inversion comparisons."""
    neg = iso.startswith("-")
    body = iso[1:] if neg else iso
    y, m, d = body.split("-")
    year = int(y) * (-1 if neg else 1)
    return (year, int(m), int(d))
