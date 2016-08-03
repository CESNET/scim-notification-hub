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
public class Manager {

    Map<String, Feed> feeds = new HashMap<String, Feed>();
    Map<String, Subscriber> subscribers = new HashMap<String, Subscriber>();


    /**
     * Add new message to the feeds specified in the message.
     * If a feed does not exist, it is created.
     * The message has to be valid scim notification event json according to the specification
     * <a>https://tools.ietf.org/html/draft-hunt-scim-notify-00#section-3</a>.
     *
     * @param json scim notification event in json format
     */
    public void newMessage(String json) {
        if (json == null) throw new IllegalArgumentException("Json cannot be null.");
        // String -> JSON
        ObjectMapper mapper = new ObjectMapper();
        ScimEventNotification sen = null;
        try {
            sen = mapper.readValue(json, ScimEventNotification.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON.");
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
        if (subscriberId == null) throw new IllegalArgumentException("SubscriberId cannot be null.");
        if (feedUri == null) throw new IllegalArgumentException("FeedUri cannot be null.");
        if (mode == null) throw new IllegalArgumentException("Mode cannot be null.");
        if (eventUri == null) throw new IllegalArgumentException("EventUri cannot be null.");

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

    public void removeSubscription(String subscriberId, String feedUri) {
        if (subscriberId == null) throw new IllegalArgumentException("SubscriberId cannot be null.");
        if (feedUri == null) throw new IllegalArgumentException("FeedUri cannot be null.");
        Subscriber subscriber = subscribers.get(subscriberId);
        subscriber.removeSubscription(feedUri);
        feeds.get(feedUri).removeSubscriber(subscriber);
    }

    public void removeSubscriber(String subscriberId) {
        if (subscriberId == null) throw new IllegalArgumentException("SubscriberId cannot be null.");
        Subscriber subscriber = subscribers.get(subscriberId);
        for (Subscription subscription : subscriber.getSubscriptions()) {
            feeds.get(subscription.getFeedUri()).removeSubscriber(subscriber);
        }
        subscribers.remove(subscriber);
    }

    public void webCallbackSend(Set<Subscriber> subscribers, ScimEventNotification sen) {
        // TODO - initiate send of the sen message to all the subscribers
    }

    public Set<ScimEventNotification> poll(String subscriberId) {
        if (subscriberId == null) throw new IllegalArgumentException("SubscriberId cannot be null.");
        Subscriber subscriber = subscribers.get(subscriberId);
        Set<ScimEventNotification> msgsToSend = new HashSet<ScimEventNotification>();
        for (Subscription subscription : subscriber.getSubscriptions()) {
            msgsToSend.addAll(feeds.get(subscription.getFeedUri()).poll(subscriber));
        }
        return msgsToSend;
    }

}
