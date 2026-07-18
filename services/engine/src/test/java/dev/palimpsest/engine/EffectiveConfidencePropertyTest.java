package dev.palimpsest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.palimpsest.engine.domain.EffectiveConfidence;
import dev.palimpsest.engine.domain.EffectiveConfidence.Origin;
import dev.palimpsest.engine.domain.EffectiveConfidence.Resolved;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Property-based coverage of the I7 effective-confidence resolution (§8 asks for
 * property-based tests "where cheap"). Pure — no DB, so it runs in every profile.
 * The asserted expectations are RELATIONS (precedence, determinism, order-
 * independence), never a copied output value (HANDOFF rule 10).
 */
class EffectiveConfidencePropertyTest {

    /** A randomly generated claim's three confidence inputs; any may be absent. */
    private record Inputs(Double manual, Double calibration, Double source) {
        String key() {
            return manual + "|" + calibration + "|" + source;
        }
    }

    private static Resolved resolve(Inputs in) {
        return EffectiveConfidence.resolve(
                in.manual(), in.calibration(), in.calibration() == null ? null : "cal-run", in.source());
    }

    private static Double maybe(Random rnd) {
        return rnd.nextInt(4) == 0 ? null : Math.round(rnd.nextDouble() * 100) / 100.0;
    }

    @Test
    void resolution_is_deterministic_and_respects_strict_precedence() {
        Random rnd = new Random(20260718L);   // seeded; expectations below are relations
        for (int i = 0; i < 500; i++) {
            Inputs in = new Inputs(maybe(rnd), maybe(rnd), maybe(rnd));
            Resolved a = resolve(in);
            Resolved b = resolve(in);

            // deterministic: identical inputs → identical origin and point
            assertThat(a.origin()).isEqualTo(b.origin());
            assertThat(a.point()).isEqualTo(b.point());

            // strict precedence: manual → calibration → source → unscored (I7)
            Origin expected =
                    in.manual() != null ? Origin.MANUAL
                  : in.calibration() != null ? Origin.CALIBRATION
                  : in.source() != null ? Origin.SOURCE
                  : Origin.UNSCORED;
            assertThat(a.origin()).isEqualTo(expected);

            // unscored is absence, never zero (I5)
            if (expected == Origin.UNSCORED) {
                assertThat(a.point()).isNull();
                assertThat(a.scored()).isFalse();
            } else {
                assertThat(a.scored()).isTrue();
            }
        }
    }

    @Test
    void resolution_is_independent_of_insertion_order() {
        Random rnd = new Random(4242L);
        List<Inputs> claims = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            claims.add(new Inputs(maybe(rnd), maybe(rnd), maybe(rnd)));
        }
        // Resolve the set in its original order, then in a shuffled order; the
        // per-claim result must not depend on the order it was processed in.
        List<String> pass1 = claims.stream().map(c -> c.key() + "=>" + resolve(c).origin() + ":" + resolve(c).point())
                .sorted().toList();
        Collections.shuffle(claims, new Random(7));
        List<String> pass2 = claims.stream().map(c -> c.key() + "=>" + resolve(c).origin() + ":" + resolve(c).point())
                .sorted().toList();
        assertThat(pass2).isEqualTo(pass1);
    }
}
