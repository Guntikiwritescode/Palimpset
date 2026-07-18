package dev.palimpsest.engine.api;

import dev.palimpsest.engine.config.AppProperties;
import dev.palimpsest.engine.store.ClaimStore;
import dev.palimpsest.engine.store.EntityStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Entity reads: view, lookup, ego-network (the slider), filtered claims, pair dossier, random. */
@RestController
@RequestMapping("/api/v1/entities")
public class EntityController {

    private final EntityStore entities;
    private final ClaimStore claims;
    private final AppProperties props;

    public EntityController(EntityStore entities, ClaimStore claims, AppProperties props) {
        this.entities = entities;
        this.claims = claims;
        this.props = props;
    }

    @GetMapping("/{id}")
    public Dtos.Envelope<Dtos.EntityViewDto> view(@PathVariable long id,
                                                  @RequestParam(defaultValue = "raw") String resolution,
                                                  HttpServletRequest req) {
        var v = entities.view(id).orElseThrow(() -> ApiException.notFound("no entity " + id));
        return Dtos.Envelope.of(v, ApiSupport.meta(req));
    }

    @GetMapping("/lookup")
    public Dtos.Envelope<Dtos.EntitySummaryDto> lookup(@RequestParam String authority,
                                                       @RequestParam String externalId,
                                                       HttpServletRequest req) {
        var s = entities.lookup(authority, externalId)
                .orElseThrow(() -> ApiException.notFound("no entity for " + authority + ":" + externalId));
        return Dtos.Envelope.of(s, ApiSupport.meta(req));
    }

    @GetMapping("/random")
    public Dtos.Envelope<Dtos.EntitySummaryDto> random(@RequestParam(required = false) String type,
                                                       @RequestParam(required = false, defaultValue = "1") int minScoredDegree,
                                                       HttpServletRequest req) {
        var s = entities.random(type, minScoredDegree)
                .orElseThrow(() -> ApiException.notFound("no qualifying entity"));
        return Dtos.Envelope.of(s, ApiSupport.meta(req));
    }

    @GetMapping("/{id}/network")
    public Dtos.Envelope<Dtos.NetworkDto> network(@PathVariable long id,
            @RequestParam(required = false) Double minConfidence,
            @RequestParam(required = false) String windowStart,
            @RequestParam(required = false) String windowEnd,
            @RequestParam(required = false) String temporalMode,
            @RequestParam(required = false, defaultValue = "false") boolean includeUnscored,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest req) {
        int cap = props.getNetwork().getEdgeCap();
        var p = new ClaimStore.NetworkParams(
                Params.expand(windowStart, false), Params.expand(windowEnd, true),
                Params.confidence(minConfidence), includeUnscored, Params.temporalMode(temporalMode),
                Params.clampLimit(limit, cap, cap));
        var result = claims.network(id, p).orElseThrow(() -> ApiException.notFound("no entity " + id));
        var meta = new Dtos.Meta(ApiSupport.requestId(req), result.edges().size(), result.truncated(),
                result.counts(), null, null, "asserted");
        return Dtos.Envelope.of(new Dtos.NetworkDto(result.focus(), result.edges()), meta);
    }

    @GetMapping("/{id}/claims")
    public Dtos.Envelope<java.util.List<Dtos.ClaimDetailDto>> entityClaims(@PathVariable long id,
            @RequestParam(required = false) String predicate,
            @RequestParam(required = false, defaultValue = "any") String role,
            @RequestParam(required = false, defaultValue = "asserted") String status,
            @RequestParam(required = false) Double minConfidence,
            @RequestParam(required = false, defaultValue = "false") boolean includeUnscored,
            @RequestParam(required = false) String windowStart,
            @RequestParam(required = false) String windowEnd,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest req) {
        int lim = Params.clampLimit(limit, 50, 500);
        Long after = cursor == null ? null : Long.parseLong(cursor);
        var page = claims.listEntityClaims(id, predicate, role, status, minConfidence, includeUnscored,
                Params.expand(windowStart, false), Params.expand(windowEnd, true), after, lim);
        var meta = new Dtos.Meta(ApiSupport.requestId(req), page.claims().size(), null, null,
                new Dtos.Page(cursor, lim, page.nextCursor()), null, status);
        return Dtos.Envelope.of(page.claims(), meta);
    }

    @GetMapping("/{a}/relations/{b}")
    public Dtos.Envelope<Dtos.PairDossierDto> pair(@PathVariable long a, @PathVariable long b,
            @RequestParam(required = false, defaultValue = "any") String status, HttpServletRequest req) {
        boolean assertedOnly = "asserted".equalsIgnoreCase(status);
        var meta = new Dtos.Meta(ApiSupport.requestId(req), null, null, null, null, null, status);
        return Dtos.Envelope.of(claims.pair(a, b, assertedOnly), meta);
    }
}
