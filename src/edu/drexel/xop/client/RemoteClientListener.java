/*
 * Copyright (C) Drexel University 2012
 */
package edu.drexel.xop.client;

/**
 * @author duc
 *         <p/>
 *         Description: Implementors of this interface are notified by the ClientManager
 *         when _remote_ clients are added or removed.
 * @since Mar 27, 2012
 */
public interface RemoteClientListener {
    /**
     * @param remoteClient the client being added.
     */
    public void remoteClientAdded(RemoteClient remoteClient);

    /**
     * @param remoteClient the client being removed.
     */
    public void remoteClientRemoved(RemoteClient remoteClient);
}
