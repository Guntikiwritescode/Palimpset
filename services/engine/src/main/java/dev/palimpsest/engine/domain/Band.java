package dev.palimpsest.engine.domain;

/**
 * Confidence band labels (ARCHITECTURE §13.3). The mapping is a semantic
 * commitment: the explorer may restyle it but not change the thresholds.
 * Unscored is absence, not a band value (I5) — it is its own label.
 */
public enum Band {
    VERY_STRONG("very_strong"),
    STRONG("strong"),
    MODERATE("moderate"),
    WEAK("weak"),
    VERY_WEAK("very_weak"),
    UNSCORED("unscored");

    private final String label;

    Band(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Unscored (null) maps to UNSCORED — never to 0, never ranked among scored (I5). */
    public static Band of(Double point) {
        if (point == null) {
            return UNSCORED;
        }
        if (point >= 0.90) return VERY_STRONG;
        if (point >= 0.70) return STRONG;
        if (point >= 0.40) return MODERATE;
        if (point >= 0.20) return WEAK;
        return VERY_WEAK;
    }
}
