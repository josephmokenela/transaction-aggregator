package io.mokenela.transactionaggregator.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Database wiring for the {@code dev} profile.
 *
 * <p>Uses an H2 in-memory database so no Docker or PostgreSQL installation is needed
 * during local development. The schema and seed data are applied once at startup via
 * {@link ResourceDatabasePopulator} — no Flyway versioning is needed because the
 * in-memory database is recreated fresh on every restart.</p>
 *
 * <p>For docker/prod, {@link PostgresConfig} takes over (active on {@code !dev}).</p>
 */
@Configuration
@Profile("dev")
class H2DevConfig {

    private static final String JDBC_URL =
            "jdbc:h2:mem:transaction_aggregator;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";
    private static final String R2DBC_URL =
            "r2dbc:h2:mem:///transaction_aggregator;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;USER=sa;PASSWORD=";

    /**
     * Creates the H2 DataSource and immediately applies schema + seed data so
     * the database is fully initialised before the R2DBC ConnectionFactory is built.
     */
    @Bean
    @Primary
    DataSource devDataSource() {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(JDBC_URL);
        ds.setUsername("sa");
        ds.setPassword("");

        var populator = new ResourceDatabasePopulator();
        populator.setSeparator(";");
        populator.addScript(new ClassPathResource("h2/schema.sql"));
        populator.addScript(new ClassPathResource("h2/data.sql"));
        DatabasePopulatorUtils.execute(populator, ds);

        return ds;
    }

    /**
     * R2DBC ConnectionFactory — declared after {@code devDataSource} so Spring
     * guarantees the schema is applied before any reactive query runs.
     */
    @Bean
    @Primary
    ConnectionFactory devConnectionFactory(DataSource devDataSource) {
        return ConnectionFactories.get(R2DBC_URL);
    }
}
