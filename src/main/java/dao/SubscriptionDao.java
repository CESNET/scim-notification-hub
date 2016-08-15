package dao;

import core.Feed;
import core.Subscriber;
import core.Subscription;

import java.util.Set;

/**
 * Created by xmauritz on 8/9/16.
 */
public interface SubscriptionDao {

    /**
     * Create new subscription to the specified feed and assign it to the specified subscriber.
     *
     * @param subscription to be created
     * @param subscriber   owner of the subscription
     * @param feed         to which the subscription belongs
     * @param lastSeenMsg  id of the msg that was last seen by poll subscriber in the feed (null for webCallback subscriber)
     */
    public void create(Subscription subscription, Subscriber subscriber, Feed feed, Long lastSeenMsg);

    /**
     * Remove a subscription according to its subscriber and feed.
     *
     * @param subscriberIdentifier who owns the subscription
     * @param feedUri              of the subscription
     */
    public void remove(String subscriberIdentifier, String feedUri);

    /**
     * Remove a subscription according to its id.
     *
     * @param id of the subscription
     */
    public void remove(Long id);

    /**
     * Retrieve all ids of the subscriptions that belong to the specified subscriber.
     *
     * @param subscriber owner of the subscriptions
     * @return set of all subscription ids
     */
    public Set<Long> getAllIdsForSubscriber(Subscriber subscriber);
}
