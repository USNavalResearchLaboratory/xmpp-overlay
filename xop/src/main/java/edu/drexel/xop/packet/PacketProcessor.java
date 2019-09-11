package edu.drexel.xop.packet;

import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * An interface for handling packets from Transport or Local Clients
 *
 */
public interface PacketProcessor {
    /**
     * Do something with the packet
     * @param fromJID the source of the packet
     * @param p the packet to be processed
     */
    void processPacket(JID fromJID, Packet p);

    // /**
    //  * redirects packets from the network transport to their destination or response back to the network
    //  * @param fromJID the source
    //  * @param p the packet
    //  */
    // void processPacketFromNetwork(JID fromJID, Packet p);

    // void processPacketFromGateway(JID fromJID, Packet p);
}
