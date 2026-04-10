package io.mokenela.transactionaggregator.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Explicit database wiring for non-dev profiles.
 *
 * Spring Boot 4.x does not auto-configure a JDBC DataSource when R2DBC is
 * present, so FlywayAutoConfiguration never fires. We wire the chain manually:
 *   DataSource (JDBC) → Flyway.migrate() → ConnectionFactory (R2DBC)
 *
 * This guarantees schema migrations complete before any R2DBC query runs.
 */
@Configuration
@Profile("!dev")
class PostgresConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.r2dbc.url}")
    private String r2dbcUrl;

    @Bean
    @Primary
    DataSource dataSource() {
        return DataSourceBuilder.create()
                .url(jdbcUrl)
                .username(username)
                .password(password)
                .build();
    }

    @Bean
    Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    @Primary
    ConnectionFactory connectionFactory(Flyway flyway) {
        return ConnectionFactories.get(
                ConnectionFactoryOptions.parse(r2dbcUrl)
                        .mutate()
                        .option(ConnectionFactoryOptions.USER, username)
                        .option(ConnectionFactoryOptions.PASSWORD, password)
                        .build());
    }
}
