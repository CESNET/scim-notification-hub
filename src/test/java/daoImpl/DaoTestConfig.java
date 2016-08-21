package daoImpl;

import config.SpringConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Configuration for the Core + DAO layer unit testing.
 * Database operations enabled.
 *
 * @author Jiri Mauritz
 */
@Configuration
@Import(SpringConfig.class)
public class DaoTestConfig {

    @Bean
    public DataSource dataSource() {
        /* ======== test in-memory DB - overriding production configuration ======== */
        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
        return builder
                .setType(EmbeddedDatabaseType.DERBY)
                .build();
    }
}
