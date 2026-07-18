package dev.palimpsest.engine.api;

import dev.palimpsest.engine.projector.ProjectorService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Probes and the projector maintenance endpoint (rebuild-projections, §6.3). */
@RestController
public class ProbeController {

    private final JdbcTemplate jdbc;
    private final ProjectorService projector;

    public ProbeController(JdbcTemplate jdbc, ProjectorService projector) {
        this.jdbc = jdbc;
        this.projector = projector;
    }

    // Probes answer at BOTH root (deploy/base/palimpsest/*-deployment.yaml probes and
    // scripts/kind_smoke.sh curl `${ENGINE}/readyz` at root) and under the /api/v1 base
    // (BUILD-CONTRACT §4). Before WP-R1 they were only under /api/v1, so the k8s
    // readiness/liveness probes 500'd and the WP5 smoke could never reach Ready.
    @GetMapping({"/healthz", "/api/v1/healthz"})
    public Map<String, Object> healthz() {
        return Map.of("status", "ok");
    }

    @GetMapping({"/readyz", "/api/v1/readyz"})
    public ResponseEntity<Map<String, Object>> readyz() {
        try {
            // ready = DB reachable + migrations current (base tables present).
            jdbc.queryForObject("SELECT 1 FROM claim_status_current LIMIT 1", Integer.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException empty) {
            // table exists but empty — still ready
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "not-ready", "detail", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("status", "ready", "outboxLag", projector.outboxLag()));
    }

    @PostMapping("/api/v1/admin/rebuild-projections")
    public Dtos.Envelope<Map<String, Object>> rebuild(HttpServletRequest req) {
        int n = projector.rebuildAll();
        return Dtos.Envelope.of(Map.of("entitiesRecomputed", n), ApiSupport.meta(req));
    }
}
