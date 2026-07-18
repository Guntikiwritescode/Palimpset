package dev.palimpsest.engine.api;

import dev.palimpsest.engine.actions.PathService;
import dev.palimpsest.engine.store.ActionStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** License-gated export (PP6), the audit feed, and bounded path search (Flow H). */
@RestController
@RequestMapping("/api/v1")
public class ExportEventsPathController {

    private final ActionStore store;
    private final PathService paths;
    private final JdbcTemplate jdbc;

    public ExportEventsPathController(ActionStore store, PathService paths, JdbcTemplate jdbc) {
        this.store = store;
        this.paths = paths;
        this.jdbc = jdbc;
    }

    /**
     * The license gate, machine-enforced: refuse any export that would include
     * content from a source with license_confirmed=false, naming the sources (§5.1,
     * rule 4, PP6). Otherwise stream the asserted claims.
     */
    @GetMapping("/export/claims")
    public ResponseEntity<?> exportClaims(@RequestParam(defaultValue = "ndjson") String format,
                                          HttpServletRequest req) {
        List<String> blocked = store.unconfirmedSourcesInScope();
        if (!blocked.isEmpty()) {
            var pd = org.springframework.http.ProblemDetail.forStatus(org.springframework.http.HttpStatus.FORBIDDEN);
            pd.setTitle("Export blocked by the license gate");
            pd.setDetail("These sources are not license-confirmed and cannot be exported: " + String.join(", ", blocked));
            pd.setProperty("blockedSources", blocked);
            pd.setProperty("requestId", ApiSupport.requestId(req));
            return ResponseEntity.status(403).contentType(MediaType.parseMediaType("application/problem+json")).body(pd);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.id, c.subject_entity_id, c.predicate, c.object_entity_id, cc.confidence_point "
              + "FROM claim c JOIN claim_status_current st ON st.claim_id=c.id AND st.status='asserted' "
              + "LEFT JOIN claim_confidence_current cc ON cc.claim_id=c.id ORDER BY c.id");
        if ("csv".equals(format)) {
            StringBuilder sb = new StringBuilder("claim_id,subject,predicate,object,confidence\n");
            for (var r : rows) {
                sb.append(r.get("id")).append(',').append(r.get("subject_entity_id")).append(',')
                  .append(r.get("predicate")).append(',').append(r.getOrDefault("object_entity_id", ""))
                  .append(',').append(r.getOrDefault("confidence_point", "")).append('\n');
            }
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv")).body(sb.toString());
        }
        StringBuilder sb = new StringBuilder();
        for (var r : rows) {
            sb.append("{\"claimId\":").append(r.get("id")).append(",\"predicate\":\"").append(r.get("predicate"))
              .append("\"}\n");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-ndjson")).body(sb.toString());
    }

    @GetMapping("/events")
    public Dtos.Envelope<List<Dtos.EventDto>> events(@RequestParam(required = false) String since,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit, HttpServletRequest req) {
        int lim = Params.clampLimit(limit, 50, 200);
        Long after = cursor == null ? null : Long.parseLong(cursor);
        var events = store.events(since, after, lim);
        String next = events.size() == lim ? String.valueOf(events.get(events.size() - 1).id()) : null;
        var meta = new Dtos.Meta(ApiSupport.requestId(req), events.size(), null, null,
                new Dtos.Page(cursor, lim, next), null, null);
        return Dtos.Envelope.of(events, meta);
    }

    @GetMapping("/paths")
    public Dtos.Envelope<PathService.PathResult> paths(@RequestParam long from, @RequestParam long to,
            @RequestParam(required = false) Double minConfidence,
            @RequestParam(required = false) String windowStart,
            @RequestParam(required = false) String windowEnd,
            @RequestParam(required = false) String temporalMode,
            @RequestParam(required = false, defaultValue = "4") int maxDepth,
            @RequestParam(required = false, defaultValue = "3") int maxPaths,
            HttpServletRequest req) {
        int depth = Math.min(Math.max(maxDepth, 1), 4);
        int pathCap = Math.min(Math.max(maxPaths, 1), 5);
        String w0 = Params.expand(windowStart, false);
        String w1 = Params.expand(windowEnd, true);
        var result = paths.find(from, to, Params.confidence(minConfidence),
                w0 == null ? "0001-01-01" : w0, w1 == null ? "9999-12-31" : w1, depth, pathCap);
        var meta = new Dtos.Meta(ApiSupport.requestId(req), result.paths().size(), result.truncated(),
                null, null, null, null);
        return Dtos.Envelope.of(result, meta);
    }
}
