package edu.drexel.xop.gateway;

import edu.drexel.xop.packet.LocalPacketProcessor;
import edu.drexel.xop.util.logger.LogUtils;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Handle stream from XMPP server
 *
 */
class ReceivingGatewayConnection extends GatewayConnection {
    private static final Logger logger = LogUtils.getLogger(ReceivingGatewayConnection.class.getName());

    private boolean clientMode = false;
    private int port;

    private AtomicBoolean killSwitch = new AtomicBoolean(false);

    private LocalPacketProcessor packetProcessor;

    ReceivingGatewayConnection(Socket sock, LocalPacketProcessor packetProcessor) {
        this.packetProcessor = packetProcessor;
        socket = sock;
        port = sock.getPort();
        logger.info("Connected to external server: " + sock.getInetAddress() + ":" + sock.getPort());
    }

    public void stop() {
        killSwitch.set(true);
    }

    public GatewayXMLProcessor getXMLProcessor() {
        return new ReceivingGatewayXMLProcessor(this, packetProcessor);
    }

    public boolean getClientMode() {
        return clientMode;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "ReceivingGatewayConnection";
    }
}
