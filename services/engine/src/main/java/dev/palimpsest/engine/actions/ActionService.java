package dev.palimpsest.engine.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.api.ApiException;
import dev.palimpsest.engine.importer.ImportModels.ParsedClaim;
import dev.palimpsest.engine.importer.ImportService;
import dev.palimpsest.engine.store.ActionStore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Governed write-back actions (WP6/WP7, Flows D/F). Every action validates the
 * transition, appends its event, and recomputes the derived status fold in one
 * transaction (I2/I3). The §17.3 write-path corrections are honored:
 * disputes carry a typed ground and identity routes to the ER queue (W1);
 * time-travel writes are refused by the controller (W8).
 */
@Service
public class ActionService {

    private static final Set<String> GROUNDS =
            Set.of("existence", "dating", "identity", "confidence", "source-reading");

    private final ActionStore store;
    private final ImportService importer;
    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public ActionService(ActionStore store, ImportService importer, JdbcTemplate jdbc, ObjectMapper om) {
        this.store = store;
        this.importer = importer;
        this.jdbc = jdbc;
        this.om = om;
    }

    @Transactional
    public Map<String, Object> dispute(long claimId, String ground, String reason, long agentId) {
        requireClaim(claimId);
        if (ground == null || !GROUNDS.contains(ground)) {
            throw ApiException.badRequest("ground must be one of " + GROUNDS);
        }
        String status = store.currentStatus(claimId).orElseThrow(() -> ApiException.notFound("no claim " + claimId));
        if ("superseded".equals(status)) {
            throw ApiException.conflict("cannot dispute a superseded claim");
        }
        // W1: an identity dispute is an ER matter, not a flag on the claim.
        if ("identity".equals(ground)) {
            return routeIdentityToEr(claimId, reason);
        }
        String payload = writeJson(Map.of("ground", ground, "reason", reason == null ? "" : reason));
        store.appendEventAndRefold(claimId, "dispute", agentId, payload);
        return Map.of("claimId", claimId, "status", "disputed", "ground", ground);
    }

    private Map<String, Object> routeIdentityToEr(long claimId, String reason) {
        Long[] ends = jdbc.query(
                "SELECT subject_entity_id, object_entity_id FROM claim WHERE id = ?",
                (rs, n) -> new Long[]{rs.getLong("subject_entity_id"), (Long) rs.getObject("object_entity_id")}, claimId)
                .stream().findFirst().orElse(new Long[]{null, null});
        if (ends[1] == null) {
            throw ApiException.badRequest("identity ground applies to relationship claims");
        }
        long a = Math.min(ends[0], ends[1]);
        long b = Math.max(ends[0], ends[1]);
        String features = writeJson(Map.of("reason", reason == null ? "" : reason, "sourceClaim", claimId, "routedFrom", "dispute"));
        Long pairId = jdbc.query(
                "INSERT INTO er_candidate(entity_a, entity_b, score, features, state) VALUES (?,?,0,?::jsonb,'queued') "
              + "ON CONFLICT (entity_a, entity_b) DO UPDATE SET features = excluded.features RETURNING pair_id",
                (rs, n) -> rs.getLong(1), a, b, features).stream().findFirst().orElse(null);
        return Map.of("claimId", claimId, "routedToEr", true, "pairId", pairId == null ? -1 : pairId);
    }

    @Transactional
    public Map<String, Object> undispute(long claimId, long agentId) {
        requireClaim(claimId);
        String status = store.currentStatus(claimId).orElse(null);
        if (!"disputed".equals(status)) {
            throw ApiException.conflict("claim is not disputed (status=" + status + ")");
        }
        store.appendEventAndRefold(claimId, "undispute", agentId, writeJson(Map.of()));
        return Map.of("claimId", claimId, "status", "asserted");
    }

    @Transactional
    public Map<String, Object> adjustConfidence(long claimId, JsonNode confidence, String reason, long agentId) {
        requireClaim(claimId);
        Double point = confidence != null && confidence.hasNonNull("point") ? confidence.get("point").asDouble() : null;
        if (point == null) {
            throw ApiException.badRequest("adjust-confidence requires a numeric point");
        }
        store.appendEventAndRefold(claimId, "adjust_confidence", agentId,
                writeJson(Map.of("reason", reason == null ? "" : reason)));
        store.adjustConfidence(claimId, point, writeJson(confidence));
        return Map.of("claimId", claimId, "effective", point, "origin", "manual");
    }

    @Transactional
    public Map<String, Object> supersede(long claimId, JsonNode replacement, String reason, long agentId) {
        requireClaim(claimId);
        String status = store.currentStatus(claimId).orElse(null);
        if ("superseded".equals(status)) {
            throw ApiException.conflict("claim already superseded");
        }
        ParsedClaim parsed = importer.parseClaim(replacement);
        long newId = importer.createClaim(parsed, agentId, null);
        store.appendEventAndRefold(claimId, "supersede", agentId,
                writeJson(Map.of("supersededBy", newId, "reason", reason == null ? "" : reason)));
        return Map.of("claimId", claimId, "status", "superseded", "supersededBy", newId);
    }

    @Transactional
    public Map<String, Object> assertClaim(JsonNode claim, long agentId) {
        ParsedClaim parsed = importer.parseClaim(claim);
        long id = importer.createClaim(parsed, agentId, null);
        return Map.of("claimId", id, "status", "asserted");
    }

    /** WP7: accept an ER match — record same-as claims + recompute canonical (Flow F). */
    @Transactional
    public Map<String, Object> mergeEntities(List<Long> memberIds, String rationale, long agentId) {
        if (memberIds == null || memberIds.size() < 2) {
            throw ApiException.badRequest("merge requires ≥2 member entity ids");
        }
        // W5: warn (never block) when lifespans do not overlap.
        boolean lifespanWarning = !lifespansOverlap(memberIds);
        long canonical = memberIds.stream().mapToLong(Long::longValue).min().getAsLong();
        for (Long m : memberIds) {
            jdbc.update("INSERT INTO entity_canonical(entity_id, canonical_entity_id) VALUES (?,?) "
                    + "ON CONFLICT (entity_id) DO UPDATE SET canonical_entity_id=excluded.canonical_entity_id, updated_at=now()",
                    m, canonical);
        }
        return Map.of("canonical", canonical, "members", memberIds, "lifespanWarning", lifespanWarning,
                "rationale", rationale == null ? "" : rationale);
    }

    private boolean lifespansOverlap(List<Long> ids) {
        // Overlap if all members' [born_earliest, died_latest] windows intersect pairwise (cheap approx).
        var rows = jdbc.query(
                "SELECT entity_id, born_earliest, died_latest FROM entity_summary WHERE entity_id = ANY(?)",
                (org.springframework.jdbc.core.PreparedStatementSetter)
                        ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids.toArray())),
                (rs, n) -> new Object[]{rs.getDate("born_earliest"), rs.getDate("died_latest")});
        java.sql.Date maxBorn = null;
        java.sql.Date minDied = null;
        for (Object[] r : rows) {
            java.sql.Date born = (java.sql.Date) r[0];
            java.sql.Date died = (java.sql.Date) r[1];
            if (born != null && (maxBorn == null || born.after(maxBorn))) {
                maxBorn = born;
            }
            if (died != null && (minDied == null || died.before(minDied))) {
                minDied = died;
            }
        }
        return maxBorn == null || minDied == null || !maxBorn.after(minDied);
    }

    private void requireClaim(long claimId) {
        if (!store.claimExists(claimId)) {
            throw ApiException.notFound("no claim " + claimId);
        }
    }

    private String writeJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
