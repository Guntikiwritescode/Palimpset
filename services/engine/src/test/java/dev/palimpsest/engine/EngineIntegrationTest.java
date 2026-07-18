package dev.palimpsest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * End-to-end acceptance against a real PostgreSQL 16 (Testcontainers in CI, or an
 * external DB via PALIMPSEST_TEST_JDBC_URL — see {@link IntegrationTestBase}).
 * Covers Flow A import + idempotency and the SIGNATURE INTERACTION: the slider
 * thins the network monotonically (the interaction on synthetic data, Q-6).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EngineIntegrationTest extends IntegrationTestBase {

    private String fixture(String name) throws Exception {
        return Files.readString(Path.of("../../fixtures/synthetic/" + name));
    }

    private long focusId(String externalId) throws Exception {
        return getJson("/api/v1/entities/lookup?authority=synth&externalId=" + externalId)
                .get("data").get("id").asLong();
    }

    /** claimIds of the focus entity's network edges at a confidence threshold (scored only). */
    private Set<Long> edgeClaimIds(long focus, double minConfidence) throws Exception {
        JsonNode root = getJson("/api/v1/entities/" + focus + "/network?minConfidence=" + minConfidence
                + "&includeUnscored=false&limit=500");
        assertThat(root.get("meta").get("counts").has("possibly")).isTrue();   // Q-3 counts present
        Set<Long> ids = new LinkedHashSet<>();
        root.get("data").get("edges").forEach(e -> ids.add(e.get("claimId").asLong()));
        return ids;
    }

    @Test
    @Order(1)
    void import_is_idempotent_and_the_slider_thins_the_network() throws Exception {
        JsonNode ent = importBatch("entities", "synth-fixture", fixture("entities.ndjson"));
        assertThat(ent.get("inserted").asInt()).isGreaterThan(0);

        JsonNode first = importBatch("claims", "synth-fixture", fixture("claims.ndjson"));
        assertThat(first.get("inserted").asInt()).isGreaterThan(0);
        assertThat(first.get("rejected")).isEmpty();

        // Re-ingesting identical data inserts nothing (Flow A step 8).
        JsonNode again = importBatch("claims", "synth-fixture", fixture("claims.ndjson"));
        assertThat(again.get("inserted").asInt()).isEqualTo(0);
        assertThat(again.get("duplicates").asInt()).isEqualTo(first.get("inserted").asInt());

        long focus = focusId("1");
        int e0 = edgeClaimIds(focus, 0.0).size();
        int e6 = edgeClaimIds(focus, 0.6).size();
        int e9 = edgeClaimIds(focus, 0.9).size();
        assertThat(e0).isGreaterThan(e6);
        assertThat(e6).isGreaterThan(e9);
        assertThat(e9).isGreaterThanOrEqualTo(1);
    }

    /**
     * Property (§8 "property-based where cheap"): the slider is monotone — the edge
     * set at a HIGHER threshold is a SUBSET of the edge set at a lower one, for every
     * threshold pair. Seeded RNG; the asserted expectation is the subset RELATION,
     * never a copied count (HANDOFF rule 10).
     */
    @Test
    @Order(2)
    void slider_is_monotone_higher_threshold_edges_are_a_subset() throws Exception {
        long focus = focusId("1");
        Random rnd = new Random(20260718L);
        for (int i = 0; i < 50; i++) {
            double a = Math.round(rnd.nextDouble() * 100) / 100.0;
            double b = Math.round(rnd.nextDouble() * 100) / 100.0;
            double lo = Math.min(a, b);
            double hi = Math.max(a, b);
            Set<Long> loSet = edgeClaimIds(focus, lo);
            Set<Long> hiSet = edgeClaimIds(focus, hi);
            assertThat(loSet)
                    .as("edges@%.2f must contain every edge@%.2f (slider monotone)", lo, hi)
                    .containsAll(hiSet);
        }
    }
}
