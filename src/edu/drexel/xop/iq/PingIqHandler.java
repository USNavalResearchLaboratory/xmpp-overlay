/**
 * (c) 2013 Drexel University
 */

package edu.drexel.xop.iq;

import java.util.logging.Logger;

import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

import edu.drexel.xop.core.PacketFilter;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Implements proper responses to XEP-0199 messages (pings)
 * 
 * @author di
 */
public class PingIqHandler extends IqHandler implements PacketFilter {

    private static final String PING_XMLNS = "urn:xmpp:ping";
    private static final Logger logger = LogUtils.getLogger(PingIqHandler.class.getName());

    public PingIqHandler() {
        logger.fine("PingIqHandler being constructed");
    }

    @Override
    public IQ handleIq(IQ req) {
        logger.finest("Handling : " + req);
        return IQ.createResultIQ(req);
    }

    public boolean accept(Packet packet) {
        if (!(packet instanceof IQ)) {
            return false;
        }
        IQ iq = (IQ) packet;
        if (iq.getType() != IQ.Type.get) {
            return false;
        }
        return iq.getChildElement().getNamespaceURI().equals(PING_XMLNS);
    }
}