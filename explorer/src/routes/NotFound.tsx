import { Link } from "react-router-dom";
import { EmptyState } from "../components/States";

export function NotFoundRoute() {
  return (
    <div className="panel">
      <EmptyState title="Nothing here.">
        That page isn't part of the explorer. <Link to="/">Back to search</Link>.
      </EmptyState>
    </div>
  );
}
