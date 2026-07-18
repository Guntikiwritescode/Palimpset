# BUILD-CONTRACT — the pinned interfaces every component programs against

Internal coordination doc for this build. It exists so independently-built
components (engine, pipeline, SDK, explorer, deploy) integrate. It restates
decisions already fixed by `contracts/` and `ARCHITECTURE.md`; where this doc and
`ARCHITECTURE.md` disagree, ARCHITECTURE wins — file a note, don't diverge.

Not a design document. Design lives in `docs/ARCHITECTURE.md`. This is the wire.

---

## 0. Fixed toolchain / naming

| Thing | Value |
|---|---|
| Engine build tool | **Maven** (`services/engine/pom.xml`), Spring Boot 3.3.x, Java 21 |
| Engine base package | `dev.palimpsest.engine` |
| Pipeline | Python 3.12, package `palimpsest_pipeline`, deps: `jsonschema`, `requests`, `pytest` |
| SDK | `openapi-typescript` (types) + `openapi-fetch` (runtime) → `sdk/typescript/` |
| Explorer | React 18 + TS + Vite; graph via `react-force-graph-2d` (canvas, enter/exit anim) |
| DB (local) | `palimpsest` on `127.0.0.1:5432`; roles `engine_rw`/`analytics_ro`/`migrate` |
| API base | `/api/v1`, JSON, bearer auth |

Local JDBC: `jdbc:postgresql://127.0.0.1:5432/palimpsest`, user `engine_rw`, pw `engine_pw`
(dev only; real secret is a k8s Secret). Migrations run as `migrate`/`migrate_pw`.

---

## 1. Interchange (pipeline → engine)

Governed by `contracts/claim.schema.json` v0.1.0. Two line kinds:

- **entities** (`kind=entities`): validate against `#/$defs/entityRecord`.
  `{ "schema_version":"0.1.0", "ref":{"authority","id"}, "entity_type", "external_ids":[...] }`
- **claims** (`kind=claims`): validate against the root claim schema.

Import is `POST /api/v1/import/batches?kind=entities|claims`, body `application/x-ndjson`,
batches ≤ 5000 lines, headers `X-Palimpsest-Run`, `X-Palimpsest-Source`, `Authorization: Bearer`.
Response `202`: `{ "run","batch","received","inserted","duplicates","superseded","rejected":[{"line","reason"}] }`.

---

## 2. Response envelope + errors

Success: `{ "data": <payload>, "meta": { "requestId": str, "page"?: {"cursor","limit","nextCursor"?}, "asOfSystem"?: str } }`

Error: `application/problem+json` — `{ "type","title","status","detail","instance","requestId" }`.

Cursor pagination: opaque `cursor` over a stable sort key; default `limit` 50, max 500.

---

## 3. DTOs (camelCase JSON)

```
EntitySummary {
  id: number, displayName: string, entityType: string,
  description: string|null, gender: string|null,
  lifeDates: { bornEarliest, bornLatest, diedEarliest, diedLatest } (each "YYYY-MM-DD"|null),
  degreeScored: number, degreeUnscored: number
}

FuzzyInterval {
  startEarliest, startLatest, endEarliest, endLatest: (string "YYYY-MM-DD"|null),
  approximate: boolean, original: object|null
}

Confidence {                       // effective confidence, resolved by I7
  effective: number|null,          // null ⇒ unscored (never 0)
  origin: "source_native"|"calibration"|"manual"|"inferred"|null,
  raw: any, calibrated: boolean, scored: boolean
}
// band label ("very_strong" ≥.90 · "strong" .70–.89 · "moderate" .40–.69 ·
// "weak" .20–.39 · "very_weak" <.20 · "unscored") is derived in the explorer.

Edge {
  claimId: number, counterpart: EntitySummary, predicate: string,
  confidence: Confidence, validTime: FuzzyInterval,
  scored: boolean, certainlyActive: boolean, undated: boolean, status: string
}

ClaimDetail {
  id, subject: EntitySummary, predicate, object: {entity?:EntitySummary, literal?:{kind,value,authority?}},
  validTime: FuzzyInterval, confidence: Confidence,
  method: string, methodDetail: object|null, status: string,
  assertedBy: {slug,kind,displayName}, recordedAt: string
}

Support { source: {slug,title,version,retrievalUri,license,licenseConfirmed},
          record: {recordKind,externalId,contentHash,raw} }

Evidence { claim: ClaimDetail, support: Support[] }   // Flow C
```

---

## 4. Read endpoints (WP1) — exact shapes

| Method & path | data payload | notes |
|---|---|---|
| `GET /entities/{id}?resolution=raw\|canonical` | `{ summary: EntitySummary, externalIds:[{authority,externalId}], attributeClaims: {predicate, claims: ClaimDetail[]}[], coverage: {bySource:[{slug,relationshipClaims,attributeClaims}], scored, unscored, calibration:"uncalibrated"} }` | competing claims = >1 in a predicate group |
| `GET /entities/lookup?authority&externalId` | `EntitySummary` | 404 if unknown (Q-4: fixtures resolve here) |
| `GET /entities/{id}/network?minConfidence&windowStart&windowEnd&temporalMode&includeUnscored&limit` | `{ focus: EntitySummary, edges: Edge[] }` | **meta.counts = {possibly,certainly,undated}** (Q-3); meta.truncated |
| `GET /entities/{id}/claims?predicate&role&status&minConfidence&includeUnscored&windowStart&windowEnd&temporalMode&cursor&limit` | `ClaimDetail[]` | general filtered listing |
| `GET /entities/{a}/relations/{b}?status` | `{ a: EntitySummary, b: EntitySummary, claims: ClaimDetail[] }` | pair dossier; competing/disputed grouped |
| `GET /entities/random?type&minScoredDegree` | `EntitySummary` | Explore someone |
| `GET /claims/{id}` | `ClaimDetail` | |
| `GET /claims/{id}/evidence` | `Evidence` | Flow C; includes license flags + raw |
| `GET /claims/{id}/history` | `{events:[{eventType,actor:{slug,kind,displayName},occurredAt,payload}]}` | audit trail |
| `GET /search/entities?q&type&cursor&limit` | `EntitySummary[]` | trigram over display_name + aliases; q ≥ 2 chars |
| `GET /meta/relation-types` `/meta/sources` `/meta/agents` | registries | sources carry license,licenseConfirmed |
| `GET /stats/summary` | `{ entities:{total,byType}, claims:{total,byPredicate}, sourceRecords, sources:[...], confidenceHistogram:[{band,count}], anomalyCounters:{...}, gender:{male,female,unknown}, temporalCodeShare, noRelationshipPct }` | honesty page; all LIVE |
| `GET /runs` `/runs/{id}` | import-run history | |
| `GET /healthz` `/readyz` | probes | ready = DB reachable + migrations current |

`temporalMode` ∈ {possibly, certainly}. Default status filter excludes disputed+superseded
(W3) and `meta` states the filter. `minConfidence` never lets unscored through (I5).

Network semantics (§13.4): the endpoint returns edges passing (window, possibly, includeUnscored)
with each edge's `effectiveConfidence`, `certainlyActive`, `undated`, up to `limit` (cap 500,
ordered by effective confidence desc). The explorer holds this set and filters by confidence
**client-side** for the instant slider; it recomputes the 3 counts locally as it drags. So
`minConfidence` on the first fetch should be 0 with `includeUnscored=true` for the slider surface.

---

## 5. Write endpoints (WP6/WP7) — shapes

`POST /actions/dispute {claimId, ground:"existence"|"dating"|"identity"|"confidence"|"source-reading", reason}`
(identity ⇒ routes to ER queue, W1) · `/actions/undispute` · `/actions/assert-claim` (interchange claim) ·
`/actions/supersede {claimId, replacement:<interchange claim>, reason}` ·
`/actions/adjust-confidence {claimId, confidence:<confidence obj>, reason}` ·
`/actions/merge-entities {memberEntityIds:[...], rationale}`.
`GET /er/queue` · `GET /paths?from&to&minConfidence&windowStart&windowEnd&temporalMode&maxDepth≤4&maxPaths≤5`
· `GET /events?since&cursor&limit` · `GET /export/claims?...&format=ndjson|csv` (**403 refusing license_confirmed=false**).
Any action from an `asOfSystem` context ⇒ 409 (W8).

---

## 6. Rendering grammar semantics the explorer MUST honor (§13.3, §5 defect register)

- Confidence chip = number + band label + origin, never a number alone (PP2).
- Slider **defaults to 0** (§17.F2); the demo's 0.6 is a pre-set URL. All 4 controls live in the URL (PP5).
- **Certainty is a highlight within the graph, not a filter mode** (D2/A2). certainly-active edges emphasized; count stays in the result line permanently.
- **Unscored toggle is scoped to the claims list** (D4/A3); dashed-edge grammar implemented but UNWIRED on the canvas (Q-2), with a test that it renders for synthetic unscored edges.
- Result line states possibly / certainly / undated simultaneously (D5/A7): "29 ties not ruled out in 1600 · 2 required by the evidence · 3 carry no dates at all".
- Life dates render with an **unscored chip + derived-date marker**, never bare (F3/D6). Circa ⇒ `approximate` marker, **no ±window** (D6).
- Search rows show a **scored** tie count only (D8). Two same-named entities distinguishable by life dates (J2).
- Honesty page derives **every** figure live — a grep for hard-coded statistics fails WP4 (§16). Omit the retracted precision figure (D1/Q-1).
- Empty state says *why*; absence looks like absence (PP4). Every rendered fact ≤ 2 clicks from its `source_record` (PP1). License-unconfirmed content carries the amber badge (PP6).

---

## 7. Fixtures

- CI/`make demo` fixture is **synthetic** (`fixtures/synthetic/`), never source-derived (A5). Reproduces shape + anomaly classes, not the measured numbers.
- FIX-* measured assertions (FIX-CORPUS 15,882/261,177/187,482; FIX-ANOMALY 365/14/1,575/1/6; FIX-BACON) run only in the **slow suite** against a locally-supplied dump (Q-6).
- Fixtures resolve by SDFB external id via `/entities/lookup?authority=sdfb&externalId=…` (Q-4); internal id 15429 is illustrative prose only.
