// DTOs — verbatim from BUILD-CONTRACT §3 (camelCase JSON). This surface is kept
// deliberately small and typed so it swaps mechanically for the generated SDK in
// sdk/typescript/ (openapi-typescript types + openapi-fetch runtime) later.

/** ISO date string "YYYY-MM-DD" or null. */
export type IsoDate = string | null;

export interface LifeDates {
  bornEarliest: IsoDate;
  bornLatest: IsoDate;
  diedEarliest: IsoDate;
  diedLatest: IsoDate;
}

export interface EntitySummary {
  id: number;
  displayName: string;
  entityType: string;
  description: string | null;
  gender: string | null;
  lifeDates: LifeDates;
  /** Scored relationship degree. Search rows advertise ONLY this (D8). */
  degreeScored: number;
  /** Unscored degree. Never ranked, never surfaced as a search tie-count (D8/I5). */
  degreeUnscored: number;
}

export interface FuzzyInterval {
  startEarliest: IsoDate;
  startLatest: IsoDate;
  endEarliest: IsoDate;
  endLatest: IsoDate;
  approximate: boolean;
  /** Source's native encoding, verbatim — never destroyed, never hidden (§13.3). */
  original: Record<string, unknown> | null;
}

export type ConfidenceOrigin =
  | "source_native"
  | "calibration"
  | "manual"
  | "inferred"
  | null;

export interface Confidence {
  /** effective confidence 0..1; null ⇒ unscored (never 0). Resolved by I7. */
  effective: number | null;
  origin: ConfidenceOrigin;
  raw: unknown;
  calibrated: boolean;
  scored: boolean;
}

export interface Edge {
  claimId: number;
  counterpart: EntitySummary;
  predicate: string;
  confidence: Confidence;
  validTime: FuzzyInterval;
  scored: boolean;
  /** Certainly-active in the current window — HIGHLIGHT within the graph (D2). */
  certainlyActive: boolean;
  /** No dates at all — counted separately (A7). */
  undated: boolean;
  status: string;
}

export interface Literal {
  kind: string;
  value: string;
  authority?: string;
}

export interface ClaimObject {
  entity?: EntitySummary;
  literal?: Literal;
}

export interface AssertedBy {
  slug: string;
  kind: string;
  displayName: string;
}

export interface ClaimDetail {
  id: number;
  subject: EntitySummary;
  predicate: string;
  object: ClaimObject;
  validTime: FuzzyInterval;
  confidence: Confidence;
  method: string;
  methodDetail: Record<string, unknown> | null;
  status: string;
  assertedBy: AssertedBy;
  recordedAt: string;
}

export interface SourceRef {
  slug: string;
  title: string;
  version: string;
  retrievalUri: string;
  license: string;
  licenseConfirmed: boolean;
}

export interface SupportRecord {
  recordKind: string;
  externalId: string;
  contentHash: string;
  raw: Record<string, unknown>;
}

export interface Support {
  source: SourceRef;
  record: SupportRecord;
}

/** Flow C payload. */
export interface Evidence {
  claim: ClaimDetail;
  support: Support[];
}

// ---- endpoint payloads (BUILD-CONTRACT §4) ----

export interface ExternalIdRef {
  authority: string;
  externalId: string;
}

export interface PredicateGroup {
  predicate: string;
  claims: ClaimDetail[];
}

export interface CoverageBySource {
  slug: string;
  relationshipClaims: number;
  attributeClaims: number;
}

export interface Coverage {
  bySource: CoverageBySource[];
  scored: number;
  unscored: number;
  calibration: "uncalibrated" | string;
}

export interface EntityDetail {
  summary: EntitySummary;
  externalIds: ExternalIdRef[];
  attributeClaims: PredicateGroup[];
  coverage: Coverage;
}

export interface NetworkCounts {
  possibly: number;
  certainly: number;
  undated: number;
}

export interface NetworkResult {
  focus: EntitySummary;
  edges: Edge[];
}

export interface PairDossier {
  a: EntitySummary;
  b: EntitySummary;
  claims: ClaimDetail[];
}

export interface ConfidenceHistogramBin {
  band: string;
  count: number;
}

export interface StatsSummary {
  entities: { total: number; byType: Record<string, number> };
  claims: { total: number; byPredicate: Record<string, number> };
  sourceRecords: number;
  sources: SourceRef[];
  confidenceHistogram: ConfidenceHistogramBin[];
  anomalyCounters: Record<string, number>;
  gender: { male: number; female: number; unknown: number };
  /** Share of relationship intervals resting on a single temporal code (§16 Panel 5). */
  temporalCodeShare: { code: string; share: number };
  /** People with no recorded relationship (§16 Panel 4). */
  noRelationshipPct: number;
  /** People carrying a birth year before the advertised 1500 slice (§16 Panel 2). */
  preSliceBirths?: { count: number; share: number; earliestYear: number };
  /** Confidence thresholds as measured slices (§16 Panel 3). */
  thresholdShares?: { atLeast60: number; atLeast90: number };
  /** Temporal span as measured (§16 Panel 2). */
  span?: { earliestYear: number; latestYear: number; advertised: string };
}

export interface ImportRun {
  id: string;
  source: string;
  sourceVersion: string;
  startedAt: string;
  finishedAt: string | null;
  received: number;
  inserted: number;
  duplicates: number;
  superseded: number;
  rejected: number;
}

export interface MetaSource extends SourceRef {
  recordCount: number;
}

// ---- envelope (BUILD-CONTRACT §2) ----

export interface Page {
  cursor: string | null;
  limit: number;
  nextCursor?: string;
}

export interface Meta {
  requestId: string;
  page?: Page;
  asOfSystem?: string;
  /** Network endpoint returns the three counts here (Q-3). */
  counts?: NetworkCounts;
  truncated?: boolean;
  /** Every aggregate declares its status filter (W3). */
  statusFilter?: string;
  [k: string]: unknown;
}

export interface Envelope<T> {
  data: T;
  meta: Meta;
}

/** application/problem+json (BUILD-CONTRACT §2). */
export interface ProblemJson {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  requestId: string;
}
