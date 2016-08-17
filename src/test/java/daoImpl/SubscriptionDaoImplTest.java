package daoImpl;

import core.Feed;
import core.Subscriber;
import core.Subscription;
import core.SubscriptionModeEnum;
import dao.SubscriptionDao;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Created by xmauritz on 8/8/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DaoTestConfig.class)
public class SubscriptionDaoImplTest {

    @Inject
    private DataSource dataSource;

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private DaoTestUtils testUtils;

    @Inject
    private SubscriptionDao subscriptionDao;

    private static final String URI = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String SBSC_ID = "id";

    private Subscriber subscriber;
    private Feed feed;
    private Subscription subscription;

    @Before
    public void setUp() throws Exception {
        // load db schema
        Resource create = new ClassPathResource("sql/createTablesDerby.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), create);

        // prepare data
        subscriber = new Subscriber(SBSC_ID);
        feed = new Feed(URI);
        subscription = new Subscription(URI, SubscriptionModeEnum.poll, URI);
        testUtils.createSubscriberInDb(subscriber);
        testUtils.createFeedInDb(feed);
    }

    @After
    public void tearDown() throws Exception {
        Resource drop = new ClassPathResource("sql/dropTablesDerby.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), drop);
    }

    @Test
    public void createTest() throws Exception {
        subscriptionDao.create(subscription, subscriber, feed);
        assertNotNull(subscription.getId());
        Subscription returned = getByIdFromDb(subscription.getId());
        assertEquals(subscription.getId(), returned.getId());
        assertEquals(subscription, returned);
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void removeTest() throws Exception {
        testUtils.createSubscriptionInDb(subscription, feed, subscriber, null);
        assertNotNull(getByIdFromDb(subscription.getId()));
        subscriptionDao.remove(SBSC_ID, URI);

        // throw exception due to no such subscription in db
        getByIdFromDb(subscription.getId());
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void removeByIdTest() throws Exception {
        testUtils.createSubscriptionInDb(subscription, feed, subscriber, null);
        assertNotNull(getByIdFromDb(subscription.getId()));
        subscriptionDao.remove(subscription.getId());

        // throw exception due to no such subscription in db
        getByIdFromDb(subscription.getId());
    }

    @Test
    public void getAllIdsForSubscriberTest() throws Exception {
        Feed feed1 = new Feed("other");
        testUtils.createFeedInDb(feed1);
        Subscription subscr = new Subscription("other", SubscriptionModeEnum.webCallback, URI);
        testUtils.createSubscriptionInDb(subscription, feed, subscriber, null);
        testUtils.createSubscriptionInDb(subscr, feed1, subscriber, null);

        Set<Long> ids = subscriptionDao.getAllIdsForSubscriber(subscriber);

        assertEquals(2, ids.size());
        assertTrue(ids.contains(subscription.getId()));
        assertTrue(ids.contains(subscr.getId()));
    }

    @Test
    public void getAllIdsForFeedTest() throws Exception {
        Subscriber sbsc = new Subscriber("second");
        Subscription subscr = new Subscription(URI, SubscriptionModeEnum.webCallback, URI);
        testUtils.createSubscriberInDb(sbsc);
        testUtils.createSubscriptionInDb(subscription, feed, subscriber, null);
        testUtils.createSubscriptionInDb(subscr, feed, sbsc, null);

        Set<Long> ids = subscriptionDao.getAllIdsForFeed(feed);

        assertEquals(2, ids.size());
        assertTrue(ids.contains(subscription.getId()));
        assertTrue(ids.contains(subscr.getId()));
    }


    /* ============= Illegal values =============== */

    @Test(expected = NullPointerException.class)
    public void createWithNullSubscription() throws Exception {
        subscriptionDao.create(null, subscriber, feed);
    }

    @Test(expected = NullPointerException.class)
    public void createWithNullSubscriber() throws Exception {
        subscriptionDao.create(subscription, null, feed);
    }

    @Test(expected = NullPointerException.class)
    public void createWithNullFeed() throws Exception {
        subscriptionDao.create(subscription, subscriber, null);
    }

    @Test(expected = NullPointerException.class)
    public void createWithSubscriberNotStored() throws Exception {
        subscriber.setId(null);
        subscriptionDao.create(subscription, subscriber, feed);
    }

    @Test(expected = NullPointerException.class)
    public void createWithFeedNotStored() throws Exception {
        feed.setId(null);
        subscriptionDao.create(subscription, subscriber, feed);
    }

    @Test(expected = NullPointerException.class)
    public void removeWithSubscriberIdNull() throws Exception {
        subscriptionDao.remove(null, URI);
    }

    @Test(expected = NullPointerException.class)
    public void removeWithNullUri() throws Exception {
        subscriptionDao.remove(SBSC_ID, null);
    }

    @Test(expected = NullPointerException.class)
    public void removeWithNullId() throws Exception {
        subscriptionDao.remove(null);
    }

    @Test(expected = NullPointerException.class)
    public void getAllIdsForNullSubsciber() throws Exception {
        subscriptionDao.getAllIdsForSubscriber(null);
    }

    @Test(expected = IllegalStateException.class)
    public void getAllIdsForNotStoredSubsciber() throws Exception {
        subscriber.setId(null);
        subscriptionDao.getAllIdsForSubscriber(subscriber);
    }

    @Test(expected = NullPointerException.class)
    public void getAllIdsForNullFeed() throws Exception {
        subscriptionDao.getAllIdsForFeed(null);
    }

    @Test(expected = IllegalStateException.class)
    public void getAllIdsForNotStoredFeed() throws Exception {
        feed.setId(null);
        subscriptionDao.getAllIdsForFeed(feed);
    }
    /* ========= PRIVATE METHODS ================== */

    public Subscription getByIdFromDb(Long id) {
        class SubscriptionMapper implements RowMapper<Subscription> {
            public Subscription mapRow(ResultSet rs, int rowNum) throws SQLException {
                Subscription subscription = new Subscription(
                        rs.getString("uri"),
                        SubscriptionModeEnum.valueOf(rs.getString("mode")),
                        rs.getString("eventUri"));
                subscription.setId(rs.getLong("id"));
                return subscription;
            }
        }
        if (id == null) throw new IllegalStateException("Subscription is not stored.");
        String SQL = "SELECT * FROM subscription  JOIN feed ON subscription.feedId=feed.id " +
                "WHERE subscription.id=" + id;
        return jdbcTemplate.queryForObject(SQL, new SubscriptionMapper());
    }
}