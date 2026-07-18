-- PALIMPSEST — Claim store schema (storage contract)
-- Version: 0.1.0  · Target: PostgreSQL 16
--
-- This file is a FROZEN CONTRACT (ARCHITECTURE §2, HANDOFF §4 rule 1).
-- It is applied verbatim as Flyway migration V1. No edits without the §2
-- change-control process and owner sign-off. Any further schema change is an
-- additive, numbered migration (V2 lives in the engine's migration resources).
--
-- PROVENANCE NOTE (deviation DEV-001, docs/DECISIONS.md): the Phase-0 artifact
-- described by the specification was not available in this repository or the
-- handoff package. This file was authored fresh, faithful to ARCHITECTURE §2,
-- §3.3 (invariants I1–I8), §6.1, and §13.3. It is therefore NOT a byte-identical
-- restoration of a prior Phase-0 file. Treat this as the contract of record for
-- this build; if the original Phase-0 artifact surfaces, reconcile via §2.
--
-- Non-negotiable properties enforced here (ARCHITECTURE §2, §3.3):
--   * `claim` rows are immutable (I1) — enforced by a trigger, not convention.
--   * `claim_event` is append-only and is the system-time history (I2).
--   * `claim_status_current` is a materialized fold of events, never
--     independently authoritative (I3).
--   * every claim has >= 1 `claim_support` row to a `source_record` carrying the
--     verbatim upstream payload (I4).
--   * unscored confidence is absence (confidence_point NULL), never 0 (I5).
--   * `source.license_confirmed` defaults false and gates publication (I8).
--   * `entity_external_id` holds only identity-bearing 1:1 authority anchors;
--     many-to-one cross-references (e.g. ODNB) are `has-external-id` *claims*.

BEGIN;

-- ---------------------------------------------------------------------------
-- Agents — who asserts. One human token, distinct pipeline/model tokens (ADR-005).
-- ---------------------------------------------------------------------------
CREATE TABLE agent (
    id           bigserial PRIMARY KEY,
    kind         text        NOT NULL CHECK (kind IN ('human', 'pipeline', 'model')),
    slug         text        NOT NULL UNIQUE,
    display_name text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE  agent      IS 'Attributed actors: the scholar (human), the pipeline, and models. Every claim_event names one.';

-- ---------------------------------------------------------------------------
-- Sources — datasets/documents. license_confirmed gates publication (I8).
-- ---------------------------------------------------------------------------
CREATE TABLE source (
    id                bigserial PRIMARY KEY,
    slug              text        NOT NULL UNIQUE,
    title             text        NOT NULL,
    version           text        NOT NULL,
    retrieval_uri     text,
    license           text,
    license_confirmed boolean     NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT now()
);
COMMENT ON COLUMN source.license_confirmed IS 'Defaults false. false ⇒ internal-use-only; the /export gate and UI badges key off this (I8, PP6).';

-- ---------------------------------------------------------------------------
-- Source records — the verbatim upstream payload behind every claim.
-- Uniqueness on (source_id, record_kind, external_id); content_hash detects
-- duplicate vs superseding lines on re-ingest (Flow A step 6c).
-- ---------------------------------------------------------------------------
CREATE TABLE source_record (
    id           bigserial PRIMARY KEY,
    source_id    bigint      NOT NULL REFERENCES source (id),
    record_kind  text        NOT NULL,
    external_id  text        NOT NULL,
    content_hash text        NOT NULL,
    raw          jsonb       NOT NULL,
    retrieved_at timestamptz NOT NULL DEFAULT now(),
    -- Identity includes content_hash so an unchanged re-harvest is a duplicate
    -- (same 4-tuple) while a changed upstream record is a NEW version of the same
    -- (source, kind, external_id) linked by supersedes_record_id (Flow A 6c, V2).
    UNIQUE (source_id, record_kind, external_id, content_hash)
);
COMMENT ON COLUMN source_record.raw IS 'Verbatim upstream payload. Nothing downstream may destroy or hide it (Flow C).';

-- ---------------------------------------------------------------------------
-- Entities — created ONLY by explicit entity records or explicit action.
-- The engine never fabricates an entity as a side effect of a relation (I6).
-- ---------------------------------------------------------------------------
CREATE TABLE entity (
    id          bigserial PRIMARY KEY,
    entity_type text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- Identity-bearing 1:1 authority anchors only (for SDFB data, only 'sdfb').
CREATE TABLE entity_external_id (
    authority   text   NOT NULL,
    external_id text   NOT NULL,
    entity_id   bigint NOT NULL REFERENCES entity (id),
    PRIMARY KEY (authority, external_id),
    UNIQUE (entity_id, authority)
);
COMMENT ON TABLE entity_external_id IS 'Identity-bearing 1:1 anchors. Many-to-one refs (ODNB) are has-external-id CLAIMS, not rows here.';

-- ---------------------------------------------------------------------------
-- Relation types — the controlled vocabulary of predicates.
-- range_kind='entity' predicates are the ones the ego-network query walks.
-- ---------------------------------------------------------------------------
CREATE TABLE relation_type (
    slug       text        PRIMARY KEY,
    label      text        NOT NULL,
    category   text        NOT NULL CHECK (category IN ('relationship', 'attribute')),
    range_kind text        NOT NULL CHECK (range_kind IN ('entity', 'literal')),
    is_symmetric boolean   NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON COLUMN relation_type.range_kind IS 'entity ⇒ object is an entity (walked by /network); literal ⇒ object is a value (attribute claim).';

-- ---------------------------------------------------------------------------
-- Claim — the atomic, immutable assertion (ARCHITECTURE §5, §13.3).
--
-- Object is exactly one of: an entity ref, or a literal value.
-- Valid time is the four-date fuzzy model; the source's native encoding is
-- preserved verbatim under valid_time_original and is never destroyed (§13.3).
-- Confidence is either source-native scalar in [0,1], or unscored (NULL point).
-- ---------------------------------------------------------------------------
CREATE TABLE claim (
    id                    bigserial PRIMARY KEY,

    subject_entity_id     bigint NOT NULL REFERENCES entity (id),
    predicate             text   NOT NULL REFERENCES relation_type (slug),
    object_entity_id      bigint REFERENCES entity (id),
    object_literal        jsonb,

    -- Four-date fuzzy valid-time model. Any bound may be NULL (unknown).
    valid_start_earliest  date,
    valid_start_latest    date,
    valid_end_earliest    date,
    valid_end_latest      date,
    valid_approximate     boolean NOT NULL DEFAULT false,   -- circa marker (no invented ±window; §17.2 S3)
    valid_time_original   jsonb,                             -- verbatim native encoding (e.g. {"type_code":"AF/IN","year":"1561"})

    -- Confidence: source-native scalar OR unscored (absence, never zero; I5).
    confidence_kind       text    NOT NULL CHECK (confidence_kind IN ('source_native_scalar', 'unscored')),
    confidence_point      real    CHECK (confidence_point IS NULL OR (confidence_point >= 0 AND confidence_point <= 1)),
    confidence_scale      text,                              -- e.g. 'sdfb_max_certainty_0_100'
    confidence_raw        jsonb,                             -- e.g. 97
    confidence_calibrated boolean NOT NULL DEFAULT false,

    method                text    NOT NULL CHECK (method IN ('manual', 'imported', 'extracted', 'inferred')),
    method_detail         jsonb,

    asserted_by_agent_id  bigint  NOT NULL REFERENCES agent (id),
    import_run_id         text,                              -- FK added in V2 (import_run)

    recorded_at           timestamptz NOT NULL DEFAULT now(),-- system-time lower bound

    -- Object is exactly one of entity ref / literal.
    CONSTRAINT claim_object_exactly_one
        CHECK ((object_entity_id IS NOT NULL) <> (object_literal IS NOT NULL)),

    -- I5: unscored ⇒ no point, never calibrated.
    CONSTRAINT claim_unscored_is_absence
        CHECK (confidence_kind <> 'unscored'
               OR (confidence_point IS NULL AND confidence_calibrated = false)),

    -- Scored ⇒ a point exists.
    CONSTRAINT claim_scored_has_point
        CHECK (confidence_kind <> 'source_native_scalar' OR confidence_point IS NOT NULL),

    -- Fuzzy-time ordering sanity within each known pair (inverted bounds are
    -- dropped upstream by the adapter; this is a store-level backstop).
    CONSTRAINT claim_valid_start_ordered
        CHECK (valid_start_earliest IS NULL OR valid_start_latest IS NULL
               OR valid_start_earliest <= valid_start_latest),
    CONSTRAINT claim_valid_end_ordered
        CHECK (valid_end_earliest IS NULL OR valid_end_latest IS NULL
               OR valid_end_earliest <= valid_end_latest)
);
COMMENT ON TABLE claim IS 'Immutable (I1). Two sources that disagree produce two claims — never a silent merge (PP3).';

CREATE INDEX claim_subject_idx   ON claim (subject_entity_id);
CREATE INDEX claim_object_idx    ON claim (object_entity_id) WHERE object_entity_id IS NOT NULL;
CREATE INDEX claim_predicate_idx ON claim (predicate);

-- I1: claims are never updated or deleted. Enforced in the store, not by hope.
CREATE OR REPLACE FUNCTION claim_is_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'claim rows are immutable (I1): % on claim is forbidden', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER claim_no_update BEFORE UPDATE ON claim
    FOR EACH ROW EXECUTE FUNCTION claim_is_immutable();
CREATE TRIGGER claim_no_delete BEFORE DELETE ON claim
    FOR EACH ROW EXECUTE FUNCTION claim_is_immutable();

-- ---------------------------------------------------------------------------
-- Claim support — every claim links to >= 1 source_record (I4). Enforced at
-- creation time by the engine; the store guarantees the FK integrity.
-- ---------------------------------------------------------------------------
CREATE TABLE claim_support (
    claim_id         bigint NOT NULL REFERENCES claim (id),
    source_record_id bigint NOT NULL REFERENCES source_record (id),
    PRIMARY KEY (claim_id, source_record_id)
);

-- ---------------------------------------------------------------------------
-- Claim event — append-only system-time history (I2). The status fold and the
-- audit trail derive from this and nothing else.
-- ---------------------------------------------------------------------------
CREATE TABLE claim_event (
    id             bigserial PRIMARY KEY,
    claim_id       bigint      NOT NULL REFERENCES claim (id),
    event_type     text        NOT NULL CHECK (event_type IN
                        ('assert', 'dispute', 'undispute', 'supersede', 'adjust_confidence')),
    actor_agent_id bigint      NOT NULL REFERENCES agent (id),
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    payload        jsonb
);
CREATE INDEX claim_event_claim_idx ON claim_event (claim_id, occurred_at);

CREATE OR REPLACE FUNCTION claim_event_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'claim_event is append-only (I2): % is forbidden', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER claim_event_no_update BEFORE UPDATE ON claim_event
    FOR EACH ROW EXECUTE FUNCTION claim_event_is_append_only();
CREATE TRIGGER claim_event_no_delete BEFORE DELETE ON claim_event
    FOR EACH ROW EXECUTE FUNCTION claim_event_is_append_only();

-- ---------------------------------------------------------------------------
-- Claim status current — the materialized fold of claim_event (I3).
-- The engine recomputes the affected row in the same transaction as the event.
-- Reconstructible at any time from claim_event (rebuild-projections).
-- ---------------------------------------------------------------------------
CREATE TABLE claim_status_current (
    claim_id   bigint      PRIMARY KEY REFERENCES claim (id),
    status     text        NOT NULL CHECK (status IN ('asserted', 'disputed', 'superseded')),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX claim_status_current_status_idx ON claim_status_current (status);

-- ---------------------------------------------------------------------------
-- Entity canonical — derived same-as folding (Flow F). canonical_entity_id is
-- the lowest entity id in the same-as connected component. Default reads are
-- resolution=raw; resolution=canonical folds via this table.
-- ---------------------------------------------------------------------------
CREATE TABLE entity_canonical (
    entity_id           bigint PRIMARY KEY REFERENCES entity (id),
    canonical_entity_id bigint NOT NULL REFERENCES entity (id),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Temporal predicates over the four-date fuzzy model (§13.3, §20 A2/A7).
--
-- An interval has start in [s_e, s_l] and end in [e_e, e_l]; a window is [w0,w1].
--
-- possibly_active: the interval is NOT ruled out during the window — i.e. it
--   could overlap. Unknown bounds cannot rule anything out, so an undated claim
--   (all bounds NULL) is trivially possibly-active (A7 counts these separately).
--
-- certainly_active: the evidence *forces* the tie through the window — it
--   overlaps for every admissible (start,end). This needs an upper bound on the
--   start (s_l) and a lower bound on the end (e_e); with SDFB's dominant AF/IN
--   start + BF/IN end those are absent, so only ~0.18% of claims can qualify
--   (A2 — which is why the UI highlights rather than filters on certainty).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION possibly_active(
    s_e date, s_l date, e_e date, e_l date, w0 date, w1 date
) RETURNS boolean AS $$
    SELECT (s_e IS NULL OR s_e <= w1)   -- earliest possible start not after window end
       AND (e_l IS NULL OR e_l >= w0);  -- latest possible end not before window start
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION certainly_active(
    s_e date, s_l date, e_e date, e_l date, w0 date, w1 date
) RETURNS boolean AS $$
    SELECT s_l IS NOT NULL AND e_e IS NOT NULL   -- both bounding sides must be known
       AND s_l <= w1                              -- certainly started by window end
       AND e_e >= w0;                             -- certainly not ended before window start
$$ LANGUAGE sql IMMUTABLE;

-- True when a claim carries no temporal bounds at all (A7 — counted separately).
CREATE OR REPLACE FUNCTION is_undated(
    s_e date, s_l date, e_e date, e_l date
) RETURNS boolean AS $$
    SELECT s_e IS NULL AND s_l IS NULL AND e_e IS NULL AND e_l IS NULL;
$$ LANGUAGE sql IMMUTABLE;

-- ---------------------------------------------------------------------------
-- Convenience view: asserted claims only (the default read surface).
-- ---------------------------------------------------------------------------
CREATE VIEW v_asserted_claim AS
    SELECT c.*
    FROM claim c
    JOIN claim_status_current s ON s.claim_id = c.id
    WHERE s.status = 'asserted';

COMMIT;
