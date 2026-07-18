// Server-side network semantics for the mock (BUILD-CONTRACT §4 network row, §13.4).
// Applies the window predicate (possibly-active / certainly-active), computes
// per-edge certainlyActive for the window, then the confidence filter and the three
// meta counts. The explorer holds the minConfidence=0 result and filters confidence
// CLIENT-side; this function is the server it must agree with (§8 parity test).

import type { Edge, NetworkCounts } from "../api/types";
import { computeCounts, filterEdges } from "../lib/network";
import { networkOf } from "./data";

export interface ResolveParams {
  minConfidence: number;
  windowStart: number | null;
  windowEnd: number | null;
  includeUnscored: boolean;
  limit: number;
}

function year(iso: string | null): number | null {
  if (!iso) return null;
  const m = /^(-?\d{1,4})/.exec(iso);
  return m ? Number(m[1]) : null;
}

function possiblyActive(edge: Edge, w0: number, w1: number): boolean {
  if (edge.undated) return true; // possibly_active is trivially true for undated (A7)
  const sE = year(edge.validTime.startEarliest);
  const eL = year(edge.validTime.endLatest);
  const startsByWindowEnd = sE === null || sE <= w1;
  const endsAfterWindowStart = eL === null || eL >= w0;
  return startsByWindowEnd && endsAfterWindowStart;
}

function certainlyActive(edge: Edge, w0: number, w1: number): boolean {
  if (edge.undated) return false;
  const sL = year(edge.validTime.startLatest);
  const eE = year(edge.validTime.endEarliest);
  // Required through the window: certainly started by w0 AND certainly not ended before w1.
  const startedForSure = sL !== null && sL <= w0;
  const notEndedForSure = eE !== null && eE >= w1;
  return startedForSure && notEndedForSure;
}

export interface ResolvedNetwork {
  edges: Edge[];
  counts: NetworkCounts;
  truncated: boolean;
}

export function resolveNetwork(focusId: number, p: ResolveParams): ResolvedNetwork {
  const all = networkOf(focusId);
  const windowed = all
    .map((e): Edge => {
      if (p.windowStart === null || p.windowEnd === null) {
        // No window ⇒ nothing is "required across all of history": certainly = false.
        return { ...e, certainlyActive: false };
      }
      return { ...e, certainlyActive: certainlyActive(e, p.windowStart, p.windowEnd) };
    })
    .filter((e) => {
      if (p.windowStart === null || p.windowEnd === null) return true;
      return possiblyActive(e, p.windowStart, p.windowEnd);
    });

  const passed = filterEdges(windowed, {
    minConfidence: p.minConfidence,
    includeUnscored: p.includeUnscored,
  }).sort((a, b) => (b.confidence.effective ?? 0) - (a.confidence.effective ?? 0));

  const truncated = passed.length > p.limit;
  const edges = truncated ? passed.slice(0, p.limit) : passed;
  return { edges, counts: computeCounts(edges), truncated };
}
