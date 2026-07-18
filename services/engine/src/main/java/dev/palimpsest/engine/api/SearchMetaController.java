package dev.palimpsest.engine.api;

import dev.palimpsest.engine.store.SearchStore;
import dev.palimpsest.engine.store.StatsStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Name search, registries, corpus stats (honesty page), and import-run history. */
@RestController
@RequestMapping("/api/v1")
public class SearchMetaController {

    private final SearchStore search;
    private final StatsStore stats;

    public SearchMetaController(SearchStore search, StatsStore stats) {
        this.search = search;
        this.stats = stats;
    }

    @GetMapping("/search/entities")
    public Dtos.Envelope<List<Dtos.EntitySummaryDto>> search(@RequestParam String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest req) {
        if (q == null || q.trim().length() < 2) {
            throw ApiException.badRequest("q must be at least 2 characters");
        }
        int lim = Params.clampLimit(limit, 20, 100);
        int offset = cursor == null ? 0 : Integer.parseInt(cursor);
        var page = search.search(q.trim(), type, offset, lim);
        var meta = new Dtos.Meta(ApiSupport.requestId(req), page.results().size(), null, null,
                new Dtos.Page(cursor, lim, page.nextCursor()), null, null);
        return Dtos.Envelope.of(page.results(), meta);
    }

    @GetMapping("/meta/relation-types")
    public Dtos.Envelope<List<Dtos.RelationTypeDto>> relationTypes(HttpServletRequest req) {
        return Dtos.Envelope.of(stats.relationTypes(), ApiSupport.meta(req));
    }

    @GetMapping("/meta/sources")
    public Dtos.Envelope<List<Dtos.SourceDto>> sources(HttpServletRequest req) {
        return Dtos.Envelope.of(stats.sources(), ApiSupport.meta(req));
    }

    @GetMapping("/meta/agents")
    public Dtos.Envelope<List<Dtos.AgentDto>> agents(HttpServletRequest req) {
        return Dtos.Envelope.of(stats.agents(), ApiSupport.meta(req));
    }

    @GetMapping("/stats/summary")
    public Dtos.Envelope<Dtos.StatsDto> stats(HttpServletRequest req) {
        return Dtos.Envelope.of(stats.summary(), ApiSupport.meta(req));
    }

    @GetMapping("/runs")
    public Dtos.Envelope<List<Dtos.RunDto>> runs(HttpServletRequest req) {
        return Dtos.Envelope.of(stats.runs(), ApiSupport.meta(req));
    }

    @GetMapping("/runs/{id}")
    public Dtos.Envelope<Dtos.RunDto> run(@PathVariable String id, HttpServletRequest req) {
        var r = stats.run(id).orElseThrow(() -> ApiException.notFound("no run " + id));
        return Dtos.Envelope.of(r, ApiSupport.meta(req));
    }
}
