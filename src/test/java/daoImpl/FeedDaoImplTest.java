package daoImpl;

import core.*;
import dao.FeedDao;
import dao.SubscriberDao;
import dao.SubscriptionDao;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.StringUtils;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test of the FeedDao implementation.
 *
 * @author Jiri Mauritz
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DaoTestConfig.class)
public class FeedDaoImplTest {

    @Inject
    private DataSource dataSource;

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private DaoTestUtils testUtils;

    @Inject
    @InjectMocks
    private FeedDao feedDao;

    @Mock
    private ScimEventNotificationDaoImpl senDao;

    @Mock
    private SubscriberDao subscriberDao;

    @Mock
    private SubscriptionDao subscriptionDao;

    private static final String URImail = "https://perun.cesnet.cz/scim-notification/storage-fi.ics.muni.cz/mailman";
    private static final String URIvpn = "https://perun.cesnet.cz/scim-notification/storage-fss.ics.muni.cz/openvpn";
    private static final String URIedu = "https://perun.cesnet.cz/scim-notification/eduroam/eduroam_radius";

    private static final String[] FILE_NAMES = new String[]{"sen1.json", "sen2.json", "sen3.json", "sen4.json"};

    private List<ScimEventNotification> sens;
    private Subscriber subscriber;
    private Feed feedMail;
    private Feed feedEdu;
    private Feed feedVpn;
    private Subscription subscription;


    @Before
    public void setUp() throws Exception {
        // set local variables
        sens = new ArrayList<>();
        subscriber = new Subscriber("subid");
        feedMail = new Feed(URImail);
        feedEdu = new Feed(URIedu);
        feedVpn = new Feed(URIvpn);
        subscription = new Subscription(URImail, SubscriptionModeEnum.poll, URImail);

        // set up mocks
        MockitoAnnotations.initMocks(this);

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
    }

    @After
    public void tearDown() throws Exception {
        Resource drop = new ClassPathResource("sql/dropTables.sql");
        ScriptUtils.executeSqlScript(dataSource.getConnection(), drop);
    }

    @Test
    public void updateIdentifiers() throws Exception {
        testUtils.createFeedInDb(feedMail);
        testUtils.createFeedInDb(feedEdu);
        Map<String, Feed> feeds = new HashMap<>();
        feeds.put(feedVpn.getUri(), feedVpn);
        feedDao.updateIdentifiers(feeds);

        assertTrue(feeds.containsKey(feedMail.getUri()));
        assertTrue(feeds.containsKey(feedEdu.getUri()));
        assertFalse(feeds.containsKey(feedVpn.getUri()));
    }

    @Test
    public void update() throws Exception {
        // set feed in db
        testUtils.createFeedInDb(feedMail);
        testUtils.createSubscriberInDb(subscriber);
        testUtils.createSenInDb(sens.get(0), feedMail.getId(), null);
        testUtils.createSenInDb(sens.get(1), feedMail.getId(), sens.get(0).getId());
        testUtils.createSenInDb(sens.get(3), feedMail.getId(), sens.get(1).getId());
        testUtils.createSubscriptionInDb(subscription, feedMail, subscriber, sens.get(0).getId());
        Subscriber sbsc = new Subscriber("second");
        testUtils.createSubscriberInDb(sbsc);
        Subscription sub = new Subscription(feedMail.getUri(), SubscriptionModeEnum.webCallback, URImail);
        testUtils.createSubscriptionInDb(sub, feedMail, sbsc, null);
        storeSlowestSubscriber(feedMail.getId(), subscriber.getId());

        // store other feeds, such that updated sens from db contain links to them and equality holds
        testUtils.createFeedInDb(feedEdu);
        testUtils.createSenInDb(sens.get(0), feedEdu.getId(), null);
        testUtils.createFeedInDb(feedVpn);
        testUtils.createSenInDb(sens.get(1), feedVpn.getId(), null);

        // mock methods
        Map<Subscriber, ScimEventNotification> pollSubscribers = new HashMap<>();
        pollSubscribers.put(subscriber, sens.get(0));
        when(subscriberDao.getPollSubscribers(feedMail)).thenReturn(pollSubscribers);
        when(subscriberDao.getWebCallbackSubscribers(feedMail)).thenReturn(new HashSet<>(Arrays.asList(sbsc)));
        Set<Long> ids = new HashSet<>(Arrays.asList(sens.get(0).getId(), sens.get(1).getId(), sens.get(3).getId()));
        when(senDao.getIdsForFeed(feedMail)).thenReturn(ids);
        when(senDao.getById(anyLong())).thenAnswer(new Answer<ScimEventNotification>() {
            public ScimEventNotification answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Map<Long, ScimEventNotification> senMap = new HashMap<>();
                senMap.put(sens.get(0).getId(), sens.get(0));
                senMap.put(sens.get(1).getId(), sens.get(1));
                senMap.put(sens.get(3).getId(), sens.get(3));
                return senMap.get(args[0]);
            }
        });
        when(senDao.getMessagePredecessor(any(ScimEventNotification.class), eq(feedMail))).thenAnswer(new Answer<Long>() {
            public Long answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                Map<ScimEventNotification, Long> predMap = new HashMap<>();
                predMap.put(sens.get(0), null);
                predMap.put(sens.get(1), sens.get(0).getId());
                predMap.put(sens.get(3), sens.get(1).getId());
                return predMap.get(args[0]);
            }
        });

        // update
        feedDao.update(feedMail);

        // assert feed
        assertTrue(feedMail.getPollSubscribersLastMsg().containsKey(subscriber));
        assertEquals(sens.get(0), feedMail.getPollSubscribersLastMsg().get(subscriber));
        assertEquals(3, feedMail.getMessages().size());
        assertEquals(sens.get(0), feedMail.getMessages().get(0));
        assertEquals(sens.get(1), feedMail.getMessages().get(1));
        assertEquals(sens.get(3), feedMail.getMessages().get(2));
        assertEquals(1, feedMail.getCallbackSubscribers().size());
        assertTrue(feedMail.getCallbackSubscribers().contains(sbsc));
        assertEquals(subscriber, feedMail.getSlowestPollSubscriber());
    }

    @Test
    public void updateWithRemovals() throws Exception {
        testUtils.createFeedInDb(feedMail);

        // set up feed in memory
        Subscriber sbscWC = new Subscriber("second");
        Subscription subWC = new Subscription(feedMail.getUri(), SubscriptionModeEnum.webCallback, URImail);
        sbscWC.addSubscription(subWC);
        Subscriber sbscPoll = new Subscriber("third");
        Subscription subPoll = new Subscription(feedMail.getUri(), SubscriptionModeEnum.poll, URImail);
        sbscPoll.addSubscription(subPoll);
        subscriber.addSubscription(subscription);
        Map<Subscriber, ScimEventNotification> pollSubscribers = new HashMap<>();
        pollSubscribers.put(subscriber, null);
        pollSubscribers.put(sbscPoll, sens.get(3));
        feedMail.setPollSubscribersLastMsg(pollSubscribers);
        feedMail.setSlowestPollSubscriber(subscriber);
        feedMail.addSubscriber(sbscWC);
        feedMail.newMsg(sens.get(0));
        feedMail.newMsg(sens.get(1));
        feedMail.newMsg(sens.get(3));

        // mock methods
        when(subscriberDao.getPollSubscribers(feedMail)).thenReturn(new HashMap<Subscriber, ScimEventNotification>());
        when(subscriberDao.getWebCallbackSubscribers(feedMail)).thenReturn(new HashSet<Subscriber>());
        when(senDao.getIdsForFeed(feedMail)).thenReturn(new HashSet<Long>());

        // update
        feedDao.update(feedMail);

        // assert feed is ravaged
        assertTrue(feedMail.getPollSubscribersLastMsg().isEmpty());
        assertTrue(feedMail.getCallbackSubscribers().isEmpty());
        assertTrue(feedMail.getMessages().isEmpty());
        assertNull(feedMail.getSlowestPollSubscriber());
    }

    @Test
    public void storeState() throws Exception {
        // set up feed in memory
        Subscriber sbscWC = new Subscriber("second");
        Subscription subWC = new Subscription(feedMail.getUri(), SubscriptionModeEnum.webCallback, URImail);
        sbscWC.addSubscription(subWC);
        Subscriber sbscPoll = new Subscriber("third");
        Subscription subPoll = new Subscription(feedMail.getUri(), SubscriptionModeEnum.poll, URImail);
        sbscPoll.addSubscription(subPoll);
        subscriber.addSubscription(subscription);
        Map<Subscriber, ScimEventNotification> pollSubscribers = new HashMap<>();
        pollSubscribers.put(subscriber, null);
        pollSubscribers.put(sbscPoll, sens.get(3));
        feedMail.setPollSubscribersLastMsg(pollSubscribers);
        feedMail.setSlowestPollSubscriber(subscriber);
        feedMail.addSubscriber(sbscWC);
        feedMail.newMsg(sens.get(0));
        feedMail.newMsg(sens.get(1));
        feedMail.newMsg(sens.get(3));

        // create feed
        testUtils.createFeedInDb(feedMail);

        // store feed
        Set<Long> ids = new HashSet<>(Arrays.asList(sens.get(0).getId(), 6874l));
        when(senDao.getIdsForFeed(feedMail)).thenReturn(ids);
        feedDao.storeState(feedMail);

        // verify calls
        verify(subscriberDao).create(subscriber);
        verify(subscriberDao).create(sbscPoll);
        verify(subscriberDao).create(sbscWC);
        verify(subscriptionDao).create(subscription, subscriber, feedMail);
        verify(subscriptionDao).create(subPoll, sbscPoll, feedMail);
        verify(subscriptionDao).storeLastSeenMsg(subPoll, sens.get(3).getId());
        verify(subscriptionDao).create(subWC, sbscWC, feedMail);
        verify(senDao).storeSen(sens.get(0), feedMail.getId(), null);
        verify(senDao).storeSen(sens.get(1), feedMail.getId(), sens.get(0).getId());
        verify(senDao).storeSen(sens.get(3), feedMail.getId(), sens.get(1).getId());
        verify(senDao).removeSenFromFeed(6874l, feedMail.getId());
    }

    @Test
    public void storeStateWithRavagedFeed() throws Exception {
        // set up feed in db
        testUtils.createFeedInDb(feedMail);
        testUtils.createSubscriberInDb(subscriber);
        testUtils.createSenInDb(sens.get(0), feedMail.getId(), null);
        testUtils.createSenInDb(sens.get(1), feedMail.getId(), sens.get(0).getId());
        testUtils.createSubscriptionInDb(subscription, feedMail, subscriber, sens.get(0).getId());
        Subscriber sbsc = new Subscriber("second");
        testUtils.createSubscriberInDb(sbsc);
        Subscription sub = new Subscription(feedMail.getUri(), SubscriptionModeEnum.webCallback, URImail);
        testUtils.createSubscriptionInDb(sub, feedMail, sbsc, null);
        storeSlowestSubscriber(feedMail.getId(), subscriber.getId());

        // mock methods
        Set<Long> ids = new HashSet<>(Arrays.asList(subscription.getId(), sub.getId()));
        when(subscriptionDao.getAllIdsForFeed(feedMail)).thenReturn(ids);
        ids = new HashSet<>(Arrays.asList(sens.get(0).getId(), sens.get(1).getId()));
        when(senDao.getIdsForFeed(feedMail)).thenReturn(ids);

        // store ravaged feed
        feedDao.storeState(feedMail);

        // verify calls
        verify(subscriptionDao).remove(subscription.getId());
        verify(subscriptionDao).remove(sub.getId());
        verify(senDao).removeSenFromFeed(sens.get(0).getId(), feedMail.getId());
        verify(senDao).removeSenFromFeed(sens.get(1).getId(), feedMail.getId());
    }

    @Test
    public void create() throws Exception {
        // create
        feedDao.create(feedMail);

        // assert
        assertEquals(feedMail.getUri(), getFeedById(feedMail.getId()).getUri());
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void remove() throws Exception {
        // set up feed in memory
        Subscriber sbscWC = new Subscriber("second");
        Subscription subWC = new Subscription(feedMail.getUri(), SubscriptionModeEnum.webCallback, URImail);
        sbscWC.addSubscription(subWC);
        Subscriber sbscPoll = new Subscriber("third");
        Subscription subPoll = new Subscription(feedMail.getUri(), SubscriptionModeEnum.poll, URImail);
        sbscPoll.addSubscription(subPoll);
        subscriber.addSubscription(subscription);
        Map<Subscriber, ScimEventNotification> pollSubscribers = new HashMap<>();
        pollSubscribers.put(subscriber, null);
        pollSubscribers.put(sbscPoll, sens.get(3));
        feedMail.setPollSubscribersLastMsg(pollSubscribers);
        feedMail.setSlowestPollSubscriber(subscriber);
        feedMail.addSubscriber(sbscWC);
        feedMail.newMsg(sens.get(0));
        feedMail.newMsg(sens.get(1));

        // create feeds
        testUtils.createFeedInDb(feedMail);
        testUtils.createFeedInDb(feedEdu);
        testUtils.createSubscriberInDb(subscriber);
        testUtils.createSubscriberInDb(sbscPoll);
        testUtils.createSubscriberInDb(sbscWC);
        testUtils.createSubscriptionInDb(subscription, feedMail, subscriber, null);
        testUtils.createSubscriptionInDb(subWC, feedMail, sbscWC, null);
        testUtils.createSubscriptionInDb(subPoll, feedMail, sbscPoll, null);
        testUtils.createSenInDb(sens.get(0), feedMail.getId(), null);
        testUtils.createSenInDb(sens.get(0), feedEdu.getId(), null);
        testUtils.createSenInDb(sens.get(1), feedMail.getId(), sens.get(0).getId());

        // mock
        when(senDao.getIdsForFeed(feedMail))
                .thenReturn(new HashSet<>(Arrays.asList(sens.get(0).getId(), sens.get(1).getId())));

        // remove
        feedDao.remove(feedMail);

        // assert subscriptions are not in db
        assertSubscriptionNotInDb(subscription);
        assertSubscriptionNotInDb(subWC);
        assertSubscriptionNotInDb(subPoll);

        // assert sen 1 is not in db
        assertSenNotInDb(sens.get(1));

        // assert feed is not is db
        getFeedById(feedMail.getId());
    }

    /* ============= Illegal values =============== */

    @Test(expected = NullPointerException.class)
    public void updateIdentifierWithNullMap() throws Exception {
        feedDao.updateIdentifiers(null);
    }

    @Test(expected = NullPointerException.class)
    public void updateWithNullFeed() throws Exception {
        feedDao.update(null);
    }

    @Test(expected = IllegalStateException.class)
    public void updateWithNotStoredFeed() throws Exception {
        feedDao.update(feedMail);
    }

    @Test(expected = NullPointerException.class)
    public void storeStateWithNullFeed() throws Exception {
        feedDao.storeState(null);
    }

    @Test(expected = IllegalStateException.class)
    public void storeStateWithNotStoredFeed() throws Exception {
        feedDao.storeState(feedMail);
    }

    @Test(expected = NullPointerException.class)
    public void createWithNullFeed() throws Exception {
        feedDao.create(null);
    }

    @Test(expected = NullPointerException.class)
    public void removeWithNullFeed() throws Exception {
        feedDao.remove(null);
    }

    @Test(expected = IllegalStateException.class)
    public void removeWithNotStoredFeed() throws Exception {
        feedDao.remove(feedMail);
    }


    /* ========= PRIVATE METHODS ================== */

    private void storeSlowestSubscriber(Long feedId, Long subscriberId) {
        String SQL = "UPDATE scim_feed SET slowest_subscriber_id=? WHERE id=?";
        jdbcTemplate.update(SQL, feedId, subscriberId);
    }

    private Feed getFeedById(Long id) {
        final class FeedMapper implements RowMapper<Feed> {
            public Feed mapRow(ResultSet rs, int rowNum) throws SQLException {
                Feed feed = new Feed(rs.getString("uri"));
                feed.setId(rs.getLong("id"));
                return feed;
            }
        }
        String SQL = "SELECT * FROM scim_feed WHERE id=?";
        return jdbcTemplate.queryForObject(SQL, new FeedMapper(), id);
    }

    private void assertSubscriptionNotInDb(Subscription subscription) {
        String SQL = "SELECT COUNT(*) FROM scim_subscription WHERE id=?";
        assertEquals(0, jdbcTemplate.queryForObject(SQL, Integer.class, subscription.getId()).intValue());
    }

    private void assertSenNotInDb(ScimEventNotification sen) {
        String SQL = "SELECT COUNT(*) FROM scim_event_notification WHERE id=?";
        assertEquals(0, jdbcTemplate.queryForObject(SQL, Integer.class, sen.getId()).intValue());
    }
}