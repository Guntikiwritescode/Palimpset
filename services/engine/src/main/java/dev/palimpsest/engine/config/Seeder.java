package dev.palimpsest.engine.config;

import dev.palimpsest.engine.projector.ProjectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Seeds reference data the engine owns: the attributed agents (ADR-005), the
 * Part-1 relation-type vocabulary, and the known sources with their license
 * flags (SDFB unconfirmed — the gate defaults closed). Idempotent.
 *
 * <p>Also supports {@code --rebuild-projections}: rebuild every read model from
 * base tables and exit (the DR path and the projector's correctness oracle).
 */
@Component
@Order(1)
public class Seeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Seeder.class);

    private final JdbcTemplate jdbc;
    private final ProjectorService projector;

    public Seeder(JdbcTemplate jdbc, ProjectorService projector) {
        this.jdbc = jdbc;
        this.projector = projector;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAgents();
        seedRelationTypes();
        seedSources();
        if (args.containsOption("rebuild-projections")) {
            int n = projector.rebuildAll();
            log.info("rebuild-projections complete: {} entities recomputed", n);
        }
    }

    private void seedAgents() {
        upsertAgent("scholar", "human", "The Scholar");
        upsertAgent("pipeline", "pipeline", "Ingestion Pipeline");
        upsertAgent("model", "model", "Analytics Model");
    }

    private void upsertAgent(String slug, String kind, String name) {
        jdbc.update("INSERT INTO agent(kind, slug, display_name) VALUES (?,?,?) "
                + "ON CONFLICT (slug) DO NOTHING", kind, slug, name);
    }

    private void seedRelationTypes() {
        // slug, label, category, range_kind, symmetric
        rt("associated-with", "associated with", "relationship", "entity", false);
        rt("corresponded-with", "corresponded with", "relationship", "entity", false);
        rt("same-as", "same as", "relationship", "entity", true);
        rt("has-name", "has name", "attribute", "literal", false);
        rt("born", "born", "attribute", "literal", false);
        rt("died", "died", "attribute", "literal", false);
        rt("has-gender", "has gender", "attribute", "literal", false);
        rt("has-description", "has description", "attribute", "literal", false);
        rt("has-external-id", "has external id", "attribute", "literal", false);
    }

    private void rt(String slug, String label, String category, String rangeKind, boolean symmetric) {
        jdbc.update("INSERT INTO relation_type(slug, label, category, range_kind, is_symmetric) VALUES (?,?,?,?,?) "
                + "ON CONFLICT (slug) DO NOTHING", slug, label, category, rangeKind, symmetric);
    }

    private void seedSources() {
        // SDFB: license UNCONFIRMED — the gate defaults closed (I8, §11).
        jdbc.update("INSERT INTO source(slug, title, version, retrieval_uri, license, license_confirmed) "
                + "VALUES (?,?,?,?,?,false) ON CONFLICT (slug) DO NOTHING",
                "sdfb-2017-10-13", "Six Degrees of Francis Bacon", "2017-10-13",
                "http://www.sixdegreesoffrancisbacon.com/", "SDFB non-commercial (statement of intent, unconfirmed)");
        // Synthetic fixture: invented data, safe to expose.
        jdbc.update("INSERT INTO source(slug, title, version, retrieval_uri, license, license_confirmed) "
                + "VALUES (?,?,?,?,?,true) ON CONFLICT (slug) DO NOTHING",
                "synth-fixture", "Synthetic fixture (invented)", "1",
                null, "synthetic — invented data, not source-derived");
    }
}
