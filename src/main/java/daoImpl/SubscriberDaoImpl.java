package daoImpl;

import core.*;
import dao.SubscriberDao;
import org.springframework.dao.DataAccessException;
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
 * DAO for the subscriber object.
 *
 * @author Jiri Mauritz
 */
@Named
@Singleton
public class SubscriberDaoImpl implements SubscriberDao {

    private static final String TABLE_NAME = "scim_subscriber";

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private ScimEventNotificationDaoImpl senDao;

    // Row Mapper for the subscriber object
    public static final class SubsciberMapper implements RowMapper<Subscriber> {
        public Subscriber mapRow(ResultSet rs, int rowNum) throws SQLException {
            Subscriber subscriber = new Subscriber(rs.getString("identifier"));
            subscriber.setId(rs.getLong("id"));
            return subscriber;
        }
    }

    @Override
    public void update(Map<String, Subscriber> subscribers) {
        try {
            if (subscribers == null) throw new NullPointerException("Subscribers cannot be null .");
            subscribers.clear();
            for (Subscriber subscriber : getAll()) {
                subscribers.put(subscriber.getIdentifier(), subscriber);
            }
        } catch (DataAccessException e) {
            // database problem, continue without support of the database
            e.printStackTrace();
        }
    }

    @Override
    public void create(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        Map<String, Object> params = new HashMap<>();
        params.put("identifier", subscriber.getIdentifier());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscriber.setId(id.longValue());
    }

    @Override
    public void remove(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        if (subscriber.getId() == null) throw new IllegalStateException("Subscriber is not stored.");
        String SQL = "DELETE FROM " + TABLE_NAME + " WHERE id=?";
        int rows = jdbcTemplate.update(SQL, subscriber.getId());
        if (rows > 1) throw new IllegalStateException("More than one subscriber removed.");
    }


    @Override
    public Map<Subscriber, ScimEventNotification> getPollSubscribers(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored yet.");
        Set<Subscriber> subscribers = getAllForFeedWithMode(feed, SubscriptionModeEnum.poll);
        Map<Subscriber, ScimEventNotification> pollSubscribers = new HashMap<>();
        // add last seen msgs
        ScimEventNotification lastSeenMsg;
        String SQL = "SELECT last_seen_msg FROM scim_subscription WHERE subscriber_id=? AND feed_id=?";
        for (Subscriber subscriber : subscribers) {
            if (typeOfSubscriber(subscriber, feed).equals(SubscriptionModeEnum.poll)) {
                // only if the subscriber has poll mode
                Long senId = jdbcTemplate.queryForObject(SQL, Long.class, subscriber.getId(), feed.getId());
                lastSeenMsg = senId == null ? null : senDao.getById(senId);
                pollSubscribers.put(subscriber, lastSeenMsg);
            }
        }
        return pollSubscribers;
    }

    @Override
    public Set<Subscriber> getWebCallbackSubscribers(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored yet.");
        Set<Subscriber> subscribers = getAllForFeedWithMode(feed, SubscriptionModeEnum.webCallback);

        // remove all poll subscribers
        Iterator iter = subscribers.iterator();
        while (iter.hasNext()) {
            if (typeOfSubscriber((Subscriber) iter.next(), feed).equals(SubscriptionModeEnum.poll)) {
                iter.remove();
            }
        }
        return subscribers;
    }

    /* ============ PRIVATE METHODS ============= */

    private Set<Subscriber> getAll() {
        String SQL = "SELECT * FROM " + TABLE_NAME;
        Set<Subscriber> subscribers = new HashSet<>(jdbcTemplate.query(SQL, new SubsciberMapper()));
        // add subscriptions
        for (Subscriber subscriber : subscribers) {
            SQL = "SELECT " + SubscriptionDaoImpl.FIELDS + " FROM " + SubscriptionDaoImpl.TABLE_NAME + " JOIN scim_feed ON "
                    + SubscriptionDaoImpl.TABLE_NAME + ".feed_id=scim_feed.id WHERE subscriber_id=?";
            subscriber.setSubscriptions(new HashSet<>(
                    jdbcTemplate.query(SQL, new SubscriptionDaoImpl.SubscriptionMapper(), subscriber.getId())));
        }
        return subscribers;
    }

    private Set<Subscriber> getAllForFeedWithMode(Feed feed, SubscriptionModeEnum mode) {
        String SQL = "SELECT * FROM " + TABLE_NAME + " JOIN scim_subscription ON scim_subscriber.id=scim_subscription.subscriber_id " +
                "WHERE scim_subscription.feed_id=? AND scim_subscription.mode=?";
        Set<Subscriber> subscribers = new HashSet<>(jdbcTemplate.query(SQL, new SubsciberMapper(), feed.getId(), mode.name()));
        // add subscriptions
        for (Subscriber subscriber : subscribers) {
            SQL = "SELECT " + SubscriptionDaoImpl.FIELDS + " FROM " + SubscriptionDaoImpl.TABLE_NAME + " JOIN scim_feed ON "
                    + SubscriptionDaoImpl.TABLE_NAME + ".feed_id=scim_feed.id WHERE subscriber_id=?";
            subscriber.setSubscriptions(new HashSet<>(
                    jdbcTemplate.query(SQL, new SubscriptionDaoImpl.SubscriptionMapper(), subscriber.getId())));
        }
        return subscribers;
    }

    private SubscriptionModeEnum typeOfSubscriber(Subscriber subscriber, Feed feed) {
        for (Subscription subscription : subscriber.getSubscriptions()) {
            if (subscription.getFeedUri().equals(feed.getUri())) {
                return subscription.getMode();
            }
        }
        return null;
    }
}
