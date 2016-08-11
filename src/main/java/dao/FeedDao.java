package dao;

import core.*;
import core.Feed;

import java.util.Map;
import java.util.Set;

/**
 * Created by xmauritz on 8/8/16.
 */
public interface FeedDao {

    /**
     * Check the feed's identifiers according to the data storage.
     * If there is match, no feed is updated.
     * If there is more feeds in db, update them.
     *
     * @param feeds to be updated
     */
    public void updateIdentifiers(Map<String, Feed> feeds);

    /**
     * Deep update of the feed.
     *
     * @param feed to be updated
     */
    public void update(Feed feed);


    /**
     * Add new message to the feed.
     *
     * @param feed owner of the msg
     * @param sen  to be created
     */
    public void newMsg(Feed feed, ScimEventNotification sen);

    /**
     * Store the inner state of the feed into data storage.
     *
     * @param feed
     */
    public void storeState(Feed feed);

    /**
     * Create a new feed.
     *
     * @param feed to be created
     */
    public void create(Feed feed);

    /**
     * Remove a new feed.
     *
     * @param feed to be removed
     */
    public void remove(Feed feed);
}
