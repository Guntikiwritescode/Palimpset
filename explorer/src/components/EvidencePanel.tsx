import { Link } from "react-router-dom";
import type { Evidence } from "../api/types";
import { UNCALIBRATED_WARNING } from "../lib/confidence";
import { ConfidenceChip } from "./ConfidenceChip";
import { LicenseBadge, ProvenanceStrip } from "./Badges";
import { ValidTime } from "./DateViews";
import { assertionText } from "../lib/citation";

/**
 * Evidence panel (Flow C, §13.4): plain-language claim, provenance strip, the
 * uncalibrated warning, the raw record (collapsible), and the amber license badge
 * when license_confirmed=false. Every rendered fact reaches its source here (PP1).
 */
export function EvidencePanel({ evidence }: { evidence: Evidence }) {
  const { claim, support } = evidence;
  return (
    <section className="panel" aria-label="Evidence">
      <h2>Evidence</h2>

      <p data-testid="plain-claim">{assertionText(claim)}</p>

      <div style={{ margin: "0.5rem 0" }}>
        <ConfidenceChip confidence={claim.confidence} /> · valid time:{" "}
        <ValidTime interval={claim.validTime} />
      </div>

      {support.map((s) => {
        const raw = s.record.raw;
        const scorePart =
          claim.confidence.scored && claim.confidence.raw != null
            ? `max_certainty ${String(claim.confidence.raw)}`
            : "unscored";
        return (
          <div key={s.record.externalId} className="panel" style={{ background: "transparent" }}>
            <div className="provenance" data-testid="provenance-strip">
              {claim.method} · {s.source.version} · {scorePart} · uncalibrated
            </div>
            {!s.source.licenseConfirmed && (
              <p style={{ margin: "0.5rem 0" }}>
                <LicenseBadge source={s.source} />
              </p>
            )}
            {claim.confidence.scored && (
              <p className="warn" data-testid="uncalibrated-warning">
                {UNCALIBRATED_WARNING}
              </p>
            )}
            <ProvenanceStrip claim={claim} sourceSlug={s.source.slug} />
            <p className="small muted">
              {s.source.title}, {s.source.version} —{" "}
              <a href={s.source.retrievalUri} target="_blank" rel="noreferrer noopener">
                {s.source.retrievalUri}
              </a>
            </p>
            <details>
              <summary>Raw source record</summary>
              <pre className="raw" data-testid="raw-record">
                {JSON.stringify(raw, null, 2)}
              </pre>
              <p className="small mono muted">content hash: {s.record.contentHash}</p>
            </details>
          </div>
        );
      })}

      <p className="small">
        <Link to={`/claim/${claim.id}`}>Permalink to this claim</Link>
      </p>
    </section>
  );
}
