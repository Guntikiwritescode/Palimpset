// The four ConfidenceControls live entirely in the URL query (PP5): ?c=&y0=&y1=&mode=&unscored=
// Back/forward and link-sharing reproduce the exact view. The slider DEFAULTS TO 0
// (§17.F2) — the demo's 0.6 is a pre-set URL, not the default.

export type TemporalMode = "possibly" | "certainly";

export interface ControlsState {
  /** minConfidence 0..1, step 0.05. Default 0. */
  c: number;
  /** window start year (inclusive). null ⇒ all time. */
  y0: number | null;
  /** window end year (inclusive). null ⇒ all time. */
  y1: number | null;
  /** possibly (default) | certainly. certainly HIGHLIGHTS within the graph (D2) — never empties it. */
  mode: TemporalMode;
  /**
   * Unscored toggle — scoped to the CLAIMS LIST only (D4). Defaults ON: in this
   * corpus names/dates/descriptions are all unscored, so hiding them by default
   * would empty the primary surface and hide the messy record the product exists
   * to show (F2). Turning it OFF renders their absence with the reason (PP4).
   */
  unscored: boolean;
}

export const DEFAULT_CONTROLS: ControlsState = {
  c: 0,
  y0: null,
  y1: null,
  mode: "possibly",
  unscored: true,
};

function clampConfidence(n: number): number {
  if (Number.isNaN(n)) return 0;
  const stepped = Math.round(n / 0.05) * 0.05;
  return Math.min(1, Math.max(0, Number(stepped.toFixed(2))));
}

function parseYear(v: string | null): number | null {
  if (v === null || v === "") return null;
  const n = Number(v);
  return Number.isInteger(n) ? n : null;
}

export function parseControls(sp: URLSearchParams): ControlsState {
  const cRaw = sp.get("c");
  const c = cRaw === null || cRaw === "" ? 0 : clampConfidence(Number(cRaw));
  const y0 = parseYear(sp.get("y0"));
  const y1 = parseYear(sp.get("y1"));
  const modeRaw = sp.get("mode");
  const mode: TemporalMode = modeRaw === "certainly" ? "certainly" : "possibly";
  const unscoredRaw = sp.get("unscored");
  // Default ON; only an explicit "0"/"false" turns it off.
  const unscored = !(unscoredRaw === "0" || unscoredRaw === "false");
  return { c, y0, y1, mode, unscored };
}

/**
 * Serialize to query params. Defaults are OMITTED so a pristine view has a clean URL
 * and only deliberate deviations appear in the shared link.
 */
export function controlsToParams(s: ControlsState): URLSearchParams {
  const sp = new URLSearchParams();
  if (s.c !== 0) sp.set("c", String(Number(s.c.toFixed(2))));
  if (s.y0 !== null) sp.set("y0", String(s.y0));
  if (s.y1 !== null) sp.set("y1", String(s.y1));
  if (s.mode !== "possibly") sp.set("mode", s.mode);
  if (!s.unscored) sp.set("unscored", "0");
  return sp;
}

export function controlsToSearchString(s: ControlsState): string {
  const sp = controlsToParams(s);
  const str = sp.toString();
  return str ? `?${str}` : "";
}

export { clampConfidence };
