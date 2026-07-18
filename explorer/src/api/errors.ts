import type { ProblemJson } from "./types";

/** Raised for any non-2xx response. Carries the problem+json body when present. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemJson | null;
  readonly requestId: string | null;

  constructor(status: number, problem: ProblemJson | null, fallback?: string) {
    super(problem?.detail || fallback || `Request failed (${status})`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.requestId = problem?.requestId ?? null;
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}
