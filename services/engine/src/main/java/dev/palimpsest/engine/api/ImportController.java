package dev.palimpsest.engine.api;

import dev.palimpsest.engine.config.AgentPrincipal;
import dev.palimpsest.engine.importer.ImportService;
import dev.palimpsest.engine.projector.ProjectorService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bulk import endpoint (Flow A). Streams NDJSON, one request = one batch.
 * Requires a bearer token (the engine attributes the assert events to it).
 * After the batch commits, the projector drains the outbox so reads are fresh.
 */
@RestController
@RequestMapping("/api/v1/import")
public class ImportController {

    private static final int MAX_LINES = 5000;
    private static final long MAX_BYTES = 32L * 1024 * 1024;

    private final ImportService importer;
    private final ProjectorService projector;

    public ImportController(ImportService importer, ProjectorService projector) {
        this.importer = importer;
        this.projector = projector;
    }

    @PostMapping("/batches")
    public ResponseEntity<Dtos.Envelope<Dtos.ImportReportDto>> batch(
            @RequestParam(required = false) String kind,
            HttpServletRequest req) throws Exception {

        AgentPrincipal agent = ApiSupport.agent(req);
        if (!agent.isAuthenticated()) {
            throw ApiException.forbidden("import requires a bearer token");
        }
        String kindStr = kind != null ? kind : req.getHeader("X-Palimpsest-Kind");
        ImportService.Kind k;
        if ("entities".equalsIgnoreCase(kindStr)) {
            k = ImportService.Kind.ENTITIES;
        } else if ("claims".equalsIgnoreCase(kindStr)) {
            k = ImportService.Kind.CLAIMS;
        } else {
            throw ApiException.badRequest("kind must be 'entities' or 'claims'");
        }
        String runId = header(req, "X-Palimpsest-Run", "adhoc-run");
        String sourceSlug = header(req, "X-Palimpsest-Source", "unknown");
        int batchNo = parseInt(req.getHeader("X-Palimpsest-Batch"), 1);

        List<String> lines = new ArrayList<>();
        long bytes = 0;
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                bytes += line.length() + 1;
                if (bytes > MAX_BYTES) {
                    throw ApiException.badRequest("import batch exceeds 32 MB");
                }
                if (!line.isBlank()) {
                    lines.add(line);
                }
                if (lines.size() > MAX_LINES) {
                    throw ApiException.badRequest("import batch exceeds " + MAX_LINES + " lines");
                }
            }
        }

        var report = importer.importBatch(k, lines, runId, sourceSlug, agent.agentId(), batchNo);
        projector.drainAll();

        var meta = new Dtos.Meta(ApiSupport.requestId(req), report.received(), null, null, null, null, null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Dtos.Envelope.of(report, meta));
    }

    private static String header(HttpServletRequest req, String name, String def) {
        String v = req.getHeader(name);
        return v == null || v.isBlank() ? def : v;
    }

    private static int parseInt(String v, int def) {
        try {
            return v == null ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
