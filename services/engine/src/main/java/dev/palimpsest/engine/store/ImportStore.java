package dev.palimpsest.engine.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.importer.ImportModels.ParsedClaim;
import dev.palimpsest.engine.importer.ImportModels.Ref;
import dev.palimpsest.engine.importer.ImportModels.Support;
import java.sql.Date;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Low-level write helpers used inside the importer/action transactions. Every
 * write path funnels through the engine (ADR-004). The higher-level invariants
 * (I2: event+outbox in the same tx; I3: status fold; I4: ≥1 support) are enforced
 * by the calling service, which composes these primitives in one transaction.
 */
@Repository
public class ImportStore {

    public enum RecordStatus { INSERTED, DUPLICATE, SUPERSEDED }

    public record RecordResult(long id, RecordStatus status) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public ImportStore(JdbcTemplate jdbc, ObjectMapper om) {
        this.jdbc = jdbc;
        this.om = om;
    }

    // ---- agents / sources --------------------------------------------------
    public Optional<Long> agentIdBySlug(String slug) {
        return jdbc.query("SELECT id FROM agent WHERE slug = ?", (rs, n) -> rs.getLong(1), slug).stream().findFirst();
    }

    /** Upsert a source by slug; unknown sources default license_confirmed=false (the safe gate). */
    public long upsertSource(String slug) {
        Optional<Long> existing = jdbc.query("SELECT id FROM source WHERE slug = ?",
                (rs, n) -> rs.getLong(1), slug).stream().findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO source(slug, title, version, license_confirmed) VALUES (?,?,?,false)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, slug);
            ps.setString(2, slug);
            ps.setString(3, "unknown");
            return ps;
        }, kh);
        return keyId(kh);
    }

    // ---- entity resolution / creation --------------------------------------
    public Optional<Long> resolveEntity(Ref ref) {
        return jdbc.query("SELECT entity_id FROM entity_external_id WHERE authority = ? AND external_id = ?",
                (rs, n) -> rs.getLong(1), ref.authority(), ref.id()).stream().findFirst();
    }

    /** Create an entity + its identity anchors. Only path that creates entities (I6). */
    public long createEntity(String entityType, Ref ref, List<Ref> externalIds) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO entity(entity_type) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, entityType);
            return ps;
        }, kh);
        long id = keyId(kh);
        // The ref itself is an anchor; plus any declared external_ids (deduped).
        jdbc.update("INSERT INTO entity_external_id(authority, external_id, entity_id) VALUES (?,?,?) "
                + "ON CONFLICT (authority, external_id) DO NOTHING", ref.authority(), ref.id(), id);
        for (Ref e : externalIds) {
            jdbc.update("INSERT INTO entity_external_id(authority, external_id, entity_id) VALUES (?,?,?) "
                    + "ON CONFLICT (authority, external_id) DO NOTHING", e.authority(), e.id(), id);
        }
        outbox("entity", String.valueOf(id), "entity.created", null);
        return id;
    }

    public boolean relationTypeExists(String slug) {
        Integer c = jdbc.queryForObject("SELECT count(*) FROM relation_type WHERE slug = ?", Integer.class, slug);
        return c != null && c > 0;
    }

    // ---- source records ----------------------------------------------------
    public RecordResult upsertSourceRecord(long sourceId, Support sp) {
        // Duplicate = exact (source, kind, external_id, content_hash) already exists.
        Optional<Long> exact = jdbc.query(
                "SELECT id FROM source_record WHERE source_id=? AND record_kind=? AND external_id=? AND content_hash=?",
                (rs, n) -> rs.getLong(1), sourceId, sp.recordKind(), sp.externalId(), sp.contentHash())
                .stream().findFirst();
        if (exact.isPresent()) {
            return new RecordResult(exact.get(), RecordStatus.DUPLICATE);
        }
        // Superseding = same (source, kind, external_id) with a different hash.
        Optional<Long> prior = jdbc.query(
                "SELECT id FROM source_record WHERE source_id=? AND record_kind=? AND external_id=? "
              + "ORDER BY id DESC LIMIT 1",
                (rs, n) -> rs.getLong(1), sourceId, sp.recordKind(), sp.externalId()).stream().findFirst();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO source_record(source_id, record_kind, external_id, content_hash, raw, supersedes_record_id) "
                  + "VALUES (?,?,?,?,?::jsonb,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, sourceId);
            ps.setString(2, sp.recordKind());
            ps.setString(3, sp.externalId());
            ps.setString(4, sp.contentHash());
            ps.setString(5, writeJson(sp.raw()));
            if (prior.isPresent()) {
                ps.setLong(6, prior.get());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }
            return ps;
        }, kh);
        long id = keyId(kh);
        outbox("source", String.valueOf(id), "source_record.upserted", null);
        return new RecordResult(id, prior.isPresent() ? RecordStatus.SUPERSEDED : RecordStatus.INSERTED);
    }

    // ---- claims ------------------------------------------------------------
    public long insertClaim(ParsedClaim c, long subjectId, Long objectEntityId, long agentId, String importRunId) {
        KeyHolder kh = new GeneratedKeyHolder();
        JsonNode vt = c.validTime();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO claim(subject_entity_id, predicate, object_entity_id, object_literal, "
                  + " valid_start_earliest, valid_start_latest, valid_end_earliest, valid_end_latest, "
                  + " valid_approximate, valid_time_original, confidence_kind, confidence_point, "
                  + " confidence_scale, confidence_raw, confidence_calibrated, method, method_detail, "
                  + " asserted_by_agent_id, import_run_id) "
                  + "VALUES (?,?,?,?::jsonb, ?,?,?,?, ?,?::jsonb, ?,?, ?,?::jsonb,?, ?,?::jsonb, ?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, subjectId);
            ps.setString(i++, c.predicate());
            if (objectEntityId != null) {
                ps.setLong(i++, objectEntityId);
            } else {
                ps.setNull(i++, java.sql.Types.BIGINT);
            }
            ps.setString(i++, c.objectLiteral() == null ? null : writeJson(c.objectLiteral()));
            ps.setDate(i++, date(vt, "start_earliest"));
            ps.setDate(i++, date(vt, "start_latest"));
            ps.setDate(i++, date(vt, "end_earliest"));
            ps.setDate(i++, date(vt, "end_latest"));
            ps.setBoolean(i++, vt != null && vt.path("approximate").asBoolean(false));
            ps.setString(i++, vt != null && vt.has("original") ? writeJson(vt.get("original")) : null);
            ps.setString(i++, c.confidenceKind());
            if (c.confidencePoint() != null) {
                ps.setObject(i++, c.confidencePoint(), java.sql.Types.REAL);
            } else {
                ps.setNull(i++, java.sql.Types.REAL);
            }
            ps.setString(i++, c.confidenceScale());
            ps.setString(i++, c.confidenceRaw() == null ? null : writeJson(c.confidenceRaw()));
            ps.setBoolean(i++, c.confidenceCalibrated());
            ps.setString(i++, c.method());
            ps.setString(i++, c.methodDetail() == null || c.methodDetail().isNull() ? null : writeJson(c.methodDetail()));
            ps.setLong(i++, agentId);
            ps.setString(i++, importRunId);
            return ps;
        }, kh);
        return keyId(kh);
    }

    /**
     * Idempotency (Flow A step 8): a claim is "already imported" if a claim with
     * the same subject/predicate/object/valid-bounds/confidence is already linked
     * to the given support record. Re-ingesting identical data therefore inserts
     * nothing. jsonb IS NOT DISTINCT FROM compares by normalized content.
     */
    public Optional<Long> findExistingClaim(long supportRecordId, ParsedClaim c, long subjectId, Long objectEntityId) {
        JsonNode vt = c.validTime();
        return jdbc.query(
                "SELECT c.id FROM claim c JOIN claim_support cs ON cs.claim_id = c.id "
              + "WHERE cs.source_record_id = ? AND c.subject_entity_id = ? AND c.predicate = ? "
              + "  AND c.object_entity_id IS NOT DISTINCT FROM ? "
              + "  AND c.object_literal IS NOT DISTINCT FROM ?::jsonb "
              + "  AND c.valid_start_earliest IS NOT DISTINCT FROM ? "
              + "  AND c.valid_end_latest   IS NOT DISTINCT FROM ? "
              + "  AND c.confidence_point    IS NOT DISTINCT FROM ?::real LIMIT 1",
                (rs, n) -> rs.getLong(1),
                supportRecordId, subjectId, c.predicate(), objectEntityId,
                c.objectLiteral() == null ? null : writeJson(c.objectLiteral()),
                date(vt, "start_earliest"), date(vt, "end_latest"), c.confidencePoint())
                .stream().findFirst();
    }

    public void addSupport(long claimId, long recordId) {
        jdbc.update("INSERT INTO claim_support(claim_id, source_record_id) VALUES (?,?) ON CONFLICT DO NOTHING",
                claimId, recordId);
    }

    public void assertClaim(long claimId, long agentId) {
        jdbc.update("INSERT INTO claim_event(claim_id, event_type, actor_agent_id) VALUES (?, 'assert', ?)",
                claimId, agentId);
        jdbc.update("INSERT INTO claim_status_current(claim_id, status) VALUES (?, 'asserted') "
                + "ON CONFLICT (claim_id) DO UPDATE SET status='asserted', updated_at=now()", claimId);
        outbox("claim", String.valueOf(claimId), "claim.asserted", null);
    }

    /** Materialize effective confidence (I7); at import origin='source'. */
    public void writeConfidenceCurrent(long claimId, ParsedClaim c) {
        String confJson = confidenceObjectJson(c);
        jdbc.update(
                "INSERT INTO claim_confidence_current(claim_id, confidence, confidence_point, origin) "
              + "VALUES (?, ?::jsonb, ?, 'source') "
              + "ON CONFLICT (claim_id) DO UPDATE SET confidence=excluded.confidence, "
              + "  confidence_point=excluded.confidence_point, origin='source', updated_at=now()",
                ps -> {
                    ps.setLong(1, claimId);
                    ps.setString(2, confJson);
                    if (c.confidencePoint() != null) {
                        ps.setObject(3, c.confidencePoint(), java.sql.Types.REAL);
                    } else {
                        ps.setNull(3, java.sql.Types.REAL);
                    }
                });
    }

    private String confidenceObjectJson(ParsedClaim c) {
        var node = om.createObjectNode();
        node.put("kind", c.confidenceKind());
        if ("source_native_scalar".equals(c.confidenceKind())) {
            node.put("scale", c.confidenceScale());
            node.set("raw", c.confidenceRaw());
            node.put("point", c.confidencePoint());
            node.put("calibrated", c.confidenceCalibrated());
        }
        return writeJson(node);
    }

    public void outbox(String aggregate, String aggregateId, String kind, JsonNode payload) {
        jdbc.update("INSERT INTO outbox(aggregate, aggregate_id, kind, payload) VALUES (?,?,?,?::jsonb)",
                aggregate, aggregateId, kind, payload == null ? null : writeJson(payload));
    }

    // ---- import_run --------------------------------------------------------
    public void ensureImportRun(String runId, String sourceSlug) {
        jdbc.update("INSERT INTO import_run(run_id, source_slug) VALUES (?,?) ON CONFLICT (run_id) DO NOTHING",
                runId, sourceSlug);
    }

    public void tallyImportRun(String runId, int received, int inserted, int duplicates, int superseded, int rejected) {
        jdbc.update("UPDATE import_run SET batches=batches+1, received=received+?, inserted=inserted+?, "
                + "duplicates=duplicates+?, superseded=superseded+?, rejected=rejected+?, finished_at=now() "
                + "WHERE run_id=?", received, inserted, duplicates, superseded, rejected, runId);
    }

    public void mergeManifest(String runId, JsonNode manifest) {
        if (manifest == null || manifest.isNull()) {
            return;
        }
        jdbc.update("UPDATE import_run SET manifest = ?::jsonb WHERE run_id = ?", writeJson(manifest), runId);
    }

    // ---- helpers -----------------------------------------------------------
    private static long keyId(KeyHolder kh) {
        Number id = (Number) kh.getKeys().get("id");
        return id.longValue();
    }

    private static Date date(JsonNode vt, String field) {
        if (vt == null || !vt.hasNonNull(field)) {
            return null;
        }
        return Date.valueOf(LocalDate.parse(vt.get(field).asText()));
    }

    private String writeJson(JsonNode node) {
        try {
            return node == null ? null : om.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize json", e);
        }
    }
}
