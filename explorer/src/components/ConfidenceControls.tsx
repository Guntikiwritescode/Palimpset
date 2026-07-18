import { useId } from "react";
import type { NetworkCounts } from "../api/types";
import { BAND_BOUNDARIES, bandFor, bandLabel } from "../lib/confidence";
import type { ControlsState } from "../lib/urlState";

interface Props {
  state: ControlsState;
  onChange: (next: ControlsState) => void;
  counts: NetworkCounts;
  /** Subtle inline loader on the result line while the server re-queries (§13.4). */
  serverLoading?: boolean;
}

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

function windowLabel(s: ControlsState): string {
  if (s.y0 === null) return "";
  if (s.y1 === null || s.y0 === s.y1) return ` in ${s.y0}`;
  return ` in ${s.y0}–${s.y1}`;
}

function thresholdLabel(c: number): string {
  if (c === 0) return "no minimum — all ties shown";
  return `at least ${bandLabel(bandFor(c))}`;
}

export function ConfidenceControls({ state, onChange, counts, serverLoading }: Props) {
  const sliderId = useId();
  const yearId = useId();
  const set = (patch: Partial<ControlsState>) => onChange({ ...state, ...patch });

  return (
    <section className="panel controls" aria-label="Confidence and time controls">
      <div className="slider-wrap">
        <label htmlFor={sliderId}>
          Minimum attestation strength: <b>{state.c.toFixed(2)}</b>{" "}
          <span className="muted">({thresholdLabel(state.c)})</span>
        </label>
        <input
          id={sliderId}
          type="range"
          min={0}
          max={1}
          step={0.05}
          value={state.c}
          aria-valuemin={0}
          aria-valuemax={1}
          aria-valuenow={state.c}
          aria-valuetext={`${state.c.toFixed(2)}, ${thresholdLabel(state.c)}`}
          onChange={(e) => set({ c: Number(e.target.value) })}
          data-testid="confidence-slider"
        />
        <div className="ticks" aria-hidden="true">
          {/* tick marks at the band boundaries so the semantic bands are felt, not decimals */}
          <span>0</span>
          {BAND_BOUNDARIES.map((b) => (
            <span key={b}>{b.toFixed(2)}</span>
          ))}
          <span>1</span>
        </div>
      </div>

      <div className="row">
        <label htmlFor={yearId}>Year window:</label>
        <input
          id={yearId}
          type="number"
          inputMode="numeric"
          placeholder="all time"
          value={state.y0 ?? ""}
          onChange={(e) => {
            const v = e.target.value.trim();
            if (v === "") set({ y0: null, y1: null });
            else {
              const y = Number(v);
              if (Number.isInteger(y)) set({ y0: y, y1: y }); // single year → Jan 1–Dec 31
            }
          }}
          style={{ width: "6rem" }}
          data-testid="year-window"
        />
        <span className="muted small">a single year covers Jan 1–Dec 31</span>
        {state.y0 !== null && (
          <button className="btn" onClick={() => set({ y0: null, y1: null })}>
            Clear window
          </button>
        )}
      </div>

      <div className="row">
        <span id="mode-label" className="muted small">
          Emphasis:
        </span>
        <div className="seg" role="group" aria-labelledby="mode-label">
          <button
            type="button"
            aria-pressed={state.mode === "possibly"}
            onClick={() => set({ mode: "possibly" })}
          >
            possibly active
          </button>
          <button
            type="button"
            aria-pressed={state.mode === "certainly"}
            onClick={() => set({ mode: "certainly" })}
            data-testid="mode-certainly"
          >
            certainly active
          </button>
        </div>
        <span className="muted small">
          “Certainly” highlights the ties the evidence <em>requires</em>; it never empties the graph.
        </span>
      </div>

      <label className="toggle">
        <input
          type="checkbox"
          checked={state.unscored}
          onChange={(e) => set({ unscored: e.target.checked })}
          data-testid="unscored-toggle"
        />
        Show unscored attribute claims <span className="muted small">(claims list only)</span>
      </label>

      <p className="result-line" role="status" aria-live="polite" data-testid="result-line">
        Showing{" "}
        <b data-testid="possibly-count">{counts.possibly}</b>{" "}
        {plural(counts.possibly, "tie not ruled out", "ties not ruled out").replace(/^\d+\s/, "")}
        {windowLabel(state)} ·{" "}
        <b className="req" data-testid="certainly-count">
          {counts.certainly}
        </b>{" "}
        required by the evidence (highlighted) ·{" "}
        <b data-testid="undated-count">{counts.undated}</b>{" "}
        {counts.undated === 1 ? "carries" : "carry"} no dates at all.
        {serverLoading && (
          <span className="inline-loader" aria-live="polite">
            {" "}
            · updating…
          </span>
        )}
      </p>
    </section>
  );
}
