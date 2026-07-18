// Typed read client for the PALIMPSEST engine (BUILD-CONTRACT §4). Small on
// purpose: every method returns the unwrapped `data` (plus `meta` where the UI
// needs it, e.g. network counts). Swapping to the generated openapi-fetch SDK
// later means replacing `request()` with the SDK call — signatures stay put.

import { ApiError } from "./errors";
import type {
  ClaimDetail,
  EntityDetail,
  EntitySummary,
  Envelope,
  Evidence,
  ImportRun,
  Meta,
  MetaSource,
  NetworkResult,
  PairDossier,
  ProblemJson,
  StatsSummary,
} from "./types";

export const API_BASE = "/api/v1";

export interface NetworkParams {
  minConfidence?: number;
  windowStart?: string | null;
  windowEnd?: string | null;
  temporalMode?: "possibly" | "certainly";
  includeUnscored?: boolean;
  limit?: number;
}

export interface WithMeta<T> {
  data: T;
  meta: Meta;
}

function qs(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
}

function toUrl(path: string): string {
  const rel = `${API_BASE}${path}`;
  // Node's fetch (undici) rejects relative URLs; the browser and jsdom both expose an
  // origin. MSW matches on pathname regardless of origin, so this is safe everywhere.
  if (typeof window !== "undefined" && window.location?.origin) {
    return `${window.location.origin}${rel}`;
  }
  return rel;
}

async function request<T>(path: string): Promise<WithMeta<T>> {
  let res: Response;
  try {
    res = await fetch(toUrl(path), {
      headers: { accept: "application/json" },
    });
  } catch (e) {
    // Network-layer failure (offline, DNS). Presented as a service-unavailable state.
    throw new ApiError(0, null, e instanceof Error ? e.message : "Network error");
  }
  const text = await res.text();
  if (!res.ok) {
    let problem: ProblemJson | null = null;
    try {
      problem = text ? (JSON.parse(text) as ProblemJson) : null;
    } catch {
      problem = null;
    }
    throw new ApiError(res.status, problem, res.statusText);
  }
  const body = JSON.parse(text) as Envelope<T>;
  return { data: body.data, meta: body.meta };
}

async function data<T>(path: string): Promise<T> {
  return (await request<T>(path)).data;
}

export const api = {
  searchEntities(q: string, opts: { type?: string; limit?: number } = {}) {
    return data<EntitySummary[]>(
      `/search/entities${qs({ q, type: opts.type, limit: opts.limit ?? 25 })}`,
    );
  },

  getEntity(id: number | string, resolution: "raw" | "canonical" = "canonical") {
    return data<EntityDetail>(`/entities/${id}${qs({ resolution })}`);
  },

  /** Network fetch — returns edges AND meta.counts/truncated (Q-3). */
  getNetwork(id: number | string, params: NetworkParams = {}) {
    return request<NetworkResult>(
      `/entities/${id}/network${qs({
        minConfidence: params.minConfidence,
        windowStart: params.windowStart,
        windowEnd: params.windowEnd,
        temporalMode: params.temporalMode,
        includeUnscored: params.includeUnscored,
        limit: params.limit,
      })}`,
    );
  },

  getRandomEntity(opts: { type?: string; minScoredDegree?: number } = {}) {
    return data<EntitySummary>(`/entities/random${qs(opts)}`);
  },

  getPair(a: number | string, b: number | string, status?: string) {
    return data<PairDossier>(`/entities/${a}/relations/${b}${qs({ status })}`);
  },

  getClaim(id: number | string) {
    return data<ClaimDetail>(`/claims/${id}`);
  },

  getEvidence(id: number | string) {
    return data<Evidence>(`/claims/${id}/evidence`);
  },

  getStats() {
    return data<StatsSummary>(`/stats/summary`);
  },

  getRuns() {
    return data<ImportRun[]>(`/runs`);
  },

  getSources() {
    return data<MetaSource[]>(`/meta/sources`);
  },
};

export type Api = typeof api;
