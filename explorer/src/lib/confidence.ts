// Confidence grammar (§13.3, PP2). A chip is ALWAYS number + band label + origin;
// a bare number is a defect. Unscored is absence, not zero (I5) — never ranked,
// never passes a threshold.

import type { Confidence, ConfidenceOrigin } from "../api/types";

export type Band =
  | "very_strong"
  | "strong"
  | "moderate"
  | "weak"
  | "very_weak"
  | "unscored";

export const BAND_BOUNDARIES = [0.2, 0.4, 0.7, 0.9] as const;

export interface BandInfo {
  band: Band;
  label: string;
}

const BAND_LABELS: Record<Band, string> = {
  very_strong: "very strong",
  strong: "strong",
  moderate: "moderate",
  weak: "weak",
  very_weak: "very weak",
  unscored: "unscored",
};

/** Bands: ≥.90 very strong · .70–.89 strong · .40–.69 moderate · .20–.39 weak · <.20 very weak. */
export function bandFor(effective: number | null): Band {
  if (effective === null) return "unscored";
  if (effective >= 0.9) return "very_strong";
  if (effective >= 0.7) return "strong";
  if (effective >= 0.4) return "moderate";
  if (effective >= 0.2) return "weak";
  return "very_weak";
}

export function bandLabel(band: Band): string {
  return BAND_LABELS[band];
}

/** Origin text: always present on a chip. */
export function originLabel(origin: ConfidenceOrigin, calibrated: boolean, raw?: unknown): string {
  switch (origin) {
    case "source_native":
      return "source-native (uncalibrated)";
    case "calibration":
      return calibrated && raw != null ? `calibrated (${String(raw)})` : "calibrated";
    case "manual":
      return "manual override";
    case "inferred":
      return "inferred (algorithm)";
    default:
      return "origin unrecorded";
  }
}

/** Number shown to 2 dp for scored confidences; unscored has no number. */
export function formatConfidenceValue(effective: number | null): string | null {
  if (effective === null) return null;
  return effective.toFixed(2);
}

/**
 * The full chip text as a single accessible string, e.g.
 * "0.97 · very strong · source-native (uncalibrated)". Never a number alone (PP2).
 */
export function confidenceChipText(c: Confidence): string {
  const band = c.scored ? bandFor(c.effective) : "unscored";
  const label = bandLabel(band);
  if (band === "unscored" || c.effective === null) {
    return `unscored · ${originLabel(c.origin, c.calibrated, c.raw)}`;
  }
  return `${formatConfidenceValue(c.effective)} · ${label} · ${originLabel(c.origin, c.calibrated, c.raw)}`;
}

/** Monotonic canvas encodings — opacity and thickness track confidence, never color alone. */
export function edgeOpacity(effective: number | null): number {
  if (effective === null) return 0.35;
  return 0.25 + 0.65 * Math.min(1, Math.max(0, effective));
}

export function edgeThickness(effective: number | null): number {
  if (effective === null) return 1;
  return 0.75 + 3.25 * Math.min(1, Math.max(0, effective));
}

/**
 * Dashed-edge grammar (§13.2c): unscored edges render DASHED, scored edges solid.
 * Implemented but left UNWIRED on the canvas (Q-2/D4) — no control toggles it, and
 * this corpus has no unscored relationship edges; it earns a control at WP2b/WP9.
 */
export function edgeDashArray(scored: boolean): number[] | null {
  return scored ? null : [4, 3];
}

/** The single uncalibrated-warning sentence (§13.3). Shown wherever a source-native number appears at size. */
export const UNCALIBRATED_WARNING =
  "This is the source's own score, normalized. It has not been calibrated against ground truth, so treat it as a ranking signal, not a probability.";
