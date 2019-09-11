/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.core;

import org.xmpp.packet.Packet;

/**
 * An interface for listening and processing packets.
 *
 * @author David Millar
 */
public interface PacketListener {

    /**
     * implement this to handle an xmpp packet
     *
     * @param p
     */
    public void processPacket(Packet p);

}
