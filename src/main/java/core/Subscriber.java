package core;

import java.util.HashSet;
import java.util.Set;

/**
 * Subscriber makes subscription to the feeds to get notifications.
 * The settings of each feed is defined independently by Subscription object.
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

    public Set<Subscription> getSubscriptions() {
        return subscriptions;
    }

    public void addToSubscriptions(Subscription subscription) {
        this.subscriptions.add(subscription);
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
