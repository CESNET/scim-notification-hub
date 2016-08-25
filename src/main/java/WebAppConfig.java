import config.SpringConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Configuration of the web application.
 * Using Spring Boot and Spring Web to run the app.
 *
 * @author Jiri Mauritz
 */
@Configuration
@Import(SpringConfig.class)
@ComponentScan(basePackages = "rest")
@PropertySource("file:/etc/scim-notification/web.properties")
@EnableWebMvc
public class WebAppConfig {

}
