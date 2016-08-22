package daoImpl;

import core.*;
import dao.SubscriptionDao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * DAO for the subscription object.
 *
 * @author Jiri Mauritz
 */
@Named
@Singleton
public class SubscriptionDaoImpl implements SubscriptionDao {

    static final String TABLE_NAME = "scim_subscription";
    static final String FIELDS = "scim_subscription.mode, scim_subscription.event_uri, scim_feed.uri";

    @Inject
    private JdbcTemplate jdbcTemplate;

    // Row Mapper for the subscription object
    static final class SubscriptionMapper implements RowMapper<Subscription> {
        public Subscription mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Subscription(
                    rs.getString("uri"),
                    SubscriptionModeEnum.valueOf(rs.getString("mode")),
                    rs.getString("event_uri"));
        }
    }

    @Override
    public void create(Subscription subscription, Subscriber subscriber, Feed feed) {
        if (subscription == null) throw new NullPointerException("Subscription cannot be null.");
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (subscriber.getId() == null) throw new NullPointerException("Subscriber is not stored yet.");
        if (feed.getId() == null) throw new NullPointerException("Feed is not stored yet.");
        Map<String, Object> params = new HashMap<>();
        params.put("mode", subscription.getMode().name());
        params.put("event_uri", subscription.getEventUri());
        params.put("subscriber_id", subscriber.getId());
        params.put("feed_id", feed.getId());
        ScimEventNotification lastSeenSen = feed.getPollSubscribersLastMsg().get(subscriber);
        params.put("last_seen_msg", lastSeenSen == null ? null : lastSeenSen.getId());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscription.setId(id.longValue());
    }

    @Override
    public void storeLastSeenMsg(Subscription subscription, Long lastSeenMsg) {
        if (subscription == null) throw new NullPointerException("Subscription cannot be null.");
        if (lastSeenMsg == null) throw new NullPointerException("Subscription cannot be null.");
        if (subscription.getId() == null) throw new IllegalStateException("Subscription is not stored yet.");
        if (subscription.getMode().equals(SubscriptionModeEnum.webCallback)) {
            throw new IllegalStateException("Subscription must be of poll mode to save last seen msg.");
        }
        String SQL = "UPDATE " + TABLE_NAME + " SET last_seen_msg=? WHERE id=?";
        jdbcTemplate.update(SQL, lastSeenMsg, subscription.getId());

    }

    @Override
    public void remove(String subscriberIdentifier, String feedUri) {
        if (subscriberIdentifier == null) throw new NullPointerException("SubscriberIdentifier cannot be null.");
        if (feedUri == null) throw new NullPointerException("FeedUri cannot be null.");
        String SQL = "DELETE FROM " + TABLE_NAME + " WHERE subscriber_id=(SELECT id FROM scim_subscriber WHERE identifier=" +
                "?) AND feed_id=(SELECT id FROM scim_feed WHERE uri=?)";
        int rows = jdbcTemplate.update(SQL, subscriberIdentifier, feedUri);
        if (rows > 1) throw new IllegalStateException("More than one subscription removed.");
    }

    @Override
    public void remove(Long id) {
        if (id == null) throw new NullPointerException("Id if the subscriber cannot be null.");
        String SQL = "DELETE FROM " + TABLE_NAME + " WHERE id=?";
        jdbcTemplate.update(SQL, id);
    }

    @Override
    public Set<Long> getAllIdsForSubscriber(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        if (subscriber.getId() == null) throw new IllegalStateException("Subscriber is not stored yet.");
        String SQL = "SELECT id FROM " + TABLE_NAME + " WHERE subscriber_id=?";
        return new HashSet<>(jdbcTemplate.queryForList(SQL, Long.class, subscriber.getId()));
    }

    @Override
    public Set<Long> getAllIdsForFeed(Feed feed) {
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored.");
        String SQL = "SELECT id FROM " + TABLE_NAME + " WHERE feed_id=?";
        return new HashSet<>(jdbcTemplate.queryForList(SQL, Long.class, feed.getId()));
    }
}
