package edu.drexel.xop.net.api;

import mil.navy.nrl.protosd.api.distobejcts.DiscoverableObject;

/**
 * Discoverable object listener is implemented by objects wishing to retrieve notifications
 * of new discoverable object events.
 *
 * @author Ian Taylor
 */
public interface DiscoverableObjectListener {

    /**
     * Notifies when a new discoverable object has been discovered
     *
     * @param discoverableObject the discoverable object that has been discovered.
     */
    public void discoverableObjectAdded(DiscoverableObject discoverableObject);

    /**
     * Notifies when a service has been removed
     *
     * @param discoverableObject the discoverable object, which has been removed
     */
    public void discoverableObjectRemoved(DiscoverableObject discoverableObject);

}
