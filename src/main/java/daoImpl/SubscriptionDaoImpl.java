package daoImpl;

import core.Feed;
import core.Subscriber;
import core.Subscription;
import core.SubscriptionModeEnum;
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
 * Created by xmauritz on 8/9/16.
 */
@Named
@Singleton
public class SubscriptionDaoImpl implements SubscriptionDao {

    static final String TABLE_NAME = "subscription";
    static final String FIELDS = "subscription.mode, subscription.eventUri, feed.uri";

    @Inject
    private JdbcTemplate jdbcTemplate;

    // Row Mapper for the subscription object
    static final class SubscriptionMapper implements RowMapper<Subscription> {
        public Subscription mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Subscription(
                    rs.getString("uri"),
                    SubscriptionModeEnum.valueOf(rs.getString("mode")),
                    rs.getString("eventUri"));
        }
    }

    public void create(Subscription subscription, Subscriber subscriber, Feed feed, Long lastSeenMsg) {
        if (subscription == null) throw new NullPointerException("Subscription cannot be null.");
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        if (feed == null) throw new NullPointerException("Feed cannot be null.");
        if (subscriber.getId() == null) throw new NullPointerException("Subscriber is not stored yet.");
        if (feed.getId() == null) throw new NullPointerException("Feed is not stored yet.");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("mode", subscription.getMode().name());
        params.put("eventUri", subscription.getEventUri());
        params.put("subscriberId", subscriber.getId());
        params.put("feedId", feed.getId());
        params.put("lastSeenMsg", lastSeenMsg);
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscription.setId(id.longValue());
    }

    public void remove(String subscriberIdentifier, String feedUri) {
        if (subscriberIdentifier == null) throw new NullPointerException("SubscriberIdentifier cannot be null.");
        if (feedUri == null) throw new IllegalStateException("FeedUri cannot be null.");
        String SQL = "DELETE FROM " + TABLE_NAME + " WHERE subscriberId=(SELECT id FROM subscriber WHERE identifier=" +
                "?) AND feedId=(SELECT id FROM feed WHERE uri=?)";
        int rows = jdbcTemplate.update(SQL, subscriberIdentifier, feedUri);
        if (rows > 1) throw new IllegalStateException("More than one subscription removed.");
    }

//    public Set<Subscription> getAllForFeed(Feed feed) {
//        if (feed == null) throw new NullPointerException("Feed cannot be null.");
//        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored yet.");
//        String SQL = "SELECT " + FIELDS + " FROM " + TABLE_NAME + " WHERE feedId=" + feed.getId();
//        return new HashSet<Subscription>(jdbcTemplate.query(SQL, new SubscriptionMapper()));
//    }

}
