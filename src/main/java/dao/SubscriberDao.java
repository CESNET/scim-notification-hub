package dao;

import core.Feed;
import core.Subscriber;

import java.util.Map;
import java.util.Set;

/**
 * Created by xmauritz on 8/8/16.
 */
public interface SubscriberDao {

    /**
     * Update the subscriber's attributes according to the data storage.
     *
     * @param subscriber subscriber to be updated
     */
    public void update(Map<String, Subscriber> subscribers);

    /**
     * Create a new subscriber.
     *
     * @param subscriber to be created
     */
    public void create(Subscriber subscriber);

    /**
     * Remove a new subscriber.
     *
     * @param subscriber to be removed
     */
    public void remove(Subscriber subscriber);
}
