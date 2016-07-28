package core;

import java.util.*;

/**
 * Feed provides endpoint for subscribers to subscribe for notification of one type.
 * The feeds are categorized according to the Service Provider.
 *
 * @author Jiri Mauritz
 */
public class Feed {
    private String uri;
    private LinkedList<ScimEventNotification> messages;
    private Map<Subscriber, ScimEventNotification> subscribersLastMsg;
    private Subscriber slowestSubscriber;

    public Feed(String uri) {
        if (uri == null) throw new NullPointerException("Uri cannot be null.");
        this.uri = uri;
        this.messages = new LinkedList<ScimEventNotification>();
        this.subscribersLastMsg = new HashMap<Subscriber, ScimEventNotification>();
        this.slowestSubscriber = null;
    }

    public String getUri() {
        return uri;
    }

    public List<ScimEventNotification> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void newMsg(ScimEventNotification sen) {
        if (sen == null) throw new NullPointerException("ScimEventNotification cannot be null.");
        this.messages.addFirst(sen);
    }

    public Set<Subscriber> getSubscriber() {
        return subscribersLastMsg.keySet();
    }

    public void addSubscriber(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        this.subscribersLastMsg.put(subscriber, null);
        this.slowestSubscriber = subscriber;
    }

    public void removeSubscriber(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        this.subscribersLastMsg.remove(subscriber);
    }

    public List<ScimEventNotification> poll(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        if (!subscribersLastMsg.containsKey(subscriber)) {
            throw new IllegalArgumentException("Subscriber " + subscriber.getIdentifier() + " is not subscribed to the feed " + uri);
        }

        List<ScimEventNotification> msgsToSend = new ArrayList<ScimEventNotification>();
        if (subscriber.equals(slowestSubscriber)) {
            // slowest subscriber has not seen any of saved message -> return all messages
            msgsToSend.addAll(messages);
            updateAfterPollBySlowest();
        } else {
            // get last message, that the subscriber has already read
            ScimEventNotification lastMsg = subscribersLastMsg.get(subscriber);
            // iterate from the beginning of our message queue and remember all messages that the subscriber has not seen
            for (ScimEventNotification sen : messages) {
                if (sen.equals(lastMsg)) break;
                msgsToSend.add(sen);
            }
            // set new last read message for the subscriber (the first one in the queue)
            subscribersLastMsg.put(subscriber, messages.isEmpty() ? null : messages.getFirst());
        }
        return msgsToSend;
    }

    /**
     * Delete all messages that have been read by all and update slowestSubscriber.
     */
    private void updateAfterPollBySlowest() {
        // if there is another subscriber that haven't read everything, set him as slowest one, delete no msg
        for (Map.Entry entry : subscribersLastMsg.entrySet()) {
            if (entry.getValue() == null && !entry.getKey().equals(slowestSubscriber)) {
                slowestSubscriber = (Subscriber) entry.getKey();
                return;
            }
        }
        // delete messages until we find one that has not been seen by somebody
        Iterator iter = messages.descendingIterator();
        while (iter.hasNext()) {
            ScimEventNotification sen = (ScimEventNotification) iter.next();
            if (subscribersLastMsg.containsValue(sen)) {
                for (Map.Entry entry : subscribersLastMsg.entrySet()) {
                    // iterate over all subscribers with last seen sen and set them null
                    if (entry.getValue().equals(sen)) {
                        slowestSubscriber = (Subscriber) entry.getKey();
                        subscribersLastMsg.put(slowestSubscriber, null);
                    }
                }
                iter.remove();
                return;
            }
            iter.remove();
        }
    }
}
