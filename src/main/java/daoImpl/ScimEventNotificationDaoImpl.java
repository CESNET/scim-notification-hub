package daoImpl;

import core.Feed;
import core.ScimEventNotification;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;

/**
 * Created by xmauritz on 8/12/16.
 */
@Named
@Singleton
public class ScimEventNotificationDaoImpl {

    private static final String TABLE_NAME = "scim_event_notification";

    @Inject
    private JdbcTemplate jdbcTemplate;

    public void storeSen(ScimEventNotification sen, Long feedId, Long nextMsgId) {
        if (sen == null) throw new NullPointerException("ScimEventNotification cannot be null");
        if (feedId == null) throw new NullPointerException("Feed id cannot be null");
        if (nextMsgId == null) throw new NullPointerException("Next msg id cannot be null");
        if (sen.getId() == null) {
            // sen is not stored yet
            storePureSen(sen);
            // store feed - sen relationship
            storeFeedSenRelationship(sen, feedId, nextMsgId);
            // store attributes
            storeMultipleRowsForSen("sen_attribute", "name", sen.getId(), sen.getAttributes());
            // store resource uris
            storeMultipleRowsForSen("sen_resource_uri", "uri", sen.getId(), sen.getFeedUris());
            // store schemas
            List<String> schemasToStore = sen.getSchemas();
            schemasToStore.remove(ScimEventNotification.EVENT_SCHEMA); // remove the EVENT_SCHEMA, it's in all
            storeMultipleRowsForSen("sen_schema", "name", sen.getId(), schemasToStore);

        } else {
            // sen is already stored, just update nextMsgId
            String SQL = "UPDATE feed_sen SET nextMsg=? WHERE feedId=? AND senId=?";
            jdbcTemplate.update(SQL, nextMsgId, feedId, sen.getId());
        }
    }

    public void removeSen(Long senId) {
        if (senId == null) throw new IllegalStateException("ScimEventNotification id cannot be null.");
        String SQL = "DELETE FROM " + TABLE_NAME + " WHERE id=?";
        int rows = jdbcTemplate.update(SQL, senId);
        if (rows > 1) throw new IllegalStateException("More than one subscriber removed.");
    }

    public Set<Long> getIdsForFeed(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        String SQL = "SELECT id FROM " + TABLE_NAME + " JOIN feed_sen ON scim_event_notification.id=feed_sen.senId " +
                " WHERE feed_sen.feedId=?";
        return new HashSet<Long>(jdbcTemplate.queryForList(SQL, Long.class, feed.getId()));
    }

    /* ============ PRIVATE METHODS ============= */

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
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        sen.setId(id.longValue());
    }

    private void storeFeedSenRelationship(ScimEventNotification sen, Long feedId, Long nextMsgId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.clear();
        params.put("feedId", feedId);
        params.put("senId", sen.getId());
        params.put("nextMsg", nextMsgId);
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("feed_sen");
        jdbcInsert.execute(params);
    }

    private void storeMultipleRowsForSen(String tableName, String columnName, Long senId, List<String> values) {
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(tableName);
        Map<String, Object> params = new HashMap<String, Object>();
        for (String value: values) {
            params.clear();
            params.put(columnName, value);
            params.put("senId", senId);
            jdbcInsert.execute(params);
        }
    }
}
