package dev.palimpsest.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

/**
 * The engine must validate against the SAME bytes the pipeline emits against.
 * This asserts the schema packaged onto the classpath is byte-identical to the
 * frozen repo contract (so a stale copy fails the build, not silently at runtime).
 */
class SchemaContractTest {

    @Test
    void packaged_schema_is_byte_identical_to_the_frozen_contract() throws Exception {
        byte[] classpath;
        try (var in = getClass().getResourceAsStream("/contracts/claim.schema.json")) {
            assertThat(in).as("frozen schema must be packaged on the classpath").isNotNull();
            classpath = in.readAllBytes();
        }
        // Module cwd during tests is services/engine; the frozen file is ../../contracts.
        Path repo = Path.of("../../contracts/claim.schema.json");
        assertThat(Files.exists(repo)).as("repo-root frozen contract present").isTrue();
        byte[] source = Files.readAllBytes(repo);
        assertThat(sha256(classpath)).isEqualTo(sha256(source));
    }

    private static String sha256(byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : d) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
