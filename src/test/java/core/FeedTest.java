package core;

import org.codehaus.jackson.map.ObjectMapper;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Test for the fundamental class Feed.
 * Using prepared testing instances of SEN.
 *
 * @author Jiri Mauritz
 */
public class FeedTest {

    private static final String[] FILE_NAMES = new String[]{"sen1.json", "sen2.json", "sen3.json"};
    private static final String FEED1 = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String FEED2 = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/openvpn";


    private List<ScimEventNotification> sens = new ArrayList<ScimEventNotification>();
    private Feed feed = new Feed(FEED1);

    @Before
    public void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // load sen objects from files
        for (String fileName : FILE_NAMES) {
            List<String> jsonLines = Files.readAllLines(Paths.get(ClassLoader.getSystemResource(fileName).toURI()), Charset.defaultCharset());
            sens.add(mapper.readValue(StringUtils.collectionToDelimitedString(jsonLines, "\n"), ScimEventNotification.class));
        }
    }

    @Test
    public void getUri() throws Exception {
        assertEquals(FEED1, feed.getUri());
    }

    @Test
    public void manageMessages() throws Exception {
        // with no poll subscriber, don't save any message
        feed.newMsg(sens.get(0));
        assertTrue(feed.getMessages().isEmpty());

        // add a poll subscriber
        Subscriber sbsc = new Subscriber("sbsc");
        sbsc.addSubscription(new Subscription(FEED1, SubscriptionModeEnum.poll, FEED1));
        feed.addSubscriber(sbsc);

        // add messages
        for (ScimEventNotification sen : sens) {
            feed.newMsg(sen);
        }

        // get them
        List<ScimEventNotification> returned = feed.getMessages();
        for (ScimEventNotification sen : sens) {
            assertTrue(returned.contains(sen));
        }
    }

    @Test
    public void manageSubscribers() throws Exception {
        // webCallback subscriber
        Subscriber sbsc1 = new Subscriber("first");
        sbsc1.addSubscription(new Subscription(FEED1, SubscriptionModeEnum.webCallback, "https://subscribers.link.com"));

        // poll subscriber
        Subscriber sbsc2 = new Subscriber("second");
        sbsc2.addSubscription(new Subscription(FEED1, SubscriptionModeEnum.poll, FEED1));

        // add
        feed.addSubscriber(sbsc1);
        feed.addSubscriber(sbsc2);

        // get
        Set<Subscriber> sbscList = feed.getSubscribers();
        assertTrue(sbscList.contains(sbsc1));
        assertTrue(sbscList.contains(sbsc2));

        //remove
        feed.removeSubscriber(sbsc1);
        sbscList = feed.getSubscribers();
        assertFalse(sbscList.contains(sbsc1));
    }

    @Test
    public void webCallback() throws Exception {
        // webCallback subscriber
        Subscriber sbsc = new Subscriber("first");
        sbsc.addSubscription(new Subscription(FEED1, SubscriptionModeEnum.webCallback, "https://subscribers.link.com"));
        feed.addSubscriber(sbsc);

        // add messages and assert subscriber will be notified
        for (ScimEventNotification sen : sens) {
            assertTrue(feed.newMsg(sen).contains(sbsc));
        }
    }

    @Test
    public void poll() throws Exception {
        // poll subscriber
        Subscriber sbsc1 = new Subscriber("first");
        sbsc1.addSubscription(new Subscription(FEED1, SubscriptionModeEnum.poll, FEED1));
        feed.addSubscriber(sbsc1);

        // add message
        feed.newMsg(sens.get(0));

        // poll 1
        List<ScimEventNotification> returned = feed.poll(sbsc1);
        assertTrue(returned.size() == 1);
        assertTrue(returned.contains(sens.get(0)));
        assertTrue(feed.getMessages().isEmpty());

        // add second poll subscriber
        Subscriber sbsc2 = new Subscriber("second");
        sbsc2.addSubscription(new Subscription(FEED1, SubscriptionModeEnum.poll, FEED2));
        feed.addSubscriber(sbsc2);

        // add next message
        feed.newMsg(sens.get(1));

        // poll 2
        returned = feed.poll(sbsc1);
        assertTrue(returned.size() == 1);
        assertTrue(returned.contains(sens.get(1)));
        assertTrue(feed.getMessages().size() == 1); // sbsc2 hasn't seen the msg yet, it should still be there

        // add next message
        feed.newMsg(sens.get(2));

        // poll 3
        returned = feed.poll(sbsc2);
        assertFalse(returned.contains(sens.get(0))); // because the msg 0 had come before sbsc2 was subscribed
        assertTrue(returned.contains(sens.get(1)));
        assertTrue(returned.contains(sens.get(2)));

        // poll 4
        returned = feed.poll(sbsc1);
        assertFalse(returned.contains(sens.get(1)));
        assertTrue(returned.contains(sens.get(2)));
        assertTrue(feed.getMessages().isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void nullNewMsg() throws Exception {
        feed.newMsg(null);
    }

    @Test(expected = NullPointerException.class)
    public void addNullSubscriber() throws Exception {
        feed.addSubscriber(null);
    }

    @Test(expected = NullPointerException.class)
    public void removeNullSubscriber() throws Exception {
        feed.removeSubscriber(null);
    }
}