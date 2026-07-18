// Network filtering + the three counts (§13.4, D2/D5, Q-3). The SAME confidence
// filter runs client-side (instant slider) and server-side (the mock handler), so
// a client filter at threshold c equals a server re-query at c — the optimization
// must never change the answer (§8 parity test).

import type { Edge, NetworkCounts } from "../api/types";

export interface ConfidenceFilter {
  /** minConfidence 0..1. Never lets unscored through (I5). */
  minConfidence: number;
  /** Whether unscored edges are returned at all. minConfidence has no effect on them. */
  includeUnscored: boolean;
}

/**
 * Confidence filter shared by client and server. Scored edges pass when their
 * effective confidence ≥ minConfidence; unscored edges pass only when
 * includeUnscored is set (never via the threshold — absence is not zero, I5).
 */
export function filterEdges(edges: Edge[], f: ConfidenceFilter): Edge[] {
  return edges.filter((e) => {
    if (e.scored && e.confidence.effective !== null) {
      return e.confidence.effective >= f.minConfidence;
    }
    // unscored
    return f.includeUnscored;
  });
}

/**
 * The three simultaneous counts (D5/A7) over a window-scoped edge set:
 *  - possibly  : dated ties not ruled out in the window
 *  - certainly : the subset the evidence REQUIRES through the window (highlight)
 *  - undated   : ties carrying no dates at all (possibly_active is trivially true, A7)
 */
export function computeCounts(edges: Edge[]): NetworkCounts {
  let possibly = 0;
  let certainly = 0;
  let undated = 0;
  for (const e of edges) {
    if (e.undated) {
      undated += 1;
    } else {
      possibly += 1;
      if (e.certainlyActive) certainly += 1;
    }
  }
  return { possibly, certainly, undated };
}

export interface FilteredNetwork {
  /** All edges to draw (possibly ∪ undated); certainly-active are highlighted, not filtered. */
  visible: Edge[];
  counts: NetworkCounts;
}

/** Apply the confidence filter and recompute the three counts locally. */
export function filterNetwork(edges: Edge[], f: ConfidenceFilter): FilteredNetwork {
  const visible = filterEdges(edges, f);
  return { visible, counts: computeCounts(visible) };
}

export interface DecadeBucket {
  decadeStart: number;
  label: string;
  count: number;
}

/**
 * Per-decade possibly-active counts across [from, to] (§13.2 decade strip). Computed
 * from bounds already loaded. Undated edges have no year to place and are excluded.
 */
export function decadeCounts(
  edges: Edge[],
  from = 1500,
  to = 1700,
  step = 10,
): DecadeBucket[] {
  const buckets: DecadeBucket[] = [];
  for (let d = from; d < to; d += step) {
    const decEnd = d + step - 1;
    let count = 0;
    for (const e of edges) {
      if (e.undated) continue;
      const s = yearFrom(e.validTime.startEarliest);
      const en = yearFrom(e.validTime.endLatest);
      const startOk = s === null || s <= decEnd;
      const endOk = en === null || en >= d;
      if (startOk && endOk) count += 1;
    }
    buckets.push({ decadeStart: d, label: `${d}s`, count });
  }
  return buckets;
}

function yearFrom(iso: string | null): number | null {
  if (!iso) return null;
  const m = /^(-?\d{1,4})/.exec(iso);
  return m ? Number(m[1]) : null;
}

export interface RankedTies {
  /** Scored ties in confidence order (desc) — the accessible mirror of the canvas. */
  scored: Edge[];
  /** Unscored ties as a SEPARATE class, never ranked among scored (PP4/D4). */
  unscored: Edge[];
}

/** Ordered tie list for the screen-reader / narrow-viewport fallback (§13.7). */
export function rankedTies(edges: Edge[]): RankedTies {
  const scored = edges
    .filter((e) => e.scored && e.confidence.effective !== null)
    .sort((a, b) => (b.confidence.effective ?? 0) - (a.confidence.effective ?? 0));
  const unscored = edges.filter((e) => !e.scored || e.confidence.effective === null);
  return { scored, unscored };
}

/** Single-year expands to Jan 1–Dec 31 (§13.2). */
export function yearWindow(year: number): { windowStart: string; windowEnd: string } {
  const y = String(year).padStart(4, "0");
  return { windowStart: `${y}-01-01`, windowEnd: `${y}-12-31` };
}
