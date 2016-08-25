package daoImpl;

import core.*;
import dao.FeedDao;
import dao.SubscriberDao;
import dao.SubscriptionDao;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * DAO for the feed object.
 *
 * @author Jiri Mauritz
 */
@Named
@Singleton
@Transactional
public class FeedDaoImpl implements FeedDao {

    private static final String TABLE_NAME = "scim_feed";

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

    @Override
    public void updateIdentifiers(Map<String, Feed> feeds) {
        if (feeds == null) throw new NullPointerException("Feeds cannot be null.");
        Set<String> urisMemory = feeds.keySet();
        Set<String> urisDatabase = getFeedUris();
        if (urisDatabase.equals(urisMemory)) return;
        // remove
        Set<String> toRemove = new HashSet<>(urisMemory);
        toRemove.removeAll(urisDatabase);
        for (String uri : toRemove) {
            feeds.remove(uri);
        }
        // add
        Set<String> toAdd = new HashSet<>(urisDatabase);
        toAdd.removeAll(urisMemory);
        for (String uri : toAdd) {
            Feed feed = getByUri(uri);
            feeds.put(uri, feed);
        }
    }

    @Override
    public void update(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored.");
        Feed returned = getByUri(feed.getUri());
        feed.setId(returned.getId());
        feed.setSlowestPollSubscriber(getSlowestSubscriber(feed));
        feed.setPollSubscribersLastMsg(subscriberDao.getPollSubscribers(feed));
        feed.setCallbackSubscribers(subscriberDao.getWebCallbackSubscribers(feed));

        // update messages
        // get all messages with link to the previous message
        Set<Long> idsForFeed = senDao.getIdsForFeed(feed);
        if (idsForFeed.isEmpty()) {
            // no message
            feed.setMessages(new LinkedList<ScimEventNotification>());
            return;
        }
        LinkedList<ScimEventNotification> messages = new LinkedList<>();
        Map<Long, ScimEventNotification> queuePredecessors = new HashMap<>();
        ScimEventNotification firstMsg = null;
        for (Long senId : idsForFeed) {
            ScimEventNotification sen = senDao.getById(senId);
            Long prevMsgId = senDao.getMessagePredecessor(sen, feed);
            if (prevMsgId == null) {
                // save the first message in the queue
                firstMsg = sen;
            } else {
                queuePredecessors.put(prevMsgId, sen);
            }
        }
        // first message has to have prevMsgId set to null -> if there is none, it is illegal state
        if (firstMsg == null) throw new IllegalStateException("There is no first message for queue of the feed uri: '" +
                feed.getUri() + "'.");
        // add msgs to the queue in the right order
        messages.addLast(firstMsg);
        ScimEventNotification msgToAdd = firstMsg;
        while (queuePredecessors.containsKey(msgToAdd.getId())) {
            msgToAdd = queuePredecessors.get(msgToAdd.getId());
            messages.addLast(msgToAdd);
        }
        feed.setMessages(messages);
    }

    @Override
    public void storeState(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (feed.getId() == null) throw new IllegalStateException("Feed must be created before storing.");
        // store subscribers
        Set<Long> subscriptionsToRemove = subscriptionDao.getAllIdsForFeed(feed);
        for (Subscriber subscriber : feed.getSubscribers()) {
            if (subscriber.getId() == null) {
                subscriberDao.create(subscriber);
            }
            // store subscription
            for (Subscription subscription : subscriber.getSubscriptions()) {
                // find subscription for this feed
                if (subscription.getFeedUri().equals(feed.getUri())) {
                    // feed that belongs to our feed
                    if (subscription.getId() == null) {
                        // create subscription if not created
                        subscriptionDao.create(subscription, subscriber, feed);
                    }
                    if (subscription.getMode().equals(SubscriptionModeEnum.poll)) {
                        // store last seen msg if the subscription is in poll mode
                        ScimEventNotification lastSeenMsg = feed.getPollSubscribersLastMsg().get(subscriber);
                        if (lastSeenMsg != null) {
                            subscriptionDao.storeLastSeenMsg(subscription, lastSeenMsg.getId());
                        }
                    }
                    subscriptionsToRemove.remove(subscription.getId());
                    break;
                }
            }
        }
        // remove extra subscriptions
        for (Long subId : subscriptionsToRemove) {
            subscriptionDao.remove(subId);
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
        safelyRemoveMsgs(senIdsToRemove, feed.getId());
    }

    @Override
    public void create(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null");
        if (feed.getId() != null) throw new IllegalStateException("Feed is already stored.");
        Map<String, Object> params = new HashMap<>();
        params.put("uri", feed.getUri());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        feed.setId(id.longValue());
    }

    @Override
    public void remove(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored yet.");
        Set<Long> senIds = senDao.getIdsForFeed(feed);
        // remove sen-feed relationships
        String SQL = "DELETE FROM scim_feed_sen WHERE feed_id=?";
        jdbcTemplate.update(SQL, feed.getId());
        // remove sens that has no foreign key in feed_sen
        for (Long senId : senIds) {
            SQL = "DELETE FROM scim_event_notification WHERE id=? AND NOT EXISTS (SELECT * FROM scim_feed_sen WHERE sen_id=?)";
            jdbcTemplate.update(SQL, senId, senId);
        }
        // remove the feed itself
        SQL = "DELETE FROM " + TABLE_NAME + " WHERE id=?";
        int rows = jdbcTemplate.update(SQL, feed.getId());
        if (rows > 1) throw new IllegalStateException("More than one feed removed.");
    }


    /* ============ PRIVATE METHODS ============= */

    private Set<String> getFeedUris() {
        String SQL = "SELECT uri FROM " + TABLE_NAME;
        return new HashSet<>(jdbcTemplate.queryForList(SQL, String.class));
    }

    private Feed getByUri(String uri) {
        String SQL = "SELECT * FROM " + TABLE_NAME + " WHERE uri=?";
        return jdbcTemplate.queryForObject(SQL, new FeedMapper(), uri);
    }

    private Subscriber getSlowestSubscriber(Feed feed) {
        String SQL = "SELECT scim_subscriber.id, scim_subscriber.identifier FROM " + TABLE_NAME + " JOIN scim_subscriber ON " +
                "scim_feed.slowest_subscriber_id=scim_subscriber.id WHERE scim_feed.id=?";
        Subscriber subscriber;
        try {
            subscriber = jdbcTemplate.queryForObject(SQL, new SubscriberDaoImpl.SubsciberMapper(), feed.getId());
        } catch (EmptyResultDataAccessException e) {
            // no slowest subscriber
            return null;
        }
        return subscriber;
    }

    private void storeSlowestSubscriber(Feed feed) {
        String SQL = "UPDATE " + TABLE_NAME + " SET slowest_subscriber_id=? WHERE id=?";
        jdbcTemplate.update(SQL, feed.getSlowestPollSubscriber() == null ? null : feed.getSlowestPollSubscriber().getId(), feed.getId());
    }

    private void safelyRemoveMsgs(Set<Long> senIds, Long feedId) {
        // messages have to be removed in the right order, not to cause DataViolationException
        if (!senIds.isEmpty()) {
            String SQL = "SELECT sen_id FROM scim_feed_sen WHERE prev_msg_id=? AND feed_id=?";

            Long id = null;
            Long nextMsgId;
            while (!senIds.isEmpty()) {
                if (id == null) {
                    id = senIds.iterator().next();
                }
                try {
                    nextMsgId = jdbcTemplate.queryForObject(SQL, Long.class, id, feedId);
                    if (!senIds.contains(nextMsgId)) {
                        senDao.removeSenFromFeed(id, feedId);
                        senIds.remove(id);
                        id = null;
                    } else {
                        id = nextMsgId;
                    }
                } catch (EmptyResultDataAccessException e) {
                    senDao.removeSenFromFeed(id, feedId);
                    senIds.remove(id);
                    id = null;
                }
            }
        }
    }
}
