package io.mokenela.transactionaggregator.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI / Swagger UI metadata.
 *
 * <p>The actual endpoint discovery and schema generation is handled automatically by
 * {@code springdoc-openapi-starter-webflux-ui}. This class only supplies the human-readable
 * metadata shown at the top of the Swagger UI page.</p>
 *
 * <ul>
 *   <li>API docs (JSON): {@code GET /api-docs}</li>
 *   <li>Swagger UI:       {@code GET /swagger-ui.html}</li>
 * </ul>
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Transaction Aggregator API",
                version = "1.0",
                description = """
                        Reactive REST API for aggregating customer financial transactions
                        from multiple data sources, with automatic categorisation,
                        period-based aggregation, and flexible search.
                        """,
                contact = @Contact(name = "Joseph Mokenela"),
                license = @License(name = "MIT")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local development"),
                @Server(url = "http://transaction-aggregator:8080", description = "Docker Compose")
        }
)
@Configuration
public class OpenApiConfig {
}
