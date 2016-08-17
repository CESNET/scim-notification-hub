package integration;

import core.*;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

/**
 *
 *
 * @author Jiri Mauritz
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = IntegrationTestConfig.class)
public class IntegrationTest {

    @Inject
    private DataSource dataSource;

    @Inject
    @Spy
    private ManagerImpl manager;

    private static final String[] FILE_NAMES = new String[]{"sen1.json", "sen2.json", "sen3.json", "sen4.json"};
    private static final String FEED1 = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String FEED2 = "https://perun.cesnet.cz/scim-notification/storage-fss.ics.muni.cz/openvpn";
    private static final String SBSC1_ID = "id";
    private static final String SBSC2_ID = "id2";

    private List<String> sens;

    @Before
    public void setUp() throws Exception {
        // load sen objects from files
        sens = new ArrayList<String>();
        for (String fileName : FILE_NAMES) {
            List<String> jsonLines = Files.readAllLines(Paths.get(ClassLoader.getSystemResource(fileName).toURI()));
            sens.add(String.join("\n", jsonLines));
        }

        // set up mocks
        MockitoAnnotations.initMocks(this);

        // load db tables
        Resource create = new ClassPathResource("sql/createTablesDerby.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), create);
    }

    @After
    public void tearDown() throws Exception {
        Resource drop = new ClassPathResource("sql/dropTablesDerby.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), drop);
    }

    @Test
    public void integrationTest() throws Exception {

    }

    private void checkSens(Set<ScimEventNotification> sens, String ... feedUrisArray) {
        assertEquals(feedUrisArray.length, sens.size());
        Set<String> feedUris = new HashSet<String>(Arrays.asList(feedUrisArray));
        Iterator<ScimEventNotification> iter = sens.iterator();
        Set<String> returnedFeedUris = new HashSet<String>();
        while (iter.hasNext()) {
            returnedFeedUris.addAll(iter.next().getFeedUris());
        }
        assertTrue(returnedFeedUris.containsAll(feedUris));
    }
}