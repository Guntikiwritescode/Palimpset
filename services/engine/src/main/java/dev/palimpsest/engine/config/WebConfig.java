package dev.palimpsest.engine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS (pinned to the explorer origin, §7) and the OpenAPI document metadata. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    public WebConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.getCors().getExplorerOrigin())
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id");
    }

    @Bean
    public OpenAPI palimpsestOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PALIMPSEST Engine API")
                .version("v1")
                .description("The ontology engine — sole write authority. Reads program against the "
                        + "generated TypeScript SDK. license_confirmed=false content is internal-use only.")
                .license(new License().name("internal — see repository LICENSE decision")));
    }
}
