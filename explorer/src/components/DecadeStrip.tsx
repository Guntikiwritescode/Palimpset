import { useMemo } from "react";
import type { Edge } from "../api/types";
import { decadeCounts } from "../lib/network";

interface Props {
  edges: Edge[];
  activeStart: number | null;
  onPick: (decadeStart: number, decadeEnd: number) => void;
}

/**
 * Decade strip (§13.2): per-decade possibly-active counts across 1500–1700, computed
 * from bounds already loaded. Click sets the window. These are biographical envelopes
 * — when a tie was *possible*, not when it was *attested* (F5).
 */
export function DecadeStrip({ edges, activeStart, onPick }: Props) {
  const buckets = useMemo(() => decadeCounts(edges), [edges]);
  const max = Math.max(1, ...buckets.map((b) => b.count));

  return (
    <section className="panel" aria-label="Ties possibly active by decade">
      <h2>Possibly active by decade</h2>
      <div className="decades" role="group" aria-label="Set the year window by decade">
        {buckets.map((b) => (
          <button
            key={b.decadeStart}
            type="button"
            className={`bar ${activeStart === b.decadeStart ? "active" : ""}`}
            title={`${b.label}: ${b.count} possibly-active ${b.count === 1 ? "tie" : "ties"}`}
            aria-label={`${b.label}: ${b.count} possibly-active ties. Set window to this decade.`}
            aria-pressed={activeStart === b.decadeStart}
            onClick={() => onPick(b.decadeStart, b.decadeStart + 9)}
          >
            <span
              className="fill"
              style={{ height: `${(b.count / max) * 100}%` }}
              aria-hidden="true"
            />
          </button>
        ))}
      </div>
      <div className="decade-axis" aria-hidden="true">
        <span>1500</span>
        <span>1600</span>
        <span>1690s</span>
      </div>
      <p className="muted small">Bars count ties whose evidence does not rule them out during the decade.</p>
    </section>
  );
}
