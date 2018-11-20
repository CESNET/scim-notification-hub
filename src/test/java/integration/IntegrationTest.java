package integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.ManagerImpl;
import core.ScimEventNotification;
import core.SubscriptionModeEnum;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.StringUtils;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Integration test on Manager class with large load of data.
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
    private static final String FEEDmail = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String FEEDedu = "https://perun.cesnet.cz/scim-notification/eduroam/eduroam_radius";
    private static final String FEEDvpn = "https://perun.cesnet.cz/scim-notification/storage-fss.ics.muni.cz/openvpn";
    private static final String SBSC1_ID = "id";
    private static final String SBSC2_ID = "id2";
    private static final String SBSC3_ID = "id3";

    private List<String> senJsons;
    private List<ScimEventNotification> sens;

    @Before
    public void setUp() throws Exception {
        // load sen objects from files
        ObjectMapper mapper = new ObjectMapper();
        senJsons = new ArrayList<>();
        sens = new ArrayList<>();
        for (String fileName : FILE_NAMES) {
            List<String> jsonLines = Files.readAllLines(Paths.get(ClassLoader.getSystemResource(fileName).toURI()), Charset.defaultCharset());
            String senJson = StringUtils.collectionToDelimitedString(jsonLines, "\n");
            senJsons.add(senJson);
            sens.add(mapper.readValue(senJson, ScimEventNotification.class));
        }

        // set up mocks
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        Resource drop = new ClassPathResource("sql/deleteTables.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), drop);
    }

    @Test
    public void integrationTest() throws Exception {
        // two feeds and two subscribers
        manager.newSubscription(SBSC1_ID, FEEDmail, SubscriptionModeEnum.poll, FEEDmail);
        manager.newSubscription(SBSC1_ID, FEEDedu, SubscriptionModeEnum.webCallback, FEEDedu);
        manager.newSubscription(SBSC2_ID, FEEDmail, SubscriptionModeEnum.poll, FEEDmail);
        manager.newSubscription(SBSC2_ID, FEEDvpn, SubscriptionModeEnum.poll, FEEDvpn);

        // first message to feed mail and edu
        manager.newMessage(senJsons.get(0));

        // verify webcallback
        verify(manager).webCallbackSend(eq(new HashSet<>(Arrays.asList(FEEDedu))), any(ScimEventNotification.class));

        // poll of the first subscriber
        Set<ScimEventNotification> toSend = manager.poll(SBSC1_ID);
        assertEquals(1, toSend.size());
        ScimEventNotification returned = toSend.iterator().next();
        sens.get(0).setId(returned.getId());
        assertEquals(sens.get(0), toSend.iterator().next());

        // second message
        manager.newMessage(senJsons.get(1));

        // remove second subscription
        manager.removeSubscription(SBSC2_ID, FEEDmail);

        // check message 2 is prepared for SBSC2
        toSend = manager.poll(SBSC2_ID);
        returned = toSend.iterator().next();
        sens.get(1).setId(returned.getId());
        assertEquals(new HashSet<>(Arrays.asList(sens.get(1))), toSend);

        // add next subscriber
        manager.newSubscription(SBSC3_ID, FEEDmail, SubscriptionModeEnum.poll, FEEDmail);

        // new message to feed mail
        manager.newMessage(senJsons.get(3));

        // check msg 3 was returned to SBSC3
        toSend = manager.poll(SBSC3_ID);
        returned = toSend.iterator().next();
        sens.get(3).setId(returned.getId());
        assertEquals(new HashSet<>(Arrays.asList(sens.get(3))), toSend);

        // check no messages are prepared for SBSC2
        toSend = manager.poll(SBSC2_ID);
        assertEquals(new HashSet<>(), toSend);

        // next message
        Mockito.reset(manager);
        manager.newMessage(senJsons.get(2));
        // verify webcallback
        verify(manager).webCallbackSend(eq(new HashSet<>(Arrays.asList(FEEDedu))), any(ScimEventNotification.class));

        // remove subscriber
        manager.removeSubscriber(SBSC3_ID);

        // check
        try {
            manager.poll(SBSC3_ID);
        } catch (IllegalArgumentException e) {
            // ok

            // poll for the subscriber 2
            toSend = manager.poll(SBSC2_ID);
            assertEquals(1, toSend.size());
            returned = toSend.iterator().next();
            sens.get(2).setId(returned.getId());
            assertEquals(sens.get(2), toSend.iterator().next());

            // poll for the subscriber 1
            toSend = manager.poll(SBSC1_ID);
            assertEquals(new HashSet<>(Arrays.asList(sens.get(3), sens.get(1))), toSend);
            return;
        }
        fail("Subscriber 3 should have been removed");
    }

}
