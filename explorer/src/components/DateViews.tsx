import type { FuzzyInterval, LifeDates } from "../api/types";
import { formatLifeDates, formatValidTime, nativeEncoding } from "../lib/fuzzyDate";

/**
 * Life dates in the identity header. Never bare fact (F3/D6): they carry an
 * UNSCORED chip and a DERIVED-date marker, like any other unscored assertion.
 */
export function LifeDates({ lifeDates }: { lifeDates: LifeDates }) {
  const text = formatLifeDates(lifeDates);
  return (
    <span className="lifedates">
      <span>{text}</span>{" "}
      <span className="chip unscored" title="Life dates are unscored claims — the source gives them no confidence score.">
        <span aria-hidden="true">▱▱▱▱▱</span> unscored
      </span>{" "}
      <span
        className="marker"
        title="Bounds are derived from the source's date codes, not stated directly (§13.3 method note)."
      >
        derived
      </span>
    </span>
  );
}

/** A claim/edge valid-time interval; hover reveals the source's native encoding verbatim. */
export function ValidTime({ interval }: { interval: FuzzyInterval }) {
  const text = formatValidTime(interval);
  const native = nativeEncoding(interval);
  return (
    <span className="marker" title={`Source encoding: ${native}`}>
      {text}
      {interval.approximate ? " (approximate)" : ""}
    </span>
  );
}
