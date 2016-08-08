package config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Created by xmauritz on 8/8/16.
 */
@Configuration
@Import(SpringConfig.class)
public class TestConfig {

    @Bean
    public DataSource dataSource() {
        /* ======== test in-memory DB - overriding production configuration ======== */
        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
        return builder
                .setType(EmbeddedDatabaseType.DERBY)
                .build();
    }
}
