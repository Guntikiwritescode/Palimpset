import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { EdgeGlyph } from "./EdgeGlyph";
import { NetworkCanvas } from "./NetworkCanvas";
import { renderWithRouter, makeEdge } from "../test/utils";
import type { EntitySummary } from "../api/types";

// The dashed-unscored-edge grammar is IMPLEMENTED but UNWIRED on the canvas (Q-2/D4).
// This test asserts it renders correctly when given synthetic unscored edges.

describe("dashed-unscored-edge grammar (Q-2)", () => {
  it("renders an unscored edge DASHED and a scored edge solid", () => {
    const { rerender } = render(<EdgeGlyph edge={makeEdge(1, null)} />);
    const dashed = screen.getByTestId("edge-glyph").querySelector("line");
    expect(dashed).toHaveAttribute("stroke-dasharray", "4 3");
    expect(screen.getByTestId("edge-glyph")).toHaveAttribute("data-scored", "false");

    rerender(<EdgeGlyph edge={makeEdge(2, 0.9)} />);
    const solid = screen.getByTestId("edge-glyph").querySelector("line");
    expect(solid).not.toHaveAttribute("stroke-dasharray");
    expect(screen.getByTestId("edge-glyph")).toHaveAttribute("data-scored", "true");
  });

  it("in the tie list, unscored ties sit in a separate section, never ranked among scored", () => {
    const focus: EntitySummary = {
      id: 1,
      displayName: "Focus",
      entityType: "person",
      description: null,
      gender: null,
      lifeDates: { bornEarliest: null, bornLatest: null, diedEarliest: null, diedLatest: null },
      degreeScored: 1,
      degreeUnscored: 1,
    };
    const edges = [makeEdge(10, 0.8), makeEdge(11, null)];
    renderWithRouter(<NetworkCanvas focus={focus} edges={edges} mode="possibly" />);

    // scored tie row present; the unscored tie is in its own class
    expect(screen.getAllByTestId("tie-row")).toHaveLength(1);
    const unscoredRows = screen.getAllByTestId("tie-row-unscored");
    expect(unscoredRows).toHaveLength(1);
    // and its glyph is dashed
    const glyph = unscoredRows[0].querySelector("line");
    expect(glyph).toHaveAttribute("stroke-dasharray", "4 3");
    expect(screen.getByText(/never ranked among scored/i)).toBeInTheDocument();
  });
});
