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
import java.util.*;

/**
 * Created by xmauritz on 8/16/16.
 */
@Named
@Singleton
public class DaoTestUtils {

    @Inject
    private JdbcTemplate jdbcTemplate;

    public void createSubscriberInDb(Subscriber subscriber) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("identifier", subscriber.getIdentifier());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("subscriber").usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscriber.setId(id.longValue());
    }

    public void createFeedInDb(Feed feed) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("uri", feed.getUri());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("feed").usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        feed.setId(id.longValue());
    }

    public void createSubscriptionInDb(Subscription subscription, Feed feed, Subscriber subscriber, Long lastSeenMsg) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("mode", subscription.getMode().name());
        params.put("eventUri", subscription.getEventUri());
        params.put("subscriberId", subscriber.getId());
        params.put("feedId", feed.getId());
        params.put("lastSeenMsg", lastSeenMsg);
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("subscription").usingGeneratedKeyColumns("id");
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
            storeMultipleRowsForSen("sen_attribute", "name", sen.getId(), sen.getAttributes());
            // store resource uris
            storeMultipleRowsForSen("sen_resource_uri", "uri", sen.getId(), sen.getResourceUris());
            // store schemas
            Set<String> schemasToStore = new HashSet<String>(sen.getSchemas());
            schemasToStore.remove(ScimEventNotification.EVENT_SCHEMA); // remove the EVENT_SCHEMA, it's in all
            storeMultipleRowsForSen("sen_schema", "name", sen.getId(), schemasToStore);

        } else {
            // sen is already stored
            int records = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feed_sen WHERE feedId=? AND senId=?", Integer.class, feedId, sen.getId());
            if (records > 0) {
                // feed has already record of sen -> just update value
                String SQL = "UPDATE feed_sen SET prevMsgId=? WHERE feedId=? AND senId=?";
                jdbcTemplate.update(SQL, prevMsgId, feedId, sen.getId());
            } else {
                // feed has no record of sen -> create new
                storeFeedSenRelationship(sen, feedId, prevMsgId);
            }
        }
    }

    private void storePureSen(ScimEventNotification sen) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("publisherUri", sen.getPublisherUri());
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
        Map<String, Object> params = new HashMap<String, Object>();
        params.clear();
        params.put("feedId", feedId);
        params.put("senId", sen.getId());
        params.put("prevMsgId", prevMsgId);
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("feed_sen");
        jdbcInsert.execute(params);
    }

    private void storeMultipleRowsForSen(String tableName, String columnName, Long senId, Set<String> values) {
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(tableName);
        Map<String, Object> params = new HashMap<String, Object>();
        for (String value : values) {
            params.clear();
            params.put(columnName, value);
            params.put("senId", senId);
            jdbcInsert.execute(params);
        }
    }
}
