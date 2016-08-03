package core;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test of the Manager class.
 *
 * @author Jiri Mauritz
 */
public class ManagerTest {

    private static final String[] FILE_NAMES = new String[]{"sen1.json", "sen2.json", "sen3.json"};
    private static final String FEED1 = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String FEED2 = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/openvpn";
    private static final String SBSC_ID = "id";

    private List<String> sens = new ArrayList<String>();
    private static final Manager manager = new Manager();

    @Before
    public void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // load sen objects from files
        for (String fileName : FILE_NAMES) {
            List<String> jsonLines = Files.readAllLines(Paths.get(ClassLoader.getSystemResource(fileName).toURI()));
            sens.add(String.join("\n", jsonLines));
        }
    }

    @Test
    public void newMessage() throws Exception {
        manager.newMessage(sens.get(0));
        // TODO - mock the manager and check if webCallback was called
    }

    @Test
    public void newSubscription() throws Exception {
        manager.newSubscription(SBSC_ID, FEED1, SubscriptionModeEnum.poll, FEED1);
    }

    @Test
    public void removeSubscription() throws Exception {

    }

    @Test
    public void removeSubscriber() throws Exception {

    }

    @Test
    public void poll() throws Exception {

    }

}