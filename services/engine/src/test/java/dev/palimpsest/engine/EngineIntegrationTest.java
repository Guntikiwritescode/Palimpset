package dev.palimpsest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.config.EngineApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end acceptance against a real PostgreSQL 16 (Testcontainers in CI;
 * disabled without Docker — the same flow was verified live against the local
 * cluster in this build, see the WP1/WP2 checkpoint). Asserts Flow A import,
 * idempotency, and the SIGNATURE INTERACTION (edge count strictly decreases as
 * the confidence threshold rises — the interaction on synthetic data, Q-6).
 */
@SpringBootTest(classes = EngineApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EngineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("palimpsest.migrate-url", pg::getJdbcUrl);   // ignored by binding; set explicit flyway below
        r.add("spring.flyway.url", pg::getJdbcUrl);
        r.add("spring.flyway.user", pg::getUsername);
        r.add("spring.flyway.password", pg::getPassword);
    }

    @Autowired
    TestRestTemplate rest;
    private final ObjectMapper om = new ObjectMapper();

    private HttpHeaders ndjson(String kind) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/x-ndjson"));
        h.setBearerAuth("dev-pipeline-token");
        h.set("X-Palimpsest-Run", "it-run");
        h.set("X-Palimpsest-Source", "synth-fixture");
        h.set("X-Palimpsest-Kind", kind);
        return h;
    }

    private JsonNode post(String kind, String body) throws Exception {
        var resp = rest.exchange("/api/v1/import/batches?kind=" + kind, HttpMethod.POST,
                new HttpEntity<>(body, ndjson(kind)), String.class);
        return om.readTree(resp.getBody()).get("data");
    }

    private String fixture(String name) throws Exception {
        return Files.readString(Path.of("../../fixtures/synthetic/" + name));
    }

    @Test
    @Order(1)
    void import_is_idempotent_and_the_slider_thins_the_network() throws Exception {
        JsonNode ent = post("entities", fixture("entities.ndjson"));
        assertThat(ent.get("inserted").asInt()).isGreaterThan(0);

        JsonNode first = post("claims", fixture("claims.ndjson"));
        assertThat(first.get("inserted").asInt()).isGreaterThan(0);
        assertThat(first.get("rejected")).isEmpty();

        // Re-ingesting identical data inserts nothing (Flow A step 8).
        JsonNode again = post("claims", fixture("claims.ndjson"));
        assertThat(again.get("inserted").asInt()).isEqualTo(0);
        assertThat(again.get("duplicates").asInt()).isEqualTo(first.get("inserted").asInt());

        // Resolve the hub by external id (Q-4), then drag the threshold up.
        long focus = om.readTree(rest.getForObject(
                "/api/v1/entities/lookup?authority=synth&externalId=1", String.class))
                .get("data").get("id").asLong();

        int e0 = edges(focus, 0.0);
        int e6 = edges(focus, 0.6);
        int e9 = edges(focus, 0.9);
        assertThat(e0).isGreaterThan(e6);
        assertThat(e6).isGreaterThan(e9);
        assertThat(e9).isGreaterThanOrEqualTo(1);
    }

    private int edges(long focus, double c) throws Exception {
        String body = rest.getForObject(
                "/api/v1/entities/" + focus + "/network?minConfidence=" + c + "&includeUnscored=true&limit=500",
                String.class);
        JsonNode root = om.readTree(body);
        // meta.counts present (Q-3)
        assertThat(root.get("meta").get("counts").has("possibly")).isTrue();
        return root.get("data").get("edges").size();
    }
}
