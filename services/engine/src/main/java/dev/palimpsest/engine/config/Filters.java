package dev.palimpsest.engine.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cross-cutting request filters: request id (traceable) and bearer auth (ADR-005). */
public final class Filters {

    private Filters() {
    }

    /** Assigns/propagates a request id into responses and the trace context. */
    @Component
    @Order(1)
    public static class RequestIdFilter extends OncePerRequestFilter {
        public static final String ATTR = "palimpsest.requestId";

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String id = req.getHeader("X-Request-Id");
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            req.setAttribute(ATTR, id);
            res.setHeader("X-Request-Id", id);
            MDC.put("requestId", id);
            try {
                chain.doFilter(req, res);
            } finally {
                MDC.remove("requestId");
            }
        }
    }

    /**
     * Bearer auth. Writes (/import, /actions) require a valid token; reads require
     * one only while the license gate holds (require-auth-for-reads). An
     * authenticated principal is stashed for controllers to attribute events.
     */
    @Component
    @Order(2)
    public static class AuthFilter extends OncePerRequestFilter {

        private final AgentResolver resolver;
        private final boolean requireAuthForReads;

        public AuthFilter(AgentResolver resolver, AppProperties props) {
            this.resolver = resolver;
            this.requireAuthForReads = props.getAuth().isRequireAuthForReads();
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String path = req.getRequestURI();
            String auth = req.getHeader("Authorization");
            AgentPrincipal principal = new AgentPrincipal(null, null, null);
            if (auth != null && auth.startsWith("Bearer ")) {
                var p = resolver.principalForToken(auth.substring(7).trim());
                if (p.isPresent()) {
                    principal = p.get();
                }
            }
            req.setAttribute(AgentPrincipal.REQUEST_ATTR, principal);

            boolean isWrite = path.startsWith("/api/v1/import") || path.startsWith("/api/v1/actions");
            boolean needsAuth = isWrite || (requireAuthForReads && path.startsWith("/api/v1/"));
            if (needsAuth && !principal.isAuthenticated()) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/problem+json");
                res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                        + "\"detail\":\"a valid bearer token is required\"}");
                return;
            }
            chain.doFilter(req, res);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest req) {
            String p = req.getRequestURI();
            return p.startsWith("/actuator") || p.startsWith("/v3/api-docs") || p.startsWith("/swagger-ui")
                    || p.equals("/api/v1/healthz") || p.equals("/api/v1/readyz");
        }
    }
}
