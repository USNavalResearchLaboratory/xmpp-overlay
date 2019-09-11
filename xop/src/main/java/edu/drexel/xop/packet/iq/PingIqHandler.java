package edu.drexel.xop.packet.iq;

/**
 * (c) 2013 Drexel University
 */

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements proper responses to XEP-0199 messages (pings)
 * 
 * @author di
 */
class PingIqHandler {//implements PacketProcessor {

    private static final Logger logger = LogUtils.getLogger(PingIqHandler.class.getName());
    private ClientManager clientManager;

    PingIqHandler(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    public void processPacket(Packet p) {
        IQ packet = (IQ)p;
        packet = IQ.createResultIQ(packet);
        if( logger.isLoggable(Level.FINEST) ) logger.finest("Responding to ping IQ with: " + packet.toString());
//        XOProxy.getInstance().processOutgoingPacket(packet);
        ProxyUtils.sendPacketToLocalClient(packet, clientManager);
    }
}