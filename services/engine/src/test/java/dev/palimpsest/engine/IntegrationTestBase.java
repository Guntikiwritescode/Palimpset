package dev.palimpsest.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.palimpsest.engine.config.EngineApplication;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for the engine integration tests (§8: import → read → action, against a
 * real PostgreSQL 16). Runs against EITHER:
 *   • an externally-supplied database (env {@code PALIMPSEST_TEST_JDBC_URL}) — used
 *     where Docker is unavailable, e.g. this build's environment (DEV-002); OR
 *   • a Testcontainers {@code postgres:16} (the CI default; needs Docker).
 *
 * <p><b>F5 fix.</b> The predecessor carried
 * {@code @Testcontainers(disabledWithoutDocker = true)}, which SILENTLY SKIPPED the
 * only integration test when Docker was absent — a green build that tested nothing.
 * This base instead <b>fails loudly</b>: with no external URL it starts a container,
 * and if Docker is absent that start throws (the class errors, red) rather than
 * skipping. To deliberately run without integration coverage, exclude the {@code it}
 * tag via the Maven profile {@code -P no-it} — a named, visible choice, never a
 * silent default. CI additionally asserts these tests actually executed
 * ({@code scripts/check_engine_tests.sh}) so they cannot vanish again.
 */
@SpringBootTest(classes = EngineApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("it")
abstract class IntegrationTestBase {

    /** Externally-supplied JDBC URL (local PG); null ⇒ use Testcontainers. */
    static final String EXTERNAL_URL = blankToNull(System.getenv("PALIMPSEST_TEST_JDBC_URL"));

    /** Singleton container, started once per JVM only when no external URL is given. */
    static final PostgreSQLContainer<?> PG;

    static {
        if (EXTERNAL_URL == null) {
            // No external DB → require Docker. If Docker is absent, start() throws
            // and the test class errors (loud), which is the whole point of F5.
            PG = new PostgreSQLContainer<>("postgres:16");
            PG.start();
        } else {
            PG = null;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        String url  = EXTERNAL_URL != null ? EXTERNAL_URL : PG.getJdbcUrl();
        String user = envOr("PALIMPSEST_TEST_JDBC_USER", PG != null ? PG.getUsername() : "postgres");
        String pass = envOr("PALIMPSEST_TEST_JDBC_PASSWORD", PG != null ? PG.getPassword() : "postgres");
        // Datasource + Flyway run as the same principal here (a superuser in both
        // modes), so migrations apply and the app has DML.
        r.add("spring.datasource.url", () -> url);
        r.add("spring.datasource.username", () -> user);
        r.add("spring.datasource.password", () -> pass);
        r.add("spring.flyway.url", () -> url);
        r.add("spring.flyway.user", () -> user);
        r.add("spring.flyway.password", () -> pass);
    }

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected JdbcTemplate jdbc;
    protected final ObjectMapper om = new ObjectMapper();

    // ---- HTTP helpers --------------------------------------------------------

    private HttpHeaders bearer(String token, MediaType type) {
        HttpHeaders h = new HttpHeaders();
        if (type != null) {
            h.setContentType(type);
        }
        h.setBearerAuth(token);
        return h;
    }

    /** POST an NDJSON import batch (Flow A) as the pipeline agent; returns the `data` node. */
    protected JsonNode importBatch(String kind, String source, String ndjson) throws Exception {
        HttpHeaders h = bearer("dev-pipeline-token", MediaType.parseMediaType("application/x-ndjson"));
        h.set("X-Palimpsest-Run", "it-" + kind);
        h.set("X-Palimpsest-Source", source);
        var resp = rest.exchange("/api/v1/import/batches?kind=" + kind, HttpMethod.POST,
                new HttpEntity<>(ndjson, h), String.class);
        return om.readTree(resp.getBody()).get("data");
    }

    /** POST a governed action as the scholar agent; returns the full ResponseEntity. */
    protected ResponseEntity<String> action(String path, String jsonBody) {
        HttpHeaders h = bearer("dev-scholar-token", MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/actions/" + path, HttpMethod.POST,
                new HttpEntity<>(jsonBody, h), String.class);
    }

    /** GET and return the parsed body. */
    protected JsonNode getJson(String url) throws Exception {
        return om.readTree(rest.getForObject(url, String.class));
    }

    /** GET returning the raw ResponseEntity (for status-code assertions, e.g. the 403 gate). */
    protected ResponseEntity<String> rawGet(String url) {
        return rest.getForEntity(url, String.class);
    }

    private static String envOr(String k, String d) {
        return blankToNull(System.getenv(k)) != null ? System.getenv(k) : d;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
