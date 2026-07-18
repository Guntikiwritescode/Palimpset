package dev.palimpsest.engine.domain;

/**
 * The I7 effective-confidence resolution, in exactly one place (ARCHITECTURE
 * §3.3 I7, §6.2). Belief about an immutable claim changes on three timescales;
 * the resolution order is strict:
 *
 * <pre>
 *   manual adjust (latest event) → active calibration run value → source-native point → unscored
 * </pre>
 *
 * The result is materialized into {@code claim_confidence_current} by the
 * importer/projector, so every read filters on one resolved value and the UI can
 * always answer "why is this number what it is" from {@code origin}.
 */
public final class EffectiveConfidence {

    /** Origin of the resolved value (mirrors claim_confidence_current.origin). */
    public enum Origin { MANUAL, CALIBRATION, SOURCE, UNSCORED }

    public record Resolved(Double point, Origin origin, String calibrationRunId) {
        public boolean scored() {
            return point != null;
        }
    }

    private EffectiveConfidence() {
    }

    /**
     * @param manualPoint     latest manual adjust value, or null
     * @param calibrationPoint active calibration run value for this claim, or null
     * @param calibrationRunId the active run id when calibrationPoint applies, else null
     * @param sourcePoint     the claim's source-native point (null when the claim is unscored)
     */
    public static Resolved resolve(Double manualPoint,
                                   Double calibrationPoint, String calibrationRunId,
                                   Double sourcePoint) {
        if (manualPoint != null) {
            return new Resolved(manualPoint, Origin.MANUAL, null);
        }
        if (calibrationPoint != null) {
            return new Resolved(calibrationPoint, Origin.CALIBRATION, calibrationRunId);
        }
        if (sourcePoint != null) {
            return new Resolved(sourcePoint, Origin.SOURCE, null);
        }
        // Unscored: absence, never zero (I5).
        return new Resolved(null, Origin.UNSCORED, null);
    }

    /** Lower-cased wire token for the origin, matching BUILD-CONTRACT Confidence.origin. */
    public static String wireOrigin(Origin origin) {
        return switch (origin) {
            case MANUAL -> "manual";
            case CALIBRATION -> "calibration";
            case SOURCE -> "source_native";
            case UNSCORED -> null;
        };
    }
}
