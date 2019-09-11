/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.presence;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import edu.drexel.xop.client.ClientConnection;
import edu.drexel.xop.client.ClientManager;
import edu.drexel.xop.client.LocalClientListener;
import edu.drexel.xop.component.ComponentBase;
import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.router.PacketRouter;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Handles presence messaging.<br/>
 * presence everywhere
 * 
 * @author David Millar
 */
public class PresenceManager extends ComponentBase implements LocalClientListener {
    private static final Logger logger = LogUtils.getLogger(PresenceManager.class.getName());

    // The key for the presences and userDiscoverableObjects is the bare jid string
    private Map<String, Presence> presences = Collections.synchronizedMap(new HashMap<String, Presence>());
    private ClientProxy proxy;
    private ClientManager clientManager;
    private PacketRouter packetRouter;

    /**
     * Constructor
     * 
     * @param packetRouter
     */
    public PresenceManager(PacketRouter packetRouter, ClientManager clientManager) {
        proxy = ClientProxy.getInstance();
        this.clientManager = clientManager;
        this.clientManager.addLocalClientListener(this);
        this.packetRouter = packetRouter;
        this.packetRouter.addRoute(this);
        
        logger.fine("PresenceManager constructed");
    }

    public void initialize(){
    	logger.fine("initializing the PresenceManager");
    }
    
    /**
     * Handle presence messages coming from the PacketRouter.
     * 
     * @param p A Presence Packet
     */
    @Override
    public void processPacket(Packet p) {
        
        if (!(p instanceof Presence)) {
            logger.warning("PresenceManager.processPacket() received a non-Presence packet!  This should not happen!");
            return;
        }

        logger.fine("PresenceManager processing packet: " + p);

        Presence presencePkt = (Presence) p;
        if (presencePkt.isAvailable() && (presencePkt.getTo() == null) && (presencePkt.getType() == null)) {
            logger.fine("an initial presence message");
        } else {
            logger.fine("this is an update");
        }
        // Add the new presence to map of presences
        String key = p.getFrom().toBareJID();
        presences.put(key, presencePkt); // replace the previous presence packet

        logger.fine("We have presences from:" + presences.keySet());

        // Send the new presence to other connected clients
        for (ClientConnection client : clientManager.getLocalClients()) {
            if (!client.getJid().toBareJID().equals(p.getFrom().toBareJID())) {
                Packet pOut = p.createCopy();
                pOut.setTo(client.getJid());
                proxy.processPacket(pOut);
            }
        }
    }

    public String toString(){
        return "PresenceManager";
    }
    
    /**
     * call this method when a client with this JID leaves the system
     * 
     * @param fromJID
     */
    public void processCloseStream(String fromJID) {
        logger.fine(fromJID + " closed the stream. Sending unavailable presence to all locally connected clients");
        presences.remove(fromJID);
        // send unavailable presence to local clients
        Presence unavailPresence = new Presence();
        unavailPresence.setType(Presence.Type.unavailable);
        for (ClientConnection clientConn : clientManager.getLocalClients()) {
            if (!clientConn.getJid().toBareJID().equals(fromJID)) {
                unavailPresence.setFrom(fromJID);
                unavailPresence.setTo(clientConn.getJid());
                proxy.processPacket(unavailPresence);
            }
        }

    }

    /**
     * sends unavailable presence messages of all localclients to all clients.
     * and removes them from the SD system.
     */
    public void sendUnavailablePresenceMessages() {
        for (ClientConnection fromClient : clientManager.getLocalClients()) {
            String fromJID = fromClient.getJid().toBareJID();
            processCloseStream(fromJID);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.drexel.xop.core.PacketFilter#accept(org.xmpp.packet.Packet)
     */
    @Override
    public boolean accept(Packet p) {
        if (p instanceof Presence) {
            if (p.getTo() == null) {
                logger.log(Level.FINE, "Accepted a Presence packet ('to' field is null)");
                return true;
            } else if (p.getTo().getNode() == null) {
                logger.log(Level.FINE, "Accepted a Presence packet ('node' name is null)");
                return true;
            }
        }
        logger.log(Level.FINE, "Not accepting packet "+p);
        return false;
    }

    // --------------------- [[ LocalClientListener Overrides ]] -------------------

    /*
     * (non-Javadoc)
     * 
     * @see edu.drexel.xop.client.LocalClientListener#clientAdded(edu.drexel.xop.client.ClientConnection)
     */
    @Override
    public void localClientAdded(ClientConnection clientConnection) {
        logger.fine("clientConnection jid: "+clientConnection.getJid());
        // Send previously connected clients' presences to newly connected client
        synchronized(presences){
            for (Presence p : presences.values()) {
                if (p != null) { // If this is null, we never got a presence from this client
                    Packet pOut = p.createCopy();
                    pOut.setTo(clientConnection.getJid());
                    proxy.processPacket(pOut);
                }
            }
        }
        JID ccJid = clientConnection.getJid();
        // Add a key for the new clientunavailPresence
        logger.fine("presences: "+presences+" ccJid: "+ccJid);
        presences.put(ccJid.toBareJID(), null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.drexel.xop.client.LocalClientListener#clientRemoved(edu.drexel.xop.client.ClientConnection)
     */
    @Override
    public void localClientRemoved(ClientConnection clientConnection) {
        presences.remove(clientConnection.getJid().toBareJID());
        synchronized(presences){
            for (Presence p : presences.values()) {
                if (p != null) {
                    Presence unavailPresence = p.createCopy();
                    unavailPresence.setTo(clientConnection.getJid());
                    unavailPresence.setType(Presence.Type.unavailable);
                    proxy.processPacket(unavailPresence);
                }
            }
        }
    }
}