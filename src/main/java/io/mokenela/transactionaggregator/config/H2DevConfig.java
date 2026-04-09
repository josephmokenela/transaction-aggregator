package io.mokenela.transactionaggregator.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
@Profile("dev")
class H2DevConfig {

    private static final String JDBC_URL =
            "jdbc:h2:mem:transaction_aggregator;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";
    private static final String R2DBC_URL =
            "r2dbc:h2:mem:///transaction_aggregator;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;USER=sa;PASSWORD=";

    @Bean
    @Primary
    DataSource devDataSource() {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(JDBC_URL);
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    // Run Flyway manually so we control the ordering — migrations complete
    // before the R2DBC connection factory is created
    @Bean
    Flyway devFlyway(DataSource devDataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(devDataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    @Primary
    ConnectionFactory devConnectionFactory(Flyway devFlyway) {
        return ConnectionFactories.get(R2DBC_URL);
    }
}
