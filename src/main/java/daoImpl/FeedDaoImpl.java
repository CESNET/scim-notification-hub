package daoImpl;

import core.Feed;
import core.ScimEventNotification;
import dao.FeedDao;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;

/**
 * Created by xmauritz on 8/11/16.
 */
@Named
@Singleton
public class FeedDaoImpl implements FeedDao {

    public void updateIdentifiers(Map<String, Feed> feeds) {

    }

    public void update(Feed feed) {

    }

    public void newMsg(Feed feed, ScimEventNotification sen) {

    }

    public void storeState(Feed feed) {

    }

    public void create(Feed feed) {

    }

    public void remove(Feed feed) {

    }
}
