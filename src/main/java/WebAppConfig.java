import config.SpringConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
@EnableWebMvc
public class WebAppConfig {

}
