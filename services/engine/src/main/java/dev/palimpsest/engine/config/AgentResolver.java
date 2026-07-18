package dev.palimpsest.engine.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Maps bearer tokens to agent slugs and resolves slugs to agent ids (cached). */
@Component
public class AgentResolver {

    private final Map<String, String> tokenToSlug = new HashMap<>();
    private final Map<String, Long> slugToId = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;

    public AgentResolver(AppProperties props, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        register(props.getAuth().getScholarToken(), "scholar");
        register(props.getAuth().getPipelineToken(), "pipeline");
        register(props.getAuth().getModelToken(), "model");
    }

    private void register(String token, String slug) {
        if (token != null && !token.isBlank()) {
            tokenToSlug.put(token, slug);
        }
    }

    public Optional<AgentPrincipal> principalForToken(String token) {
        String slug = tokenToSlug.get(token);
        if (slug == null) {
            return Optional.empty();
        }
        Long id = slugToId.computeIfAbsent(slug, s ->
                jdbc.query("SELECT id FROM agent WHERE slug = ?", (rs, n) -> rs.getLong(1), s)
                        .stream().findFirst().orElse(null));
        if (id == null) {
            return Optional.empty();
        }
        String kind = jdbc.query("SELECT kind FROM agent WHERE slug = ?", (rs, n) -> rs.getString(1), slug)
                .stream().findFirst().orElse("human");
        return Optional.of(new AgentPrincipal(id, slug, kind));
    }
}
