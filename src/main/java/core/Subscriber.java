package core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Subscriber makes subscription to the feeds to get notifications.
 * The settings of each feed is defined by the Subscription object.
 * Subscriber can be subscribed to multiple feeds.
 *
 * @author Jiri Mauritz
 */
public class Subscriber {
    private String identifier;
    private Set<Subscription> subscriptions;

    public Subscriber(String identifier) {
        this.identifier = identifier;
        this.subscriptions = new HashSet<Subscription>();
    }

    public String getIdentifier() {
        return identifier;
    }

    /**
     * Retrieve all subscriptions of the subscriber.
     * @return all subscriptions
     */
    public Set<Subscription> getSubscriptions() {
        return Collections.unmodifiableSet(subscriptions);
    }

    /**
     * Adds subscription. Only one subscription to the single feed is allowed.
     * @param subscription to be added
     */
    public void addSubscription(Subscription subscription) {
        if (subscription == null) throw new NullPointerException("Subscription cannot be null.");
        // only one subscription to the single feed is allowed
        for (Subscription sb : this.subscriptions) {
            if (sb.getFeedUri().equals(subscription.getFeedUri())) {
                throw new IllegalArgumentException("Subscription to the feed " + subscription.getFeedUri() + " already exists.");
            }
        }
        this.subscriptions.add(subscription);
    }

    /**
     * Removes subscription.
     *
     * @param feedUri of the feed to be removed
     * @return true if the subscription was removed
     */
    public boolean removeSubscription(String feedUri) {
        if (feedUri == null) throw new NullPointerException("Subscription cannot be null.");
        Subscription subscription = null;
        for (Subscription s : subscriptions) {
            if (s.getFeedUri().equals(feedUri)) {
                subscription = s;
            }
        }
        if (subscription == null) return false;
        return subscriptions.remove(subscription);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Subscriber that = (Subscriber) o;

        return identifier.equals(that.identifier);

    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public String toString() {
        return "Subscriber{" +
                "identifier='" + identifier + '\'' +
                ", subscriptions=" + subscriptions +
                '}';
    }
}
