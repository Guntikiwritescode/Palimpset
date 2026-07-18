import { Link } from "react-router-dom";
import { api } from "../api/client";
import { useAsync } from "../lib/useAsync";

/** Corpus footer (§13.1): "N people · M claims · K sources · confidence uncalibrated" — LIVE. */
export function CorpusFooter() {
  const { data } = useAsync(() => api.getStats(), []);
  return (
    <footer className="panel small" aria-label="Corpus summary">
      {data ? (
        <span>
          <b>{data.entities.total.toLocaleString()}</b> people ·{" "}
          <b>{data.claims.total.toLocaleString()}</b> claims ·{" "}
          <b>{data.sources.length}</b> {data.sources.length === 1 ? "source" : "sources"} ·{" "}
          confidence uncalibrated
        </span>
      ) : (
        <span className="muted">Loading corpus summary…</span>
      )}{" "}
      · <Link to="/about">What do these numbers mean?</Link>
    </footer>
  );
}
