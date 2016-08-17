package core;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Scim Notification Event (SEN) notifies a subscriber of a possible change in state of a
   resource contained within a specified feed.
 *
 * @author Jiri Mauritz
 */
public class ScimEventNotification implements java.io.Serializable {
    public static final String EVENT_SCHEMA = "urn:ietf:params:scim:schemas:notify:2.0:Event";
    private Long id;
    private Set<String> schemas;
    private Set<String> feedUris;
    private String publisherUri;
    private Set<String> resourceUris;
    private ScimEventTypeEnum type;
    private Set<String> attributes;
    private Map<String, Object> values;

    @JsonCreator
    public ScimEventNotification(
            @JsonProperty("schemas") final Set<String> schemas,
            @JsonProperty("feedUris") final Set<String> feedUris,
            @JsonProperty("publisherUri") final String publisherUri,
            @JsonProperty("resourceUris") final Set<String> resourceUris,
            @JsonProperty("type") final String type,
            @JsonProperty("attributes") final Set<String> attributes,
            @JsonProperty("values") final Map<String, Object> values) {
        if (!schemas.contains(EVENT_SCHEMA)) {
            throw new IllegalArgumentException("Schemas must contain schema: '" + EVENT_SCHEMA + "'.");
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

    public Set<String> getSchemas() {
        return schemas;
    }

    public Set<String> getFeedUris() {
        return Collections.unmodifiableSet(feedUris);
    }

    public String getPublisherUri() {
        return publisherUri;
    }

    public Set<String> getResourceUris() {
        return Collections.unmodifiableSet(resourceUris);
    }

    public ScimEventTypeEnum getType() {
        return type;
    }

    public Set<String> getAttributes() {
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

        if (!schemas.equals(that.schemas)) return false;
        if (!feedUris.equals(that.feedUris)) return false;
        if (!publisherUri.equals(that.publisherUri)) return false;
        if (!resourceUris.equals(that.resourceUris)) return false;
        if (type != that.type) return false;
        if (!attributes.equals(that.attributes)) return false;
        return values.equals(that.values);

    }

    @Override
    public int hashCode() {
        int result = schemas.hashCode();
        result = 31 * result + feedUris.hashCode();
        result = 31 * result + publisherUri.hashCode();
        result = 31 * result + resourceUris.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + attributes.hashCode();
        result = 31 * result + values.hashCode();
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
