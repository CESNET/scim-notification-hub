package daoImpl;

import core.Feed;
import core.ScimEventNotification;
import core.Subscriber;
import core.Subscription;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Util methods for DAO layer.
 * Methods store and retrieve data from database.
 *
 * @author Jiri Mauritz
 */
@Named
@Singleton
public class DaoTestUtils {

    @Inject
    private JdbcTemplate jdbcTemplate;

    public void createSubscriberInDb(Subscriber subscriber) {
        Map<String, Object> params = new HashMap<>();
        params.put("identifier", subscriber.getIdentifier());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("scim_subscriber").usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscriber.setId(id.longValue());
    }

    public void createFeedInDb(Feed feed) {
        Map<String, Object> params = new HashMap<>();
        params.put("uri", feed.getUri());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("scim_feed").usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        feed.setId(id.longValue());
    }

    public void createSubscriptionInDb(Subscription subscription, Feed feed, Subscriber subscriber, Long lastSeenMsg) {
        Map<String, Object> params = new HashMap<>();
        params.put("mode", subscription.getMode().name());
        params.put("event_uri", subscription.getEventUri());
        params.put("subscriber_id", subscriber.getId());
        params.put("feed_id", feed.getId());
        params.put("last_seen_msg", lastSeenMsg);
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("scim_subscription").usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscription.setId(id.longValue());
    }

    public void createSenInDb(ScimEventNotification sen, Long feedId, Long prevMsgId) {
        if (sen.getId() == null) {
            // sen is not stored yet
            storePureSen(sen);
            // store feed - sen relationship
            storeFeedSenRelationship(sen, feedId, prevMsgId);
            // store attributes
            storeMultipleRowsForSen("scim_sen_attribute", "name", sen.getId(), sen.getAttributes());
            // store resource uris
            storeMultipleRowsForSen("scim_sen_resource_uri", "uri", sen.getId(), sen.getResourceUris());
            // store schemas
            Set<String> schemasToStore = new HashSet<>(sen.getSchemas());
            schemasToStore.remove(ScimEventNotification.EVENT_SCHEMA); // remove the EVENT_SCHEMA, it's in all
            storeMultipleRowsForSen("scim_sen_schema", "name", sen.getId(), schemasToStore);
        } else {
            // sen is already stored
            int records = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scim_feed_sen WHERE feed_id=? AND sen_id=?", Integer.class, feedId, sen.getId());
            if (records > 0) {
                // feed has already record of sen -> just update value
                String SQL = "UPDATE feed_sen SET prev_msg_id=? WHERE feed_id=? AND sen_id=?";
                jdbcTemplate.update(SQL, prevMsgId, feedId, sen.getId());
            } else {
                // feed has no record of sen -> create new
                storeFeedSenRelationship(sen, feedId, prevMsgId);
            }
        }
    }

    private void storePureSen(ScimEventNotification sen) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> params = new HashMap<>();
        params.put("publisher_uri", sen.getPublisherUri());
        params.put("type", sen.getType().name());
        try {
            params.put("sen_values", mapper.writeValueAsString(sen.getValues()));
        } catch (IOException e) {
            throw new IllegalStateException("Error when parsing sen values to plain JSON to store it in DB.", e);
        }
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("scim_event_notification").usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        sen.setId(id.longValue());
    }

    private void storeFeedSenRelationship(ScimEventNotification sen, Long feedId, Long prevMsgId) {
        Map<String, Object> params = new HashMap<>();
        params.clear();
        params.put("feed_id", feedId);
        params.put("sen_id", sen.getId());
        params.put("prev_msg_id", prevMsgId);
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("scim_feed_sen");
        jdbcInsert.execute(params);
    }

    private void storeMultipleRowsForSen(String tableName, String columnName, Long senId, Set<String> values) {
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(tableName);
        Map<String, Object> params = new HashMap<>();
        for (String value : values) {
            params.clear();
            params.put(columnName, value);
            params.put("sen_id", senId);
            jdbcInsert.execute(params);
        }
    }
}
