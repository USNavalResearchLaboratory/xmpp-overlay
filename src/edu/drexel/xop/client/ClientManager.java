/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmpp.packet.JID;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.router.PacketRouter;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * This is intended to be a facade to handle client operations.<br/>
 * Only put authenticated clients here.<br/>
 * The router should be registered as a clientListener in order to update it's<br/>
 * routing table when users connect/disconnect.<br/>
 * 
 * @author David Millar
 */
public class ClientManager {

    private PacketRouter packetRouter;
    private static final Logger logger = LogUtils.getLogger(ClientManager.class.getName());

    private List<LocalClientListener> localClientListeners = Collections.synchronizedList(new LinkedList<LocalClientListener>());
    private final List<RemoteClientListener> remoteClientListeners = Collections.synchronizedList(new LinkedList<RemoteClientListener>());

    // Always reference clients by their bare jid. Resources are subject to change.

    /**
     * set of all the local and remote client jids.
     */
    private Set<String> bareJids = Collections.synchronizedSet(new HashSet<String>());

    /**
     * A map of the LocalClient connections
     */
    private Map<String, ClientConnection> localClientConnections = Collections.synchronizedMap(new HashMap<String, ClientConnection>());

    // keyed from the bare jid
    private Map<String, RemoteClient> remoteClients = Collections.synchronizedMap(new HashMap<String, RemoteClient>());

    // private Set<RemoteClient> remoteClients = Collections.synchronizedSet(new HashSet<RemoteClient>());

    public ClientManager(PacketRouter packetRouter) {
        logger.fine("ClientManager initializing");
        this.packetRouter = packetRouter;
        this.addLocalClientListener(this.packetRouter);
    }

    public void addLocalUser(ClientConnection clientConnection) {

        logger.log(Level.INFO, "Client connected with JID: " + clientConnection.getJid());

        // if this user is already connected, delete the old connection before starting the new one
        if (localClientConnections.get(clientConnection.getJid().toBareJID()) != null) {
            packetRouter.handleCloseStream(clientConnection.getJid().toBareJID());
            localClientListeners.remove(localClientConnections.get(clientConnection.getJid().toBareJID()));
            logger.log(Level.INFO, "Deleting stale user: " + clientConnection.getJid().toBareJID());
        }
        localClientConnections.put(clientConnection.getJid().toBareJID(), clientConnection);
        bareJids.add(clientConnection.getJid().toBareJID());

        for (LocalClientListener listener : localClientListeners) {
            listener.localClientAdded(clientConnection);
        }
    }

    /**
     * removes a local user given a
     * 
     * @param jid
     */
    public void removeLocalClient(String jid) {
        ClientConnection clientConnection = localClientConnections.get(jid);
        logger.log(Level.INFO, "removing clientconnection: " + clientConnection.getJid().toBareJID());
        ClientConnection cc = localClientConnections.remove(clientConnection.getJid().toBareJID());
        for (LocalClientListener listener : localClientListeners) {
            if (listener != null) {
                listener.localClientRemoved(cc);
            } else {
                logger.log(Level.SEVERE, "A null element was put into clientListeners for "
                    + clientConnection.getJid().toBareJID());
            }
        }
        bareJids.remove(cc.getJid().toBareJID());
        cc.stop();
    }

    /**
     * @param remoteClient
     */
    public void addRemoteClient(RemoteClient remoteClient) {
        logger.log(Level.INFO, "adding remote client: " + remoteClient.getJid() + " bareJID:"
            + remoteClient.getJid().toBareJID());
        remoteClients.put(remoteClient.getJid().toBareJID(), remoteClient);
        bareJids.add(remoteClient.getJid().toBareJID());
        synchronized (remoteClientListeners) {
            for (RemoteClientListener listener : remoteClientListeners) {
                listener.remoteClientAdded(remoteClient);
            }
        }
    }

    public void removeRemoteClient(String bareJid) {
        logger.log(Level.INFO, "removing remoteClient: " + bareJid);
        RemoteClient remoteClient = remoteClients.remove(bareJid);
        bareJids.remove(bareJid);
        logger.log(Level.FINE, " (remoteClient == null): " + (remoteClient == null));

        synchronized (remoteClientListeners) {
            for (RemoteClientListener listener : remoteClientListeners) {
                if (listener != null) {
                    listener.remoteClientRemoved(remoteClient);
                } else {
                    logger.log(Level.WARNING, "null RemoteClientListener");
                }
            }
        }
    }

    public boolean userExists(JID jid) {
        return localClientConnections.containsKey(jid.toBareJID()) || remoteClients.containsKey(jid.toBareJID());
    }

    public Collection<ClientConnection> getLocalClients() {
        return Collections.unmodifiableCollection(localClientConnections.values());
    }

    /**
     * @param bareJidStr
     * @return true if the given jid string is a local client, false otherwise
     */
    public boolean isLocalClient(String bareJidStr) {
        return localClientConnections.containsKey(bareJidStr);
    }

    public Collection<RemoteClient> getRemoteClients() {
        return Collections.unmodifiableCollection(remoteClients.values());
    }

    public void addLocalClientListener(LocalClientListener listener) {
        localClientListeners.add(listener);
    }

    public void addRemoteClientListener(RemoteClientListener remoteListener) {
        remoteClientListeners.add(remoteListener);
    }

    public String toString() {
        return "This proxy (\"" + XopProperties.getInstance().getProperty(XopProperties.DOMAIN)
            + "\") has following clients connected: " + localClientConnections.keySet().toString() + "\n";
    }
}