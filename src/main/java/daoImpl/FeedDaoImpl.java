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

    // Row Mapper for the subscriber object
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
            // TODO: removal of subscribers
            for (Subscription subscription : subscriber.getSubscriptions()) {
                if (subscription.getId() == null) {
                    Long lastSeenMsg = subscription.getMode() == SubscriptionModeEnum.poll ?
                            feed.getPollSubscribersLastMsg().get(subscriber).getId() : null;
                    subscriptionDao.create(subscription, subscriber, feed, lastSeenMsg);
                }
            }
        }
        // store new messages
        Set<Long> senIdsToRemove = senDao.getIdsForFeed(feed);
        Iterator iter = feed.getMessages().iterator();
        if (!iter.hasNext()) return;
        ScimEventNotification current = (ScimEventNotification) iter.next();
        senIdsToRemove.remove(current.getId());
        ScimEventNotification next;
        while (iter.hasNext()) {
            next = (ScimEventNotification) iter.next();
            senDao.storeSen(current, feed.getId(), next.getId());
            current = next;
            senIdsToRemove.remove(current.getId());
        }
        // TODO: finish adding sens with last one, who will have null reference to next one
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
}
