package edu.drexel.xop.packet.iq;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements proper responses to XEP-0054 messages (vCard requests)
 * 
 * @author di
 */
class VCardIqHandler { // implements PacketProcessor {

    private static final String VCARD_XMLNS = "vcard-temp";
    private static final Logger logger = LogUtils.getLogger(VCardIqHandler.class.getName());

    private ClientManager clientManager;

    VCardIqHandler(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    void processPacket(Packet p) {
        IQ packet = IQ.createResultIQ((IQ)p);
        packet.setChildElement("vCard", VCARD_XMLNS);
        if( logger.isLoggable(Level.FINE) ) logger.fine("Responding to vCard IQ with: " + packet.toString());
//        XOProxy.getInstance().processOutgoingPacket(packet);
        ProxyUtils.sendPacketToLocalClient(packet, clientManager);
    }
}