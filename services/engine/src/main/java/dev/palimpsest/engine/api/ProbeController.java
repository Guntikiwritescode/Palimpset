package dev.palimpsest.engine.api;

import dev.palimpsest.engine.projector.ProjectorService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Probes and the projector maintenance endpoint (rebuild-projections, §6.3). */
@RestController
@RequestMapping("/api/v1")
public class ProbeController {

    private final JdbcTemplate jdbc;
    private final ProjectorService projector;

    public ProbeController(JdbcTemplate jdbc, ProjectorService projector) {
        this.jdbc = jdbc;
        this.projector = projector;
    }

    @GetMapping("/healthz")
    public Map<String, Object> healthz() {
        return Map.of("status", "ok");
    }

    @GetMapping("/readyz")
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

    @PostMapping("/admin/rebuild-projections")
    public Dtos.Envelope<Map<String, Object>> rebuild(HttpServletRequest req) {
        int n = projector.rebuildAll();
        return Dtos.Envelope.of(Map.of("entitiesRecomputed", n), ApiSupport.meta(req));
    }
}
