import { describe, expect, it } from "vitest";
import { api } from "./client";
import { filterEdges } from "../lib/network";
import { FIXTURE_IDS } from "../mocks/data";

// §13.4 / §8: the client-side confidence filter over the held minConfidence=0 set
// must equal a server re-query at the same threshold. The optimization that makes
// the slider instant must never change the answer.

async function serverIds(minConfidence: number): Promise<number[]> {
  const res = await api.getNetwork(FIXTURE_IDS.baconPhilosopher, {
    minConfidence,
    includeUnscored: true,
    limit: 500,
  });
  return res.data.edges.map((e) => e.claimId).sort((a, b) => a - b);
}

describe("client/server-mode parity (dual-mode slider)", () => {
  it("client filter equals server re-query across the band boundaries", async () => {
    const base = await api.getNetwork(FIXTURE_IDS.baconPhilosopher, {
      minConfidence: 0,
      includeUnscored: true,
      limit: 500,
    });

    for (const c of [0, 0.2, 0.4, 0.6, 0.7, 0.9]) {
      const client = filterEdges(base.data.edges, { minConfidence: c, includeUnscored: true })
        .map((e) => e.claimId)
        .sort((a, b) => a - b);
      const server = await serverIds(c);
      expect(client, `threshold ${c}`).toEqual(server);
    }
  });

  it("the network response carries the three counts in meta (Q-3)", async () => {
    const res = await api.getNetwork(FIXTURE_IDS.baconPhilosopher, {
      minConfidence: 0,
      includeUnscored: true,
      limit: 500,
    });
    expect(res.meta.counts).toBeDefined();
    expect(res.meta.counts).toHaveProperty("possibly");
    expect(res.meta.counts).toHaveProperty("certainly");
    expect(res.meta.counts).toHaveProperty("undated");
  });

  it("windowing to 1600 leaves exactly 2 ties certainly-active (FIX-BACON flavour)", async () => {
    const res = await api.getNetwork(FIXTURE_IDS.baconPhilosopher, {
      minConfidence: 0,
      includeUnscored: true,
      limit: 500,
      windowStart: "1600-01-01",
      windowEnd: "1600-12-31",
    });
    expect(res.meta.counts?.certainly).toBe(2);
    // ...within a larger possibly set that is not emptied by the certainly highlight (D2).
    expect(res.meta.counts!.possibly).toBeGreaterThan(res.meta.counts!.certainly);
  });
});
