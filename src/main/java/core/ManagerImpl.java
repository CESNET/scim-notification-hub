package core;

import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manager controls the notification hub and keeps all the feeds.
 *
 * @author Jiri Mauritz
 */
public class ManagerImpl implements Manager {

    Map<String, Feed> feeds = new HashMap<String, Feed>();
    Map<String, Subscriber> subscribers = new HashMap<String, Subscriber>();


    public void newMessage(String json) {
        if (json == null) throw new IllegalArgumentException("Json cannot be null.");
        // String -> JSON
        ObjectMapper mapper = new ObjectMapper();
        ScimEventNotification sen = null;
        try {
            sen = mapper.readValue(json, ScimEventNotification.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON.", e);
        }
        // classify the sen into right feeds
        Set<Subscriber> subscribersToBeNotified = new HashSet<Subscriber>();
        for (String feedUri : sen.getFeedUris()) {
            Feed feed = feeds.get(feedUri);
            if (feed == null) {
                // create new feed
                feed = new Feed(feedUri);
                feeds.put(feedUri, feed);
            }
            subscribersToBeNotified.addAll(feed.newMsg(sen));
        }
        webCallbackSend(subscribersToBeNotified, sen);
    }

    public void newSubscription(String subscriberId, String feedUri, SubscriptionModeEnum mode, String eventUri) {
        if (subscriberId == null) throw new NullPointerException("SubscriberId cannot be null.");
        if (feedUri == null) throw new NullPointerException("FeedUri cannot be null.");
        if (mode == null) throw new NullPointerException("Mode cannot be null.");
        if (eventUri == null) throw new NullPointerException("EventUri cannot be null.");

        // get subscriber
        Subscriber subscriber = subscribers.get(subscriberId);
        if (subscriber == null) {
            // create new subscriber
            subscriber = new Subscriber(subscriberId);
            subscribers.put(subscriberId, subscriber);
        }

        // create subscription
        Subscription subscription = new Subscription(feedUri, mode, eventUri);
        subscriber.addSubscription(subscription);

        // add to feed
        Feed feed = feeds.get(feedUri);
        if (feed == null) {
            // create new feed
            feed = new Feed(feedUri);
            feeds.put(feedUri, feed);
        }
        feed.addSubscriber(subscriber);
    }

    public boolean removeSubscription(String subscriberId, String feedUri) {
        if (subscriberId == null) throw new NullPointerException("SubscriberId cannot be null.");
        if (feedUri == null) throw new NullPointerException("FeedUri cannot be null.");
        if (!subscribers.containsKey(subscriberId)) return false;
        Subscriber subscriber = subscribers.get(subscriberId);
        if (subscriber.removeSubscription(feedUri)) {
            feeds.get(feedUri).removeSubscriber(subscriber);
            if (subscriber.getSubscriptions().isEmpty()) {
                subscribers.remove(subscriber);
            }
            return true;
        }
        // TODO: delete feed, if there is no subscriber
        return false;
    }

    public boolean removeSubscriber(String subscriberId) {
        if (subscriberId == null) throw new NullPointerException("SubscriberId cannot be null.");
        if (!subscribers.containsKey(subscriberId)) return false;
        Subscriber subscriber = subscribers.get(subscriberId);
        for (Subscription subscription : subscriber.getSubscriptions()) {
            feeds.get(subscription.getFeedUri()).removeSubscriber(subscriber);
        }
        // TODO: delete feed, if there is no subscriber
        subscribers.remove(subscriber);
        return true;
    }

    public Set<ScimEventNotification> poll(String subscriberId) {
        if (subscriberId == null) throw new IllegalArgumentException("SubscriberId cannot be null.");
        if (!subscribers.containsKey(subscriberId)) {
            throw new IllegalArgumentException("Subscriber with id " + subscriberId + " does not exists.");
        }
        Subscriber subscriber = subscribers.get(subscriberId);
        Set<ScimEventNotification> msgsToSend = new HashSet<ScimEventNotification>();
        for (Subscription subscription : subscriber.getSubscriptions()) {
            msgsToSend.addAll(feeds.get(subscription.getFeedUri()).poll(subscriber));
        }
        return msgsToSend;
    }

    void webCallbackSend(Set<Subscriber> subscribers, ScimEventNotification sen) {
        // TODO - initiate send of the sen message to all the subscribers
    }

}
