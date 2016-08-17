package core;

import config.SpringConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Created by xmauritz on 8/8/16.
 */
@Configuration
@ComponentScan(basePackages = {
        "core",
        "daoImpl"
})
public class CoreTestConfig {

}
