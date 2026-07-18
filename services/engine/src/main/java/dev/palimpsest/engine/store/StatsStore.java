package dev.palimpsest.engine.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.api.Dtos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Corpus statistics + registries + import-run history. Feeds the honesty page —
 * so every figure here is computed live from the corpus, never hard-coded (§16).
 */
@Repository
public class StatsStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;

    public StatsStore(JdbcTemplate jdbc, ObjectMapper om) {
        this.jdbc = jdbc;
        this.om = om;
    }

    public Dtos.StatsDto summary() {
        long entityTotal = count("SELECT count(*) FROM entity");
        Map<String, Object> byType = new LinkedHashMap<>();
        jdbc.query("SELECT entity_type, count(*) c FROM entity GROUP BY entity_type ORDER BY entity_type",
                rs -> { byType.put(rs.getString("entity_type"), rs.getLong("c")); });
        Map<String, Object> entities = new LinkedHashMap<>();
        entities.put("total", entityTotal);
        entities.put("byType", byType);

        long claimTotal = count("SELECT count(*) FROM claim");
        Map<String, Object> byPredicate = new LinkedHashMap<>();
        jdbc.query("SELECT predicate, count(*) c FROM claim GROUP BY predicate ORDER BY predicate",
                rs -> { byPredicate.put(rs.getString("predicate"), rs.getLong("c")); });
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("total", claimTotal);
        claims.put("byPredicate", byPredicate);

        int sourceRecords = (int) count("SELECT count(*) FROM source_record");

        List<Dtos.SourceDto> sources = jdbc.query(
                "SELECT slug, title, version, retrieval_uri, license, license_confirmed FROM source ORDER BY slug",
                (rs, n) -> new Dtos.SourceDto(rs.getString("slug"), rs.getString("title"), rs.getString("version"),
                        rs.getString("retrieval_uri"), rs.getString("license"), rs.getBoolean("license_confirmed")));

        // Confidence histogram by band (scored) + unscored (§13.3 bands).
        List<Dtos.HistogramBinDto> hist = new ArrayList<>();
        Map<String, Integer> bands = new LinkedHashMap<>();
        for (String b : List.of("very_strong", "strong", "moderate", "weak", "very_weak", "unscored")) {
            bands.put(b, 0);
        }
        jdbc.query(
                "SELECT CASE "
              + " WHEN cc.confidence_point IS NULL THEN 'unscored' "
              + " WHEN cc.confidence_point >= 0.90 THEN 'very_strong' "
              + " WHEN cc.confidence_point >= 0.70 THEN 'strong' "
              + " WHEN cc.confidence_point >= 0.40 THEN 'moderate' "
              + " WHEN cc.confidence_point >= 0.20 THEN 'weak' ELSE 'very_weak' END AS band, "
              + " count(*) c "
              + "FROM claim c LEFT JOIN claim_confidence_current cc ON cc.claim_id = c.id "
              + "JOIN relation_type rt ON rt.slug = c.predicate AND rt.range_kind = 'entity' "
              + "GROUP BY 1",
                rs -> { bands.put(rs.getString("band"), rs.getInt("c")); });
        bands.forEach((band, c) -> hist.add(new Dtos.HistogramBinDto(band, c)));

        // Anomaly counters: summed across import_run manifests.
        Map<String, Object> anomalies = new LinkedHashMap<>();
        jdbc.query("SELECT manifest FROM import_run WHERE manifest IS NOT NULL", rs -> {
            try {
                var node = om.readTree(rs.getString("manifest"));
                var counters = node.get("anomaly_counters");
                if (counters != null && counters.isObject()) {
                    counters.fields().forEachRemaining(e ->
                            anomalies.merge(e.getKey(), e.getValue().asLong(),
                                    (a, b) -> ((Number) a).longValue() + ((Number) b).longValue()));
                }
            } catch (Exception ignored) {
            }
        });

        // Gender split (from has-gender claims; unknown = persons without one).
        Map<String, Integer> gender = new LinkedHashMap<>();
        gender.put("male", 0);
        gender.put("female", 0);
        gender.put("unknown", 0);
        jdbc.query(
                "SELECT lower(coalesce(object_literal->>'value','unknown')) g, count(*) c "
              + "FROM claim WHERE predicate = 'has-gender' GROUP BY 1",
                rs -> {
                    String g = rs.getString("g");
                    gender.merge("male".equals(g) ? "male" : "female".equals(g) ? "female" : "unknown",
                            rs.getInt("c"), Integer::sum);
                });
        long personTotal = count("SELECT count(*) FROM entity WHERE entity_type = 'person'");
        long withGender = gender.get("male") + gender.get("female");
        gender.put("unknown", (int) Math.max(0, personTotal - withGender));

        // Temporal code share: fraction of relationship claims whose start code is AF/IN.
        Map<String, Object> codeShare = new LinkedHashMap<>();
        jdbc.query(
                "SELECT coalesce(valid_time_original->'start'->>'type_code', "
              + "                valid_time_original->>'type_code', 'none') code, count(*) c "
              + "FROM claim c JOIN relation_type rt ON rt.slug=c.predicate AND rt.range_kind='entity' "
              + "GROUP BY 1 ORDER BY c DESC",
                rs -> { codeShare.put(rs.getString("code"), rs.getLong("c")); });

        // No-relationship percentage (persons with zero ties).
        Double noRelPct = null;
        if (personTotal > 0) {
            long noRel = count("SELECT count(*) FROM entity_summary WHERE entity_type='person' AND degree_scored=0 AND degree_unscored=0");
            noRelPct = 100.0 * noRel / personTotal;
        }

        return new Dtos.StatsDto(entities, claims, sourceRecords, sources, hist, anomalies, gender, codeShare, noRelPct);
    }

    public List<Dtos.RelationTypeDto> relationTypes() {
        return jdbc.query(
                "SELECT slug, label, category, range_kind, is_symmetric FROM relation_type ORDER BY slug",
                (rs, n) -> new Dtos.RelationTypeDto(rs.getString("slug"), rs.getString("label"),
                        rs.getString("category"), rs.getString("range_kind"), rs.getBoolean("is_symmetric")));
    }

    public List<Dtos.SourceDto> sources() {
        return jdbc.query(
                "SELECT slug, title, version, retrieval_uri, license, license_confirmed FROM source ORDER BY slug",
                (rs, n) -> new Dtos.SourceDto(rs.getString("slug"), rs.getString("title"), rs.getString("version"),
                        rs.getString("retrieval_uri"), rs.getString("license"), rs.getBoolean("license_confirmed")));
    }

    public List<Dtos.AgentDto> agents() {
        return jdbc.query("SELECT slug, kind, display_name FROM agent ORDER BY slug",
                (rs, n) -> new Dtos.AgentDto(rs.getString("slug"), rs.getString("kind"), rs.getString("display_name")));
    }

    public List<Dtos.RunDto> runs() {
        return jdbc.query("SELECT * FROM import_run ORDER BY started_at DESC", runMapper());
    }

    public Optional<Dtos.RunDto> run(String id) {
        return jdbc.query("SELECT * FROM import_run WHERE run_id = ?", runMapper(), id).stream().findFirst();
    }

    private org.springframework.jdbc.core.RowMapper<Dtos.RunDto> runMapper() {
        return (rs, n) -> new Dtos.RunDto(rs.getString("run_id"), rs.getString("source_slug"),
                str(rs.getTimestamp("started_at")), str(rs.getTimestamp("finished_at")),
                rs.getInt("batches"), rs.getInt("received"), rs.getInt("inserted"),
                rs.getInt("duplicates"), rs.getInt("superseded"), rs.getInt("rejected"),
                Mappers.json(om, rs.getString("manifest")));
    }

    private static String str(java.sql.Timestamp t) {
        return t == null ? null : t.toInstant().toString();
    }

    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0 : v;
    }
}
