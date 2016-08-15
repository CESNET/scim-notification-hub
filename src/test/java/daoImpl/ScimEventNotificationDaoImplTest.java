package daoImpl;

import core.*;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by xmauritz on 8/15/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DaoTestConfig.class)
public class ScimEventNotificationDaoImplTest {

    @Inject
    private DataSource dataSource;

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private ScimEventNotificationDaoImpl senDao;

    private static final String URImail = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    public static final String URIedu = "https://perun.cesnet.cz/scim-notification/eduroam/eduroam_radius";
    private static final String[] FILE_NAMES = new String[]{"sen1.json", "sen2.json", "sen3.json"};

    private List<ScimEventNotification> sens = new ArrayList<ScimEventNotification>();
    private Feed feed = new Feed(URImail);
    private Feed feedEdu = new Feed(URIedu);

    @Before
    public void setUp() throws Exception {
        // load db tables
        Resource create = new ClassPathResource("sql/createTablesDerby.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), create);

        // load sens
        ObjectMapper mapper = new ObjectMapper();
        // load sen objects from files
        for (String fileName : FILE_NAMES) {
            List<String> jsonLines = Files.readAllLines(Paths.get(ClassLoader.getSystemResource(fileName).toURI()));
            sens.add(mapper.readValue(String.join("\n", jsonLines), ScimEventNotification.class));
        }

        // prepare data
        createFeedInDb(feed);
    }

    @After
    public void tearDown() throws Exception {
        Resource drop = new ClassPathResource("sql/dropTablesDerby.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), drop);
    }

    @Test
    public void storeSenTest() throws Exception {
        createFeedInDb(feedEdu);
        senDao.storeSen(sens.get(1), feed.getId(), null);
        assertNotNull(sens.get(1).getId());
        senDao.storeSen(sens.get(0), feed.getId(), sens.get(1).getId());
        assertNotNull(sens.get(0).getId());
        senDao.storeSen(sens.get(0), feedEdu.getId(), null);
        assertNotNull(sens.get(0).getId());
        ScimEventNotification senFromDb = getById(sens.get(0).getId());
        ScimEventNotification senToCompare = sens.get(0);
        assertEquals(senToCompare, senFromDb);
        assertEquals(sens.get(1).getId(), getPrevMsgId(sens.get(0), feed));
    }

    @Test(expected = IllegalStateException.class)
    public void removeSenTest() throws Exception {
        createSenInDb(sens.get(0), feed.getId(), null);
        senDao.removeSen(sens.get(0).getId());
        getById(sens.get(0).getId());
    }

    @Test
    public void getIdsForFeedTest() throws Exception {
        createFeedInDb(feedEdu);
        createSenInDb(sens.get(0), feed.getId(), null);
        createSenInDb(sens.get(1), feed.getId(), null);
        createSenInDb(sens.get(2), feedEdu.getId(), null);

        Set<Long> ids = senDao.getIdsForFeed(feed);

        assertTrue(ids.contains(sens.get(0).getId()));
        assertTrue(ids.contains(sens.get(1).getId()));
        assertFalse(ids.contains(sens.get(2).getId()));
    }

    @Test
    public void getByIdTest() throws Exception {
        createFeedInDb(feedEdu);
        createSenInDb(sens.get(1), feed.getId(), null);
        createSenInDb(sens.get(0), feed.getId(), null);
        createSenInDb(sens.get(0), feedEdu.getId(), null);

        ScimEventNotification returned = senDao.getById(sens.get(0).getId());

        assertEquals(sens.get(0), returned);
    }

    @Test
    public void getMessagePredecessorTest() throws Exception {
        createSenInDb(sens.get(1), feed.getId(), null);
        createSenInDb(sens.get(0), feed.getId(), sens.get(1).getId());

        assertEquals(null, senDao.getMessagePredecessor(sens.get(1), feed));
        assertEquals(sens.get(1).getId(), senDao.getMessagePredecessor(sens.get(0), feed));
    }


    /* ============= Illegal values =============== */

    @Test(expected = NullPointerException.class)
    public void storeSenWithNullSen() throws Exception {
        senDao.storeSen(null, feed.getId(), null);
    }

    @Test(expected = NullPointerException.class)
    public void storeSenWithNullFeedId() throws Exception {
        senDao.storeSen(sens.get(0), null, null);
    }

    @Test(expected = NullPointerException.class)
    public void removeSenWithNullSenId() throws Exception {
        senDao.removeSen(null);
    }

    @Test(expected = IllegalStateException.class)
    public void removeSenWithNonExistingSen() throws Exception {
        senDao.removeSen(1l);
    }

    @Test(expected = NullPointerException.class)
    public void getIdsForFeedWithNullFeed() throws Exception {
        senDao.getIdsForFeed(null);
    }

    @Test(expected = IllegalStateException.class)
    public void getIdsForFeedWithNotStoredFeed() throws Exception {
        feed.setId(null);
        senDao.getIdsForFeed(feed);
    }

    @Test(expected = NullPointerException.class)
    public void getByIdWithNullId() throws Exception {
        senDao.getById(null);
    }

    @Test(expected = IllegalStateException.class)
    public void getByIdWithNonExistingSen() throws Exception {
        senDao.getById(1l);
    }

    @Test(expected = NullPointerException.class)
    public void getMessagePredecessorWithNullSen() throws Exception {
        senDao.getMessagePredecessor(null, feed);
    }

    @Test(expected = NullPointerException.class)
    public void getMessagePredecessorWithNullFeed() throws Exception {
        createSenInDb(sens.get(0), feed.getId(), null);
        senDao.getMessagePredecessor(sens.get(0), null);
    }

    @Test(expected = IllegalStateException.class)
    public void getMessagePredecessorWithNonExistingSen() throws Exception {
        senDao.getMessagePredecessor(sens.get(0), feed);
    }

    @Test(expected = IllegalStateException.class)
    public void getMessagePredecessorWithNonExistingFeed() throws Exception {
        createSenInDb(sens.get(0), feed.getId(), null);
        senDao.getMessagePredecessor(sens.get(0), new Feed("not stored feed"));
    }

    /* ========= PRIVATE METHODS ================== */

    private void createSubscriberInDb(Subscriber subscriber) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("identifier", subscriber.getIdentifier());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("subscriber").usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscriber.setId(id.longValue());
    }

    private void createFeedInDb(Feed feed) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("uri", feed.getUri());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("feed").usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        feed.setId(id.longValue());
    }

    private void createSubscriptionInDb(Subscription subscription, Feed feed, Subscriber subscriber, Long lastSeenMsg) {
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

    private void createSenInDb(ScimEventNotification sen, Long feedId, Long prevMsgId) {
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

    private void storeMultipleRowsForSen(String tableName, String columnName, Long senId, List<String> values) {
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(tableName);
        Map<String, Object> params = new HashMap<String, Object>();
        for (String value : values) {
            params.clear();
            params.put(columnName, value);
            params.put("senId", senId);
            jdbcInsert.execute(params);
        }
    }

    private ScimEventNotification getById(Long id) {
        if (id == null) throw new NullPointerException("Id cannot be null.");

        // get sen row from db
        String SQL = "SELECT * FROM scim_event_notification WHERE id=?";
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

    private Long getPrevMsgId(ScimEventNotification sen, Feed feed) {
        String SQL = "SELECT prevMsgId FROM feed_sen WHERE feedId=? AND senId=?";
        return jdbcTemplate.queryForObject(SQL, Long.class, feed.getId(), sen.getId());

    }
}