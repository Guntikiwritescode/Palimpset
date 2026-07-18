import type { ReactElement } from "react";
import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { Edge } from "../api/types";

export function renderWithRouter(ui: ReactElement, initialEntries: string[] = ["/"]) {
  return render(<MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter>);
}

export function makeEdge(claimId: number, eff: number | null, opts: Partial<Edge> = {}): Edge {
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
    validTime: {
      startEarliest: "1580-01-01",
      startLatest: null,
      endEarliest: null,
      endLatest: "1601-12-31",
      approximate: false,
      original: {},
    },
    scored,
    certainlyActive: false,
    undated: false,
    status: "asserted",
    ...opts,
  };
}
