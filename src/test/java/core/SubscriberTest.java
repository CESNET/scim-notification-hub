package core;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Testing Subscriber class.
 *
 * @author Jiri Mauritz
 */
public class SubscriberTest {

    private static final String FEED1 = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String FEED2 = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/openvpn";
    private static final String ID = "id";

    private Subscriber sbsc = new Subscriber(ID);

    @Test
    public void getIdentifier() throws Exception {
        assertEquals(ID, sbsc.getIdentifier());
    }

    @Test(expected = NullPointerException.class)
    public void addNullSubscription() throws Exception {
        sbsc.addSubscription(null);
    }

    @Test(expected = NullPointerException.class)
    public void removeNullSubscription() throws Exception {
        sbsc.removeSubscription(null);
    }

    @Test
    public void manageSubscriptions() throws Exception {
        Subscription sb1 = new Subscription(FEED1, SubscriptionModeEnum.poll, FEED1);
        Subscription sb2 = new Subscription(FEED2, SubscriptionModeEnum.poll, FEED2);

        // add subscriptions
        sbsc.addSubscription(sb1);
        sbsc.addSubscription(sb2);

        // get subscriptions
        Set<Subscription> subscriptionSet = sbsc.getSubscriptions();
        assertTrue(subscriptionSet.contains(sb1));
        assertTrue(subscriptionSet.contains(sb2));

        // remove subscriptions
        assertTrue(sbsc.removeSubscription(FEED2));
        assertFalse(sbsc.removeSubscription(FEED2));

        assertTrue(sbsc.getSubscriptions().size() == 1);
        assertTrue(sbsc.getSubscriptions().contains(sb1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void uniqueSubscriptions() throws Exception {
        Subscription sb1 = new Subscription(FEED1, SubscriptionModeEnum.poll, FEED1);
        Subscription sb2 = new Subscription(FEED1, SubscriptionModeEnum.webCallback, FEED2);

        // add subscriptions
        sbsc.addSubscription(sb1);
        sbsc.addSubscription(sb2);
    }
}