import type { Edge } from "../api/types";
import { edgeDashArray, edgeThickness } from "../lib/confidence";

/**
 * The tie-strength glyph used in the ranked tie list. It renders the SAME grammar
 * the canvas uses: thickness tracks confidence, and unscored edges are DASHED
 * (§13.2c). Shape/thickness carry meaning without relying on color (§13.7).
 */
export function EdgeGlyph({ edge }: { edge: Edge }) {
  const dash = edgeDashArray(edge.scored);
  const width = edgeThickness(edge.confidence.effective);
  return (
    <svg
      className="edge-glyph"
      width={40}
      height={12}
      viewBox="0 0 40 12"
      role="img"
      aria-label={edge.scored ? "attested tie" : "unscored tie (dashed)"}
      data-scored={edge.scored ? "true" : "false"}
      data-testid="edge-glyph"
    >
      <line
        x1={2}
        y1={6}
        x2={38}
        y2={6}
        stroke="currentColor"
        strokeWidth={width}
        strokeDasharray={dash ? dash.join(" ") : undefined}
        strokeLinecap="round"
      />
    </svg>
  );
}
