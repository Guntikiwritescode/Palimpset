package dev.palimpsest.engine.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.palimpsest.engine.actions.ActionService;
import dev.palimpsest.engine.config.AgentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Governed write-back actions (Flow D/F). Auth enforced by the filter. */
@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {

    private final ActionService actions;

    public ActionController(ActionService actions) {
        this.actions = actions;
    }

    public record DisputeReq(long claimId, String ground, String reason) {
    }

    public record UndisputeReq(long claimId) {
    }

    public record AdjustReq(long claimId, JsonNode confidence, String reason) {
    }

    public record SupersedeReq(long claimId, JsonNode replacement, String reason) {
    }

    public record MergeReq(List<Long> memberEntityIds, String rationale) {
    }

    @PostMapping("/dispute")
    public Dtos.Envelope<Map<String, Object>> dispute(@RequestBody DisputeReq r, HttpServletRequest req) {
        forbidTimeTravel(req);
        return env(actions.dispute(r.claimId(), r.ground(), r.reason(), agentId(req)), req);
    }

    @PostMapping("/undispute")
    public Dtos.Envelope<Map<String, Object>> undispute(@RequestBody UndisputeReq r, HttpServletRequest req) {
        forbidTimeTravel(req);
        return env(actions.undispute(r.claimId(), agentId(req)), req);
    }

    @PostMapping("/adjust-confidence")
    public Dtos.Envelope<Map<String, Object>> adjust(@RequestBody AdjustReq r, HttpServletRequest req) {
        forbidTimeTravel(req);
        return env(actions.adjustConfidence(r.claimId(), r.confidence(), r.reason(), agentId(req)), req);
    }

    @PostMapping("/supersede")
    public Dtos.Envelope<Map<String, Object>> supersede(@RequestBody SupersedeReq r, HttpServletRequest req) {
        forbidTimeTravel(req);
        return env(actions.supersede(r.claimId(), r.replacement(), r.reason(), agentId(req)), req);
    }

    @PostMapping("/assert-claim")
    public Dtos.Envelope<Map<String, Object>> assertClaim(@RequestBody JsonNode claim, HttpServletRequest req) {
        forbidTimeTravel(req);
        return env(actions.assertClaim(claim, agentId(req)), req);
    }

    @PostMapping("/merge-entities")
    public Dtos.Envelope<Map<String, Object>> merge(@RequestBody MergeReq r, HttpServletRequest req) {
        forbidTimeTravel(req);
        return env(actions.mergeEntities(r.memberEntityIds(), r.rationale(), agentId(req)), req);
    }

    // W8: writing from a time-travel context is nonsense — reject 409.
    private void forbidTimeTravel(HttpServletRequest req) {
        if (req.getParameter("asOfSystem") != null || req.getHeader("X-Palimpsest-AsOf") != null) {
            throw ApiException.conflict("actions cannot be submitted from a time-travel (asOfSystem) context");
        }
    }

    private long agentId(HttpServletRequest req) {
        AgentPrincipal p = ApiSupport.agent(req);
        if (!p.isAuthenticated()) {
            throw ApiException.forbidden("actions require a bearer token");
        }
        return p.agentId();
    }

    private Dtos.Envelope<Map<String, Object>> env(Map<String, Object> data, HttpServletRequest req) {
        return Dtos.Envelope.of(data, ApiSupport.meta(req));
    }
}
