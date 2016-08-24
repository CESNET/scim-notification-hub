package core;

import java.util.Set;

/**
 * Manager controls the notification hub and represents the only endpoint to the core of the hub.
 *
 * @author Jiri Mauritz
 */
public interface Manager {

    /**
     * Add new message to the feeds specified in the message.
     * If a feed does not exist, it is created.
     * The message has to be valid scim notification event json according to the specification
     * <a>https://tools.ietf.org/html/draft-hunt-scim-notify-00#section-3</a>.
     *
     * @param json scim notification event in json format
     * @throws IllegalArgumentException if the json in null or not valid
     */
    public void newMessage(String json);

    /**
     * Creates new subscription of the subscriber to the feed with the given mode.
     * If the subscriber or the feed do not exist, they are created.
     *
     * @param subscriberId identificator of the subscriber
     * @param feedUri      uri of the feed
     * @param mode         mode of the subscription (web callback / poll)
     * @param eventUri     uri for notification
     * @throws IllegalArgumentException if the subscription already exists
     */
    public void newSubscription(String subscriberId, String feedUri, SubscriptionModeEnum mode, String eventUri);

    /**
     * Subscription is removed from specified feed by calling this method.
     * If the subscriber or feed does not exists, returns false.
     *
     * @param subscriberId id of the subscriber
     * @param feedUri      uri of the feed
     * @return true if the subscription was removed
     */
    public boolean removeSubscription(String subscriberId, String feedUri);

    /**
     * Removes subscriber and all his subscriptions in feeds.
     * If the subscriber is not subscribed anywhere, returns false.
     *
     * @param subscriberId id of the subscriber
     * @return true if the subscriber was removed
     */
    public boolean removeSubscriber(String subscriberId);

    /**
     * Polling of the scim notification events looks into each subscriber's feed
     * and return collection of all new messages from all feeds.
     *
     * @param subscriberId id of the subscriber
     * @return set of all new messages to be send to the subscriber
     */
    public Set<ScimEventNotification> poll(String subscriberId);

    /**
     * Retrieve identifiers of all subscribers.
     *
     * @return set of all identifiers
     */
    public Set<String> getSubscriberIdentifiers();

    /**
     * Retrieve subscriber according to his identifier.
     *
     * @param identifier of the subcriber
     * @return subscriber
     */
    public Subscriber getSubscriberByIdentifier(String identifier);
}
