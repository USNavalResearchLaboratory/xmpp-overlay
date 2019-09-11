package edu.drexel.xop.core;

import edu.drexel.xop.client.XOPConnection;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.JID;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages local and discovered XMPP Clients and their availability status.
 *
 * Follows the Singleton to ensure that there is
 * only one <tt>ClientManager</tt> for all authenticated clients
 */
public class ClientManager {
    private static final Logger logger = LogUtils.getLogger(ClientManager.class.getName());

    // public static ClientManager instance = null;

    private final Map<JID, XMPPClient> xmppClients;
    private Map<JID, XMPPClient> remoteClients;
    private Map<JID, XMPPClient> localClients;
    private Set<JID> availableClients;

    public ClientManager() {
        logger.info("Creating ClientManager");

        /*
        Note: we're initializing all maps here to be synchronizedMaps to force blocking access for puts and gets.
        It's slower, however we're often iterating over the whole map and we need to block access when doing that
        and when putting new elements in there.

        https://stackoverflow.com/questions/510632/whats-the-difference-between-concurrenthashmap-and-collections-synchronizedmap

        - dnn 2014-09-26
         */
        xmppClients = Collections.synchronizedMap(new HashMap<>());
        remoteClients = Collections.synchronizedMap(new HashMap<>());
        localClients = Collections.synchronizedMap(new HashMap<>());
        availableClients = Collections.synchronizedSet(new HashSet<>());
    }


    /**
     *
     * @param xopConnection the connection to match the jid
     * @return the JID associated for this connection
     */
    public JID getJIDForLocalConnection(XOPConnection xopConnection) {
        for( XMPPClient tempClient : xmppClients.values() ){
            if( tempClient instanceof LocalXMPPClient &&
                xopConnection.equals(((LocalXMPPClient) tempClient).getXopConnection())
            ) {
                return tempClient.getFullJID();
            }
        }
        return null;
    }

    /**
     *
     * @param jid the jid of the xmpp client (remote or local)
     * @return the xmpp client
     */
    XMPPClient getXMPPClient(JID jid){
        return xmppClients.get(new JID(jid.toBareJID()));
    }

    public XMPPClient getLocalXMPPClient(JID jid) {
        return localClients.get(new JID(jid.toBareJID()));
    }

    public XMPPClient getRemoteXMPPClient(JID jid) {
        return remoteClients.get(new JID(jid.toBareJID()));
    }

    public Collection<XMPPClient> getXMPPClients(){
        return xmppClients.values();
    }

    /**
     * @param jid the JID to find the client connection
     * @return the associated xopConnection for the given JID, null otherwise
     */
    public XOPConnection getConnection(JID jid) {
        //look for a xopConnection matching this JID
        // XMPPClient xmppClient = xmppClients.get(new JID(jid.toBareJID()));
        XMPPClient xmppClient = localClients.get(new JID(jid.toBareJID()));
        if (xmppClient != null) { //&& (xmppClient instanceof LocalXMPPClient) ){
            logger.info("found a connection for this jid: "+jid);
            return ((LocalXMPPClient) xmppClient).getXopConnection();
        } else {
            logger.warning("no connection found for JID: " + new JID(jid.toBareJID()) + ", returning null!");
            return null;
        }
    }

    /**
     *
     * @param clientJID the client jid to be removed
     */
    public void removeJIDFromAvailableSet(JID clientJID){
        availableClients.remove(clientJID);
    }

    public void addJIDToAvailableSet(JID clientJID) {
        availableClients.add(clientJID);
    }

    /**
     * @return a copy of the availableClientJIDs (to avoid ConcurrentModificationExceptions)
     */
    public Set<JID> getAvailableClientJIDs(){
        return new HashSet<>(availableClients);
    }

    /**
     * adds presence as a client
     * @param xmppClient the newly discovered to be added
     */
    public void addDiscoveredXMPPClient(XMPPClient xmppClient){
        xmppClients.put(xmppClient.getBareJID(), xmppClient);
        availableClients.add(xmppClient.getFullJID());
        remoteClients.put(xmppClient.getBareJID(), xmppClient);
    }


    /**
     * called by CLientXMLProcessor when an XMPP client goes online.
     * @param localXMPPClient the locally connected XMPP client to add
     */
    public void addLocalXMPPClient(LocalXMPPClient localXMPPClient) {
        // System.out.println("Adding a LOCAL XMPP Client " + localXMPPClient.getFullJID()
        //         + " on: " + localXMPPClient.getXopConnection().getHostName());
        logger.fine("Adding a LOCAL XMPP Client " + localXMPPClient.getFullJID()
                + " on: " + localXMPPClient.getXopConnection().getHostName());

        //make sure that this connection is not already registered
        JID jid = localXMPPClient.getFullJID();
        JID bareJid = localXMPPClient.getBareJID();
        if( xmppClients.containsKey(bareJid) || clientExists(jid) ) {
            logger.fine("xmppClients: " + xmppClients.keySet());
            logger.fine("localClients: " + localClients.keySet());
            logger.warning("xopConnection already registered: " + jid);
            System.out.println("xopConnection already registered: " + jid);
            return;
        }
        logger.fine("Adding " + bareJid + " as LocalXMPPClient");
        xmppClients.put(bareJid, localXMPPClient);
        localClients.put(bareJid, localXMPPClient);
        logger.fine("Exiting addLocalXMPPClient()");
    }

    void removeXMPPClient(JID clientJID) {
        xmppClients.remove(new JID(clientJID.toBareJID()));
        remoteClients.remove(clientJID);
        localClients.remove(new JID(clientJID.toBareJID()));
        logger.fine("After REMOVE xmppClients: " + xmppClients.keySet());
        logger.fine("After REMOVE localClients: " + localClients.keySet());
        // availableClients.remove(clientJID); // the fulljid
    }

    void removeAllClients() {
        availableClients.clear();
        remoteClients.clear();
        xmppClients.clear();
        localClients.clear();
    }


    /**
     * Locally connected clients are XMPP clients connected directly to this XOP instance.
     * E.g Xabber connected to an XO instance.
     * @return a set of JIDs of the online/offline local users
     */
    public Set<JID> getLocalClientJIDs() {
        return localClients.keySet();
    }

    /**
     *
     * @return all the online/offline discovered local and remote clients
     */
    public Set<JID> getRemoteClients() {
        return remoteClients.keySet();
    }

    /**
     * Determines if the JID or Bare JID already is registered.
     * @param jid the jid
     * @return true if this JID or bare jid is registered, false otherwise
     */
    public boolean clientExists(JID jid) {
        return xmppClients.containsKey(new JID(jid.toBareJID()) ) ;
    }

    /**
     * Is the client represented by jid locally connected to this instance of XOP?
     * @param jid the jid to test
     * @return true if this jid is a locally connected client, false otherwise
     */
    public boolean isLocal(JID jid) {
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("xmppClients.containsKey(jid) " + xmppClients.containsKey(jid));
            logger.finer("remoteClients.containsKey(jid) " + remoteClients.containsKey(jid));
            logger.finer("isLocal: " + (xmppClients.containsKey(jid) && !remoteClients.containsKey(jid)));
        }
        // return xmppClients.containsKey(jid) && !remoteClients.containsKey(jid);
        return localClients.containsKey(jid);
    }
}
