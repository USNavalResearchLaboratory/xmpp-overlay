package edu.drexel.xop.component.s2s;

import org.xmpp.packet.Packet;

/**
 * 
 * interface for routing packets from the server connection to the component.
 *
 */
interface PacketRoutingDevice {
    public void routePacket(Packet p);
}
