import { describe, expect, it } from "vitest";
import {
  DEFAULT_CONTROLS,
  controlsToParams,
  controlsToSearchString,
  parseControls,
  type ControlsState,
} from "./urlState";

describe("URL state — the four controls live in the query (PP5)", () => {
  it("defaults the slider to 0 (§17.F2)", () => {
    expect(parseControls(new URLSearchParams("")).c).toBe(0);
    expect(DEFAULT_CONTROLS.c).toBe(0);
  });

  it("defaults mode to possibly and unscored ON (claims list shows the messy record)", () => {
    const s = parseControls(new URLSearchParams(""));
    expect(s.mode).toBe("possibly");
    expect(s.unscored).toBe(true);
  });

  it("omits defaults from the URL for a clean shareable link", () => {
    expect(controlsToSearchString(DEFAULT_CONTROLS)).toBe("");
  });

  it("round-trips a non-default view exactly", () => {
    const state: ControlsState = { c: 0.6, y0: 1600, y1: 1609, mode: "certainly", unscored: false };
    const params = controlsToParams(state);
    const back = parseControls(params);
    expect(back).toEqual(state);
  });

  it("expands the certainly + unscored-off + window into explicit params", () => {
    const s: ControlsState = { c: 0.9, y0: 1600, y1: 1600, mode: "certainly", unscored: false };
    const sp = controlsToParams(s);
    expect(sp.get("c")).toBe("0.9");
    expect(sp.get("y0")).toBe("1600");
    expect(sp.get("mode")).toBe("certainly");
    expect(sp.get("unscored")).toBe("0");
  });

  it("clamps confidence to the 0.05 grid within [0,1]", () => {
    expect(parseControls(new URLSearchParams("c=0.63")).c).toBeCloseTo(0.65, 2);
    expect(parseControls(new URLSearchParams("c=5")).c).toBe(1);
    expect(parseControls(new URLSearchParams("c=-1")).c).toBe(0);
  });
});
