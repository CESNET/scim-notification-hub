package daoImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.Feed;
import core.ScimEventNotification;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.StringUtils;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Test of the Scim Event Notification Dao implementation.
 *
 * @author Jiri Mauritz
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DaoTestConfig.class)
public class ScimEventNotificationDaoImplTest {

    @Inject
    private DataSource dataSource;

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private DaoTestUtils testUtils;

    @Inject
    private ScimEventNotificationDaoImpl senDao;

    private static final String URImail = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String URIedu = "https://perun.cesnet.cz/scim-notification/eduroam/eduroam_radius";
    private static final String[] FILE_NAMES = new String[]{"sen1.json", "sen2.json", "sen3.json"};

    private List<ScimEventNotification> sens = new ArrayList<>();
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
            List<String> jsonLines = Files.readAllLines(Paths.get(ClassLoader.getSystemResource(fileName).toURI()), Charset.defaultCharset());
            sens.add(mapper.readValue(StringUtils.collectionToDelimitedString(jsonLines, "\n"), ScimEventNotification.class));
        }

        // prepare data
        testUtils.createFeedInDb(feed);
    }

    @After
    public void tearDown() throws Exception {
        Resource drop = new ClassPathResource("sql/dropTables.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), drop);
    }

    @Test
    public void storeSenTest() throws Exception {
        testUtils.createFeedInDb(feedEdu);
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
        testUtils.createSenInDb(sens.get(0), feed.getId(), null);
        senDao.removeSenFromFeed(sens.get(0).getId(), feed.getId());
        getById(sens.get(0).getId());
    }

    @Test
    public void getIdsForFeedTest() throws Exception {
        testUtils.createFeedInDb(feedEdu);
        testUtils.createSenInDb(sens.get(0), feed.getId(), null);
        testUtils.createSenInDb(sens.get(1), feed.getId(), null);
        testUtils.createSenInDb(sens.get(2), feedEdu.getId(), null);

        Set<Long> ids = senDao.getIdsForFeed(feed);

        assertTrue(ids.contains(sens.get(0).getId()));
        assertTrue(ids.contains(sens.get(1).getId()));
        assertFalse(ids.contains(sens.get(2).getId()));
    }

    @Test
    public void getByIdTest() throws Exception {
        testUtils.createFeedInDb(feedEdu);
        testUtils.createSenInDb(sens.get(1), feed.getId(), null);
        testUtils.createSenInDb(sens.get(0), feed.getId(), null);
        testUtils.createSenInDb(sens.get(0), feedEdu.getId(), null);

        ScimEventNotification returned = senDao.getById(sens.get(0).getId());

        assertEquals(sens.get(0), returned);
    }

    @Test
    public void getMessagePredecessorTest() throws Exception {
        testUtils.createSenInDb(sens.get(1), feed.getId(), null);
        testUtils.createSenInDb(sens.get(0), feed.getId(), sens.get(1).getId());

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
        senDao.removeSenFromFeed(null, feed.getId());
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
        testUtils.createSenInDb(sens.get(0), feed.getId(), null);
        senDao.getMessagePredecessor(sens.get(0), null);
    }

    @Test(expected = IllegalStateException.class)
    public void getMessagePredecessorWithNonExistingSen() throws Exception {
        senDao.getMessagePredecessor(sens.get(0), feed);
    }

    @Test(expected = IllegalStateException.class)
    public void getMessagePredecessorWithNonExistingFeed() throws Exception {
        testUtils.createSenInDb(sens.get(0), feed.getId(), null);
        senDao.getMessagePredecessor(sens.get(0), new Feed("not stored feed"));
    }

    /* ========= PRIVATE METHODS ================== */

    private ScimEventNotification getById(Long id) {
        if (id == null) throw new NullPointerException("Id cannot be null.");

        // get sen row from db
        String SQL = "SELECT * FROM scim_event_notification WHERE id=?";
        SqlRowSet rs = jdbcTemplate.queryForRowSet(SQL, id);
        if (!rs.next()) throw new IllegalStateException("ScimEventNotification is not stored.");

        // get schemas
        SQL = "SELECT name FROM scim_sen_schema WHERE sen_id=?";
        Set<String> schemas = new HashSet<>(jdbcTemplate.queryForList(SQL, String.class, id));
        schemas.add(ScimEventNotification.EVENT_SCHEMA);

        // get feed uris
        SQL = "SELECT scim_feed.uri FROM scim_feed JOIN scim_feed_sen ON scim_feed.id=scim_feed_sen.feed_id WHERE sen_id=?";
        Set<String> feedUris = new HashSet<>(jdbcTemplate.queryForList(SQL, String.class, id));

        // get publisher uri
        String publisherUri = rs.getString("publisher_uri");

        // get resource uris
        SQL = "SELECT uri FROM scim_sen_resource_uri WHERE sen_id=?";
        Set<String> resourceUris = new HashSet<>(jdbcTemplate.queryForList(SQL, String.class, id));

        // get type
        String type = rs.getString("type");

        // get attributes
        SQL = "SELECT name FROM scim_sen_attribute WHERE sen_id=?";
        Set<String> attributes = new HashSet<>(jdbcTemplate.queryForList(SQL, String.class, id));

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
        } catch (SQLException e) {
            throw new IllegalStateException("Wrong clob object for sen values.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Wrong clob object for sen values.", e);
        }
        return sb.toString();
    }

    private Long getPrevMsgId(ScimEventNotification sen, Feed feed) {
        String SQL = "SELECT prev_msg_id FROM scim_feed_sen WHERE feed_id=? AND sen_id=?";
        return jdbcTemplate.queryForObject(SQL, Long.class, feed.getId(), sen.getId());

    }
}
