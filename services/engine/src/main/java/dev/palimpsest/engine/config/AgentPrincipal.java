package dev.palimpsest.engine.config;

/** The authenticated actor for a request (ADR-005). Null slug ⇒ anonymous read. */
public record AgentPrincipal(Long agentId, String slug, String kind) {
    public static final String REQUEST_ATTR = "palimpsest.agent";

    public boolean isAuthenticated() {
        return agentId != null;
    }
}
