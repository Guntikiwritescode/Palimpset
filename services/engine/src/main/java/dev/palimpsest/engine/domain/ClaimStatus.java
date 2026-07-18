package dev.palimpsest.engine.domain;

import java.util.List;

/**
 * The claim status fold (ARCHITECTURE §3.3 I3). {@code claim_status_current} is
 * exactly the fold of {@code claim_event}; it is never independently
 * authoritative. This pure function is the fold, used by the engine to recompute
 * the row in the same transaction as an event, and by {@code rebuild-projections}
 * as the correctness oracle.
 */
public enum ClaimStatus {
    ASSERTED,
    DISPUTED,
    SUPERSEDED;

    public String wire() {
        return name().toLowerCase();
    }

    /**
     * Fold an ordered event-type list (oldest first) into the current status.
     * Rules: assert → asserted; dispute → disputed; undispute → asserted;
     * supersede → superseded (terminal). adjust_confidence does not change status.
     */
    public static ClaimStatus fold(List<String> orderedEventTypes) {
        ClaimStatus status = null;
        for (String e : orderedEventTypes) {
            switch (e) {
                case "assert" -> status = ASSERTED;
                case "dispute" -> {
                    if (status == SUPERSEDED) {
                        throw new IllegalStateException("cannot dispute a superseded claim");
                    }
                    status = DISPUTED;
                }
                case "undispute" -> {
                    if (status == DISPUTED) {
                        status = ASSERTED;
                    }
                }
                case "supersede" -> status = SUPERSEDED;
                case "adjust_confidence" -> { /* no status change */ }
                default -> throw new IllegalArgumentException("unknown event type: " + e);
            }
        }
        if (status == null) {
            throw new IllegalStateException("a claim must have at least one assert event (I2/I4)");
        }
        return status;
    }
}
