package daoImpl;

import core.Subscriber;
import dao.SubscriberDao;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
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
public class SubscriberDaoImplTest {

    @Inject
    private DataSource dataSource;

    @Inject
    private JdbcTemplate jdbcTemplate;

    @Inject
    private SubscriberDao subscriberDao;

    public static final String SBSC_ID = "subid";

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
    public void updateTest() throws Exception {
        Subscriber subscriber1 = new Subscriber(SBSC_ID);
        Subscriber subscriber2 = new Subscriber(SBSC_ID + "1");
        Subscriber subscriber3 = new Subscriber(SBSC_ID + "2");
        Map<String, Subscriber> subscribers = new HashMap<String, Subscriber>();
        subscribers.put(SBSC_ID, subscriber1);
        createSubscriberInDb(subscriber2);
        createSubscriberInDb(subscriber3);

        // update
        subscriberDao.update(subscribers);
        assertFalse(subscribers.containsKey(SBSC_ID));
        assertTrue(subscribers.containsKey(SBSC_ID + "1"));
        assertTrue(subscribers.containsKey(SBSC_ID + "2"));
        assertEquals(subscriber2, subscribers.get(SBSC_ID + "1"));
        assertEquals(subscriber3, subscribers.get(SBSC_ID + "2"));
    }

    @Test
    public void createSubscriberTest() throws Exception {
        Subscriber subscriber = new Subscriber(SBSC_ID);
        subscriberDao.create(subscriber);
        assertNotNull(subscriber.getId());
        Subscriber returned = getByIdFromDb(subscriber.getId());
        assertEquals(subscriber.getId(), returned.getId());
        assertEquals(subscriber, returned);
    }


    /* ========= PRIVATE METHODS ================== */

    private void createSubscriberInDb(Subscriber subscriber) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("identifier", subscriber.getIdentifier());
        SimpleJdbcInsert jdbcInsert = new SimpleJdbcInsert(jdbcTemplate).withTableName("subscriber").usingGeneratedKeyColumns("id");
        Number id = jdbcInsert.executeAndReturnKey(params);
        subscriber.setId(id.longValue());
    }

    public Subscriber getByIdFromDb(Long id) {
        class SubsciberMapper implements RowMapper<Subscriber> {
            public Subscriber mapRow(ResultSet rs, int rowNum) throws SQLException {
                Subscriber subscriber = new Subscriber(rs.getString("identifier"));
                subscriber.setId(rs.getLong("id"));
                return subscriber;
            }
        }
        if (id == null) throw new IllegalStateException("Subscriber is not stored.");
        String SQL = "SELECT * FROM subscriber WHERE id=" + id;
        return jdbcTemplate.queryForObject(SQL, new SubsciberMapper());
    }
}