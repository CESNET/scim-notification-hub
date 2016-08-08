package dao;

import core.Subscriber;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.inject.Inject;
import javax.inject.Named;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by xmauritz on 8/8/16.
 */
@Named
public class SubscriberDao {

    @Inject
    private JdbcTemplate jdbcTemplate;

    private static final class SubsciberMapper implements RowMapper<Subscriber> {
        public Subscriber mapRow(ResultSet rs, int rowNum) throws SQLException {
            Subscriber subscriber = new Subscriber(rs.getString("identifier"));
            return subscriber;
        }
    }

    public static final String TABLE_NAME = "subscriber";

    public void create(Subscriber subscriber) {
        if (subscriber == null) throw new NullPointerException("Subscriber cannot be null.");
        String SQL = "INSERT INTO " + TABLE_NAME + "(identifier) values (?)";
        jdbcTemplate.update(SQL, subscriber.getIdentifier());
    }

    public Subscriber get(String identifier) {
        String SQL = "SELECT * FROM " + TABLE_NAME + " WHERE identifier = ?";
        return jdbcTemplate.queryForObject(SQL, new Object[] {identifier}, new SubsciberMapper());
    }
}
