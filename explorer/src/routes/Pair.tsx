import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { ClaimDetail } from "../api/types";
import { useAsync } from "../lib/useAsync";
import { ConfidenceChip } from "../components/ConfidenceChip";
import { ProvenanceStrip, StatusNote, statusClass } from "../components/Badges";
import { ValidTime } from "../components/DateViews";
import { CitationButton } from "../components/CitationButton";
import { EmptyState, ErrorState, Skeleton } from "../components/States";

function groupByPredicate(claims: ClaimDetail[]): { predicate: string; claims: ClaimDetail[] }[] {
  const map = new Map<string, ClaimDetail[]>();
  for (const c of claims) {
    const arr = map.get(c.predicate) ?? [];
    arr.push(c);
    map.set(c.predicate, arr);
  }
  return [...map.entries()].map(([predicate, cs]) => ({ predicate, claims: cs }));
}

export function PairRoute() {
  const { a = "", b = "" } = useParams();
  const { data, error, loading, reload } = useAsync(() => api.getPair(Number(a), Number(b)), [a, b]);

  if (loading) return <div className="panel"><Skeleton rows={4} /></div>;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;

  const groups = groupByPredicate(data.claims);

  return (
    <div>
      <header className="panel">
        <h1>
          <Link to={`/entity/${data.a.id}`}>{data.a.displayName}</Link> &{" "}
          <Link to={`/entity/${data.b.id}`}>{data.b.displayName}</Link>
        </h1>
        <p className="muted small">
          The complete evidentiary record between these two people. Where a predicate carries more than one
          claim they appear side by side — never collapsed to a winner.
        </p>
      </header>

      {data.claims.length === 0 ? (
        <div className="panel">
          <EmptyState title="No attested ties between these two in this source.">
            An absent tie means no surviving attested evidence here — never that no relationship existed.
          </EmptyState>
        </div>
      ) : (
        groups.map((g) => {
          const competing = g.claims.length > 1;
          return (
            <section className="panel" key={g.predicate}>
              <h2>
                {g.predicate.replace(/-/g, " ")}
                {competing && <span className="muted small"> · competing / disputed</span>}
              </h2>
              <div className="claim-cols">
                {g.claims.map((c) => (
                  <div key={c.id} className={`claim-card ${statusClass(c.status)}`}>
                    <div className="value">
                      <ConfidenceChip confidence={c.confidence} /> <StatusNote status={c.status} />
                    </div>
                    <p className="small muted" style={{ margin: "0.25rem 0" }}>
                      valid time: <ValidTime interval={c.validTime} />
                    </p>
                    <ProvenanceStrip claim={c} />
                    <div style={{ marginTop: "0.4rem" }}>
                      <Link className="small" to={`/claim/${c.id}`}>
                        evidence →
                      </Link>
                    </div>
                    <div style={{ marginTop: "0.4rem" }}>
                      <CitationButton claim={c} label="Copy citation" />
                    </div>
                  </div>
                ))}
              </div>
            </section>
          );
        })
      )}
    </div>
  );
}
