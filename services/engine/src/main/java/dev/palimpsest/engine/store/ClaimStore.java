package dev.palimpsest.engine.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.api.Dtos;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Claim-centric reads: the ego-network/slider query (Flow B), claim detail,
 * evidence (Flow C), history, the pair dossier, and filtered claim lists.
 * Effective confidence is read from claim_confidence_current (I7 materialized).
 */
@Repository
public class ClaimStore {

    public record NetworkParams(String windowStart, String windowEnd, double minConfidence,
                                boolean includeUnscored, String temporalMode, int limit) {
    }

    public record NetworkResult(Dtos.EntitySummaryDto focus, List<Dtos.EdgeDto> edges,
                                Dtos.NetworkCounts counts, boolean truncated) {
    }

    public record ClaimListPage(List<Dtos.ClaimDetailDto> claims, String nextCursor) {
    }

    private record PartialClaim(long id, long subjectId, Long objectEntityId, Dtos.LiteralDto literal,
                                String predicate, Dtos.FuzzyIntervalDto validTime, Dtos.ConfidenceDto confidence,
                                String method, Object methodDetail, String status, Dtos.AgentDto assertedBy,
                                String recordedAt, String importRunId) {
    }

    private static final String CLAIM_DETAIL_SELECT =
            "SELECT c.id, c.subject_entity_id, c.object_entity_id, c.object_literal::text AS object_literal, "
          + "  c.predicate, c.method, c.method_detail::text AS method_detail, c.import_run_id, "
          + "  to_char(c.recorded_at, 'YYYY-MM-DD\"T\"HH24:MI:SS.MSOF') AS recorded_at, "
          + "  c.valid_start_earliest, c.valid_start_latest, c.valid_end_earliest, c.valid_end_latest, "
          + "  c.valid_approximate, c.valid_time_original::text AS vt_original, "
          + "  cc.confidence_point, cc.confidence::text AS confidence_json, cc.origin, "
          + "  st.status, ag.slug AS agent_slug, ag.kind AS agent_kind, ag.display_name AS agent_name "
          + "FROM claim c "
          + "JOIN claim_status_current st ON st.claim_id = c.id "
          + "JOIN agent ag ON ag.id = c.asserted_by_agent_id "
          + "LEFT JOIN claim_confidence_current cc ON cc.claim_id = c.id ";

    private static final String FULL_RANGE_START = "0001-01-01";
    private static final String FULL_RANGE_END = "9999-12-31";

    private final JdbcTemplate jdbc;
    private final SummaryStore summaries;
    private final ObjectMapper om;

    public ClaimStore(JdbcTemplate jdbc, SummaryStore summaries, ObjectMapper om) {
        this.jdbc = jdbc;
        this.summaries = summaries;
        this.om = om;
    }

    private RowMapper<PartialClaim> partialMapper() {
        return (rs, n) -> {
            Long objId = (Long) rs.getObject("object_entity_id");
            Dtos.LiteralDto literal = null;
            if (objId == null) {
                literal = parseLiteral(rs.getString("object_literal"));
            }
            Dtos.ConfidenceDto conf = Mappers.confidence(om,
                    Mappers.nullableDouble(rs, "confidence_point"), rs.getString("origin"),
                    rs.getString("confidence_json"));
            return new PartialClaim(
                    rs.getLong("id"), rs.getLong("subject_entity_id"), objId, literal,
                    rs.getString("predicate"), Mappers.fuzzy(rs, om), conf,
                    rs.getString("method"), Mappers.json(om, rs.getString("method_detail")),
                    rs.getString("status"),
                    new Dtos.AgentDto(rs.getString("agent_slug"), rs.getString("agent_kind"), rs.getString("agent_name")),
                    rs.getString("recorded_at"), rs.getString("import_run_id"));
        };
    }

    private Dtos.LiteralDto parseLiteral(String json) {
        if (json == null) {
            return null;
        }
        try {
            var n = om.readTree(json);
            Object value = n.has("value") ? om.convertValue(n.get("value"), Object.class) : null;
            String authority = n.has("authority") ? n.get("authority").asText() : null;
            return new Dtos.LiteralDto(n.path("kind").asText(null), value, authority);
        } catch (Exception e) {
            return new Dtos.LiteralDto("string", json, null);
        }
    }

    private List<Dtos.ClaimDetailDto> assemble(List<PartialClaim> parts) {
        Set<Long> ids = new LinkedHashSet<>();
        for (PartialClaim p : parts) {
            ids.add(p.subjectId());
            if (p.objectEntityId() != null) {
                ids.add(p.objectEntityId());
            }
        }
        Map<Long, Dtos.EntitySummaryDto> sums = summaries.summariesByIds(ids);
        List<Dtos.ClaimDetailDto> out = new ArrayList<>(parts.size());
        for (PartialClaim p : parts) {
            Dtos.ObjectDto object = p.objectEntityId() != null
                    ? new Dtos.ObjectDto(sums.get(p.objectEntityId()), null)
                    : new Dtos.ObjectDto(null, p.literal());
            out.add(new Dtos.ClaimDetailDto(p.id(), sums.get(p.subjectId()), p.predicate(), object,
                    p.validTime(), p.confidence(), p.method(), p.methodDetail(), p.status(),
                    p.assertedBy(), p.recordedAt(), p.importRunId()));
        }
        return out;
    }

    public Optional<Dtos.ClaimDetailDto> claimDetail(long id) {
        List<PartialClaim> parts = jdbc.query(CLAIM_DETAIL_SELECT + "WHERE c.id = ?", partialMapper(), id);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(assemble(parts).get(0));
    }

    // ---- network / slider --------------------------------------------------
    public Optional<NetworkResult> network(long entityId, NetworkParams p) {
        Optional<Dtos.EntitySummaryDto> focus = summaries.summary(entityId);
        if (focus.isEmpty()) {
            return Optional.empty();
        }
        String w0 = p.windowStart() != null ? p.windowStart() : FULL_RANGE_START;
        String w1 = p.windowEnd() != null ? p.windowEnd() : FULL_RANGE_END;

        String where =
                "FROM claim c "
              + "JOIN relation_type rt ON rt.slug = c.predicate AND rt.range_kind = 'entity' "
              + "JOIN claim_status_current st ON st.claim_id = c.id AND st.status = 'asserted' "
              + "LEFT JOIN claim_confidence_current cc ON cc.claim_id = c.id "
              + "WHERE (c.subject_entity_id = ? OR c.object_entity_id = ?) "
              + "  AND c.object_entity_id IS NOT NULL "
              + "  AND possibly_active(c.valid_start_earliest, c.valid_start_latest, c.valid_end_earliest, c.valid_end_latest, ?::date, ?::date) "
              + "  AND ( (cc.confidence_point IS NOT NULL AND cc.confidence_point >= ?) OR (? AND cc.confidence_point IS NULL) )";

        Object[] whereArgs = {entityId, entityId, w0, w1, p.minConfidence(), p.includeUnscored()};

        // Counts over the full matching set (Q-3: possibly / certainly / undated).
        Dtos.NetworkCounts counts = jdbc.queryForObject(
                "SELECT count(*) AS possibly, "
              + " count(*) FILTER (WHERE certainly_active(c.valid_start_earliest,c.valid_start_latest,c.valid_end_earliest,c.valid_end_latest, ?::date, ?::date)) AS certainly, "
              + " count(*) FILTER (WHERE is_undated(c.valid_start_earliest,c.valid_start_latest,c.valid_end_earliest,c.valid_end_latest)) AS undated "
              + where,
                (rs, n) -> new Dtos.NetworkCounts(rs.getInt("possibly"), rs.getInt("certainly"), rs.getInt("undated")),
                w0, w1, entityId, entityId, w0, w1, p.minConfidence(), p.includeUnscored());

        int cap = p.limit();
        // Fetch cap+1 to detect truncation.
        List<EdgeRow> rows = jdbc.query(
                "SELECT c.id AS claim_id, c.predicate, "
              + "  CASE WHEN c.subject_entity_id = ? THEN c.object_entity_id ELSE c.subject_entity_id END AS counterpart_id, "
              + "  cc.confidence_point, cc.confidence::text AS confidence_json, cc.origin, "
              + "  c.valid_start_earliest, c.valid_start_latest, c.valid_end_earliest, c.valid_end_latest, "
              + "  c.valid_approximate, c.valid_time_original::text AS vt_original, "
              + "  certainly_active(c.valid_start_earliest,c.valid_start_latest,c.valid_end_earliest,c.valid_end_latest, ?::date, ?::date) AS certainly, "
              + "  is_undated(c.valid_start_earliest,c.valid_start_latest,c.valid_end_earliest,c.valid_end_latest) AS undated, "
              + "  st.status "
              + where
              + " ORDER BY cc.confidence_point DESC NULLS LAST, c.id ASC LIMIT ?",
                edgeRowMapper(),
                concat(new Object[]{entityId, w0, w1}, whereArgs, new Object[]{cap + 1}));

        boolean truncated = rows.size() > cap;
        if (truncated) {
            rows = rows.subList(0, cap);
        }

        Set<Long> counterpartIds = new LinkedHashSet<>();
        for (EdgeRow r : rows) {
            counterpartIds.add(r.counterpartId);
        }
        Map<Long, Dtos.EntitySummaryDto> sums = summaries.summariesByIds(counterpartIds);

        List<Dtos.EdgeDto> edges = new ArrayList<>(rows.size());
        for (EdgeRow r : rows) {
            edges.add(new Dtos.EdgeDto(r.claimId, sums.get(r.counterpartId), r.predicate,
                    r.confidence, r.validTime, r.confidence.scored(), r.certainly, r.undated, r.status));
        }
        return Optional.of(new NetworkResult(focus.get(), edges, counts, truncated));
    }

    private record EdgeRow(long claimId, long counterpartId, String predicate,
                           Dtos.ConfidenceDto confidence, Dtos.FuzzyIntervalDto validTime,
                           boolean certainly, boolean undated, String status) {
    }

    private RowMapper<EdgeRow> edgeRowMapper() {
        return (ResultSet rs, int n) -> new EdgeRow(
                rs.getLong("claim_id"), rs.getLong("counterpart_id"), rs.getString("predicate"),
                Mappers.confidence(om, Mappers.nullableDouble(rs, "confidence_point"), rs.getString("origin"),
                        rs.getString("confidence_json")),
                Mappers.fuzzy(rs, om), rs.getBoolean("certainly"), rs.getBoolean("undated"),
                rs.getString("status"));
    }

    // ---- evidence / history ------------------------------------------------
    public Optional<Dtos.EvidenceDto> evidence(long claimId) {
        Optional<Dtos.ClaimDetailDto> detail = claimDetail(claimId);
        if (detail.isEmpty()) {
            return Optional.empty();
        }
        List<Dtos.SupportDto> support = jdbc.query(
                "SELECT s.slug, s.title, s.version, s.retrieval_uri, s.license, s.license_confirmed, "
              + "  sr.record_kind, sr.external_id, sr.content_hash, sr.raw::text AS raw "
              + "FROM claim_support cs "
              + "JOIN source_record sr ON sr.id = cs.source_record_id "
              + "JOIN source s ON s.id = sr.source_id "
              + "WHERE cs.claim_id = ? ORDER BY sr.id",
                (rs, n) -> new Dtos.SupportDto(
                        new Dtos.SourceDto(rs.getString("slug"), rs.getString("title"), rs.getString("version"),
                                rs.getString("retrieval_uri"), rs.getString("license"), rs.getBoolean("license_confirmed")),
                        new Dtos.SourceRecordDto(rs.getString("record_kind"), rs.getString("external_id"),
                                rs.getString("content_hash"), Mappers.json(om, rs.getString("raw")))),
                claimId);
        return Optional.of(new Dtos.EvidenceDto(detail.get(), support));
    }

    public Dtos.HistoryDto history(long claimId) {
        List<Dtos.EventDto> events = jdbc.query(
                "SELECT e.id, e.claim_id, e.event_type, e.payload::text AS payload, "
              + "  to_char(e.occurred_at, 'YYYY-MM-DD\"T\"HH24:MI:SS.MSOF') AS occurred_at, "
              + "  ag.slug, ag.kind, ag.display_name "
              + "FROM claim_event e JOIN agent ag ON ag.id = e.actor_agent_id "
              + "WHERE e.claim_id = ? ORDER BY e.occurred_at, e.id",
                (rs, n) -> new Dtos.EventDto(rs.getLong("id"), rs.getLong("claim_id"), rs.getString("event_type"),
                        new Dtos.AgentDto(rs.getString("slug"), rs.getString("kind"), rs.getString("display_name")),
                        rs.getString("occurred_at"), Mappers.json(om, rs.getString("payload"))),
                claimId);
        return new Dtos.HistoryDto(events);
    }

    // ---- pair dossier ------------------------------------------------------
    public Dtos.PairDossierDto pair(long a, long b, boolean assertedOnly) {
        String statusClause = assertedOnly ? " AND st.status = 'asserted' " : "";
        List<PartialClaim> parts = jdbc.query(
                CLAIM_DETAIL_SELECT
              + "WHERE ((c.subject_entity_id = ? AND c.object_entity_id = ?) "
              + "    OR (c.subject_entity_id = ? AND c.object_entity_id = ?)) " + statusClause
              + "ORDER BY c.predicate, c.id",
                partialMapper(), a, b, b, a);
        List<Dtos.ClaimDetailDto> claims = assemble(parts);
        Dtos.EntitySummaryDto sa = summaries.summary(a).orElse(null);
        Dtos.EntitySummaryDto sb = summaries.summary(b).orElse(null);
        return new Dtos.PairDossierDto(sa, sb, claims);
    }

    // ---- attribute claims grouped by predicate (for entity view) ----------
    public List<Dtos.AttributeGroupDto> attributeGroups(long entityId) {
        List<PartialClaim> parts = jdbc.query(
                CLAIM_DETAIL_SELECT
              + "JOIN relation_type rt ON rt.slug = c.predicate AND rt.range_kind = 'literal' "
              + "WHERE c.subject_entity_id = ? AND st.status <> 'superseded' "
              + "ORDER BY c.predicate, c.id",
                partialMapper(), entityId);
        List<Dtos.ClaimDetailDto> claims = assemble(parts);
        Map<String, List<Dtos.ClaimDetailDto>> grouped = new LinkedHashMap<>();
        for (Dtos.ClaimDetailDto c : claims) {
            grouped.computeIfAbsent(c.predicate(), k -> new ArrayList<>()).add(c);
        }
        List<Dtos.AttributeGroupDto> out = new ArrayList<>();
        grouped.forEach((pred, cs) -> out.add(new Dtos.AttributeGroupDto(pred, cs)));
        return out;
    }

    // ---- filtered claim list (cursor over claim id) -----------------------
    public ClaimListPage listEntityClaims(long entityId, String predicate, String role, String status,
                                          Double minConfidence, boolean includeUnscored,
                                          String windowStart, String windowEnd, Long afterId, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder("WHERE ");
        if ("subject".equals(role)) {
            w.append("c.subject_entity_id = ? ");
            args.add(entityId);
        } else if ("object".equals(role)) {
            w.append("c.object_entity_id = ? ");
            args.add(entityId);
        } else {
            w.append("(c.subject_entity_id = ? OR c.object_entity_id = ?) ");
            args.add(entityId);
            args.add(entityId);
        }
        if (!"any".equalsIgnoreCase(status)) {
            w.append("AND st.status = ? ");
            args.add(status == null ? "asserted" : status);
        }
        if (predicate != null && !predicate.isBlank()) {
            w.append("AND c.predicate = ? ");
            args.add(predicate);
        }
        if (minConfidence != null) {
            w.append("AND ((cc.confidence_point IS NOT NULL AND cc.confidence_point >= ?) OR (? AND cc.confidence_point IS NULL)) ");
            args.add(minConfidence);
            args.add(includeUnscored);
        } else if (!includeUnscored) {
            w.append("AND cc.confidence_point IS NOT NULL ");
        }
        if (windowStart != null && windowEnd != null) {
            w.append("AND possibly_active(c.valid_start_earliest,c.valid_start_latest,c.valid_end_earliest,c.valid_end_latest, ?::date, ?::date) ");
            args.add(windowStart);
            args.add(windowEnd);
        }
        if (afterId != null) {
            w.append("AND c.id > ? ");
            args.add(afterId);
        }
        args.add(limit + 1);
        List<PartialClaim> parts = jdbc.query(
                CLAIM_DETAIL_SELECT + w + "ORDER BY c.id ASC LIMIT ?", partialMapper(), args.toArray());
        boolean more = parts.size() > limit;
        if (more) {
            parts = parts.subList(0, limit);
        }
        List<Dtos.ClaimDetailDto> claims = assemble(parts);
        String next = more && !claims.isEmpty() ? String.valueOf(claims.get(claims.size() - 1).id()) : null;
        return new ClaimListPage(claims, next);
    }

    private static Object[] concat(Object[]... arrays) {
        List<Object> out = new ArrayList<>();
        for (Object[] a : arrays) {
            for (Object o : a) {
                out.add(o);
            }
        }
        return out.toArray();
    }
}
