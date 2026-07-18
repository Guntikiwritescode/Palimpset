import { Link } from "react-router-dom";
import type { ClaimDetail, PredicateGroup } from "../api/types";
import { ConfidenceChip } from "./ConfidenceChip";
import { ProvenanceStrip, StatusNote, statusClass } from "./Badges";

function claimValue(c: ClaimDetail): string {
  if (c.object.literal) return c.object.literal.value;
  if (c.object.entity) return c.object.entity.displayName;
  return "—";
}

function ClaimCard({ claim }: { claim: ClaimDetail }) {
  return (
    <div className={`claim-card ${statusClass(claim.status)}`} data-testid="claim-card">
      <div className="value">
        <strong>{claimValue(claim)}</strong>{" "}
        {claim.object.literal?.kind && <span className="muted small">({claim.object.literal.kind})</span>}{" "}
        <StatusNote status={claim.status} />
      </div>
      <div style={{ margin: "0.25rem 0" }}>
        <ConfidenceChip confidence={claim.confidence} />
      </div>
      <ProvenanceStrip claim={claim} />
      <Link className="small" to={`/claim/${claim.id}`}>
        evidence →
      </Link>
    </div>
  );
}

interface Props {
  groups: PredicateGroup[];
  showUnscored: boolean;
}

/**
 * Attribute claims grouped by predicate. Where a predicate has >1 claim they render
 * SIDE BY SIDE under a "competing claims" heading — never collapsed to a winner (PP3).
 * Unscored claims are gated by the (claims-list-scoped) toggle; when hidden their
 * absence is stated with the reason (PP4/D4).
 */
export function ClaimsList({ groups, showUnscored }: Props) {
  return (
    <section className="panel" aria-label="Claims about this person">
      <h2>Claims</h2>
      {groups.map((g) => {
        const scored = g.claims.filter((c) => c.confidence.scored);
        const unscored = g.claims.filter((c) => !c.confidence.scored);
        const shown = showUnscored ? g.claims : scored;
        const hiddenCount = showUnscored ? 0 : unscored.length;
        const competing = shown.length > 1;
        return (
          <div key={g.predicate} style={{ marginBottom: "0.9rem" }}>
            <h3>
              {g.predicate.replace(/-/g, " ")}
              {competing && <span className="muted small"> · competing claims</span>}
            </h3>
            {shown.length === 0 ? (
              <p className="muted small" data-testid="all-hidden">
                {hiddenCount} unscored{" "}
                {hiddenCount === 1 ? "claim is" : "claims are"} hidden — turn on “Show unscored attribute claims”
                to see {hiddenCount === 1 ? "it" : "them"}. The evidence here is unscored, not absent.
              </p>
            ) : (
              <>
                <div className={competing ? "competing" : ""}>
                  <div className="claim-cols">
                    {shown.map((c) => (
                      <ClaimCard key={c.id} claim={c} />
                    ))}
                  </div>
                </div>
                {hiddenCount > 0 && (
                  <p className="muted small" data-testid="some-hidden">
                    {hiddenCount} unscored{" "}
                    {hiddenCount === 1 ? "claim" : "claims"} hidden — turn on “Show unscored attribute claims”.
                  </p>
                )}
              </>
            )}
          </div>
        );
      })}
    </section>
  );
}
