// Fuzzy-date grammar (§13.3, D6). Bounds are DERIVED, not stated. Circa carries an
// `approximate` marker and NO ±window (D6). Never a blank, never an invented range.

import type { FuzzyInterval, LifeDates } from "../api/types";

export interface FuzzyPoint {
  earliest: string | null;
  latest: string | null;
  approximate?: boolean;
}

/** Parse a signed year from "YYYY-MM-DD" (early-modern; all positive here). */
export function yearOf(iso: string | null): number | null {
  if (!iso) return null;
  const m = /^(-?\d{1,4})-/.exec(iso);
  if (!m) {
    const m2 = /^(-?\d{1,4})$/.exec(iso);
    return m2 ? Number(m2[1]) : null;
  }
  return Number(m[1]);
}

/**
 * Render a single fuzzy date point per the grammar:
 *   both bounds, same year  → "1561"        (year-precise; "c. 1561" if approximate)
 *   both bounds, diff years → "1561–1563"   (a genuine window in the record)
 *   earliest only           → "after 1561"
 *   latest only             → "before 1626"
 *   neither                 → "date unknown"
 */
export function formatFuzzyDate(p: FuzzyPoint): string {
  const e = yearOf(p.earliest);
  const l = yearOf(p.latest);
  if (e === null && l === null) return "date unknown";
  if (e !== null && l !== null) {
    if (e === l) return p.approximate ? `c. ${e}` : `${e}`;
    return `${e}–${l}`;
  }
  if (e !== null) return p.approximate ? `after c. ${e}` : `after ${e}`;
  return p.approximate ? `before c. ${l}` : `before ${l}`;
}

/** A claim's valid-time interval → a range string like "1561–1626". */
export function formatValidTime(fi: FuzzyInterval): string {
  const start = formatFuzzyDate({
    earliest: fi.startEarliest,
    latest: fi.startLatest,
    approximate: fi.approximate,
  });
  const end = formatFuzzyDate({
    earliest: fi.endEarliest,
    latest: fi.endLatest,
    approximate: fi.approximate,
  });
  const startUnknown = start === "date unknown";
  const endUnknown = end === "date unknown";
  if (startUnknown && endUnknown) return "date unknown";
  if (endUnknown) return start;
  if (startUnknown) return end;
  return `${start}–${end}`;
}

/**
 * Life-date range for the identity header, e.g. "1561–1626". The header wraps this
 * in an UNSCORED chip + a DERIVED marker — life dates are never bare fact (F3/D6).
 */
export function formatLifeDates(ld: LifeDates): string {
  const born = formatFuzzyDate({ earliest: ld.bornEarliest, latest: ld.bornLatest });
  const died = formatFuzzyDate({ earliest: ld.diedEarliest, latest: ld.diedLatest });
  const bornUnknown = born === "date unknown";
  const diedUnknown = died === "date unknown";
  if (bornUnknown && diedUnknown) return "dates unknown";
  if (diedUnknown) return `b. ${born}`;
  if (bornUnknown) return `d. ${died}`;
  return `${born}–${died}`;
}

/** The source's native encoding, shown verbatim on hover (never destroyed, §13.3). */
export function nativeEncoding(fi: FuzzyInterval): string {
  return JSON.stringify(fi.original ?? {}, null, 0);
}
