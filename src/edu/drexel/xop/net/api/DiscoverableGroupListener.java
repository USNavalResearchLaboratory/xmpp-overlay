package edu.drexel.xop.net.api;


import edu.drexel.xop.net.discovery.GroupDiscoverableObject;

/**
 * The Discoverable Group listener is implemented by objects wishing to retrieve notifications
 * of new discoverable group events.
 *
 * @author Ian Taylor
 */
public interface DiscoverableGroupListener {

    /**
     * Notifies when a new discoverable object has been discovered
     *
     * @param groupDiscoverableObject the group discoverable object that has been discovered.
     */
    public void groupAdded(GroupDiscoverableObject groupDiscoverableObject);

    /**
     * Notifies when a service has been removed
     *
     * @param groupDiscoverableObject the group discoverable object, which has been removed
     */
    public void groupRemoved(GroupDiscoverableObject groupDiscoverableObject);

}
