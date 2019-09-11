/**
 * (c) 2012 Drexel University
 */

package edu.drexel.xop.interceptor;

import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.Packet;

import java.util.logging.Logger;

/**
 * Rejects packets with identical 'to' and 'from' fields
 *
 * @author di
 */
public class SelfAddressedPacketInterceptor implements PacketInterceptor {
    private static final Logger logger = LogUtils.getLogger(SelfAddressedPacketInterceptor.class.getName());

    public SelfAddressedPacketInterceptor() {
        logger.fine("Constructing SelfAddressedPacketInterceptor");
    }

    @Override
    public boolean interceptPacket(Packet p) {
        logger.finer("Checking for self-addressed packets");
        if (p.getTo() != null && p.getFrom() != null && p.getTo().toString().equals(p.getFrom().toString())) {
            logger.finer("Dropping self-addressed packet");
            return false;
        } else {
            return true;
        }
    }
}