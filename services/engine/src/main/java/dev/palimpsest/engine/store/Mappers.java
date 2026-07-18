package dev.palimpsest.engine.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.api.Dtos;
import dev.palimpsest.engine.domain.Band;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared JDBC row → DTO helpers. Keeps the SQL in the stores and the mapping here. */
public final class Mappers {

    private Mappers() {
    }

    public static String dateStr(ResultSet rs, String col) throws SQLException {
        java.sql.Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate().toString();
    }

    /** Postgres REAL maps to Float in JDBC; read a nullable double safely. */
    public static Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double d = rs.getDouble(col);
        return rs.wasNull() ? null : d;
    }

    public static Object json(ObjectMapper om, String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return om.readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    public static Dtos.LifeDates lifeDates(ResultSet rs) throws SQLException {
        String be = dateStr(rs, "born_earliest");
        String bl = dateStr(rs, "born_latest");
        String de = dateStr(rs, "died_earliest");
        String dl = dateStr(rs, "died_latest");
        if (be == null && bl == null && de == null && dl == null) {
            return null;
        }
        return new Dtos.LifeDates(be, bl, de, dl);
    }

    public static Dtos.EntitySummaryDto entitySummary(ResultSet rs, ObjectMapper om) throws SQLException {
        return new Dtos.EntitySummaryDto(
                rs.getLong("entity_id"),
                rs.getString("display_name"),
                rs.getString("entity_type"),
                rs.getString("description"),
                rs.getString("gender"),
                lifeDates(rs),
                rs.getInt("degree_scored"),
                rs.getInt("degree_unscored"));
    }

    public static Dtos.FuzzyIntervalDto fuzzy(ResultSet rs, ObjectMapper om) throws SQLException {
        String se = dateStr(rs, "valid_start_earliest");
        String sl = dateStr(rs, "valid_start_latest");
        String ee = dateStr(rs, "valid_end_earliest");
        String el = dateStr(rs, "valid_end_latest");
        boolean approx = rs.getBoolean("valid_approximate");
        Object original = json(om, rs.getString("vt_original"));
        if (se == null && sl == null && ee == null && el == null && !approx && original == null) {
            return null;
        }
        return new Dtos.FuzzyIntervalDto(se, sl, ee, el, approx, original);
    }

    /**
     * Build the effective-confidence DTO from claim_confidence_current columns.
     * confidence_point NULL ⇒ unscored (effective=null, band=unscored, scored=false).
     */
    public static Dtos.ConfidenceDto confidence(ObjectMapper om, Double point, String origin,
                                                String confidenceJson) {
        Object raw = null;
        boolean calibrated = false;
        if (confidenceJson != null) {
            try {
                JsonNode n = om.readTree(confidenceJson);
                if (n.has("raw")) {
                    raw = om.convertValue(n.get("raw"), Object.class);
                }
                calibrated = n.path("calibrated").asBoolean(false);
            } catch (Exception ignored) {
                // fall through with defaults
            }
        }
        boolean scored = point != null;
        String wireOrigin = switch (origin == null ? "" : origin) {
            case "source" -> "source_native";
            case "calibration" -> "calibration";
            case "manual" -> "manual";
            case "inferred" -> "inferred";
            default -> scored ? "source_native" : null;
        };
        return new Dtos.ConfidenceDto(
                point,
                scored ? wireOrigin : null,
                raw,
                calibrated,
                scored,
                Band.of(point).label());
    }
}
