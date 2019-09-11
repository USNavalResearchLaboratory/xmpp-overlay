/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.core;

import org.xmpp.packet.Packet;

/**
 * An interface for filtering packets.
 *
 * @author David Millar
 */
public interface PacketFilter {

    /**
     * Checks to see if a given packet matches the filter
     *
     * @param packet Packet to test against the filter
     * @return if the packet matches the filter
     */
    public boolean accept(Packet packet);

    /**
     * Default class
     */
    public static class AcceptFilter implements PacketFilter {
        public boolean accept(Packet packet) {
            return false;
        }
    }
}
