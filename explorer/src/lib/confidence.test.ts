import { describe, expect, it } from "vitest";
import {
  bandFor,
  confidenceChipText,
  edgeDashArray,
  formatConfidenceValue,
} from "./confidence";
import type { Confidence } from "../api/types";

const src = (effective: number | null, scored = effective !== null): Confidence => ({
  effective,
  origin: scored ? "source_native" : null,
  raw: scored && effective !== null ? Math.round(effective * 100) : null,
  calibrated: false,
  scored,
});

describe("confidence-band grammar (§13.3)", () => {
  it("maps values to bands at the exact boundaries", () => {
    expect(bandFor(0.9)).toBe("very_strong");
    expect(bandFor(0.89)).toBe("strong");
    expect(bandFor(0.7)).toBe("strong");
    expect(bandFor(0.69)).toBe("moderate");
    expect(bandFor(0.4)).toBe("moderate");
    expect(bandFor(0.39)).toBe("weak");
    expect(bandFor(0.2)).toBe("weak");
    expect(bandFor(0.19)).toBe("very_weak");
    expect(bandFor(null)).toBe("unscored");
  });
});

describe("confidence-chip grammar (PP2 — never a number alone)", () => {
  it("includes number + band label + origin for scored confidence", () => {
    const text = confidenceChipText(src(0.97));
    expect(text).toContain("0.97");
    expect(text).toContain("very strong");
    expect(text).toContain("source-native (uncalibrated)");
  });

  it("has NO number for unscored, and says 'unscored' + origin", () => {
    const text = confidenceChipText(src(null, false));
    expect(text).toContain("unscored");
    expect(formatConfidenceValue(null)).toBeNull();
    expect(text).not.toMatch(/\d\.\d/);
  });
});

describe("dashed-edge grammar helper (§13.2c)", () => {
  it("returns a dash array for unscored edges and null for scored", () => {
    expect(edgeDashArray(false)).toEqual([4, 3]);
    expect(edgeDashArray(true)).toBeNull();
  });
});
