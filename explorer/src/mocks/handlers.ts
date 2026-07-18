// MSW handlers — the mock engine. Every response is the BUILD-CONTRACT §2 envelope
// {data, meta}; the network route carries meta.counts (Q-3) and meta.truncated.
// Specific /entities routes are registered before /entities/:id so they win.

import { http, HttpResponse } from "msw";
import type { Envelope, Meta, ProblemJson } from "../api/types";
import { API_BASE } from "../api/client";
import { resolveNetwork } from "./resolveNetwork";
import {
  entityDetailOf,
  entities,
  evidenceFor,
  lookupByExternalId,
  metaSources,
  pairClaims,
  randomEntity,
  runs,
  searchEntities,
  stats,
} from "./data";

let reqSeq = 0;
function meta(extra: Partial<Meta> = {}): Meta {
  reqSeq += 1;
  return { requestId: `mock-${reqSeq}`, ...extra };
}

function ok<T>(data: T, extra: Partial<Meta> = {}): HttpResponse {
  const body: Envelope<T> = { data, meta: meta(extra) };
  return HttpResponse.json(body);
}

function problem(status: number, title: string, detail: string, instance: string): HttpResponse {
  reqSeq += 1;
  const body: ProblemJson = {
    type: `https://palimpsest.dev/errors/${status}`,
    title,
    status,
    detail,
    instance,
    requestId: `mock-${reqSeq}`,
  };
  return HttpResponse.json(body, { status, headers: { "content-type": "application/problem+json" } });
}

function yearParam(v: string | null): number | null {
  if (!v) return null;
  const m = /^(-?\d{1,4})/.exec(v);
  return m ? Number(m[1]) : null;
}

const P = API_BASE;
const STATUS_FILTER = "excludes disputed and superseded";

export const handlers = [
  // ---- search ----
  http.get(`${P}/search/entities`, ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get("q") ?? "";
    const limit = Number(url.searchParams.get("limit") ?? 25);
    if (q.trim().length < 2) return ok([], { statusFilter: STATUS_FILTER });
    return ok(searchEntities(q, limit), { statusFilter: STATUS_FILTER });
  }),

  // ---- specific /entities routes BEFORE /entities/:id ----
  http.get(`${P}/entities/lookup`, ({ request }) => {
    const url = new URL(request.url);
    const authority = url.searchParams.get("authority") ?? "";
    const externalId = url.searchParams.get("externalId") ?? "";
    const found = lookupByExternalId(authority, externalId);
    if (!found) {
      return problem(404, "Not found", `No entity for ${authority}:${externalId}.`, url.pathname);
    }
    return ok(found);
  }),

  http.get(`${P}/entities/random`, ({ request }) => {
    const url = new URL(request.url);
    const minScoredDegree = Number(url.searchParams.get("minScoredDegree") ?? 5);
    return ok(randomEntity(minScoredDegree));
  }),

  http.get(`${P}/entities/:id/network`, ({ params, request }) => {
    const url = new URL(request.url);
    const id = Number(params.id);
    if (!entities.has(id)) {
      return problem(404, "Not found", `No entity ${id}.`, url.pathname);
    }
    const minConfidence = Number(url.searchParams.get("minConfidence") ?? 0);
    const includeUnscored =
      url.searchParams.get("includeUnscored") === "true" || url.searchParams.get("includeUnscored") === "1";
    const limit = Math.min(500, Number(url.searchParams.get("limit") ?? 500));
    const resolved = resolveNetwork(id, {
      minConfidence: Number.isFinite(minConfidence) ? minConfidence : 0,
      windowStart: yearParam(url.searchParams.get("windowStart")),
      windowEnd: yearParam(url.searchParams.get("windowEnd")),
      includeUnscored,
      limit,
    });
    return ok(
      { focus: entities.get(id)!, edges: resolved.edges },
      { counts: resolved.counts, truncated: resolved.truncated, statusFilter: STATUS_FILTER },
    );
  }),

  http.get(`${P}/entities/:a/relations/:b`, ({ params, request }) => {
    const url = new URL(request.url);
    const a = Number(params.a);
    const b = Number(params.b);
    const ea = entities.get(a);
    const eb = entities.get(b);
    if (!ea || !eb) {
      return problem(404, "Not found", `Unknown entity in pair ${a}/${b}.`, url.pathname);
    }
    return ok({ a: ea, b: eb, claims: pairClaims(a, b) }, { statusFilter: STATUS_FILTER });
  }),

  http.get(`${P}/entities/:id`, ({ params, request }) => {
    const url = new URL(request.url);
    const id = Number(params.id);
    const detail = entityDetailOf(id);
    if (!detail) {
      return problem(404, "Not found", `No entity ${id} in the corpus.`, url.pathname);
    }
    return ok(detail, { statusFilter: STATUS_FILTER });
  }),

  // ---- claims ----
  http.get(`${P}/claims/:id/evidence`, ({ params, request }) => {
    const url = new URL(request.url);
    const ev = evidenceFor(Number(params.id));
    if (!ev) return problem(404, "Not found", `No claim ${params.id}.`, url.pathname);
    return ok(ev);
  }),

  http.get(`${P}/claims/:id`, ({ params, request }) => {
    const url = new URL(request.url);
    const ev = evidenceFor(Number(params.id));
    if (!ev) return problem(404, "Not found", `No claim ${params.id}.`, url.pathname);
    return ok(ev.claim);
  }),

  // ---- honesty-page sources (all LIVE) ----
  http.get(`${P}/stats/summary`, () => ok(stats, { statusFilter: STATUS_FILTER })),
  http.get(`${P}/runs`, () => ok(runs)),
  http.get(`${P}/meta/sources`, () => ok(metaSources)),
];
