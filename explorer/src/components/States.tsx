import type { ReactNode } from "react";
import { isApiError } from "../api/errors";

/** Skeleton rows matching the final layout — never a full-page spinner (§13.6). */
export function Skeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div aria-busy="true" aria-live="polite">
      <span className="sr-only">Loading…</span>
      {Array.from({ length: rows }).map((_, i) => (
        <div className="skeleton" key={i} style={{ width: `${90 - i * 8}%` }} />
      ))}
    </div>
  );
}

/** Empty state ALWAYS states why (PP4): the limit, then the reason. */
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="state" role="status">
      <p>
        <strong>{title}</strong>
      </p>
      {children && <p className="small">{children}</p>}
    </div>
  );
}

/** Error state: problem-json detail in plain language, a retry, and the request id in small type. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const detail = isApiError(error)
    ? error.problem?.detail || error.message
    : error instanceof Error
      ? error.message
      : "Something went wrong.";
  const requestId = isApiError(error) ? error.requestId : null;
  const unavailable = isApiError(error) && error.status === 0;
  return (
    <div className="state" role="alert">
      <p>
        <strong>{unavailable ? "The service is unavailable." : "Could not load this."}</strong>
      </p>
      <p className="small">{detail}</p>
      {onRetry && (
        <button className="btn" onClick={onRetry}>
          Retry
        </button>
      )}
      {requestId && <p className="small mono muted">request {requestId}</p>}
    </div>
  );
}
