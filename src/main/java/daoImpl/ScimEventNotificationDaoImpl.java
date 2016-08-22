package daoImpl;

import core.Feed;
import core.ScimEventNotification;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * DAO for the Scim Event Notification object.
 *
 * @author Jiri Mauritz
 */
@Named
@Singleton
public class ScimEventNotificationDaoImpl {

    private static final String TABLE_NAME = "scim_event_notification";

    @Inject
    private JdbcTemplate jdbcTemplate;

    /**
     * Store Scim Event Notification to the storage.
     * If the sen is already stored, only prevMsgId is updated.
     * Feeds connected with the sen has to be already created.
     * Only relationship for specified feed is created, other feeds are left for other processing.
     *
     * @param sen       to be stored
     * @param feedId    of the feed, where sen belongs
     * @param prevMsgId is id of the previous message in the queue of the feed
     */
    public void storeSen(ScimEventNotification sen, Long feedId, Long prevMsgId) {
        if (sen == null) throw new NullPointerException("ScimEventNotification cannot be null");
        if (feedId == null) throw new NullPointerException("Feed id cannot be null.");
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
            Set<String> schemasToStore = new HashSet<String>(sen.getSchemas());
            schemasToStore.remove(ScimEventNotification.EVENT_SCHEMA); // remove the EVENT_SCHEMA, it's in all
            storeMultipleRowsForSen("scim_sen_schema", "name", sen.getId(), schemasToStore);

        } else {
            // sen is already stored
            int records = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scim_feed_sen WHERE feed_id=? AND sen_id=?", Integer.class, feedId, sen.getId());
            if (records > 0) {
                // feed has already record of sen -> just update value
                String SQL = "UPDATE scim_feed_sen SET prev_msg_id=? WHERE feed_id=? AND sen_id=?";
                jdbcTemplate.update(SQL, prevMsgId, feedId, sen.getId());
            } else {
                // feed has no record of sen -> create new
                storeFeedSenRelationship(sen, feedId, prevMsgId);
            }
        }
    }

    /**
     * Remove Scim Event Notification in the storage.
     * It is removed from all feeds.
     * The other records in storage (schemas, attributes, resource uris) are removed by cascade mode in the storage.
     *
     * @param senId of the scim event notification
     */
    public void removeSen(Long senId) {
        if (senId == null) throw new NullPointerException("ScimEventNotification id cannot be null.");

        // remove feed uris
        String SQL = "DELETE FROM scim_feed_sen WHERE sen_id=?";
        jdbcTemplate.update(SQL, senId);

        // remove sen row
        SQL = "DELETE FROM " + TABLE_NAME + " WHERE id=?";
        int rows = jdbcTemplate.update(SQL, senId);
        if (rows > 1) throw new IllegalStateException("More than one subscriber removed.");
        if (rows == 0) throw new IllegalStateException("No such subscriber.");
    }

    /**
     * Retrieve set of all ids from the storage that belong to the specified feed.
     *
     * @param feed for which the sens will be retrieved
     * @return set of all ids for the feed
     */
    public Set<Long> getIdsForFeed(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored.");
        String SQL = "SELECT id FROM " + TABLE_NAME + " JOIN scim_feed_sen ON scim_event_notification.id=scim_feed_sen.sen_id " +
                " WHERE scim_feed_sen.feed_id=?";
        return new HashSet<Long>(jdbcTemplate.queryForList(SQL, Long.class, feed.getId()));
    }

    /**
     * Retrieve Scim Event Notification by id from the storage.
     *
     * @param id of the Scim Event Notification
     * @return sen
     */
    public ScimEventNotification getById(Long id) {
        if (id == null) throw new NullPointerException("Id cannot be null.");

        // get sen row from db
        String SQL = "SELECT * FROM " + TABLE_NAME + " WHERE id=?";
        SqlRowSet rs = jdbcTemplate.queryForRowSet(SQL, id);
        if (!rs.next()) throw new IllegalStateException("ScimEventNotification is not stored.");

        // get schemas
        SQL = "SELECT name FROM scim_sen_schema WHERE sen_id=?";
        Set<String> schemas = new HashSet<String>(jdbcTemplate.queryForList(SQL, String.class, id));
        schemas.add(ScimEventNotification.EVENT_SCHEMA);

        // get feed uris
        SQL = "SELECT scim_feed.uri FROM scim_feed JOIN scim_feed_sen ON scim_feed.id=scim_feed_sen.feed_id WHERE sen_id=?";
        Set<String> feedUris = new HashSet<String>(jdbcTemplate.queryForList(SQL, String.class, id));

        // get publisher uri
        String publisherUri = rs.getString("publisher_uri");

        // get resource uris
        SQL = "SELECT uri FROM scim_sen_resource_uri WHERE sen_id=?";
        Set<String> resourceUris = new HashSet<String>(jdbcTemplate.queryForList(SQL, String.class, id));

        // get type
        String type = rs.getString("type");

        // get attributes
        SQL = "SELECT name FROM scim_sen_attribute WHERE sen_id=?";
        Set<String> attributes = new HashSet<String>(jdbcTemplate.queryForList(SQL, String.class, id));

        // get values
        Object senValues = rs.getObject("sen_values");
        String json;
        if (senValues instanceof Clob) {
            json = getStringOutOfClob(senValues);
        } else {
            json = (String) senValues;
        }
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> values;
        try {
            values = (Map<String, Object>) mapper.readValue(json, Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid JSON.", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException("Invalid JSON.", e);
        }

        // instantiate
        ScimEventNotification sen = new ScimEventNotification(
                schemas, feedUris, publisherUri, resourceUris, type, attributes, values);
        sen.setId(id);
        return sen;
    }

    /**
     * Retrieve id of the previous message from the storage, representing the previous message in the queue of the feed.
     * If the message is first in the queue, returns null.
     *
     * @param sen  for which predecessor is found
     * @param feed
     * @return id of the previous message, null if the sen is first in the queue
     */
    public Long getMessagePredecessor(ScimEventNotification sen, Feed feed) {
        if (sen == null) throw new NullPointerException("ScimEventNotification cannot be null.");
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (sen.getId() == null) throw new IllegalStateException("ScimEventNotification is not stored.");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored.");
        String SQL = "SELECT prev_msg_id FROM scim_feed_sen WHERE sen_id=? AND feed_id=?";
        return jdbcTemplate.queryForObject(SQL, Long.class, sen.getId(), feed.getId());
    }


    /* ============ PRIVATE METHODS ============= */

    private void storePureSen(ScimEventNotification sen) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("publisher_uri", sen.getPublisherUri());
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

    private void storeFeedSenRelationship(ScimEventNotification sen, Long feedId, Long prevMsgId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.clear();
        params.put("feed_id", feedId);
        params.put("sen_id", sen.getId());
        params.put("prev_msg_id", prevMsgId);
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("scim_feed_sen");
        jdbcInsert.execute(params);
    }

    private void storeMultipleRowsForSen(String tableName, String columnName, Long senId, Set<String> values) {
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(tableName);
        Map<String, Object> params = new HashMap<String, Object>();
        for (String value : values) {
            params.clear();
            params.put(columnName, value);
            params.put("sen_id", senId);
            jdbcInsert.execute(params);
        }
    }

    private String getStringOutOfClob(Object clobObject) {
        Clob clob = (Clob) clobObject;
        final StringBuilder sb = new StringBuilder();
        try {
            final Reader reader = clob.getCharacterStream();
            final BufferedReader br = new BufferedReader(reader);
            int b;
            while (-1 != (b = br.read())) {
                sb.append((char) b);
            }
            br.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Wrong clob object for sen values.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Wrong clob object for sen values.", e);
        }
        return sb.toString();
    }
}
