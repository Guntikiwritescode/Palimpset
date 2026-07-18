import { useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import type { Edge } from "../api/types";
import { useAsync } from "../lib/useAsync";
import { filterNetwork } from "../lib/network";
import {
  controlsToParams,
  controlsToSearchString,
  parseControls,
  type ControlsState,
} from "../lib/urlState";
import { buildViewCitation, copyToClipboard } from "../lib/citation";
import { ConfidenceControls } from "../components/ConfidenceControls";
import { NetworkCanvas } from "../components/NetworkCanvas";
import { DecadeStrip } from "../components/DecadeStrip";
import { CoveragePanel } from "../components/CoveragePanel";
import { ClaimsList } from "../components/ClaimsList";
import { LifeDates } from "../components/DateViews";
import { ErrorState, Skeleton } from "../components/States";

function filterSummary(c: ControlsState): string {
  const parts = [`minimum strength ${c.c.toFixed(2)}`];
  if (c.y0 !== null) parts.push(c.y0 === c.y1 || c.y1 === null ? `year ${c.y0}` : `years ${c.y0}–${c.y1}`);
  else parts.push("all time");
  parts.push(`${c.mode} emphasis`);
  return parts.join(", ");
}

export function EntityRoute() {
  const { id = "" } = useParams();
  const entityId = Number(id);
  const [sp, setSearchParams] = useSearchParams();
  const [controls, setControls] = useState<ControlsState>(() => parseControls(sp));

  const detail = useAsync(() => api.getEntity(entityId), [entityId]);

  const [allTimeEdges, setAllTimeEdges] = useState<Edge[] | null>(null);
  const [windowEdges, setWindowEdges] = useState<Edge[] | null>(null);
  const [truncated, setTruncated] = useState(false);
  const [serverLoading, setServerLoading] = useState(false);

  // URL → state (back/forward + link sharing reproduce the exact view, PP5).
  useEffect(() => {
    const fromUrl = parseControls(sp);
    setControls((prev) =>
      controlsToSearchString(prev) === controlsToSearchString(fromUrl) ? prev : fromUrl,
    );
  }, [sp]);

  // state → URL, debounced 250ms; replace so slider drags don't spam history (§13.4).
  useEffect(() => {
    const t = setTimeout(() => {
      const next = controlsToParams(controls);
      if (next.toString() !== sp.toString()) setSearchParams(next, { replace: true });
    }, 250);
    return () => clearTimeout(t);
  }, [controls, sp, setSearchParams]);

  // Fetch the full ego network ONCE per entity (minConfidence=0, includeUnscored, all time).
  // Held in memory for the instant slider and the decade strip (§13.4).
  useEffect(() => {
    let alive = true;
    setAllTimeEdges(null);
    setWindowEdges(null);
    api
      .getNetwork(entityId, { minConfidence: 0, includeUnscored: true, limit: 500 })
      .then((res) => {
        if (!alive) return;
        setAllTimeEdges(res.data.edges);
        setTruncated(!!res.meta.truncated);
      })
      .catch(() => alive && setAllTimeEdges([]));
    return () => {
      alive = false;
    };
  }, [entityId]);

  // Window change re-queries the server (it alters the possibly/certainly predicate);
  // a subtle inline loader, never a canvas blank-out (§13.4). Confidence never re-queries.
  useEffect(() => {
    if (controls.y0 === null) return;
    let alive = true;
    setServerLoading(true);
    const windowStart = `${controls.y0}-01-01`;
    const windowEnd = `${controls.y1 ?? controls.y0}-12-31`;
    api
      .getNetwork(entityId, { minConfidence: 0, includeUnscored: true, limit: 500, windowStart, windowEnd })
      .then((res) => {
        if (!alive) return;
        setWindowEdges(res.data.edges);
        setTruncated(!!res.meta.truncated);
      })
      .catch(() => alive && setWindowEdges([]))
      .finally(() => alive && setServerLoading(false));
    return () => {
      alive = false;
    };
  }, [entityId, controls.y0, controls.y1]);

  // No window ⇒ the windowed set mirrors the held all-time set (no round trip).
  useEffect(() => {
    if (controls.y0 === null && allTimeEdges) setWindowEdges(allTimeEdges);
  }, [controls.y0, allTimeEdges]);

  // Instant client-side confidence filter + local recomputation of the three counts.
  const { visible, counts } = useMemo(
    () => filterNetwork(windowEdges ?? [], { minConfidence: controls.c, includeUnscored: true }),
    [windowEdges, controls.c],
  );

  const [copied, setCopied] = useState(false);

  if (detail.loading) {
    return (
      <div className="panel">
        <Skeleton rows={6} />
      </div>
    );
  }
  if (detail.error || !detail.data) {
    return <ErrorState error={detail.error} onRetry={detail.reload} />;
  }

  const { summary, externalIds, attributeClaims, coverage } = detail.data;
  const primaryName = attributeClaims
    .find((g) => g.predicate === "has-name")
    ?.claims.find((c) => c.object.literal?.kind === "primary");

  return (
    <div>
      {/* (a) Identity header */}
      <header className="panel">
        <h1 style={{ margin: "0 0 0.35rem" }}>{summary.displayName}</h1>
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", alignItems: "center" }}>
          <LifeDates lifeDates={summary.lifeDates} />
          <span className="chip">{summary.entityType}</span>
          {externalIds.map((x) => (
            <span
              key={`${x.authority}-${x.externalId}`}
              className="chip"
              title="External ids are claims here — many-to-one and contradictable, not identity keys."
            >
              {x.authority}:{x.externalId}
            </span>
          ))}
        </div>
        {summary.description && <p className="muted">{summary.description}</p>}
        <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", alignItems: "center" }}>
          {primaryName && (
            <Link className="small" to={`/claim/${primaryName.id}`} data-testid="why-this-name">
              Why this name?
            </Link>
          )}
          <button
            className="btn"
            data-testid="copy-view-citation"
            onClick={async () => {
              const ok = await copyToClipboard(
                buildViewCitation({
                  entityName: summary.displayName,
                  filterSummary: filterSummary(controls),
                  reproducingUrl: window.location.href,
                }),
              );
              setCopied(ok);
            }}
          >
            {copied ? "View citation copied ✓" : "Copy citation for this view"}
          </button>
        </div>
      </header>

      {/* (b) ConfidenceControls + live result line */}
      <ConfidenceControls
        state={controls}
        onChange={setControls}
        counts={counts}
        serverLoading={serverLoading}
      />

      {/* (c) NetworkCanvas (canvas + accessible ranked tie list) */}
      {allTimeEdges === null ? (
        <div className="panel">
          <Skeleton rows={4} />
        </div>
      ) : (
        <>
          <NetworkCanvas
            focus={summary}
            edges={visible}
            mode={controls.mode}
            truncated={truncated}
            total={allTimeEdges.length}
          />
          {/* decade strip uses the held all-time bounds */}
          <DecadeStrip
            edges={allTimeEdges}
            activeStart={controls.y0}
            onPick={(start, end) => setControls((c) => ({ ...c, y0: start, y1: end }))}
          />
        </>
      )}

      {/* (d) Coverage panel */}
      <CoveragePanel coverage={coverage} />

      {/* (e) Claims list with competing claims side-by-side */}
      <ClaimsList groups={attributeClaims} showUnscored={controls.unscored} />

      <p className="small muted">
        Every tie and claim above reaches its source record in one more click.{" "}
        <Link to="/about">What do these numbers mean?</Link>
      </p>
    </div>
  );
}
