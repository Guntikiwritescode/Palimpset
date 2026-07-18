package dev.palimpsest.engine.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.api.Dtos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Entity summary reads (the projector-maintained read model, §3.4). */
@Repository
public class SummaryStore {

    private static final String COLS =
            "entity_id, display_name, entity_type, description, gender, "
            + "born_earliest, born_latest, died_earliest, died_latest, "
            + "degree_scored, degree_unscored";

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public SummaryStore(JdbcTemplate jdbc, ObjectMapper om) {
        this.jdbc = jdbc;
        this.om = om;
    }

    public Optional<Dtos.EntitySummaryDto> summary(long id) {
        List<Dtos.EntitySummaryDto> r = jdbc.query(
                "SELECT " + COLS + " FROM entity_summary WHERE entity_id = ?",
                (rs, n) -> Mappers.entitySummary(rs, om), id);
        return r.stream().findFirst();
    }

    public Map<Long, Dtos.EntitySummaryDto> summariesByIds(Collection<Long> ids) {
        Map<Long, Dtos.EntitySummaryDto> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Long[] arr = ids.toArray(new Long[0]);
        jdbc.query("SELECT " + COLS + " FROM entity_summary WHERE entity_id = ANY(?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", arr)),
                rs -> {
                    Dtos.EntitySummaryDto s = Mappers.entitySummary(rs, om);
                    out.put(s.id(), s);
                });
        return out;
    }

    /** Q-4: resolve a fixture by its authority external id. */
    public Optional<Dtos.EntitySummaryDto> lookup(String authority, String externalId) {
        List<Long> ids = jdbc.query(
                "SELECT entity_id FROM entity_external_id WHERE authority = ? AND external_id = ?",
                (rs, n) -> rs.getLong(1), authority, externalId);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return summary(ids.get(0));
    }

    public List<Dtos.ExternalIdDto> externalIds(long entityId) {
        return jdbc.query(
                "SELECT authority, external_id FROM entity_external_id WHERE entity_id = ? ORDER BY authority",
                (rs, n) -> new Dtos.ExternalIdDto(rs.getString("authority"), rs.getString("external_id")),
                entityId);
    }

    /** Explore someone: uniform over qualifying rows (min scored degree so it's not a dead end). */
    public Optional<Dtos.EntitySummaryDto> random(String type, int minScoredDegree) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM entity_summary WHERE degree_scored >= ?");
        args.add(minScoredDegree);
        if (type != null && !type.isBlank()) {
            sql.append(" AND entity_type = ?");
            args.add(type);
        }
        sql.append(" ORDER BY random() LIMIT 1");
        List<Dtos.EntitySummaryDto> r = jdbc.query(sql.toString(),
                (rs, n) -> Mappers.entitySummary(rs, om), args.toArray());
        return r.stream().findFirst();
    }
}
