// SYNTHETIC fixtures for dev + tests. Invented people and ties that reproduce the
// corpus's SHAPE and anomaly classes, never its measured numbers (BUILD-CONTRACT §7,
// §20 A5). No SDFB dump content enters here. Numbers differ deliberately from the
// real corpus so nothing can be mistaken for measured fact.

import type {
  ClaimDetail,
  Confidence,
  Coverage,
  Edge,
  EntityDetail,
  EntitySummary,
  Evidence,
  ExternalIdRef,
  FuzzyInterval,
  ImportRun,
  MetaSource,
  PredicateGroup,
  SourceRef,
  StatsSummary,
} from "../api/types";

export const SDFB_SOURCE: SourceRef = {
  slug: "sdfb",
  title: "Six Degrees of Francis Bacon (synthetic fixture)",
  version: "sdfb-fixture-0001",
  retrievalUri: "http://sixdegreesoffrancisbacon.com/",
  license: "unconfirmed",
  licenseConfirmed: false,
};

// ---- builders ---------------------------------------------------------------

function scored(effective: number): Confidence {
  return {
    effective,
    origin: "source_native",
    raw: Math.round(effective * 100),
    calibrated: false,
    scored: true,
  };
}

function unscored(): Confidence {
  return { effective: null, origin: null, raw: null, calibrated: false, scored: false };
}

function iso(year: number, month = 1, day = 1): string {
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

/**
 * Envelope interval (the 99.7% AF/IN + BF/IN case): we know the tie sits within
 * [start, end] but not exactly when it was active — so it is possibly-active across
 * the envelope and NEVER certainly-active (open inner bounds).
 */
function interval(startYear: number, endYear: number, startCode = "AF/IN", endCode = "BF/IN"): FuzzyInterval {
  return {
    startEarliest: iso(startYear),
    startLatest: null,
    endEarliest: null,
    endLatest: iso(endYear, 12, 31),
    approximate: false,
    original: { start_code: startCode, start_year: String(startYear), end_code: endCode, end_year: String(endYear) },
  };
}

/**
 * Closed interval (the rare fully-constrained case): the tie is KNOWN to span
 * [start, end], so a window inside it is REQUIRED by the evidence → certainly-active.
 */
function closedInterval(startYear: number, endYear: number): FuzzyInterval {
  return {
    startEarliest: iso(startYear),
    startLatest: iso(startYear, 12, 31),
    endEarliest: iso(endYear),
    endLatest: iso(endYear, 12, 31),
    approximate: false,
    original: { start_code: "IN", start_year: String(startYear), end_code: "IN", end_year: String(endYear) },
  };
}

const NO_INTERVAL: FuzzyInterval = {
  startEarliest: null,
  startLatest: null,
  endEarliest: null,
  endLatest: null,
  approximate: false,
  original: {},
};

interface PersonSeed {
  id: number;
  name: string;
  born: number | null;
  died: number | null;
  gender: "male" | "female" | "unknown";
  description: string;
  degreeScored: number;
  bornApprox?: boolean;
  sdfbId?: string;
  odnbId?: string;
  aliases?: string[];
}

const PEOPLE: PersonSeed[] = [
  { id: 1001, name: "Francis Bacon", born: 1561, died: 1626, gender: "male", description: "Philosopher, statesman, and essayist; Lord Chancellor of England.", degreeScored: 12, sdfbId: "10000473", odnbId: "990", aliases: ["Franciscus Baconus", "Viscount St Alban"] },
  { id: 1002, name: "Francis Bacon", born: 1600, died: 1663, gender: "male", description: "Politician and colonial administrator in New England.", degreeScored: 3, sdfbId: "10000475", odnbId: "991" },
  { id: 1003, name: "Anthony Bacon", born: 1558, died: 1601, gender: "male", description: "Intelligencer and elder brother of Francis Bacon.", degreeScored: 5, sdfbId: "10000480" },
  { id: 1004, name: "Robert Devereux", born: 1565, died: 1601, gender: "male", description: "2nd Earl of Essex; soldier and courtier.", degreeScored: 6, sdfbId: "10000490" },
  { id: 1005, name: "Elizabeth I", born: 1533, died: 1603, gender: "female", description: "Queen of England and Ireland.", degreeScored: 9, sdfbId: "10000500" },
  { id: 1006, name: "Ben Jonson", born: 1572, died: 1637, gender: "male", description: "Playwright, poet, and literary critic.", degreeScored: 7, bornApprox: true, sdfbId: "10000510" },
  { id: 1007, name: "Tobie Matthew", born: 1577, died: 1655, gender: "male", description: "Diplomat, writer, and friend of Francis Bacon.", degreeScored: 4, sdfbId: "10000520" },
  { id: 1008, name: "Lancelot Andrewes", born: 1555, died: 1626, gender: "male", description: "Bishop of Winchester and biblical scholar.", degreeScored: 4, sdfbId: "10000530" },
  { id: 1009, name: "Fulke Greville", born: 1554, died: 1628, gender: "male", description: "Poet, dramatist, and statesman.", degreeScored: 5, sdfbId: "10000540" },
  { id: 1010, name: "Meric Casaubon", born: 1599, died: 1671, gender: "male", description: "Classical scholar and editor.", degreeScored: 2, sdfbId: "10000550" },
  { id: 1011, name: "William Rawley", born: 1588, died: 1667, gender: "male", description: "Chaplain and literary executor to Francis Bacon.", degreeScored: 2, sdfbId: "10000560" },
  { id: 1012, name: "Dudley Carleton", born: 1573, died: 1632, gender: "male", description: "Diplomat and letter-writer.", degreeScored: 3, sdfbId: "10000570" },
  { id: 1013, name: "George Villiers", born: 1592, died: 1628, gender: "male", description: "1st Duke of Buckingham; royal favourite.", degreeScored: 4, sdfbId: "10000580" },
  { id: 1014, name: "Thomas Hobbes", born: 1588, died: 1679, gender: "male", description: "Philosopher; sometime amanuensis to Francis Bacon.", degreeScored: 6, sdfbId: "10000590" },
  { id: 1015, name: "Thomas Bodley", born: 1545, died: 1613, gender: "male", description: "Diplomat and founder of the Bodleian Library.", degreeScored: 3, sdfbId: "10000600" },
  { id: 1016, name: "Helen Alexander", born: 1560, died: 1631, gender: "female", description: "Recorded under married and maiden names.", degreeScored: 1, sdfbId: "10000610", aliases: ["Helen Umpherston", "Helen Currie"] },
];

export const entities = new Map<number, EntitySummary>();
for (const p of PEOPLE) {
  entities.set(p.id, {
    id: p.id,
    displayName: p.name,
    entityType: "person",
    description: p.description,
    gender: p.gender,
    lifeDates: {
      bornEarliest: p.born !== null ? iso(p.born) : null,
      bornLatest: p.born !== null ? iso(p.born, 12, 31) : null,
      diedEarliest: p.died !== null ? iso(p.died) : null,
      diedLatest: p.died !== null ? iso(p.died, 12, 31) : null,
    },
    degreeScored: p.degreeScored,
    degreeUnscored: 0,
  });
}

function summary(id: number): EntitySummary {
  const s = entities.get(id);
  if (!s) throw new Error(`no fixture entity ${id}`);
  return s;
}

// ---- relationship edges + their backing claims ------------------------------

interface EdgeSeed {
  to: number;
  eff: number;
  start?: number;
  end?: number;
  undated?: boolean;
  /** Fully-constrained (IN/IN) tie — becomes certainly-active for windows inside it. */
  closed?: boolean;
  status?: string;
}

// Francis Bacon (philosopher) ego network. Confidences are spread across the
// bands so that raising the slider strictly reduces the visible ties (E2E). Two
// edges carry closed intervals so exactly 2 are certainly-active in a 1600 window
// (the FIX-BACON flavour: many not-ruled-out, few required).
const BACON_EDGES: EdgeSeed[] = [
  { to: 1003, eff: 0.97, start: 1580, end: 1601, closed: true },
  { to: 1004, eff: 0.88, start: 1590, end: 1601, closed: true },
  { to: 1009, eff: 0.81, start: 1576, end: 1626 },
  { to: 1005, eff: 0.72, start: 1584, end: 1603 },
  { to: 1008, eff: 0.66, start: 1592, end: 1626 },
  { to: 1006, eff: 0.61, start: 1597, end: 1626 },
  { to: 1007, eff: 0.55, start: 1595, end: 1626 },
  { to: 1010, eff: 0.43, start: 1620, end: 1626 },
  { to: 1012, eff: 0.33, start: 1588, end: 1626 },
  { to: 1013, eff: 0.24, start: 1614, end: 1626 },
  { to: 1014, eff: 0.18, start: 1608, end: 1626 },
  { to: 1015, eff: 0.11, start: 1576, end: 1613 },
  { to: 1011, eff: 0.5, undated: true },
];

const OTHER_BACON_EDGES: EdgeSeed[] = [
  { to: 1013, eff: 0.62, start: 1625, end: 1628 },
  { to: 1014, eff: 0.41, start: 1630, end: 1663 },
  { to: 1012, eff: 0.28, start: 1626, end: 1632 },
];

export const claims = new Map<number, ClaimDetail>();
const networks = new Map<number, Edge[]>();

let claimSeq = 2000;
function nextClaimId(): number {
  claimSeq += 1;
  return claimSeq;
}

function buildEdges(focusId: number, seeds: EdgeSeed[]): Edge[] {
  const edges: Edge[] = [];
  for (const seed of seeds) {
    const claimId = nextClaimId();
    const vt = seed.undated
      ? NO_INTERVAL
      : seed.closed
        ? closedInterval(seed.start!, seed.end!)
        : interval(seed.start!, seed.end!);
    const conf = scored(seed.eff);
    const edge: Edge = {
      claimId,
      counterpart: summary(seed.to),
      predicate: "associated-with",
      confidence: conf,
      validTime: vt,
      scored: true,
      certainlyActive: false, // recomputed per-window by the network handler
      undated: !!seed.undated,
      status: seed.status ?? "asserted",
    };
    edges.push(edge);
    // Backing claim for /claims/{id} + /claims/{id}/evidence + pair dossier.
    claims.set(claimId, {
      id: claimId,
      subject: summary(focusId),
      predicate: "associated-with",
      object: { entity: summary(seed.to) },
      validTime: vt,
      confidence: conf,
      method: "imported",
      methodDetail: { extractor: "poisson-graphical-lasso", note: "synthetic fixture" },
      status: seed.status ?? "asserted",
      assertedBy: { slug: "sdfb", kind: "source", displayName: "Six Degrees of Francis Bacon" },
      recordedAt: "2024-01-15T00:00:00Z",
    });
  }
  return edges;
}

networks.set(1001, buildEdges(1001, BACON_EDGES));
networks.set(1002, buildEdges(1002, OTHER_BACON_EDGES));

export function networkOf(id: number): Edge[] {
  return networks.get(id) ?? [];
}

// ---- attribute claims (per entity) ------------------------------------------

function attrClaim(
  id: number,
  subjectId: number,
  predicate: string,
  literal: { kind: string; value: string },
  conf: Confidence,
  status = "asserted",
  vt: FuzzyInterval = NO_INTERVAL,
  methodDetail: Record<string, unknown> | null = null,
): ClaimDetail {
  const c: ClaimDetail = {
    id,
    subject: summary(subjectId),
    predicate,
    object: { literal: { kind: literal.kind, value: literal.value, authority: "sdfb" } },
    validTime: vt,
    confidence: conf,
    method: "imported",
    methodDetail,
    status,
    assertedBy: { slug: "sdfb", kind: "source", displayName: "Six Degrees of Francis Bacon" },
    recordedAt: "2024-01-15T00:00:00Z",
  };
  claims.set(id, c);
  return c;
}

function attributeGroupsFor(p: PersonSeed): PredicateGroup[] {
  const groups: PredicateGroup[] = [];
  const names: ClaimDetail[] = [attrClaim(nextClaimId(), p.id, "has-name", { kind: "primary", value: p.name }, unscored())];
  for (const alias of p.aliases ?? []) {
    names.push(attrClaim(nextClaimId(), p.id, "has-name", { kind: "alias", value: alias }, unscored()));
  }
  groups.push({ predicate: "has-name", claims: names });

  groups.push({
    predicate: "has-description",
    claims: [attrClaim(nextClaimId(), p.id, "has-description", { kind: "text", value: p.description }, unscored())],
  });

  if (p.born !== null) {
    groups.push({
      predicate: "born",
      claims: [
        attrClaim(
          nextClaimId(),
          p.id,
          "born",
          { kind: "date", value: p.bornApprox ? `c. ${p.born}` : String(p.born) },
          unscored(),
          "asserted",
          {
            startEarliest: iso(p.born),
            startLatest: iso(p.born, 12, 31),
            endEarliest: iso(p.born),
            endLatest: iso(p.born, 12, 31),
            approximate: !!p.bornApprox,
            original: { type_code: p.bornApprox ? "CA/IN" : "IN", year: String(p.born) },
          },
          { note: "life dates are unscored claims (F3)" },
        ),
      ],
    });
  }
  if (p.died !== null) {
    groups.push({
      predicate: "died",
      claims: [
        attrClaim(
          nextClaimId(),
          p.id,
          "died",
          { kind: "date", value: String(p.died) },
          unscored(),
          "asserted",
          {
            startEarliest: iso(p.died),
            startLatest: iso(p.died, 12, 31),
            endEarliest: iso(p.died),
            endLatest: iso(p.died, 12, 31),
            approximate: false,
            original: { type_code: "IN", year: String(p.died) },
          },
        ),
      ],
    });
  }
  return groups;
}

// Francis Bacon (philosopher) gets a competing-claims example and status variety.
const BACON_OCCUPATIONS: PredicateGroup = {
  predicate: "has-occupation",
  claims: [
    attrClaim(nextClaimId(), 1001, "has-occupation", { kind: "role", value: "Lord Chancellor of England" }, unscored()),
    attrClaim(nextClaimId(), 1001, "has-occupation", { kind: "role", value: "Natural philosopher" }, unscored()),
    attrClaim(nextClaimId(), 1001, "has-occupation", { kind: "role", value: "Alchemist" }, unscored(), "disputed"),
  ],
};

const entityDetails = new Map<number, EntityDetail>();
for (const p of PEOPLE) {
  const externalIds: ExternalIdRef[] = [];
  if (p.sdfbId) externalIds.push({ authority: "sdfb", externalId: p.sdfbId });
  if (p.odnbId) externalIds.push({ authority: "odnb", externalId: p.odnbId });

  const groups = attributeGroupsFor(p);
  if (p.id === 1001) groups.push(BACON_OCCUPATIONS);

  const relCount = networkOf(p.id).length || p.degreeScored;
  const attrCount = groups.reduce((n, g) => n + g.claims.length, 0);
  const unscoredCount = groups.reduce(
    (n, g) => n + g.claims.filter((c) => !c.confidence.scored).length,
    0,
  );
  const coverage: Coverage = {
    bySource: [{ slug: "sdfb", relationshipClaims: relCount, attributeClaims: attrCount }],
    scored: relCount,
    unscored: unscoredCount,
    calibration: "uncalibrated",
  };
  entityDetails.set(p.id, {
    summary: summary(p.id),
    externalIds,
    attributeClaims: groups,
    coverage,
  });
}

export function entityDetailOf(id: number): EntityDetail | undefined {
  return entityDetails.get(id);
}

export function lookupByExternalId(authority: string, externalId: string): EntitySummary | undefined {
  for (const p of PEOPLE) {
    if (authority === "sdfb" && p.sdfbId === externalId) return summary(p.id);
    if (authority === "odnb" && p.odnbId === externalId) return summary(p.id);
  }
  return undefined;
}

// ---- search (display name + aliases; q ≥ 2) ---------------------------------

const searchIndex: { entity: EntitySummary; terms: string[] }[] = PEOPLE.map((p) => ({
  entity: summary(p.id),
  terms: [p.name, ...(p.aliases ?? [])].map((t) => t.toLowerCase()),
}));

export function searchEntities(q: string, limit = 25): EntitySummary[] {
  const needle = q.trim().toLowerCase();
  if (needle.length < 2) return [];
  return searchIndex
    .filter((row) => row.terms.some((t) => t.includes(needle)))
    .map((row) => row.entity)
    .slice(0, limit);
}

export function randomEntity(minScoredDegree = 5): EntitySummary {
  const pool = PEOPLE.filter((p) => p.degreeScored >= minScoredDegree);
  const chosen = pool[Math.floor(Math.random() * pool.length)] ?? PEOPLE[0];
  return summary(chosen.id);
}

// ---- evidence (Flow C) ------------------------------------------------------

export function evidenceFor(claimId: number): Evidence | undefined {
  const claim = claims.get(claimId);
  if (!claim) return undefined;
  const objLabel = claim.object.entity?.displayName ?? claim.object.literal?.value ?? "";
  return {
    claim,
    support: [
      {
        source: SDFB_SOURCE,
        record: {
          recordKind: claim.predicate === "associated-with" ? "relationship" : "attribute",
          externalId: `rec-${claimId}`,
          contentHash: `sha256:${(claimId * 2654435761).toString(16)}`,
          raw: {
            subject: claim.subject.displayName,
            predicate: claim.predicate,
            object: objLabel,
            certainty: claim.confidence.raw,
            source_note: "SYNTHETIC fixture record — reproduces shape, not measured data",
          },
        },
      },
    ],
  };
}

export function pairClaims(a: number, b: number): ClaimDetail[] {
  const out: ClaimDetail[] = [];
  for (const c of claims.values()) {
    if (c.object.entity && ((c.subject.id === a && c.object.entity.id === b) || (c.subject.id === b && c.object.entity.id === a))) {
      out.push(c);
    }
  }
  return out;
}

// ---- corpus-level stats (all LIVE-derived on the honesty page) --------------
// Deliberately synthetic values, distinct from the measured corpus.

const genderTally = PEOPLE.reduce(
  (acc, p) => {
    acc[p.gender] += 1;
    return acc;
  },
  { male: 0, female: 0, unknown: 0 } as Record<string, number>,
);

const byPredicate: Record<string, number> = {
  "associated-with": networkOf(1001).length + networkOf(1002).length,
  "has-name": 0,
  "has-description": 0,
  born: 0,
  died: 0,
  "has-occupation": 0,
};
for (const c of claims.values()) {
  if (c.predicate in byPredicate && c.predicate !== "associated-with") {
    byPredicate[c.predicate] += 1;
  }
}
const claimsTotal = Object.values(byPredicate).reduce((a, b) => a + b, 0);

export const stats: StatsSummary = {
  entities: { total: PEOPLE.length, byType: { person: PEOPLE.length } },
  claims: { total: claimsTotal, byPredicate },
  sourceRecords: claimsTotal,
  sources: [SDFB_SOURCE],
  confidenceHistogram: [
    { band: "unscored", count: 24 },
    { band: "very_weak", count: 4 },
    { band: "weak", count: 5 },
    { band: "moderate", count: 6 },
    { band: "strong", count: 4 },
    { band: "very_strong", count: 2 },
  ],
  anomalyCounters: {
    odnbSentinels: 3,
    sharedOdnbIds: 1,
    invertedDateRanges: 4,
    danglingEdges: 1,
    unparseableYears: 2,
  },
  gender: { male: genderTally.male, female: genderTally.female, unknown: genderTally.unknown },
  temporalCodeShare: { code: "AF/IN", share: 0.98 },
  noRelationshipPct: 0.08,
  thresholdShares: { atLeast60: 0.21, atLeast90: 0.07 },
  span: { earliestYear: 1478, latestYear: 1699, advertised: "1500–1700" },
  preSliceBirths: { count: 2, share: 0.11, earliestYear: 1478 },
};

export const runs: ImportRun[] = [
  {
    id: "run-0001",
    source: "sdfb",
    sourceVersion: "sdfb-fixture-0001",
    startedAt: "2024-01-15T00:00:00Z",
    finishedAt: "2024-01-15T00:03:12Z",
    received: claimsTotal + 5,
    inserted: claimsTotal,
    duplicates: 3,
    superseded: 0,
    rejected: 2,
  },
];

export const metaSources: MetaSource[] = [{ ...SDFB_SOURCE, recordCount: claimsTotal }];

export const FIXTURE_IDS = {
  baconPhilosopher: 1001,
  baconOther: 1002,
  aliasPerson: 1016,
};
