package core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Testing class Subscription.
 *
 * @author Jiri Mauritz
 */
public class SubscriptionTest {
    private static final String FEED = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String URI = "https://perun.cesnet.cz/scim-notification/";

    private Subscription subscription = new Subscription(FEED, SubscriptionModeEnum.poll, URI);

    @Test
    public void getFeedUri() throws Exception {
        assertEquals(FEED, subscription.getFeedUri());
    }

    @Test
    public void getMode() throws Exception {
        assertEquals(SubscriptionModeEnum.poll, subscription.getMode());
    }

    @Test
    public void getEventUri() throws Exception {
        assertEquals(URI, subscription.getEventUri());
    }

}