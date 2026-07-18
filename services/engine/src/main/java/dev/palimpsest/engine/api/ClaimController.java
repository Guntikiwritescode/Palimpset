package dev.palimpsest.engine.api;

import dev.palimpsest.engine.store.ClaimStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Claim detail, evidence (Flow C), and the event audit trail. */
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

    private final ClaimStore claims;

    public ClaimController(ClaimStore claims) {
        this.claims = claims;
    }

    @GetMapping("/{id}")
    public Dtos.Envelope<Dtos.ClaimDetailDto> claim(@PathVariable long id, HttpServletRequest req) {
        var c = claims.claimDetail(id).orElseThrow(() -> ApiException.notFound("no claim " + id));
        return Dtos.Envelope.of(c, ApiSupport.meta(req));
    }

    @GetMapping("/{id}/evidence")
    public Dtos.Envelope<Dtos.EvidenceDto> evidence(@PathVariable long id, HttpServletRequest req) {
        var e = claims.evidence(id).orElseThrow(() -> ApiException.notFound("no claim " + id));
        return Dtos.Envelope.of(e, ApiSupport.meta(req));
    }

    @GetMapping("/{id}/history")
    public Dtos.Envelope<Dtos.HistoryDto> history(@PathVariable long id, HttpServletRequest req) {
        return Dtos.Envelope.of(claims.history(id), ApiSupport.meta(req));
    }
}
