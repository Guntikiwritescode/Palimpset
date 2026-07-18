import type { Confidence } from "../api/types";
import {
  bandFor,
  bandLabel,
  confidenceChipText,
  formatConfidenceValue,
  originLabel,
  type Band,
} from "../lib/confidence";

// Strength shown by SHAPE as well as number+label — never color alone (§13.7).
const SHAPE: Record<Band, string> = {
  very_strong: "▰▰▰▰▰",
  strong: "▰▰▰▰▱",
  moderate: "▰▰▰▱▱",
  weak: "▰▰▱▱▱",
  very_weak: "▰▱▱▱▱",
  unscored: "▱▱▱▱▱",
};

/** Confidence chip = number + band label + origin (PP2). Never a bare number. */
export function ConfidenceChip({ confidence }: { confidence: Confidence }) {
  const band = confidence.scored ? bandFor(confidence.effective) : "unscored";
  const value = formatConfidenceValue(confidence.effective);
  const origin = originLabel(confidence.origin, confidence.calibrated, confidence.raw);
  const full = confidenceChipText(confidence);
  return (
    <span className={`chip ${band === "unscored" ? "unscored" : ""}`} title={full} aria-label={full}>
      <span className="shape" aria-hidden="true">
        {SHAPE[band]}
      </span>
      {value !== null && (
        <span className="num" aria-hidden="true">
          {value}
        </span>
      )}
      <span aria-hidden="true">{bandLabel(band)}</span>
      <span className="muted small" aria-hidden="true">
        · {origin}
      </span>
    </span>
  );
}
