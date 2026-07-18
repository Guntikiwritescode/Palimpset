-- PALIMPSEST — Migration V2 (additive; the only schema change this architecture requires).
-- ARCHITECTURE §6.3. No destructive change. Every materialization here is
-- reconstructible from the V1 base tables by `rebuild-projections`.

BEGIN;

-- Trigram search over display names + aliases (§5.1). Trusted extension; the
-- db-owning migrate role may install it.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- import_run — engine-recorded ingest history (Flow A). Powers /runs and the
-- honesty page provenance strip.
-- ---------------------------------------------------------------------------
CREATE TABLE import_run (
    run_id      text        PRIMARY KEY,
    source_slug text        NOT NULL,
    started_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    batches     integer     NOT NULL DEFAULT 0,
    received    integer     NOT NULL DEFAULT 0,
    inserted    integer     NOT NULL DEFAULT 0,
    duplicates  integer     NOT NULL DEFAULT 0,
    superseded  integer     NOT NULL DEFAULT 0,
    rejected    integer     NOT NULL DEFAULT 0,
    manifest    jsonb
);

-- Soft link from claim.import_run_id (declared in V1) to the run that produced it.
ALTER TABLE claim
    ADD CONSTRAINT claim_import_run_fk
    FOREIGN KEY (import_run_id) REFERENCES import_run (run_id)
    DEFERRABLE INITIALLY DEFERRED;

-- ---------------------------------------------------------------------------
-- outbox — the transactional change feed (ADR-006). Written in the same
-- transaction as every base-table write; drained in order by the projector.
-- ---------------------------------------------------------------------------
CREATE TABLE outbox (
    event_seq    bigserial PRIMARY KEY,
    aggregate    text        NOT NULL CHECK (aggregate IN ('claim', 'entity', 'source')),
    aggregate_id text        NOT NULL,
    kind         text        NOT NULL,
    payload      jsonb,
    created_at   timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz
);
CREATE INDEX outbox_unprocessed_idx ON outbox (event_seq) WHERE processed_at IS NULL;

-- ---------------------------------------------------------------------------
-- entity_summary — projector-maintained read model for search/results/network
-- counterparts. Display name by the deterministic rule (§17.F1), NOT "highest
-- confidence" (all name claims are unscored). Degree split scored/unscored.
-- ---------------------------------------------------------------------------
CREATE TABLE entity_summary (
    entity_id      bigint  PRIMARY KEY REFERENCES entity (id),
    display_name   text    NOT NULL,
    name_claim_id  bigint  REFERENCES claim (id),
    description    text,
    gender         text,
    born_earliest  date,
    born_latest    date,
    died_earliest  date,
    died_latest    date,
    entity_type    text    NOT NULL,
    degree_scored   integer NOT NULL DEFAULT 0,
    degree_unscored integer NOT NULL DEFAULT 0,
    updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX entity_summary_display_trgm ON entity_summary USING gin (display_name gin_trgm_ops);
CREATE INDEX entity_summary_type_idx     ON entity_summary (entity_type);
CREATE INDEX entity_summary_degree_idx   ON entity_summary (degree_scored);

-- Alias / all-names search surface (§5.1 "alias index"; §17.2 S6 — 23.4% of
-- people carry alternate names not in their display name). One row per name
-- claim; projector-maintained.
CREATE TABLE entity_name_search (
    claim_id   bigint PRIMARY KEY REFERENCES claim (id),
    entity_id  bigint NOT NULL REFERENCES entity (id),
    name       text   NOT NULL,
    name_kind  text   NOT NULL DEFAULT 'primary'   -- 'primary' | 'alias'
);
CREATE INDEX entity_name_search_entity_idx ON entity_name_search (entity_id);
CREATE INDEX entity_name_search_trgm       ON entity_name_search USING gin (name gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- claim_confidence_current — materialized effective-confidence (I7). Recomputed
-- on adjust events and calibration activation; written at import with origin
-- 'source'. The network/slider query filters on confidence_point here so the
-- effective-confidence resolution lives in exactly one place.
-- ---------------------------------------------------------------------------
CREATE TABLE claim_confidence_current (
    claim_id           bigint  PRIMARY KEY REFERENCES claim (id),
    confidence         jsonb   NOT NULL,
    confidence_point   real,                       -- NULL ⇒ unscored (I5); never passes a threshold filter
    origin             text    NOT NULL CHECK (origin IN ('manual', 'calibration', 'source')),
    calibration_run_id text,
    updated_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX claim_confidence_point_idx ON claim_confidence_current (confidence_point)
    WHERE confidence_point IS NOT NULL;

-- ---------------------------------------------------------------------------
-- calibration_run / claim_calibration — Phase-3 calibration versioning (§6.2).
-- ≤1 active run enforced by a partial unique index.
-- ---------------------------------------------------------------------------
CREATE TABLE calibration_run (
    run_id     text        PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    method     text        NOT NULL,
    params     jsonb,
    metrics    jsonb,
    active     boolean     NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX calibration_run_one_active ON calibration_run ((active)) WHERE active;

CREATE TABLE claim_calibration (
    run_id       text   NOT NULL REFERENCES calibration_run (run_id),
    claim_id     bigint NOT NULL REFERENCES claim (id),
    point        real,
    distribution jsonb,
    PRIMARY KEY (run_id, claim_id)
);

-- ---------------------------------------------------------------------------
-- source_record.supersedes_record_id — upstream record changed between harvests
-- (Flow A step 6c).
-- ---------------------------------------------------------------------------
ALTER TABLE source_record
    ADD COLUMN supersedes_record_id bigint REFERENCES source_record (id);

-- ---------------------------------------------------------------------------
-- er_candidate — the adjudication queue backing store (Flow F, P2).
-- ---------------------------------------------------------------------------
CREATE TABLE er_candidate (
    pair_id          bigserial PRIMARY KEY,
    entity_a         bigint NOT NULL REFERENCES entity (id),
    entity_b         bigint NOT NULL REFERENCES entity (id),
    score            real   NOT NULL,
    features         jsonb,
    state            text   NOT NULL DEFAULT 'queued' CHECK (state IN ('queued', 'accepted', 'rejected')),
    same_as_claim_id bigint REFERENCES claim (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    decided_at       timestamptz,
    UNIQUE (entity_a, entity_b)
);
CREATE INDEX er_candidate_state_idx ON er_candidate (state);

-- ---------------------------------------------------------------------------
-- Partial / supporting indexes for the network and as-of paths.
-- ---------------------------------------------------------------------------
-- Entity-ranged claims are the ones the ego-network walks.
CREATE INDEX claim_entity_object_pred_idx ON claim (subject_entity_id, predicate)
    WHERE object_entity_id IS NOT NULL;
CREATE INDEX claim_object_entity_pred_idx ON claim (object_entity_id, predicate)
    WHERE object_entity_id IS NOT NULL;

COMMIT;
