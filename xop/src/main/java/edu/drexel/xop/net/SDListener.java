package edu.drexel.xop.net;

import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.net.InetAddress;

/**
 * These are the callbacks for a service discovery implementation
 */
public interface SDListener {

    /**
     * A gateway with the following domain has been connected
     * @param address the address this gateway is connected to
     * @param domain domain of the XMPP server
     */
    void gatewayAdded(InetAddress address, JID domain);

    /**
     * A gateway with the following domain has been removed
     * @param domain of the XMPP server
     */
    void gatewayRemoved(JID domain);

    /**
     * This XMPP presence was received
     * @param presence the received precence
     */
    void clientDiscovered(Presence presence);

    /**
     * Updating status or other object, assumes client is already "available"/"online"
     *
     * @param presence updated presence or probe presence
     */
    void clientUpdated(Presence presence);

    /**
     * This XMPP presence unavailable presence message
     * @param presence unavailable presence
     */
    void clientRemoved(Presence presence);

    /**
     * @param clientJID The JID on a remote instance that is disconnected from the XOP domain
     */
    void clientDisconnected(JID clientJID);

    /**
     * This JID alias was registered for this user
     * @param mucPresence the mucOccupant's presence
     */
    void mucOccupantJoined(Presence mucPresence);

    /**
     * The MUC occupant that exited
     * @param mucPresence the mucOccupant's presence
     */
    void mucOccupantExited(Presence mucPresence);


    /**
     * For supporting updated status and show elements for MUC occupants
     *
     * @param presence the updated presence from the mucOccupant
     */
    void mucOccupantUpdated(Presence presence);

    /**
     * The service discovery manager supports advertising rooms generated from this XO instance.
     * @param roomJID the JID of the room to be added
     */
    void roomAdded(JID roomJID);
    
    /**
     * handles removing a room when a SDManager discovers a room was removed from the system.
     * Description:
     * @param roomJID the JID of the room to be removed
     */
    void roomRemoved(JID roomJID);


}
