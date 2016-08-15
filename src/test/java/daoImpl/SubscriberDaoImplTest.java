package daoImpl;

import core.*;
import dao.SubscriberDao;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by xmauritz on 8/8/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DaoTestConfig.class)
public class SubscriberDaoImplTest {

    @Inject
    private DataSource dataSource;

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    @InjectMocks
    private SubscriberDao subscriberDao;

    private static final String URI = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String[] FILE_NAMES = new String[]{"sen1.json", "sen2.json", "sen3.json"};

    private List<ScimEventNotification> sens;
    private Subscriber subscriber;
    private Feed feed;
    private Subscription subscription;

    @Mock
    ScimEventNotificationDaoImpl senDao;

    @Before
    public void setUp() throws Exception {
        // set local variables
        sens = new ArrayList<ScimEventNotification>();
        subscriber = new Subscriber("subid");
        feed = new Feed(URI);
        subscription = new Subscription(URI, SubscriptionModeEnum.poll, URI);

        // set up mocks
        MockitoAnnotations.initMocks(this);

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
    }

    @After
    public void tearDown() throws Exception {
        Resource drop = new ClassPathResource("sql/dropTablesDerby.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), drop);
    }

    @Test
    public void updateTest() throws Exception {
        String SBSC_ID = "subid";
        Subscriber subscriber1 = new Subscriber(SBSC_ID);
        Subscriber subscriber2 = new Subscriber(SBSC_ID + "1");
        Subscriber subscriber3 = new Subscriber(SBSC_ID + "2");
        Map<String, Subscriber> subscribers = new HashMap<String, Subscriber>();
        subscribers.put(SBSC_ID, subscriber1);
        createSubscriberInDb(subscriber2);
        createSubscriberInDb(subscriber3);

        // update
        subscriberDao.update(subscribers);
        assertFalse(subscribers.containsKey(SBSC_ID));
        assertTrue(subscribers.containsKey(SBSC_ID + "1"));
        assertTrue(subscribers.containsKey(SBSC_ID + "2"));
        assertEquals(subscriber2, subscribers.get(SBSC_ID + "1"));
        assertEquals(subscriber3, subscribers.get(SBSC_ID + "2"));
    }

    @Test
    public void createTest() throws Exception {
        subscriberDao.create(subscriber);
        assertNotNull(subscriber.getId());
        Subscriber returned = getByIdFromDb(subscriber.getId());
        assertEquals(subscriber.getId(), returned.getId());
        assertEquals(subscriber, returned);
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void removeTest() throws Exception {
        subscriber.addSubscription(subscription);
        createSubscriberInDb(subscriber);

        assertNotNull(getByIdFromDb(subscriber.getId()));
        subscriberDao.remove(subscriber);

        // assert subscription was removed
        assertEquals(0, getAllSubscriptionIdsForSubscriber(subscriber).size());
        // throw exception due to no such subscriber in db
        getByIdFromDb(subscriber.getId());
    }

    @Test
    public void getPollSubscribersTest() throws Exception {
        // create proper feed in db
        Subscriber sbsc = new Subscriber("other");
        Subscriber webCallbackSbsc = new Subscriber("webCallback");
        createSubscriberInDb(subscriber);
        createSubscriberInDb(sbsc);
        createSubscriberInDb(webCallbackSbsc);

        createFeedInDb(feed);
        createSenInDb(sens.get(0), feed.getId(), null);
        Long senId = sens.get(0).getId();
        Subscription subscription1 = new Subscription(URI, SubscriptionModeEnum.poll, URI);
        Subscription webCallBackSubscription = new Subscription(URI, SubscriptionModeEnum.webCallback, URI);
        subscriber.addSubscription(subscription);
        sbsc.addSubscription(subscription1);
        createSubscriptionInDb(subscription, feed, subscriber, null);
        createSubscriptionInDb(subscription1, feed, sbsc, senId);
        createSubscriptionInDb(webCallBackSubscription, feed, webCallbackSbsc, null);

        // mock senDao.getById method
        when(senDao.getById(anyLong())).thenReturn(sens.get(0));

        // get poll subscribers together with the last seen msg in the feed
        Map<Subscriber, ScimEventNotification> pollSubscribers = subscriberDao.getPollSubscribers(feed);

        // assert size
        assertEquals(2, pollSubscribers.size());

        for (Subscriber returned : pollSubscribers.keySet()) {
            if (returned.equals(subscriber)) {
                assertEquals(subscriber.getSubscriptions(), returned.getSubscriptions());
                assertNull(pollSubscribers.get(subscriber));
            } else if (returned.equals(sbsc)) {
                assertEquals(sbsc.getSubscriptions(), returned.getSubscriptions());
                assertEquals(sens.get(0), pollSubscribers.get(sbsc));
            } else {
                fail("pollSubscribers contains extra subscriber.");
            }
        }
    }

    @Test
    public void getWebCallbackSubscribersTest() throws Exception {
        // create proper feed in db
        Subscriber sbsc1 = new Subscriber("wc1");
        Subscriber sbsc2 = new Subscriber("wc2");
        createSubscriberInDb(subscriber);
        createSubscriberInDb(sbsc1);
        createSubscriberInDb(sbsc2);

        createFeedInDb(feed);
        Subscription subscription1 = new Subscription(URI, SubscriptionModeEnum.webCallback, URI);
        Subscription subscription2 = new Subscription(URI, SubscriptionModeEnum.webCallback, URI);
        subscriber.addSubscription(subscription);
        sbsc1.addSubscription(subscription1);
        sbsc2.addSubscription(subscription2);
        createSubscriptionInDb(subscription, feed, subscriber, null);
        createSubscriptionInDb(subscription1, feed, sbsc1, null);
        createSubscriptionInDb(subscription2, feed, sbsc2, null);

        // get web callback subscribers
        Set<Subscriber> webCallbacksubscribers = subscriberDao.getWebCallbackSubscribers(feed);

        // assert size
        assertEquals(2, webCallbacksubscribers.size());

        // assert
        for (Subscriber sbsc : webCallbacksubscribers) {
            if (sbsc.equals(sbsc1)) {
                assertEquals(sbsc.getSubscriptions(), sbsc1.getSubscriptions());
            } else if (sbsc.equals(sbsc2)) {
                assertEquals(sbsc.getSubscriptions(), sbsc2.getSubscriptions());
            } else {
                fail("webCallbacksubscribers contains extra subscriber.");
            }
        }
    }


    /* ============= Illegal values =============== */

    @Test(expected = NullPointerException.class)
    public void updateWithNullSubscribers() throws Exception {
        subscriberDao.update(null);
    }

    @Test(expected = NullPointerException.class)
    public void createWithNullSubscriber() throws Exception {
        subscriberDao.create(null);
    }

    @Test(expected = NullPointerException.class)
    public void removeWithNullSubscriber() throws Exception {
        subscriberDao.remove(null);
    }

    @Test(expected = IllegalStateException.class)
    public void removeWithNotStoredSubscriber() throws Exception {
        subscriber.setId(null);
        subscriberDao.remove(subscriber);
    }

    @Test(expected = NullPointerException.class)
    public void getPollSubscribersWithNullFeed() throws Exception {
        subscriberDao.getPollSubscribers(null);
    }

    @Test(expected = IllegalStateException.class)
    public void getPollSubscribersWithNotStoredFeed() throws Exception {
        feed.setId(null);
        subscriberDao.getPollSubscribers(feed);
    }

    @Test(expected = NullPointerException.class)
    public void getWebCallbackSubscribersWithNullFeed() throws Exception {
        subscriberDao.getWebCallbackSubscribers(null);
    }

    @Test(expected = IllegalStateException.class)
    public void getWebCallbackSubscribersWithNotStoredFeed() throws Exception {
        feed.setId(null);
        subscriberDao.getWebCallbackSubscribers(feed);
    }

    /* ========= PRIVATE METHODS ================== */

    public Subscriber getByIdFromDb(Long id) {
        class SubsciberMapper implements RowMapper<Subscriber> {
            public Subscriber mapRow(ResultSet rs, int rowNum) throws SQLException {
                Subscriber subscriber = new Subscriber(rs.getString("identifier"));
                subscriber.setId(rs.getLong("id"));
                return subscriber;
            }
        }
        if (id == null) throw new IllegalStateException("Subscriber is not stored.");
        String SQL = "SELECT * FROM subscriber WHERE id=" + id;
        return jdbcTemplate.queryForObject(SQL, new SubsciberMapper());
    }

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
        storePureSen(sen);
        // store feed - sen relationship
        storeFeedSenRelationship(sen, feedId, prevMsgId);
        // store attributes
        storeMultipleRowsForSen("sen_attribute", "name", sen.getId(), sen.getAttributes());
        // store resource uris
        storeMultipleRowsForSen("sen_resource_uri", "uri", sen.getId(), sen.getResourceUris());
        // store schemas
        List<String> schemasToStore = sen.getSchemas();
        schemasToStore.remove(ScimEventNotification.EVENT_SCHEMA); // remove the EVENT_SCHEMA, it's in all
        storeMultipleRowsForSen("sen_schema", "name", sen.getId(), schemasToStore);
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

    private Set<Long> getAllSubscriptionIdsForSubscriber(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        if (subscriber.getId() == null) throw new IllegalStateException("Subscriber is not stored yet.");
        String SQL = "SELECT id FROM subscription WHERE subscriberId=?";
        return new HashSet<Long>(jdbcTemplate.queryForList(SQL, Long.class, subscriber.getId()));
    }

}