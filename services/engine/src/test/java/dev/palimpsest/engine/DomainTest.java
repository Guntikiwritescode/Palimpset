package dev.palimpsest.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.palimpsest.engine.domain.Band;
import dev.palimpsest.engine.domain.ClaimStatus;
import dev.palimpsest.engine.domain.EffectiveConfidence;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure domain tests (no DB). Every assertion states its expected value before
 * observing it (HANDOFF §4 rule 10) — these prove semantics, not determinism.
 */
class DomainTest {

    @Test
    void bands_map_to_the_fixed_thresholds() {
        // Expectations from ARCHITECTURE §13.3 — stated before observed.
        assertThat(Band.of(0.95)).isEqualTo(Band.VERY_STRONG);
        assertThat(Band.of(0.90)).isEqualTo(Band.VERY_STRONG);
        assertThat(Band.of(0.89)).isEqualTo(Band.STRONG);
        assertThat(Band.of(0.70)).isEqualTo(Band.STRONG);
        assertThat(Band.of(0.69)).isEqualTo(Band.MODERATE);
        assertThat(Band.of(0.40)).isEqualTo(Band.MODERATE);
        assertThat(Band.of(0.39)).isEqualTo(Band.WEAK);
        assertThat(Band.of(0.20)).isEqualTo(Band.WEAK);
        assertThat(Band.of(0.19)).isEqualTo(Band.VERY_WEAK);
        assertThat(Band.of(0.0)).isEqualTo(Band.VERY_WEAK);
    }

    @Test
    void unscored_is_absence_not_zero() {
        // I5: null ⇒ unscored, never mapped into a numeric band.
        assertThat(Band.of(null)).isEqualTo(Band.UNSCORED);
        assertThat(Band.of(null).label()).isEqualTo("unscored");
    }

    @Test
    void effective_confidence_resolution_order_is_strict() {
        // I7: manual → calibration → source → unscored.
        assertThat(EffectiveConfidence.resolve(0.8, 0.5, "cal", 0.3).origin())
                .isEqualTo(EffectiveConfidence.Origin.MANUAL);
        assertThat(EffectiveConfidence.resolve(null, 0.5, "cal", 0.3).origin())
                .isEqualTo(EffectiveConfidence.Origin.CALIBRATION);
        assertThat(EffectiveConfidence.resolve(null, null, null, 0.3).origin())
                .isEqualTo(EffectiveConfidence.Origin.SOURCE);
        var unscored = EffectiveConfidence.resolve(null, null, null, null);
        assertThat(unscored.origin()).isEqualTo(EffectiveConfidence.Origin.UNSCORED);
        assertThat(unscored.point()).isNull();
        assertThat(unscored.scored()).isFalse();
    }

    @Test
    void status_fold_is_derived_from_events() {
        // I3: the status is exactly the fold of the ordered event list.
        assertThat(ClaimStatus.fold(List.of("assert"))).isEqualTo(ClaimStatus.ASSERTED);
        assertThat(ClaimStatus.fold(List.of("assert", "dispute"))).isEqualTo(ClaimStatus.DISPUTED);
        assertThat(ClaimStatus.fold(List.of("assert", "dispute", "undispute"))).isEqualTo(ClaimStatus.ASSERTED);
        assertThat(ClaimStatus.fold(List.of("assert", "supersede"))).isEqualTo(ClaimStatus.SUPERSEDED);
        // adjust_confidence does not change status.
        assertThat(ClaimStatus.fold(List.of("assert", "adjust_confidence"))).isEqualTo(ClaimStatus.ASSERTED);
    }

    @Test
    void a_claim_with_no_assert_event_is_impossible() {
        assertThatThrownBy(() -> ClaimStatus.fold(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disputing_a_superseded_claim_is_illegal() {
        assertThatThrownBy(() -> ClaimStatus.fold(List.of("assert", "supersede", "dispute")))
                .isInstanceOf(IllegalStateException.class);
    }
}
