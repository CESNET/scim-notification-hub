package core;

/**
 * Subscriptions are made by the subscriber as a request to the Notification Hub.
 * The subscriber is connected to the defined feed for notifications.
 * The subscriptions are usually created by accessing "/Subscriptions" endpoint.
 *
 * @author Jiri Mauritz
 */
public class Subscription {
    private String feedUri;
    private SubscriptionModeEnum mode;
    private String eventUri;
    //TODO: Jwt, pollInterval and state

    public Subscription(String feedUri, SubscriptionModeEnum mode, String eventUri) {
        this.feedUri = feedUri;
        this.mode = mode;
        this.eventUri = eventUri;
    }

    public String getFeedUri() {
        return feedUri;
    }

    public void setFeedUri(String feedUri) {
        this.feedUri = feedUri;
    }

    public SubscriptionModeEnum getMode() {
        return mode;
    }

    public void setMode(SubscriptionModeEnum mode) {
        this.mode = mode;
    }

    public String getEventUri() {
        return eventUri;
    }

    public void setEventUri(String eventUri) {
        this.eventUri = eventUri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Subscription that = (Subscription) o;

        if (!feedUri.equals(that.feedUri)) return false;
        if (mode != that.mode) return false;
        return eventUri.equals(that.eventUri);

    }

    @Override
    public int hashCode() {
        int result = feedUri.hashCode();
        result = 31 * result + mode.hashCode();
        result = 31 * result + eventUri.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Subscription{" +
                "feedUri='" + feedUri + '\'' +
                ", mode=" + mode +
                ", eventUri='" + eventUri + '\'' +
                '}';
    }
}
