import { describe, expect, it } from "vitest";
import { computeCounts, filterEdges, filterNetwork, rankedTies } from "./network";
import type { Edge } from "../api/types";

function edge(claimId: number, eff: number | null, opts: Partial<Edge> = {}): Edge {
  const scored = eff !== null;
  return {
    claimId,
    counterpart: {
      id: 9000 + claimId,
      displayName: `Person ${claimId}`,
      entityType: "person",
      description: null,
      gender: null,
      lifeDates: { bornEarliest: null, bornLatest: null, diedEarliest: null, diedLatest: null },
      degreeScored: 0,
      degreeUnscored: 0,
    },
    predicate: "associated-with",
    confidence: { effective: eff, origin: scored ? "source_native" : null, raw: null, calibrated: false, scored },
    validTime: { startEarliest: "1580-01-01", startLatest: null, endEarliest: null, endLatest: "1601-12-31", approximate: false, original: {} },
    scored,
    certainlyActive: false,
    undated: false,
    status: "asserted",
    ...opts,
  };
}

describe("confidence filter (I5 — unscored is absence, not zero)", () => {
  const edges = [edge(1, 0.9), edge(2, 0.5), edge(3, 0.1), edge(4, null)];

  it("passes scored edges at or above the threshold", () => {
    const ids = filterEdges(edges, { minConfidence: 0.5, includeUnscored: false }).map((e) => e.claimId);
    expect(ids).toEqual([1, 2]);
  });

  it("never lets an unscored edge through the threshold", () => {
    const ids = filterEdges(edges, { minConfidence: 0, includeUnscored: false }).map((e) => e.claimId);
    expect(ids).not.toContain(4);
  });

  it("includes unscored ONLY when includeUnscored is set, regardless of threshold", () => {
    const ids = filterEdges(edges, { minConfidence: 0.99, includeUnscored: true }).map((e) => e.claimId);
    expect(ids).toContain(4);
    expect(ids).not.toContain(2);
  });
});

describe("the three counts (D5/A7)", () => {
  it("counts possibly/certainly/undated as distinct classes", () => {
    const edges = [
      edge(1, 0.9, { certainlyActive: true }),
      edge(2, 0.8),
      edge(3, 0.7, { undated: true }),
    ];
    expect(computeCounts(edges)).toEqual({ possibly: 2, certainly: 1, undated: 1 });
  });

  it("certainly is a subset of possibly, never added on top", () => {
    const { counts } = filterNetwork([edge(1, 0.9, { certainlyActive: true })], {
      minConfidence: 0,
      includeUnscored: true,
    });
    expect(counts.certainly).toBeLessThanOrEqual(counts.possibly);
  });
});

describe("ranked ties (PP4 — unscored never ranked among scored)", () => {
  it("keeps unscored ties in a separate class, out of the ranked scored list", () => {
    const { scored, unscored } = rankedTies([edge(1, 0.3), edge(2, 0.9), edge(3, null)]);
    expect(scored.map((e) => e.claimId)).toEqual([2, 1]); // desc by confidence
    expect(unscored.map((e) => e.claimId)).toEqual([3]);
    expect(scored.map((e) => e.claimId)).not.toContain(3);
  });
});
