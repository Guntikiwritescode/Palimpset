import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ConfidenceControls } from "./ConfidenceControls";
import { DEFAULT_CONTROLS } from "../lib/urlState";

const counts = { possibly: 9, certainly: 2, undated: 1 };

describe("ConfidenceControls semantics", () => {
  it("states possibly / certainly / undated simultaneously in the result line (D5/A7)", () => {
    render(<ConfidenceControls state={DEFAULT_CONTROLS} onChange={() => {}} counts={counts} />);
    expect(screen.getByTestId("possibly-count")).toHaveTextContent("9");
    expect(screen.getByTestId("certainly-count")).toHaveTextContent("2");
    expect(screen.getByTestId("undated-count")).toHaveTextContent("1");
    expect(screen.getByTestId("result-line")).toHaveTextContent(/required by the evidence/i);
  });

  it("defaults the slider to 0 (§17.F2)", () => {
    render(<ConfidenceControls state={DEFAULT_CONTROLS} onChange={() => {}} counts={counts} />);
    const slider = screen.getByTestId("confidence-slider") as HTMLInputElement;
    expect(slider.value).toBe("0");
    expect(slider).toHaveAttribute("step", "0.05");
  });

  it("moving the slider only calls onChange with a new threshold — it does not empty the counts", () => {
    const onChange = vi.fn();
    render(<ConfidenceControls state={DEFAULT_CONTROLS} onChange={onChange} counts={counts} />);
    fireEvent.change(screen.getByTestId("confidence-slider"), { target: { value: "0.7" } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ c: 0.7 }));
    // Counts are a prop; the control never filters them to zero on its own (D2).
    expect(screen.getByTestId("possibly-count")).toHaveTextContent("9");
  });

  it("certainly is a HIGHLIGHT, not a filter — selecting it never empties the graph (D2)", () => {
    const onChange = vi.fn();
    render(<ConfidenceControls state={DEFAULT_CONTROLS} onChange={onChange} counts={counts} />);
    fireEvent.click(screen.getByTestId("mode-certainly"));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ mode: "certainly" }));
    // The possibly count stays present and the certainly count remains in the line.
    expect(screen.getByTestId("possibly-count")).toHaveTextContent("9");
    expect(screen.getByTestId("certainly-count")).toHaveTextContent("2");
  });

  it("scopes the unscored toggle to the claims list", () => {
    render(<ConfidenceControls state={DEFAULT_CONTROLS} onChange={() => {}} counts={counts} />);
    expect(screen.getByTestId("unscored-toggle")).toBeInTheDocument();
    expect(screen.getByText(/claims list only/i)).toBeInTheDocument();
  });

  it("shows the inline loader (not a blank-out) while the server re-queries", () => {
    render(<ConfidenceControls state={DEFAULT_CONTROLS} onChange={() => {}} counts={counts} serverLoading />);
    expect(screen.getByTestId("result-line")).toHaveTextContent(/updating/i);
  });
});
