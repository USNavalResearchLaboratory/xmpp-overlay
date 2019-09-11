/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq;

import java.util.logging.Logger;

import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

import edu.drexel.xop.core.PacketFilter;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Implements proper responses to XEP-0054 messages (vCard requests)
 * 
 * @author di
 */
public class VCardIqHandler extends IqHandler implements PacketFilter {

    private static final String VCARD_XMLNS = "vcard-temp";
    private static final Logger logger = LogUtils.getLogger(VCardIqHandler.class.getName());

    public VCardIqHandler() {
        logger.fine("VCardIqHandler being constructed");
    }

    @Override
    public IQ handleIq(IQ req) {
        logger.finest("Handling : " + req);
        IQ resp = IQ.createResultIQ(req);
        resp.setChildElement("vCard", VCARD_XMLNS);
        return resp;
    }

    public boolean accept(Packet packet) {
        if (!(packet instanceof IQ)) {
            return false;
        }
        IQ iq = (IQ) packet;
        if (iq.getType() != IQ.Type.get) {
            return false;
        }
        return iq.getChildElement().getNamespaceURI().equals(VCARD_XMLNS);
    }
}