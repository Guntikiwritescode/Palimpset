package dev.palimpsest.engine.api;

import dev.palimpsest.engine.config.AgentPrincipal;
import dev.palimpsest.engine.config.Filters;
import jakarta.servlet.http.HttpServletRequest;

/** Small helpers for pulling the request id and authenticated agent off a request. */
public final class ApiSupport {

    private ApiSupport() {
    }

    public static String requestId(HttpServletRequest req) {
        Object id = req.getAttribute(Filters.RequestIdFilter.ATTR);
        return id == null ? null : id.toString();
    }

    public static AgentPrincipal agent(HttpServletRequest req) {
        Object a = req.getAttribute(AgentPrincipal.REQUEST_ATTR);
        return a instanceof AgentPrincipal p ? p : new AgentPrincipal(null, null, null);
    }

    public static Dtos.Meta meta(HttpServletRequest req) {
        return Dtos.Meta.req(requestId(req));
    }
}
