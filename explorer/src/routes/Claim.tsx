import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { useAsync } from "../lib/useAsync";
import { EvidencePanel } from "../components/EvidencePanel";
import { CitationButton } from "../components/CitationButton";
import { ConfidenceChip } from "../components/ConfidenceChip";
import { StatusNote } from "../components/Badges";
import { ErrorState, Skeleton } from "../components/States";

export function ClaimRoute() {
  const { id = "" } = useParams();
  const { data, error, loading, reload } = useAsync(() => api.getEvidence(Number(id)), [id]);

  if (loading) return <div className="panel"><Skeleton rows={5} /></div>;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;

  const claim = data.claim;
  const objectEntity = claim.object.entity;

  return (
    <div>
      <header className="panel">
        <p className="small muted">
          <Link to={`/entity/${claim.subject.id}`}>{claim.subject.displayName}</Link> ·{" "}
          {claim.predicate.replace(/-/g, " ")}
          {objectEntity && (
            <>
              {" "}
              · <Link to={`/entity/${objectEntity.id}`}>{objectEntity.displayName}</Link>
            </>
          )}
        </p>
        <h1 style={{ margin: "0.2rem 0" }}>
          {claim.subject.displayName} — {claim.predicate.replace(/-/g, " ")} —{" "}
          {objectEntity?.displayName ?? claim.object.literal?.value}
        </h1>
        <div>
          <ConfidenceChip confidence={claim.confidence} /> <StatusNote status={claim.status} />
        </div>
        {objectEntity && (
          <p className="small">
            <Link to={`/pair/${claim.subject.id}/${objectEntity.id}`}>See the full dossier for this pair →</Link>
          </p>
        )}
      </header>

      <EvidencePanel evidence={data} />

      <section className="panel">
        <h2>Cite this</h2>
        <CitationButton claim={claim} evidence={data} />
      </section>
    </div>
  );
}
