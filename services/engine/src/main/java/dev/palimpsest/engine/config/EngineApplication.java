package dev.palimpsest.engine.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * PALIMPSEST ontology engine — the sole write authority (ARCHITECTURE §3.3).
 *
 * <p>Runs the read API, the bulk import path, the in-process projector, and (from
 * WP6) the governed actions. Invariants I1–I8 live in exactly one codebase, here.
 */
@SpringBootApplication
@ComponentScan(basePackages = "dev.palimpsest.engine")
public class EngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
    }
}
