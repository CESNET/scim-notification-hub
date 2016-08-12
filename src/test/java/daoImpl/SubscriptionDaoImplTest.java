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
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

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
    private SubscriptionDao subscriptionDao;

    private static final String URI = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String SBSC_ID = "id";

    @Before
    public void setUp() throws Exception {
        Resource create = new ClassPathResource("sql/createTablesDerby.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), create);
    }

    @After
    public void tearDown() throws Exception {
        Resource drop = new ClassPathResource("sql/dropTablesDerby.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), drop);
    }

    @Test
    public void createTest() throws Exception {
        Subscriber subscriber = new Subscriber("subid");
        createSubscriberInDb(subscriber);
        Feed feed = new Feed(URI);
        createFeedInDb(feed);
        Subscription subscription = new Subscription(URI, SubscriptionModeEnum.poll, URI);
        subscriptionDao.create(subscription, subscriber, feed, null);
        assertNotNull(subscription.getId());
        Subscription returned = getByIdFromDb(subscription.getId());
        assertEquals(subscription.getId(), returned.getId());
        assertEquals(subscription, returned);
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void removeTest() throws Exception {
        Subscriber subscriber = new Subscriber(SBSC_ID);
        createSubscriberInDb(subscriber);
        Feed feed = new Feed(URI);
        createFeedInDb(feed);
        Subscription subscription = new Subscription(URI, SubscriptionModeEnum.poll, URI);
        createSubscriptionInDb(subscription, subscriber, feed);
        assertNotNull(getByIdFromDb(subscription.getId()));
        subscriptionDao.remove(SBSC_ID, URI);

        // throw exception due to no such subscription in db
        getByIdFromDb(subscription.getId());
    }

   /* ========= PRIVATE METHODS ================== */

    private void createSubscriptionInDb(Subscription subscription, Subscriber subscriber, Feed feed) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("mode", subscription.getMode().name());
        params.put("eventUri", subscription.getEventUri());
        params.put("subscriberId", subscriber.getId());
        params.put("feedId", feed.getId());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("subscription")
                .usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscription.setId(id.longValue());
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