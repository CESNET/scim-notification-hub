package core;

import java.util.*;

/**
 * Feed provides endpoint for subscribers to subscribe for notification of one type.
 * The feeds are defined by the Service Provider.
 *
 * @author Jiri Mauritz
 */
public class Feed {

    private Long id;

    // uri of the feed, subscribers access the uri to poll the messages
    private String uri;

    // queue of messages accessible from the beginning for adding and from the end for removing
    private LinkedList<ScimEventNotification> messages;

    // subscribers with mode webCallback
    private Set<Subscriber> callbackSubscribers;

    // subscribers with mode poll, the map remembers their last seen msg, if null -> no msgs were seen
    private Map<Subscriber, ScimEventNotification> pollSubscribersLastMsg;

    // subscriber that hasn't access the feed for the longest time
    private Subscriber slowestPollSubscriber;

    /**
     * Constructor for creating a feed, uri is required.
     *
     * @param uri
     */
    public Feed(String uri) {
        if (uri == null) throw new NullPointerException("Uri cannot be null.");
        this.id = null;
        this.uri = uri;
        this.messages = new LinkedList<ScimEventNotification>();
        this.callbackSubscribers = new HashSet<Subscriber>();
        this.pollSubscribersLastMsg = new HashMap<Subscriber, ScimEventNotification>();
        this.slowestPollSubscriber = null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns uri associated to this feed.
     *
     * @return feed uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * Method returns active messages in the feed that have not been yet distributed to all subscribers.
     *
     * @return messages in the queue of the feed
     */
    public List<ScimEventNotification> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Add new message to the feed queue.
     * Returns a set of subscribers that are subscribed for web callback and should receive this message immediately.
     *
     * @param sen message to add
     * @return set of subscribers, that should be notified about the message
     */
    public Set<Subscriber> newMsg(ScimEventNotification sen) {
        if (sen == null) throw new NullPointerException("ScimEventNotification cannot be null.");
        // if there is at least one POLL subscriber, retain the msg
        if (!pollSubscribersLastMsg.isEmpty()) {
            this.messages.addFirst(sen);
        }
        // send to all, who have set CALLBACK
        return Collections.unmodifiableSet(callbackSubscribers);
    }

    /**
     * Returns all subscribers that are subscribed to the feed.
     *
     * @return feed subscribers
     */
    public Set<Subscriber> getSubscribers() {
        Set<Subscriber> all = new HashSet<Subscriber>(callbackSubscribers);
        all.addAll(pollSubscribersLastMsg.keySet());
        return all;
    }

    /**
     * Method adds new subscriber to the feed.
     * The subscriber must have valid subscription to the feed before adding.
     *
     * @param subscriber to be added
     */
    public void addSubscriber(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        // getting the subscription matching this feed
        Subscription subscription = null;
        for (Subscription subtion : subscriber.getSubscriptions()) {
            if (uri.equals(subtion.getFeedUri())) {
                subscription = subtion;
                break;
            }
        }
        // validation
        if (subscription == null || subscription.getMode() == null || subscription.getEventUri() == null) {
            throw new IllegalStateException("Subscriber must have valid subscription to the feed before adding.");
        }
        // filter the subscriber according to his notification mode
        if (subscription.getMode().equals(SubscriptionModeEnum.poll)) {
            // poll subscriber
            this.pollSubscribersLastMsg.put(subscriber, null);
            this.slowestPollSubscriber = subscriber;
        } else {
            // webCallback subscriber
            this.callbackSubscribers.add(subscriber);
        }
    }

    /**
     * Removes subscriber from the feed.
     *
     * @param subscriber to be removed
     * @return true if the subscriber was removed
     */
    public boolean removeSubscriber(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Cannot remove null subscriber.");
        if (callbackSubscribers.remove(subscriber)) {
            return true;
        }
        if (pollSubscribersLastMsg.containsKey(subscriber)) {
            pollSubscribersLastMsg.remove(subscriber);
            updateFeedState();
            return true;
        }
        return false;
    }

    /**
     * Trigger the poll of the messages from the feed and specified subscriber.
     * The subscruber will receive only messages that has not been read by him.
     *
     * @param subscriber requesting poll
     * @return polled messages
     */
    public List<ScimEventNotification> poll(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        if (!pollSubscribersLastMsg.containsKey(subscriber)) {
            throw new IllegalArgumentException("Subscriber " + subscriber.getIdentifier() + " is not subscribed to the feed " + uri);
        }
        List<ScimEventNotification> msgsToSend = new ArrayList<ScimEventNotification>();
        if (subscriber.equals(slowestPollSubscriber)) {
            // slowest subscriber has not seen any of saved messages -> return all messages
            msgsToSend.addAll(messages);
            pollSubscribersLastMsg.put(subscriber, messages.isEmpty() ? null : messages.getFirst());
            updateFeedState();
        } else {
            // the subscriber is not slowest -> do not remove any message
            ScimEventNotification lastMsg = pollSubscribersLastMsg.get(subscriber);
            // iterate from the beginning of our message queue and remember all messages that the subscriber has not seen
            for (ScimEventNotification sen : messages) {
                if (sen.equals(lastMsg)) break;
                msgsToSend.add(sen);
            }
            // set new last read message for the subscriber (the first one in the queue)
            pollSubscribersLastMsg.put(subscriber, messages.isEmpty() ? null : messages.getFirst());
        }
        return msgsToSend;
    }

    /**
     * Delete all messages that have been read by all and update slowestPollSubscriber.
     */
    private void updateFeedState() {
        slowestPollSubscriber = null;
        // if there is another subscriber that hasn't read everything, set him as slowest one, delete no msg
        for (Map.Entry entry : pollSubscribersLastMsg.entrySet()) {
            if (entry.getValue() == null) {
                slowestPollSubscriber = (Subscriber) entry.getKey();
                return;
            }
        }
        // delete messages until we find one that has not been seen by somebody
        Iterator iter = messages.descendingIterator();
        while (iter.hasNext()) {
            ScimEventNotification sen = (ScimEventNotification) iter.next();
            if (pollSubscribersLastMsg.containsValue(sen)) {
                for (Map.Entry entry : pollSubscribersLastMsg.entrySet()) {
                    // iterate over all subscribers with last seen sen and set them null
                    if (entry.getValue().equals(sen)) {
                        slowestPollSubscriber = (Subscriber) entry.getKey();
                        pollSubscribersLastMsg.put(slowestPollSubscriber, null);
                    }
                }
                iter.remove();
                return;
            }
            iter.remove();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Feed feed = (Feed) o;

        return uri.equals(feed.uri);

    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

    @Override
    public String toString() {
        return "Feed{" +
                "uri='" + uri + '\'' +
                ", messages=" + messages +
                ", callbackSubscribers=" + callbackSubscribers +
                ", pollSubscribersLastMsg=" + pollSubscribersLastMsg +
                ", slowestPollSubscriber=" + slowestPollSubscriber +
                '}';
    }
}
