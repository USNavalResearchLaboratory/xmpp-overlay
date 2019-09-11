package edu.drexel.xop.net.api;


import edu.drexel.xop.net.discovery.UserDiscoverableObject;

/**
 * The Discoverable User listener is implemented by objects wishing to retrieve notifications
 * of new discoverable user  events.
 *
 * @author Ian Taylor
 */
public interface DiscoverableUserListener {

    /**
     * Notifies when a new discoverable user has been discovered
     *
     * @param userDiscoverableObject the user discoverable object that has been discovered.
     */
    public void userAdded(UserDiscoverableObject userDiscoverableObject);

    /**
     * Notifies when a user has been removed
     *
     * @param userDiscoverableObject the user discoverable object, which has been removed
     */
    public void userRemoved(UserDiscoverableObject userDiscoverableObject);

}
