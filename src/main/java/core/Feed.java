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
    private Map<Subscriber,ScimEventNotification> subscribersLastMsg;
    private Subscriber slowestSubscriber;

    public Feed(String uri) {
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
        this.messages.addFirst(sen);
    }

    public Set<Subscriber> getSubscriber() {
        return subscribersLastMsg.keySet();
    }

    public void addSubscriber(Subscriber subscriber) {
        this.subscribersLastMsg.put(subscriber, null) ;
        this.slowestSubscriber = subscriber;
    }

    public void removeSubscriber(Subscriber subscriber) {
        this.subscribersLastMsg.remove(subscriber) ;
    }

    public void poll(Subscriber subscriber) {
        if (subscriber.equals(slowestSubscriber)) {

        } else {

        }
    }
}
