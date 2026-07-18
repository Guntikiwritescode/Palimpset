import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ClaimsList } from "./ClaimsList";
import type { ClaimDetail, PredicateGroup } from "../api/types";

// WP2b: when two sources assert the same predicate about one person, the claims
// render SIDE BY SIDE under a "competing claims" heading — never merged (PP3).
function claim(id: number, predicate: string, value: string | number, source: string): ClaimDetail {
  return {
    id,
    subject: { id: 1, displayName: "Francis Bacon", entityType: "person", description: null, gender: null,
      lifeDates: { bornEarliest: null, bornLatest: null, diedEarliest: null, diedLatest: null },
      degreeScored: 0, degreeUnscored: 0 },
    predicate,
    object: { literal: { kind: "year", value: String(value) } },
    validTime: { startEarliest: `${value}-01-01`, startLatest: `${value}-12-31`,
      endEarliest: `${value}-01-01`, endLatest: `${value}-12-31`, approximate: false, original: null },
    confidence: { effective: null, origin: null, raw: null, calibrated: false, scored: false },
    method: "imported",
    methodDetail: null,
    status: "asserted",
    assertedBy: { slug: source, kind: "pipeline", displayName: source },
    recordedAt: "2026-07-18T00:00:00Z",
    importRunId: "r",
  } as ClaimDetail;
}

function render_(groups: PredicateGroup[]) {
  return render(
    <MemoryRouter>
      <ClaimsList groups={groups} showUnscored={true} />
    </MemoryRouter>,
  );
}

describe("ClaimsList competing claims (PP3)", () => {
  it("renders two same-predicate claims from two sources side by side under a competing heading", () => {
    render_([{ predicate: "born", claims: [claim(1, "born", 1561, "sdfb"), claim(2, "born", 1560, "folger")] }]);
    // The genuine competing life date (1561 vs 1560) — both shown, neither dropped.
    expect(screen.getByText("1561")).toBeInTheDocument();
    expect(screen.getByText("1560")).toBeInTheDocument();
    expect(screen.getByText(/competing claims/i)).toBeInTheDocument();
    expect(document.querySelector(".competing")).not.toBeNull();
    expect(screen.getAllByTestId("claim-card")).toHaveLength(2);
  });

  it("does not label a single-claim predicate as competing", () => {
    render_([{ predicate: "has-gender", claims: [claim(3, "has-gender", "male", "sdfb")] }]);
    expect(screen.queryByText(/competing claims/i)).toBeNull();
  });
});
