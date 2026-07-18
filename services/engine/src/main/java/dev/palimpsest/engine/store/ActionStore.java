package dev.palimpsest.engine.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.api.Dtos;
import dev.palimpsest.engine.domain.ClaimStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Write-path helpers for the governed actions (WP6): append an event and
 * recompute the derived status fold in the same transaction (I2/I3), plus the
 * license-gated export scan, the audit feed, and path-search neighbor lookups.
 */
@Repository
public class ActionStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public ActionStore(JdbcTemplate jdbc, ObjectMapper om) {
        this.jdbc = jdbc;
        this.om = om;
    }

    public Optional<String> currentStatus(long claimId) {
        return jdbc.query("SELECT status FROM claim_status_current WHERE claim_id = ?",
                (rs, n) -> rs.getString(1), claimId).stream().findFirst();
    }

    public boolean claimExists(long claimId) {
        Integer c = jdbc.queryForObject("SELECT count(*) FROM claim WHERE id = ?", Integer.class, claimId);
        return c != null && c > 0;
    }

    /** Append an event and recompute claim_status_current as the fold of all events (I3). */
    public void appendEventAndRefold(long claimId, String eventType, long agentId, String payloadJson) {
        jdbc.update("INSERT INTO claim_event(claim_id, event_type, actor_agent_id, payload) VALUES (?,?,?,?::jsonb)",
                claimId, eventType, agentId, payloadJson);
        List<String> types = jdbc.query(
                "SELECT event_type FROM claim_event WHERE claim_id = ? ORDER BY occurred_at, id",
                (rs, n) -> rs.getString(1), claimId);
        ClaimStatus folded = ClaimStatus.fold(types);
        jdbc.update("INSERT INTO claim_status_current(claim_id, status) VALUES (?, ?) "
                + "ON CONFLICT (claim_id) DO UPDATE SET status = excluded.status, updated_at = now()",
                claimId, folded.wire());
        jdbc.update("INSERT INTO outbox(aggregate, aggregate_id, kind, payload) VALUES ('claim', ?, ?, ?::jsonb)",
                String.valueOf(claimId), "claim." + eventType, payloadJson);
    }

    /** adjust_confidence: manual override materialized into claim_confidence_current (I7 origin=manual). */
    public void adjustConfidence(long claimId, Double point, String confidenceJson) {
        jdbc.update(
                "INSERT INTO claim_confidence_current(claim_id, confidence, confidence_point, origin) "
              + "VALUES (?, ?::jsonb, ?::real, 'manual') "
              + "ON CONFLICT (claim_id) DO UPDATE SET confidence=excluded.confidence, "
              + "  confidence_point=excluded.confidence_point, origin='manual', updated_at=now()",
                claimId, confidenceJson, point);
    }

    // ---- license-gated export scan (§5.1 /export/claims) -------------------
    /** Distinct unconfirmed-license source slugs among claims matching the filter set. */
    public List<String> unconfirmedSourcesInScope() {
        return jdbc.query(
                "SELECT DISTINCT s.slug FROM source s WHERE s.license_confirmed = false "
              + "AND EXISTS (SELECT 1 FROM source_record sr JOIN claim_support cs ON cs.source_record_id=sr.id "
              + "            WHERE sr.source_id = s.id) ORDER BY s.slug",
                (rs, n) -> rs.getString(1));
    }

    // ---- audit feed (§5.1 /events) -----------------------------------------
    public List<Dtos.EventDto> events(String since, Long afterId, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT e.id, e.claim_id, e.event_type, e.payload::text AS payload, "
              + " to_char(e.occurred_at, 'YYYY-MM-DD\"T\"HH24:MI:SS.MSOF') AS occurred_at, "
              + " ag.slug, ag.kind, ag.display_name "
              + "FROM claim_event e JOIN agent ag ON ag.id = e.actor_agent_id WHERE 1=1 ");
        if (since != null) {
            sql.append("AND e.occurred_at >= ?::timestamptz ");
            args.add(since);
        }
        if (afterId != null) {
            sql.append("AND e.id < ? ");
            args.add(afterId);
        }
        sql.append("ORDER BY e.occurred_at DESC, e.id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(),
                (rs, n) -> new Dtos.EventDto(rs.getLong("id"), rs.getLong("claim_id"), rs.getString("event_type"),
                        new Dtos.AgentDto(rs.getString("slug"), rs.getString("kind"), rs.getString("display_name")),
                        rs.getString("occurred_at"), Mappers.json(om, rs.getString("payload"))),
                args.toArray());
    }

    // ---- path search neighbors (Flow H) ------------------------------------
    public record Neighbor(long counterpartId, long claimId, double confidence, String predicate) {
    }

    /** Asserted entity-ranged neighbors of an entity passing the confidence + temporal predicate. */
    public List<Neighbor> neighbors(long entityId, double minConfidence, String w0, String w1) {
        return jdbc.query(
                "SELECT CASE WHEN c.subject_entity_id = ? THEN c.object_entity_id ELSE c.subject_entity_id END AS counterpart, "
              + "  c.id AS claim_id, cc.confidence_point, c.predicate "
              + "FROM claim c "
              + "JOIN relation_type rt ON rt.slug = c.predicate AND rt.range_kind = 'entity' "
              + "JOIN claim_status_current st ON st.claim_id = c.id AND st.status = 'asserted' "
              + "JOIN claim_confidence_current cc ON cc.claim_id = c.id "
              + "WHERE (c.subject_entity_id = ? OR c.object_entity_id = ?) AND c.object_entity_id IS NOT NULL "
              + "  AND cc.confidence_point IS NOT NULL AND cc.confidence_point >= ? "
              + "  AND possibly_active(c.valid_start_earliest,c.valid_start_latest,c.valid_end_earliest,c.valid_end_latest, ?::date, ?::date)",
                (rs, n) -> new Neighbor(rs.getLong("counterpart"), rs.getLong("claim_id"),
                        rs.getDouble("confidence_point"), rs.getString("predicate")),
                entityId, entityId, entityId, minConfidence, w0, w1);
    }
}
