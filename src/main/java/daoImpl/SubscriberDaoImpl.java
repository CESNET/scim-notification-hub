package daoImpl;

import core.Feed;
import core.Subscriber;
import core.Subscription;
import dao.SubscriberDao;
import dao.SubscriptionDao;
import org.springframework.dao.DataAccessException;
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
 * Created by xmauritz on 8/8/16.
 */
@Named
@Singleton
public class SubscriberDaoImpl implements SubscriberDao {

    private static final String TABLE_NAME = "subscriber";

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private SubscriptionDao subscriptionDao;

    // Row Mapper for the subscriber object
    private static final class SubsciberMapper implements RowMapper<Subscriber> {
        public Subscriber mapRow(ResultSet rs, int rowNum) throws SQLException {
            Subscriber subscriber = new Subscriber(rs.getString("identifier"));
            subscriber.setId(rs.getLong("id"));
            return subscriber;
        }
    }

    public void update(Map<String, Subscriber> subscribers) {
        try {
            if (subscribers == null) throw new IllegalStateException("Subscribers cannot be null .");
            subscribers.clear();
            for (Subscriber subscriber : getAll()) {
                subscribers.put(subscriber.getIdentifier(), subscriber);
            }
        } catch (DataAccessException e) {
            // database problem, continue without support of the database
            e.printStackTrace();
        }
    }

    public void create(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("identifier", subscriber.getIdentifier());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscriber.setId(id.longValue());
    }

    public void remove(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        if (subscriber.getId() == null) throw new IllegalStateException("Subscriber is not stored.");
        String SQL = "DELETE FROM " + TABLE_NAME + " WHERE id=" + subscriber.getId();
        int rows = jdbcTemplate.update(SQL);
        if (rows > 1) throw new IllegalStateException("More than one subscriber removed.");
    }


    private Set<Subscriber> getAll() {
        String SQL = "SELECT * FROM " + TABLE_NAME;
        Set<Subscriber> subscribers = new HashSet<Subscriber>(jdbcTemplate.query(SQL, new SubsciberMapper()));
        // add subscriptions
        for (Subscriber subscriber : subscribers) {
            SQL = "SELECT " + SubscriptionDaoImpl.FIELDS + " FROM " + SubscriptionDaoImpl.TABLE_NAME + " JOIN feed ON "
                    + SubscriptionDaoImpl.TABLE_NAME + ".feedId=feed.id WHERE subscriberId=" + subscriber.getId();
            subscriber.setSubscriptions(new HashSet<Subscription>(jdbcTemplate.query(SQL, new SubscriptionDaoImpl.SubscriptionMapper())));
        }
        return subscribers;
    }

//    public Subscriber getById(Long id) {
//        if (id == null) throw new IllegalStateException("Subscriber is not stored.");
//        String SQL = "SELECT * FROM " + TABLE_NAME + " WHERE id=" + id;
//        return jdbcTemplate.queryForObject(SQL, new SubsciberMapper());
//    }
//
//    public Set<Subscriber> getAllForFeed(Feed feed) {
//        if (feed == null) throw new NullPointerException("Feed cannot be null.");
//        if (feed.getId() == null) throw new IllegalStateException("Feed is not stored yet.");
//        String SQL = "SELECT " + FIELDS + " FROM subscription  WHERE feedId=" + feed.getId();
//        return new HashSet<Subscriber>(jdbcTemplate.query(SQL, new SubsciberMapper()));
//    }

}
