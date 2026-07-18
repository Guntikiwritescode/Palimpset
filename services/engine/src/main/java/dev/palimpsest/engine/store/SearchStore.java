package dev.palimpsest.engine.store;

import dev.palimpsest.engine.api.Dtos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Name search: trigram over entity_summary.display_name + the alias index
 * (entity_name_search), so the historian's first instinct — looking someone up by
 * a name they know, including maiden/married names — succeeds (§17.2 S6).
 * Cursor is an opaque offset (Q-8, flagged non-blocking).
 */
@Repository
public class SearchStore {

    private final JdbcTemplate jdbc;
    private final SummaryStore summaries;

    public SearchStore(JdbcTemplate jdbc, SummaryStore summaries) {
        this.jdbc = jdbc;
        this.summaries = summaries;
    }

    public record SearchPage(List<Dtos.EntitySummaryDto> results, String nextCursor) {
    }

    public SearchPage search(String q, String type, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT ns.entity_id, max(similarity(ns.name, ?)) AS sim "
              + "FROM entity_name_search ns "
              + "JOIN entity_summary es ON es.entity_id = ns.entity_id "
              + "WHERE (ns.name %% ? OR ns.name ILIKE ('%%' || ? || '%%')) ");
        args.add(q);
        args.add(q);
        args.add(q);
        if (type != null && !type.isBlank()) {
            sql.append("AND es.entity_type = ? ");
            args.add(type);
        }
        sql.append("GROUP BY ns.entity_id ORDER BY sim DESC, ns.entity_id ASC OFFSET ? LIMIT ?");
        args.add(offset);
        args.add(limit + 1);

        List<Long> ids = jdbc.query(sql.toString().replace("%%", "%"),
                (rs, n) -> rs.getLong("entity_id"), args.toArray());

        boolean more = ids.size() > limit;
        if (more) {
            ids = ids.subList(0, limit);
        }
        var map = summaries.summariesByIds(ids);
        List<Dtos.EntitySummaryDto> results = new ArrayList<>();
        for (Long id : ids) {
            Dtos.EntitySummaryDto s = map.get(id);
            if (s != null) {
                results.add(s);
            }
        }
        String next = more ? String.valueOf(offset + limit) : null;
        return new SearchPage(results, next);
    }
}
