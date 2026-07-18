import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { EntitySummary } from "../api/types";
import { formatLifeDates } from "../lib/fuzzyDate";
import { EmptyState, ErrorState, Skeleton } from "../components/States";
import { CorpusFooter } from "../components/CorpusFooter";

function truncate(s: string | null, n = 120): string {
  if (!s) return "";
  return s.length > n ? `${s.slice(0, n - 1)}…` : s;
}

/** One result row: name · life-date range (fuzzy grammar) · description · SCORED tie count only (D8). */
function ResultRow({ e }: { e: EntitySummary }) {
  return (
    <Link className="result-row" to={`/entity/${e.id}`} data-testid="search-result">
      <div className="name">{e.displayName}</div>
      <div className="meta">
        <span data-testid="result-lifedates">{formatLifeDates(e.lifeDates)}</span> · {e.entityType}
        {" · "}
        <span className="tiecount">
          {e.degreeScored} {e.degreeScored === 1 ? "scored tie" : "scored ties"}
        </span>
      </div>
      {e.description && <div className="desc">{truncate(e.description)}</div>}
    </Link>
  );
}

export function SearchRoute() {
  const [q, setQ] = useState("");
  const [debounced, setDebounced] = useState("");
  const [results, setResults] = useState<EntitySummary[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Debounced 250ms; minimum 2 characters (§13.1).
  useEffect(() => {
    const t = setTimeout(() => setDebounced(q.trim()), 250);
    return () => clearTimeout(t);
  }, [q]);

  useEffect(() => {
    if (debounced.length < 2) {
      setResults(null);
      setError(null);
      setLoading(false);
      return;
    }
    let alive = true;
    setLoading(true);
    setError(null);
    api
      .searchEntities(debounced)
      .then((r) => alive && setResults(r))
      .catch((e) => alive && setError(e))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [debounced]);

  const exploreSomeone = async () => {
    try {
      const e = await api.getRandomEntity({ minScoredDegree: 5 });
      navigate(`/entity/${e.id}`);
    } catch {
      /* ignore — button is a convenience */
    }
  };

  return (
    <div>
      <div className="panel">
        <label htmlFor="search" className="sr-only">
          Search for a person
        </label>
        <input
          id="search"
          ref={inputRef}
          className="search-field"
          type="search"
          placeholder="Search for a person — e.g. Bacon"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          autoComplete="off"
          data-testid="search-input"
        />
        <div style={{ marginTop: "0.6rem", display: "flex", gap: "0.75rem", alignItems: "center" }}>
          <button className="btn" onClick={exploreSomeone} data-testid="explore-someone">
            Explore someone
          </button>
          <span className="muted small">Two people can share a name — the life dates tell them apart.</span>
        </div>
      </div>

      <div className="panel" aria-live="polite">
        {error ? (
          <ErrorState error={error} onRetry={() => setDebounced((d) => `${d}`)} />
        ) : loading ? (
          <Skeleton rows={4} />
        ) : results === null ? (
          <EmptyState title="Search the corpus, or explore someone at random.">
            Type at least two letters. Results appear as you type.
          </EmptyState>
        ) : results.length === 0 ? (
          <EmptyState title={`No one matching “${debounced}”.`}>
            The corpus is early-modern Europe, roughly 1500–1700, and currently covers a single source. Try a
            surname, or “Explore someone”.
          </EmptyState>
        ) : (
          <div data-testid="results">
            {results.map((e) => (
              <ResultRow key={e.id} e={e} />
            ))}
          </div>
        )}
      </div>

      <CorpusFooter />
    </div>
  );
}
