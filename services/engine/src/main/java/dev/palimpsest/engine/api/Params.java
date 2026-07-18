package dev.palimpsest.engine.api;

/** Query-parameter normalization shared by controllers. */
public final class Params {

    private Params() {
    }

    /** Expand a bare year to Jan 1 (start) or Dec 31 (end); pass through full dates; null stays null. */
    public static String expand(String v, boolean isEnd) {
        if (v == null || v.isBlank()) {
            return null;
        }
        v = v.trim();
        if (v.matches("-?\\d{1,6}")) {
            return isEnd ? v + "-12-31" : v + "-01-01";
        }
        if (!v.matches("-?\\d{1,6}-\\d{2}-\\d{2}")) {
            throw ApiException.badRequest("bad date '" + v + "' (expected YYYY or YYYY-MM-DD)");
        }
        return v;
    }

    public static int clampLimit(Integer limit, int def, int max) {
        if (limit == null) {
            return def;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, max);
    }

    public static double confidence(Double v) {
        double c = v == null ? 0.0 : v;
        if (c < 0 || c > 1) {
            throw ApiException.badRequest("minConfidence must be in [0,1]");
        }
        return c;
    }

    public static String temporalMode(String v) {
        String m = v == null ? "possibly" : v;
        if (!m.equals("possibly") && !m.equals("certainly")) {
            throw ApiException.badRequest("temporalMode must be 'possibly' or 'certainly'");
        }
        return m;
    }
}
