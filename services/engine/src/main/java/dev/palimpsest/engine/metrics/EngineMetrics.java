package dev.palimpsest.engine.metrics;

import dev.palimpsest.engine.projector.ProjectorService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom engine meters (ARCHITECTURE §3.4, §5.1; WP5 observability). These are the
 * series the Grafana dashboards
 * ({@code deploy/base/observability/dashboards/palimpsest-ingest.json}) and
 * {@code scripts/kind_smoke.sh} contract on — before WP-R1 they were queried but
 * never emitted (evidence-gap finding F1). They register against the Micrometer
 * {@code PrometheusMeterRegistry} that {@code spring-boot-starter-actuator}
 * auto-configures; Prometheus scrapes them at {@code /actuator/prometheus}.
 *
 * <p>Emitted Prometheus names (Micrometer's naming convention lowercases, replaces
 * dots with underscores, and appends {@code _total} to counters):
 * <pre>
 *   palimpsest.import.claims             -> palimpsest_import_claims_total       (claims INSERTED, Flow A)
 *   palimpsest.import.duplicates         -> palimpsest_import_duplicates_total
 *   palimpsest.import.superseded         -> palimpsest_import_superseded_total
 *   palimpsest.import.rejected           -> palimpsest_import_rejected_total
 *   palimpsest.outbox.pending.rows       -> palimpsest_outbox_pending_rows       (gauge; 0 == caught up)
 *   palimpsest.outbox.oldest.age.seconds -> palimpsest_outbox_oldest_age_seconds (gauge)
 * </pre>
 *
 * <p>DECISION (F1): {@code palimpsest_import_claims_total} counts claims
 * <em>inserted</em> (accepted and newly persisted) on the bulk-import path — the
 * meaning the dashboard's "inserted" legend already assumes. Duplicates, superseded
 * and rejected are their own counters, so the import-outcomes panel and the metrics
 * agree. Entity-import batches and single-claim writes (Flow-not-A) deliberately do
 * not touch these import counters; the panels live under the dashboard's
 * "Import (Flow A)" row.
 */
@Component
public class EngineMetrics {

    private final Counter claimsInserted;
    private final Counter duplicates;
    private final Counter superseded;
    private final Counter rejected;

    public EngineMetrics(MeterRegistry registry, ProjectorService projector) {
        this.claimsInserted = Counter.builder("palimpsest.import.claims")
                .description("Claims inserted via the bulk-import path (Flow A)")
                .register(registry);
        this.duplicates = Counter.builder("palimpsest.import.duplicates")
                .description("Claim-import lines skipped as duplicates (idempotent re-ingest)")
                .register(registry);
        this.superseded = Counter.builder("palimpsest.import.superseded")
                .description("Support records superseded during claim import (Flow A step 6c)")
                .register(registry);
        this.rejected = Counter.builder("palimpsest.import.rejected")
                .description("Claim-import lines rejected with a per-line error (I6 and schema failures)")
                .register(registry);

        // Gauges read live on the metrics-scrape thread; the projector methods are
        // cheap single-row queries and never throw (they coalesce NULL to 0).
        Gauge.builder("palimpsest.outbox.pending.rows", projector, p -> (double) p.outboxLag())
                .description("Unprocessed transactional-outbox rows (0 == projector caught up)")
                .register(registry);
        // NB: no baseUnit — the name already carries the `.seconds` suffix, and adding
        // baseUnit("seconds") risks a doubled `_seconds_seconds` in the Prometheus name.
        Gauge.builder("palimpsest.outbox.oldest.age.seconds", projector,
                        ProjectorService::oldestUnprocessedAgeSeconds)
                .description("Age of the oldest unprocessed outbox row, in seconds (§3.4 lag SLO)")
                .register(registry);
    }

    /** Record the outcome of one CLAIMS import batch (Flow A). */
    public void recordClaimImport(int inserted, int dup, int sup, int rej) {
        if (inserted > 0) {
            claimsInserted.increment(inserted);
        }
        if (dup > 0) {
            duplicates.increment(dup);
        }
        if (sup > 0) {
            superseded.increment(sup);
        }
        if (rej > 0) {
            rejected.increment(rej);
        }
    }
}
