package dev.palimpsest.engine.projector;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The in-engine projector (ADR-006, §3.4). Consumes the transactional outbox and
 * maintains the read models: entity_summary (display name by the deterministic
 * §17.F1 rule — NOT "highest confidence", since all name claims are unscored) and
 * the entity_name_search alias index. {@code rebuildAll} recomputes everything
 * from the base tables — the disaster-recovery path and the correctness oracle in
 * tests (§6.3).
 */
@Service
public class ProjectorService {

    private final JdbcTemplate jdbc;

    public ProjectorService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Drain unprocessed outbox rows; recompute each touched entity once. Returns rows processed. */
    @Transactional
    public int drain(int batchSize) {
        List<long[]> rows = jdbc.query(
                "SELECT event_seq, aggregate, aggregate_id FROM outbox WHERE processed_at IS NULL "
              + "ORDER BY event_seq FOR UPDATE SKIP LOCKED LIMIT ?",
                (rs, n) -> new long[]{rs.getLong("event_seq"),
                        "claim".equals(rs.getString("aggregate")) ? 1 : "entity".equals(rs.getString("aggregate")) ? 2 : 0,
                        rs.getLong("aggregate_id")},
                batchSize);
        if (rows.isEmpty()) {
            return 0;
        }
        Set<Long> entities = new LinkedHashSet<>();
        Set<Long> seqs = new LinkedHashSet<>();
        for (long[] r : rows) {
            seqs.add(r[0]);
            if (r[1] == 1) { // claim
                jdbc.query("SELECT subject_entity_id, object_entity_id FROM claim WHERE id = ?", rs -> {
                    entities.add(rs.getLong("subject_entity_id"));
                    long o = rs.getLong("object_entity_id");
                    if (!rs.wasNull()) {
                        entities.add(o);
                    }
                }, r[2]);
            } else if (r[1] == 2) { // entity
                entities.add(r[2]);
            }
        }
        entities.forEach(this::recomputeEntity);
        Long[] arr = seqs.toArray(new Long[0]);
        jdbc.update(con -> {
            var ps = con.prepareStatement("UPDATE outbox SET processed_at = now() WHERE event_seq = ANY(?)");
            ps.setArray(1, con.createArrayOf("bigint", arr));
            return ps;
        });
        return rows.size();
    }

    /** Drain everything currently pending (used after an import so reads are fresh). */
    public int drainAll() {
        int total = 0;
        int n;
        do {
            n = drain(1000);
            total += n;
        } while (n > 0);
        return total;
    }

    public long outboxLag() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE processed_at IS NULL", Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Age in seconds of the OLDEST unprocessed outbox row (0 when the outbox is
     * drained). Backs the {@code palimpsest_outbox_oldest_age_seconds} gauge and
     * the §3.4 lag SLO ({@literal <} 60s p99). Read on the metrics-scrape thread,
     * so it must be cheap and never throw.
     */
    public double oldestUnprocessedAgeSeconds() {
        Double s = jdbc.queryForObject(
                "SELECT COALESCE(EXTRACT(EPOCH FROM (now() - MIN(created_at))), 0) "
              + "FROM outbox WHERE processed_at IS NULL", Double.class);
        return s == null ? 0.0 : s;
    }

    /** rebuild-projections: recompute every read model from base tables. */
    @Transactional
    public int rebuildAll() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM entity ORDER BY id", Long.class);
        for (Long id : ids) {
            recomputeEntity(id);
        }
        jdbc.update("UPDATE outbox SET processed_at = now() WHERE processed_at IS NULL");
        return ids.size();
    }

    @Transactional
    public void recomputeEntity(long entityId) {
        String entityType = jdbc.query("SELECT entity_type FROM entity WHERE id = ?",
                (rs, n) -> rs.getString(1), entityId).stream().findFirst().orElse(null);
        if (entityType == null) {
            return;
        }

        // Display name (§17.F1): prefer scored → primary → lowest claim id.
        long[] nameHolder = new long[]{0};
        String[] nameVal = new String[]{null};
        jdbc.query(
                "SELECT c.id, c.object_literal->>'value' AS name "
              + "FROM claim c JOIN claim_status_current st ON st.claim_id=c.id AND st.status='asserted' "
              + "LEFT JOIN claim_confidence_current cc ON cc.claim_id=c.id "
              + "WHERE c.subject_entity_id=? AND c.predicate='has-name' AND c.object_literal->>'value' IS NOT NULL "
              + "ORDER BY (cc.confidence_point IS NOT NULL) DESC, "
              + "  (coalesce(c.method_detail->>'name_kind','primary')='primary') DESC, c.id ASC LIMIT 1",
                rs -> { nameHolder[0] = rs.getLong("id"); nameVal[0] = rs.getString("name"); }, entityId);
        String displayName = nameVal[0] != null ? nameVal[0] : "unnamed entity #" + entityId;
        Long nameClaimId = nameHolder[0] == 0 ? null : nameHolder[0];

        String description = firstLiteral(entityId, "has-description");
        String gender = firstLiteral(entityId, "has-gender");

        java.sql.Date[] born = lifeBounds(entityId, "born");
        java.sql.Date[] died = lifeBounds(entityId, "died");

        int[] degrees = jdbc.queryForObject(
                "SELECT count(*) FILTER (WHERE cc.confidence_point IS NOT NULL) scored, "
              + "  count(*) FILTER (WHERE cc.confidence_point IS NULL) unscored "
              + "FROM claim c JOIN relation_type rt ON rt.slug=c.predicate AND rt.range_kind='entity' "
              + "JOIN claim_status_current st ON st.claim_id=c.id AND st.status='asserted' "
              + "LEFT JOIN claim_confidence_current cc ON cc.claim_id=c.id "
              + "WHERE c.subject_entity_id=? OR c.object_entity_id=?",
                (rs, n) -> new int[]{rs.getInt("scored"), rs.getInt("unscored")}, entityId, entityId);

        jdbc.update(
                "INSERT INTO entity_summary(entity_id, display_name, name_claim_id, description, gender, "
              + " born_earliest, born_latest, died_earliest, died_latest, entity_type, degree_scored, degree_unscored, updated_at) "
              + "VALUES (?,?,?,?,?, ?,?,?,?, ?,?,?, now()) "
              + "ON CONFLICT (entity_id) DO UPDATE SET display_name=excluded.display_name, name_claim_id=excluded.name_claim_id, "
              + " description=excluded.description, gender=excluded.gender, born_earliest=excluded.born_earliest, "
              + " born_latest=excluded.born_latest, died_earliest=excluded.died_earliest, died_latest=excluded.died_latest, "
              + " entity_type=excluded.entity_type, degree_scored=excluded.degree_scored, "
              + " degree_unscored=excluded.degree_unscored, updated_at=now()",
                entityId, displayName, nameClaimId, description, gender,
                born[0], born[1], died[0], died[1], entityType, degrees[0], degrees[1]);

        // Alias/all-names search surface.
        jdbc.update("DELETE FROM entity_name_search WHERE entity_id = ?", entityId);
        jdbc.update(
                "INSERT INTO entity_name_search(claim_id, entity_id, name, name_kind) "
              + "SELECT c.id, c.subject_entity_id, c.object_literal->>'value', "
              + "  coalesce(c.method_detail->>'name_kind','primary') "
              + "FROM claim c JOIN claim_status_current st ON st.claim_id=c.id AND st.status='asserted' "
              + "WHERE c.subject_entity_id=? AND c.predicate='has-name' AND c.object_literal->>'value' IS NOT NULL "
              + "ON CONFLICT (claim_id) DO NOTHING", entityId);
    }

    private String firstLiteral(long entityId, String predicate) {
        return jdbc.query(
                "SELECT c.object_literal->>'value' v FROM claim c "
              + "JOIN claim_status_current st ON st.claim_id=c.id AND st.status='asserted' "
              + "WHERE c.subject_entity_id=? AND c.predicate=? ORDER BY c.id LIMIT 1",
                (rs, n) -> rs.getString("v"), entityId, predicate).stream().findFirst().orElse(null);
    }

    private java.sql.Date[] lifeBounds(long entityId, String predicate) {
        return jdbc.query(
                "SELECT valid_start_earliest, valid_start_latest FROM claim c "
              + "JOIN claim_status_current st ON st.claim_id=c.id AND st.status='asserted' "
              + "WHERE c.subject_entity_id=? AND c.predicate=? ORDER BY c.id LIMIT 1",
                (rs, n) -> new java.sql.Date[]{rs.getDate("valid_start_earliest"), rs.getDate("valid_start_latest")},
                entityId, predicate).stream().findFirst().orElse(new java.sql.Date[]{null, null});
    }
}
