package core;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration of the core unit tests.
 * Leaves blank DAO layer using mocks without the database.
 *
 * @author Jiri Mauritz
 */
@Configuration
@ComponentScan(basePackages = {
        "core",
        "daoImpl"
})
public class CoreTestConfig {

}
