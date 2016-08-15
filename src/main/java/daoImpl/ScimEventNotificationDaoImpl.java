package daoImpl;

import core.Feed;
import core.ScimEventNotification;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.NullValueInNestedPathException;
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

    /**
     * Feeds connected with the sen has to be already created.
     * @param sen
     * @param prevMsgId
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
            storeMultipleRowsForSen("sen_attribute", "name", sen.getId(), sen.getAttributes());
            // store resource uris
            storeMultipleRowsForSen("sen_resource_uri", "uri", sen.getId(), sen.getResourceUris());
            // store schemas
            List<String> schemasToStore = new ArrayList<String>(sen.getSchemas());
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

    public void removeSen(Long senId) {
        if (senId == null) throw new NullPointerException("ScimEventNotification id cannot be null.");

        // remove feed uris
        String SQL = "DELETE FROM feed_sen WHERE senId=?";
        jdbcTemplate.update(SQL, senId);

        // remove sen row
        SQL = "DELETE FROM " + TABLE_NAME + " WHERE id=?";
        int rows = jdbcTemplate.update(SQL, senId);
        if (rows > 1) throw new IllegalStateException("More than one subscriber removed.");
        if (rows == 0) throw new IllegalStateException("No such subscriber.");
    }

    public Set<Long> getIdsForFeed(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored.");
        String SQL = "SELECT id FROM " + TABLE_NAME + " JOIN feed_sen ON scim_event_notification.id=feed_sen.senId " +
                " WHERE feed_sen.feedId=?";
        return new HashSet<Long>(jdbcTemplate.queryForList(SQL, Long.class, feed.getId()));
    }

    public ScimEventNotification getById(Long id) {
        if (id == null) throw new NullPointerException("Id cannot be null.");

        // get sen row from db
        String SQL = "SELECT * FROM " + TABLE_NAME + " WHERE id=?";
        SqlRowSet rs = jdbcTemplate.queryForRowSet(SQL, id);
        if (!rs.next()) throw new IllegalStateException("ScimEventNotification is not stored.");

        // get schemas
        SQL = "SELECT name FROM sen_schema WHERE senId=?";
        List<String> schemas = jdbcTemplate.queryForList(SQL, String.class, id);
        schemas.add(ScimEventNotification.EVENT_SCHEMA);

        // get feed uris
        SQL = "SELECT feed.uri FROM feed JOIN feed_sen ON feed.id=feed_sen.feedId WHERE senId=?";
        List<String> feedUris = jdbcTemplate.queryForList(SQL, String.class, id);

        // get publisher uri
        String publisherUri = rs.getString("publisherUri");

        // get resource uris
        SQL = "SELECT uri FROM sen_resource_uri WHERE senId=?";
        List<String> resourceUris = jdbcTemplate.queryForList(SQL, String.class, id);

        // get type
        String type = rs.getString("type");

        // get attributes
        SQL = "SELECT name FROM sen_attribute WHERE senId=?";
        List<String> attributes = jdbcTemplate.queryForList(SQL, String.class, id);

        // get values
        String json = getStringOutOfClob(rs.getObject("sen_values"));
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

    public Long getMessagePredecessor(ScimEventNotification sen, Feed feed) {
        if (sen == null) throw new NullPointerException("ScimEventNotification cannot be null.");
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (sen.getId() == null) throw new IllegalStateException("ScimEventNotification is not stored.");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored.");
        String SQL = "SELECT prevMsgId FROM feed_sen WHERE senId=? AND feedId=?";
        return jdbcTemplate.queryForObject(SQL, Long.class, sen.getId(), feed.getId());
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

    private void storeFeedSenRelationship(ScimEventNotification sen, Long feedId, Long prevMsgId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.clear();
        params.put("feedId", feedId);
        params.put("senId", sen.getId());
        params.put("prevMsgId", prevMsgId);
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
            String theName = sb.toString();
        } catch (SQLException e) {
            throw new IllegalStateException("Wrong clob object for sen values.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Wrong clob object for sen values.", e);
        }
        return sb.toString();
    }
}
