package daoImpl;

import core.*;
import dao.FeedDao;
import dao.SubscriberDao;
import dao.SubscriptionDao;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by xmauritz on 8/11/16.
 */
@Named
@Singleton
public class FeedDaoImpl implements FeedDao {

    private static final String TABLE_NAME = "feed";

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private SubscriberDao subscriberDao;

    @Inject
    private SubscriptionDao subscriptionDao;

    @Inject
    private ScimEventNotificationDaoImpl senDao;

    // Row Mapper for the feed object
    private static final class FeedMapper implements RowMapper<Feed> {
        public Feed mapRow(ResultSet rs, int rowNum) throws SQLException {
            Feed feed = new Feed(rs.getString("uri"));
            feed.setId(rs.getLong("id"));
            return feed;
        }
    }

    public void updateIdentifiers(Map<String, Feed> feeds) {
        if (feeds == null) throw new NullPointerException("Feeds cannot be null.");
        Set<String> urisMemory = feeds.keySet();
        Set<String> urisDatabase = getFeedUris();
        if (urisDatabase.equals(urisMemory)) return;
        // remove
        Set<String> toRemove = new HashSet<String>(urisMemory);
        toRemove.removeAll(urisDatabase);
        for (String uri : toRemove) {
            feeds.remove(uri);
        }
        // add
        Set<String> toAdd = new HashSet<String>(urisDatabase);
        toAdd.removeAll(urisMemory);
        for (String uri : toAdd) {
            Feed feed = getByUri(uri);
            feeds.put(uri, feed);
        }
    }

    public void update(Feed feed) {
        Feed returned = getByUri(feed.getUri());
        feed.setId(returned.getId());
        feed.setSlowestPollSubscriber(getSlowestSubscriber(feed));
        feed.setPollSubscribersLastMsg(subscriberDao.getPollSubscribers(feed));
        feed.setCallbackSubscribers(subscriberDao.getWebCallbackSubscribers(feed));

        // update messages
        LinkedList<ScimEventNotification> messages = new LinkedList<ScimEventNotification>();
        Map<Long, ScimEventNotification> queuePredecessors = new HashMap<Long, ScimEventNotification>();
        ScimEventNotification firstMsg = null;
        // get all messages with link to the previous message
        for (Long senId : senDao.getIdsForFeed(feed)) {
            ScimEventNotification sen = senDao.getById(senId);
            Long prevMsgId = senDao.getMessagePredecessor(sen, feed);
            if (prevMsgId == null) {
                // save the first message in the queue
                firstMsg = sen;
            }
            queuePredecessors.put(prevMsgId, sen);
        }
        // first message has to have prevMsgId set to null -> if there is none, it is illegal state
        if (firstMsg == null) throw new IllegalStateException("There is no first message for queue of the feed uri: '" +
                feed.getUri() + "'.");
        // add msgs to the queue in the right order
        messages.add(firstMsg);
        ScimEventNotification msgToAdd = firstMsg;
        while (queuePredecessors.containsKey(msgToAdd.getId())) {
            msgToAdd = queuePredecessors.get(msgToAdd.getId());
            messages.add(msgToAdd);
        }
        feed.setMessages(messages);
    }

    public void newMsg(Feed feed, ScimEventNotification sen) {
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (feed.getId() == null) throw new NullPointerException("Feed must be stored.");
        if (sen == null) throw new NullPointerException("ScimEventNotification cannot be null");
        senDao.storeSen(sen, feed.getId(), null);
    }

    public void storeState(Feed feed) {
        // validate existence
        try {
            getByUri(feed.getUri());
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("Feed must be created before storing.", e);
        }
        // store subscribers
        for (Subscriber subscriber : feed.getSubscribers()) {
            if (subscriber.getId() == null) {
                subscriberDao.create(subscriber);
            }
            // store subscriptions
            Set<Long> subscriptionsIdsToRemove = subscriptionDao.getAllIdsForSubscriber(subscriber);
            for (Subscription subscription : subscriber.getSubscriptions()) {
                if (subscription.getId() == null) {
                    Long lastSeenMsg = subscription.getMode() == SubscriptionModeEnum.poll ?
                            feed.getPollSubscribersLastMsg().get(subscriber).getId() : null;
                    subscriptionDao.create(subscription, subscriber, feed, lastSeenMsg);
                }
                subscriptionsIdsToRemove.remove(subscription.getId());
            }
            // remove extra subscriptions
            for (Long subscId : subscriptionsIdsToRemove) {
                subscriptionDao.remove(subscId);
            }
        }
        // store slowest subscriber
        storeSlowestSubscriber(feed);
        // store new messages
        Set<Long> senIdsToRemove = senDao.getIdsForFeed(feed);
        Long previousId = null;
        for (ScimEventNotification sen : feed.getMessages()) {
            // store reference to previous sen, first one references null
            senDao.storeSen(sen, feed.getId(), previousId);
            previousId = sen.getId();
            senIdsToRemove.remove(sen.getId());
        }
        // remove extra messages
        for (Long senId : senIdsToRemove) {
            senDao.removeSen(senId);
        }
    }

    public void create(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("uri", feed.getUri());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        feed.setId(id.longValue());
    }

    public void remove(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored yet.");
        String SQL = "DELETE FROM " + TABLE_NAME + " WHERE id=?";
        int rows = jdbcTemplate.update(SQL, feed.getId());
        if (rows > 1) throw new IllegalStateException("More than one feed removed.");
        // TODO: check whether DELETE ON CASCADE is needed to add and if it works (subscriptions and subscribers has to be removed)
    }


    /* ============ PRIVATE METHODS ============= */

    private Set<String> getFeedUris() {
        String SQL = "SELECT uri FROM " + TABLE_NAME;
        return new HashSet<String>(jdbcTemplate.queryForList(SQL, String.class));
    }

    private Feed getByUri(String uri) {
        String SQL = "SELECT * FROM " + TABLE_NAME + " WHERE uri=?";
        return jdbcTemplate.queryForObject(SQL, new FeedMapper(), uri);
    }

    private Subscriber getSlowestSubscriber(Feed feed) {
        String SQL = "SELECT * FROM " + TABLE_NAME + " JOIN subscriber ON feed.slowestSubscriberId=subscriber.id" +
                " WHERE feed.id=?";
        return jdbcTemplate.queryForObject(SQL, new SubscriberDaoImpl.SubsciberMapper(), feed.getId());
    }

    private void storeSlowestSubscriber(Feed feed) {
        String SQL = "UPDATE " + TABLE_NAME + " SET slowestSubscriberId=? WHERE id=?";
        jdbcTemplate.update(SQL, feed.getSlowestPollSubscriber().getId(), feed.getId());
    }
}
