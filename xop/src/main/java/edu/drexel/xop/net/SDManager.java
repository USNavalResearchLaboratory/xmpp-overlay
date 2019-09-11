package edu.drexel.xop.net;


import edu.drexel.xop.room.Room;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.net.InetAddress;

/**
 * Implement this interface that XO Proxy will call methods to interface with Presence Transport
 *
 */
public interface SDManager {
    /**
     * Defines what to do when the service is started
     */
    void start();

    /**
     * Defines what to do when the service is closed
     */
    void close();

    /**
     * The service discovery system advertise that it is connected to a gateway
     * @param address the network address of the gateway
     * @param domain the domain of this gateway
     */
    void addGateway(InetAddress address, JID domain);

    /**
     * The service discovery system should drop the advertiement for this gateway
     * @param domain the domain of the gateway
     */
    void removeGateway(JID domain);

    /**
     * The service discovery system should advertise this presence message
     * @param presence the presence message to be sent to the network
     */
    void advertiseClient(Presence presence);

    /**
     * The service discovery system drop the advertisement for this presence message
     * @param presence the presence message to be removed
     */
    void removeClient(Presence presence);

    /**
     * @param presence the updated presence message for the client
     */
    void updateClientStatus(Presence presence);

    /**
     * The service discovery system advertises this MUC Occupant
     * @param presence The presence message.
     */
    void advertiseMucOccupant(Presence presence);


    /**
     * The service discovery system should drop the advertisement for this alias
     *
     * @param presence the presence from the MUC occupant to be removed from the SD system.
     *                 (The fromJID is used as the mucOccupantJID.)
     */
    void removeMucOccupant(Presence presence);

    /**
     * @param presence containing updated Presence message, status to the MUC room
     */
    void updateMucOccupantStatus(Presence presence);

    /**
     * The service discovery manager must support the callback methods defined in SDListener
     * @param listener the SDListener
     */
    void addSDListener(SDListener listener);

    /**
     * The service discovery manager supports advertising rooms generated from this XO instance.
     *
     * @param room the room to be added
     */
    void advertiseMucRoom(Room room);
    
    /**
     * Handles removing a room when a SDManager discovers a room was removed from the system.
     * @param room the room to be removed
     */
    void removeMucRoom(Room room);
}
