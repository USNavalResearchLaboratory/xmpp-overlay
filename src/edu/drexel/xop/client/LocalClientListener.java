/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.client;

/**
 * LocalClientListeners are notified when local clients are added or removed from the system by
 * the ClientManager.
 *
 * @author dnguyen
 * @author David Millar
 */
public interface LocalClientListener {
    /**
     * This gets called when a client authenticates
     *
     * @param clientConnection
     */
    public void localClientAdded(ClientConnection clientConnection);

    /**
     * called when a client is removed
     *
     * @param clientConnection
     */
    public void localClientRemoved(ClientConnection clientConnection);
}
