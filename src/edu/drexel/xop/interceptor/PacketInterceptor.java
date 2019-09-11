/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.interceptor;

import org.xmpp.packet.Packet;

/**
 * Components that implement this interface will be fed packets as they are fed to the router
 * The router blocks until all the interceptors have returned.
 * 
 * @author David Millar
 */
public interface PacketInterceptor {
    /**
     * Packets going through the server are sent to this method any interceptors<br/>
     * before they are routed.
     * 
     * @param p intercepted packet
     * @return true to continue processing the packet, false to indicate that the packet is rejected
     */
    public boolean interceptPacket(Packet p);
}