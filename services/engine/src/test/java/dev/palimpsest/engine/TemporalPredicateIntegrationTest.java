package dev.palimpsest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The fuzzy-time predicates across the four-date model (§8; DomainTest does not
 * cover these). Exercises the frozen SQL functions possibly_active /
 * certainly_active / is_undated directly, over open-start, open-end, both-open,
 * undated, the dominant AF/IN+BF/IN shape, and inverted bounds. Window = calendar
 * year 1600.
 */
class TemporalPredicateIntegrationTest extends IntegrationTestBase {

    private static final String W0 = "1600-01-01";
    private static final String W1 = "1600-12-31";

    private boolean possibly(String se, String sl, String ee, String el, String w0, String w1) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT possibly_active(?::date,?::date,?::date,?::date,?::date,?::date)",
                Boolean.class, se, sl, ee, el, w0, w1));
    }

    private boolean certainly(String se, String sl, String ee, String el, String w0, String w1) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT certainly_active(?::date,?::date,?::date,?::date,?::date,?::date)",
                Boolean.class, se, sl, ee, el, w0, w1));
    }

    private boolean undated(String se, String sl, String ee, String el) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT is_undated(?::date,?::date,?::date,?::date)", Boolean.class, se, sl, ee, el));
    }

    @Test
    void both_bounds_known_and_overlapping_is_possibly_and_certainly_active() {
        assertThat(possibly("1590-01-01", "1590-12-31", "1626-01-01", "1626-12-31", W0, W1)).isTrue();
        assertThat(certainly("1590-01-01", "1590-12-31", "1626-01-01", "1626-12-31", W0, W1)).isTrue();
        assertThat(undated("1590-01-01", "1590-12-31", "1626-01-01", "1626-12-31")).isFalse();
    }

    @Test
    void open_start_is_possibly_but_never_certainly() {
        // No known upper bound on the start (s_l NULL) ⇒ cannot be *forced*.
        assertThat(possibly(null, null, "1626-01-01", "1626-12-31", W0, W1)).isTrue();
        assertThat(certainly(null, null, "1626-01-01", "1626-12-31", W0, W1)).isFalse();
        assertThat(undated(null, null, "1626-01-01", "1626-12-31")).isFalse();
    }

    @Test
    void open_end_is_possibly_but_never_certainly() {
        assertThat(possibly("1590-01-01", "1590-12-31", null, null, W0, W1)).isTrue();
        assertThat(certainly("1590-01-01", "1590-12-31", null, null, W0, W1)).isFalse();
        assertThat(undated("1590-01-01", "1590-12-31", null, null)).isFalse();
    }

    @Test
    void both_open_undated_is_possibly_active_and_flagged_undated() {
        // Unknown bounds cannot rule anything out ⇒ trivially possibly-active (A7),
        // never certainly-active, and separately counted as undated.
        assertThat(possibly(null, null, null, null, W0, W1)).isTrue();
        assertThat(certainly(null, null, null, null, W0, W1)).isFalse();
        assertThat(undated(null, null, null, null)).isTrue();
    }

    @Test
    void the_dominant_af_in_bf_in_shape_is_possibly_but_not_certainly() {
        // s_e known, s_l NULL, e_e NULL, e_l known — the ~99.7% SDFB shape (A2).
        assertThat(possibly("1590-01-01", null, null, "1626-12-31", W0, W1)).isTrue();
        assertThat(certainly("1590-01-01", null, null, "1626-12-31", W0, W1)).isFalse();
    }

    @Test
    void a_tie_ended_before_or_started_after_the_window_is_not_possibly_active() {
        assertThat(possibly("1580-01-01", "1580-12-31", "1595-01-01", "1595-12-31", W0, W1)).isFalse(); // ended before
        assertThat(possibly("1610-01-01", "1610-12-31", "1620-01-01", "1620-12-31", W0, W1)).isFalse(); // started after
    }

    @Test
    void inverted_bounds_do_not_crash_and_are_guarded_at_import_not_here() {
        // start after end. Within a 1600 window the clauses exclude it entirely.
        assertThat(possibly("1620-01-01", "1620-12-31", "1590-01-01", "1590-12-31", W0, W1)).isFalse();
        assertThat(certainly("1620-01-01", "1620-12-31", "1590-01-01", "1590-12-31", W0, W1)).isFalse();
        // Over a window that spans both, the (order-agnostic) clauses can report
        // "certainly" — a data-quality artifact. Inversion is dropped at IMPORT
        // (the valid_time_inverted anomaly class, FIX-ANOMALY), not by this predicate.
        assertThat(certainly("1620-01-01", "1620-12-31", "1590-01-01", "1590-12-31",
                "1580-01-01", "1650-12-31")).isTrue();
    }
}
