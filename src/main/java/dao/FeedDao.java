package dao;

import core.Feed;

import java.util.Map;

/**
 * Interface for the feed DAO.
 * Manages CRUD operations for the feed object.
 *
 * @author Jiri Mauritz
 */
public interface FeedDao {

    /**
     * Update the feed's identifiers according to the data storage.
     * No feed object is updated.
     *
     * @param feeds to be updated
     */
    public void updateIdentifiers(Map<String, Feed> feeds);

    /**
     * Update the feed according to the storage.
     * It is completely replaced by the storage feed, i.e. all previous data are discarded.
     *
     * @param feed to be updated
     */
    public void update(Feed feed);

    /**
     * Store the inner state of the feed into the storage.
     * WARNING: all extra records in the storage are removed, i.e. scim event notifications and subscriptions.
     * In case the subscribers have more subscriptions, only subscriptions that belongs to this feed are stored.
     *
     * @param feed to be stored
     */
    public void storeState(Feed feed);

    /**
     * Create a new feed in the storage.
     * No subscribers or messages are stored, use storeState() for storing them.
     *
     * @param feed to be created
     */
    public void create(Feed feed);

    /**
     * Remove a feed from the storage.
     * All scim event notifications and subscriptions are removed as well.
     *
     * @param feed to be removed
     */
    public void remove(Feed feed);
}
