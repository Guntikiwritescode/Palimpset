package dev.palimpsest.engine.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.api.Dtos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Entity-view reads: the /entities/{id} composite (header + coverage + claims). */
@Repository
public class EntityStore {

    private final JdbcTemplate jdbc;
    private final SummaryStore summaries;
    private final ClaimStore claims;
    private final ObjectMapper om;

    public EntityStore(JdbcTemplate jdbc, SummaryStore summaries, ClaimStore claims, ObjectMapper om) {
        this.jdbc = jdbc;
        this.summaries = summaries;
        this.claims = claims;
        this.om = om;
    }

    public Optional<Dtos.EntityViewDto> view(long id) {
        Optional<Dtos.EntitySummaryDto> summary = summaries.summary(id);
        if (summary.isEmpty()) {
            return Optional.empty();
        }
        List<Dtos.ExternalIdDto> ext = summaries.externalIds(id);
        List<Dtos.AttributeGroupDto> attrs = claims.attributeGroups(id);
        Dtos.CoverageDto coverage = coverage(id);
        return Optional.of(new Dtos.EntityViewDto(summary.get(), ext, attrs, coverage));
    }

    public Dtos.CoverageDto coverage(long id) {
        List<Dtos.CoverageSourceDto> bySource = jdbc.query(
                "SELECT s.slug, "
              + "  count(DISTINCT c.id) FILTER (WHERE rt.range_kind = 'entity') AS rel, "
              + "  count(DISTINCT c.id) FILTER (WHERE rt.range_kind = 'literal') AS attr "
              + "FROM claim c "
              + "JOIN relation_type rt ON rt.slug = c.predicate "
              + "JOIN claim_support cs ON cs.claim_id = c.id "
              + "JOIN source_record sr ON sr.id = cs.source_record_id "
              + "JOIN source s ON s.id = sr.source_id "
              + "WHERE c.subject_entity_id = ? OR c.object_entity_id = ? "
              + "GROUP BY s.slug ORDER BY s.slug",
                (rs, n) -> new Dtos.CoverageSourceDto(rs.getString("slug"), rs.getInt("rel"), rs.getInt("attr")),
                id, id);

        int[] split = jdbc.queryForObject(
                "SELECT count(*) FILTER (WHERE cc.confidence_point IS NOT NULL) AS scored, "
              + "  count(*) FILTER (WHERE cc.confidence_point IS NULL) AS unscored "
              + "FROM claim c LEFT JOIN claim_confidence_current cc ON cc.claim_id = c.id "
              + "WHERE c.subject_entity_id = ? OR c.object_entity_id = ?",
                (rs, n) -> new int[]{rs.getInt("scored"), rs.getInt("unscored")}, id, id);

        return new Dtos.CoverageDto(bySource, split[0], split[1], "uncalibrated");
    }

    public Optional<Dtos.EntitySummaryDto> lookup(String authority, String externalId) {
        return summaries.lookup(authority, externalId);
    }

    public Optional<Dtos.EntitySummaryDto> random(String type, int minScoredDegree) {
        return summaries.random(type, minScoredDegree);
    }
}
