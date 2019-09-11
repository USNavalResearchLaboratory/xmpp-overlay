package edu.drexel.xop.net.transport;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.packet.TransportPacketProcessor;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * A simple UDP Multicast Socket for one-to-one messages.
 *
 */
public class BasicOneToOneTransportService extends AbstractBasicTransportService {
	private static final Logger logger = LogUtils.getLogger(BasicOneToOneTransportService.class.getName());

    private TransportPacketProcessor transportPacketProcessor;

    public BasicOneToOneTransportService(String ifname, InetAddress group, int port,
                                         ClientManager clientManager,
                                         TransportPacketProcessor transportPacketProcessor) throws IOException {
        super(ifname, group, port, clientManager);
        this.transportPacketProcessor = transportPacketProcessor;
    }

    public void processIncomingPacket(Packet packet) {
        logger.finer("Processing incoming packet: {{" + packet + "}}");
        logger.fine("Local ClientJIDs: " + clientManager.getLocalClientJIDs());
		//only forward to local members if they are on this local XOP instance
        for (JID user : clientManager.getLocalClientJIDs()) {
			if(user.equals(packet.getTo())){
				logger.finer("Incoming XMPP one-to-one message from Simple Transport: "+ packet);
                transportPacketProcessor.processPacket(packet.getFrom(), packet);
				// XOProxy.getInstance().processIncomingPacket(packet, false);
			}
		}
	}

}
