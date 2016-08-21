package integration;

import config.SpringConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Configuration for the integration tests.
 * Dao layer and database are active.
 *
 * @author Jiri Mauritz
 */
@Configuration
@Import(SpringConfig.class)
@ComponentScan(basePackages = {
        "core",
        "daoImpl"
})
public class IntegrationTestConfig {

    @Bean
    public DataSource dataSource() {
        /* ======== test in-memory DB - overriding production configuration ======== */
        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
        return builder
                .setType(EmbeddedDatabaseType.DERBY)
                .build();
    }
}
