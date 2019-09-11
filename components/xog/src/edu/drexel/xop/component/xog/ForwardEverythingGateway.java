/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.xog;

import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author di
 */
class ForwardEverythingGateway extends Gateway {
    protected static final Logger logger = LogUtils.getLogger(ForwardEverythingGateway.class.getName());

    public ForwardEverythingGateway() {
        super();
    }

    /**
     * Only considers message not already encountered by a gateway
     */
    @Override
    public boolean shouldForward(Packet p) {
        return !Gateway.hasSubdomain(p);
    }

    /**
     * If this returns true, then we'll call processPacket on it in the PacketRouter.
     */
    @Override
    public boolean accept(Packet p) {
        if (!shouldForward(p)) {
            return false;
        }
        if (p instanceof Message) {
            if (isMessageFromManet(p)) {
                logger.log(Level.FINE, "Accepted a packet from a MUC: " + p);
                return true;
            }
        } else if (p instanceof Presence) {
            if (isPresenceFromManet(p)) {
                logger.fine("Matched presence packet: " + p);
                return true;
            } else {
                logger.finer("Rejected presence packet: " + p);
            }
        }
        return false;
    }
}