package dao;

import config.TestConfig;
import core.Subscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import javax.inject.Inject;
import javax.sql.DataSource;

import static org.junit.Assert.*;

/**
 * Created by xmauritz on 8/8/16.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class SubscriberDaoTest {

    @Inject
    private DataSource dataSource;

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
    public void createSubscriber() throws Exception {
        Subscriber subscriber = new Subscriber(SBSC_ID);
        subscriberDao.create(subscriber);
        Subscriber returned = subscriberDao.get(SBSC_ID);
        assertEquals(subscriber, returned);
    }

}