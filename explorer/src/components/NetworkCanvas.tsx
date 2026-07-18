import { Suspense, lazy, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import type { Edge, EntitySummary } from "../api/types";
import { edgeOpacity, edgeThickness } from "../lib/confidence";
import type { TemporalMode } from "../lib/urlState";
import { rankedTies } from "../lib/network";
import { ConfidenceChip } from "./ConfidenceChip";
import { EdgeGlyph } from "./EdgeGlyph";
import { ValidTime } from "./DateViews";

// Canvas is a progressive enhancement; the ranked tie list is the honest fallback
// and the screen-reader equivalent (§13.7). ForceGraph is lazy so unit tests
// (jsdom, no 2d context) never load it.
const ForceGraph2D = lazy(() => import("react-force-graph-2d"));

function hasCanvas(): boolean {
  try {
    if (typeof document === "undefined") return false;
    const c = document.createElement("canvas");
    return !!(c.getContext && c.getContext("2d"));
  } catch {
    return false;
  }
}

interface Props {
  focus: EntitySummary;
  edges: Edge[];
  mode: TemporalMode;
  truncated?: boolean;
  total?: number;
}

export function NetworkCanvas({ focus, edges, mode, truncated, total }: Props) {
  const navigate = useNavigate();
  const wrapRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(720);
  const canvas = useMemo(hasCanvas, []);
  const { scored, unscored } = useMemo(() => rankedTies(edges), [edges]);

  useEffect(() => {
    if (!wrapRef.current) return;
    const el = wrapRef.current;
    const update = () => setWidth(el.clientWidth || 720);
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const graphData = useMemo(() => {
    const nodes = [
      { id: focus.id, name: focus.displayName, focus: true },
      ...edges.map((e) => ({ id: e.counterpart.id, name: e.counterpart.displayName, focus: false })),
    ];
    const seen = new Set<number>();
    const uniqueNodes = nodes.filter((n) => (seen.has(n.id) ? false : (seen.add(n.id), true)));
    const links = edges.map((e) => ({
      source: focus.id,
      target: e.counterpart.id,
      edge: e,
    }));
    return { nodes: uniqueNodes, links };
  }, [focus, edges]);

  return (
    <section className="panel" aria-label="Ego network">
      <h2>Attested ties</h2>
      {truncated && (
        <p className="warn" role="status">
          Showing the {edges.length} strongest ties{typeof total === "number" ? ` of ${total}` : ""}. Raise the
          confidence threshold or narrow the window to see fewer, better-attested ones.
        </p>
      )}

      {canvas && edges.length > 0 && (
        <div className="canvas-wrap" ref={wrapRef} data-testid="network-canvas" aria-hidden="true">
          <Suspense fallback={<div className="state small">Preparing the graph…</div>}>
            <ForceGraph2D
              graphData={graphData}
              width={width}
              height={380}
              nodeId="id"
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              nodeLabel={(n: any) => String(n.name)}
              nodeRelSize={5}
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              nodeColor={(n: any) => (n.focus ? "#b26b00" : "#4a5d7e")}
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              linkColor={(l: any) => {
                const edge = l.edge as Edge;
                const dim = mode === "certainly" && !edge.certainlyActive;
                const o = edgeOpacity(edge.confidence.effective) * (dim ? 0.25 : 1);
                return `rgba(74, 93, 126, ${o.toFixed(2)})`;
              }}
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              linkWidth={(l: any) => {
                const edge = l.edge as Edge;
                return edgeThickness(edge.confidence.effective) * (mode === "certainly" && edge.certainlyActive ? 1.8 : 1);
              }}
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              linkLineDash={(l: any) => ((l.edge as Edge).scored ? null : [4, 3])}
              cooldownTime={1200}
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              onNodeClick={(n: any) => navigate(`/entity/${n.id}`)}
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              onLinkClick={(l: any) => navigate(`/claim/${(l.edge as Edge).claimId}`)}
            />
          </Suspense>
        </div>
      )}

      {/* The ranked tie list: accessible mirror of the canvas AND the <700px fallback. */}
      <div className="tie-fallback">
        <p className="sr-only">Ties in attestation order. This list mirrors the graph above.</p>
        {edges.length === 0 ? (
          <p className="muted small">No ties match the current filters.</p>
        ) : (
          <ol className="tie-list" data-testid="tie-list">
            {scored.map((e) => (
              <li
                key={e.claimId}
                className={e.certainlyActive ? "certainly" : ""}
                data-testid="tie-row"
                data-certainly={e.certainlyActive ? "true" : "false"}
              >
                <span>
                  <EdgeGlyph edge={e} />{" "}
                  <Link className="tie-name" to={`/entity/${e.counterpart.id}`}>
                    {e.counterpart.displayName}
                  </Link>{" "}
                  <span className="muted small">
                    · <ValidTime interval={e.validTime} />
                    {e.certainlyActive && <span className="req"> · required by the evidence</span>}
                  </span>
                </span>
                <span>
                  <ConfidenceChip confidence={e.confidence} />{" "}
                  <Link className="small" to={`/claim/${e.claimId}`}>
                    evidence
                  </Link>
                </span>
              </li>
            ))}
            {unscored.length > 0 && (
              <>
                <li className="muted small" aria-hidden="true" style={{ paddingTop: "0.5rem" }}>
                  Unscored ties (never ranked among scored):
                </li>
                {unscored.map((e) => (
                  <li key={e.claimId} data-testid="tie-row-unscored">
                    <span>
                      <EdgeGlyph edge={e} />{" "}
                      <Link className="tie-name" to={`/entity/${e.counterpart.id}`}>
                        {e.counterpart.displayName}
                      </Link>
                    </span>
                    <span>
                      <ConfidenceChip confidence={e.confidence} />{" "}
                      <Link className="small" to={`/claim/${e.claimId}`}>
                        evidence
                      </Link>
                    </span>
                  </li>
                ))}
              </>
            )}
          </ol>
        )}
      </div>
    </section>
  );
}
