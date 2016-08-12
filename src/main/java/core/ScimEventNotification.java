package core;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Scim Notification Event (SEN) notifies a subscriber of a possible change in state of a
   resource contained within a specified feed.
 *
 * @author Jiri Mauritz
 */
public class ScimEventNotification implements java.io.Serializable {
    public static final String EVENT_SCHEMA = "urn:ietf:params:scim:schemas:notify:2.0:Event";
    private Long id;
    private List<String> schemas;
    private List<String> feedUris;
    private String publisherUri;
    private List<String> resourceUris;
    private ScimEventTypeEnum type;
    private List<String> attributes;
    private Map<String, Object> values;

    @JsonCreator
    public ScimEventNotification(
            @JsonProperty("schemas") final List<String> schemas,
            @JsonProperty("feedUris") final List<String> feedUris,
            @JsonProperty("publisherUri") final String publisherUri,
            @JsonProperty("resourceUris") final List<String> resourceUris,
            @JsonProperty("type") final String type,
            @JsonProperty("attributes") final List<String> attributes,
            @JsonProperty("values") final Map<String, Object> values) {
        if (!schemas.contains(EVENT_SCHEMA)) {
            throw new IllegalArgumentException("Schemas must contain schema: urn:ietf:params:scim:schemas:notify:2.0:Event");
        }
        this.id = null;
        this.schemas = schemas;
        this.feedUris = feedUris;
        this.publisherUri = publisherUri;
        this.resourceUris = resourceUris;
        this.type = ScimEventTypeEnum.valueOf(type);
        this.attributes = attributes;
        this.values = values;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<String> getSchemas() {
        return schemas;
    }

    public List<String> getFeedUris() {
        return Collections.unmodifiableList(feedUris);
    }

    public String getPublisherUri() {
        return publisherUri;
    }

    public List<String> getResourceUris() {
        return Collections.unmodifiableList(resourceUris);
    }

    public ScimEventTypeEnum getType() {
        return type;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScimEventNotification that = (ScimEventNotification) o;

        if (feedUris != null ? !feedUris.equals(that.feedUris) : that.feedUris != null) return false;
        if (!publisherUri.equals(that.publisherUri)) return false;
        if (resourceUris != null ? !resourceUris.equals(that.resourceUris) : that.resourceUris != null) return false;
        if (type != that.type) return false;
        if (attributes != null ? !attributes.equals(that.attributes) : that.attributes != null) return false;
        return values != null ? values.equals(that.values) : that.values == null;

    }

    @Override
    public int hashCode() {
        int result = feedUris != null ? feedUris.hashCode() : 0;
        result = 31 * result + publisherUri.hashCode();
        result = 31 * result + (resourceUris != null ? resourceUris.hashCode() : 0);
        result = 31 * result + type.hashCode();
        result = 31 * result + (attributes != null ? attributes.hashCode() : 0);
        result = 31 * result + (values != null ? values.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ScimEventNotification{" +
                "schemas=" + schemas +
                ", feedUris=" + feedUris +
                ", publisherUri='" + publisherUri + '\'' +
                ", resourceUris=" + resourceUris +
                ", type=" + type +
                ", attributes=" + attributes +
                ", values=" + values +
                '}';
    }
}
