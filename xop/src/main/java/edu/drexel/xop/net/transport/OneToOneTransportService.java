package edu.drexel.xop.net.transport;

import edu.drexel.transportengine.api.TransportEngineAPI;
import edu.drexel.transportengine.api.TransportEngineAPI.MessageCallback;
import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.packet.TransportPacketProcessor;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.xop.util.addressing.NetUtilsKt;
import org.dom4j.DocumentException;
import org.json.simple.JSONObject;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uses the transport engine to send and receive messages from a group
 *
 */
public class OneToOneTransportService implements XOPTransportService, MessageCallback {
	private static final Logger logger = LogUtils.getLogger(OneToOneTransportService.class.getName());
    // TODO dnguyen 08-07-2014: Use XOP.ONETOONE.ADDRESS and XOP.ONETOONE.LISTENPORT for TransportEngine Address and Port
    private static final String onetoone_id = "onetooneid";
    private final String senderID;
    private TransportEngineAPI api;
    private ClientManager clientManager;
    private TransportPacketProcessor transportPacketProcessor;

    public OneToOneTransportService(ClientManager clientManager,
                                    TransportPacketProcessor transportPacketProcessor) throws IOException {
        this.clientManager = clientManager;
        this.transportPacketProcessor = transportPacketProcessor;
		logger.info("Initializing OneToOne on: "+XOP.TRANSPORT.TE.ADDRESS+":"+XOP.TRANSPORT.TE.PORT);
		api = new TransportEngineAPI(XOP.TRANSPORT.TE.ADDRESS, XOP.TRANSPORT.TE.PORT);
		api.start();

        long persistTime = 0;
        if (XOP.TRANSPORT.TE.PERSISTTIME != 0){
            persistTime = (System.currentTimeMillis() /1000L) + XOP.TRANSPORT.TE.PERSISTTIME;
        }
        api.executeChangeProperties(XOP.TRANSPORT.TE.RELIABLE, 0 , XOP.TRANSPORT.TE.ORDERED);
        api.executeSubscription(onetoone_id.toLowerCase(), true);
        api.registerMessageCallback(this);
        logger.info("created TransportManager for One-to-One transport");

        senderID = NetUtilsKt.getBindAddress(XOP.TRANSPORT.SEND_INTERFACE).getHostName();

	}

	public void close() {
		try {
			api.executeEndSession();
		} catch (IOException e) {
			logger.severe("Unable to end transport engine session gracefully: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void sendPacket(Packet packet) {
		try {
			long persistTime = 0; 
			if (XOP.TRANSPORT.TE.PERSISTTIME != 0){
				persistTime = (System.currentTimeMillis() /1000L) + XOP.TRANSPORT.TE.PERSISTTIME;
			}
			logger.info(">> Sending message to " + onetoone_id + " with reliability=" + XOP.TRANSPORT.TE.RELIABLE);
			api.executeChangeProperties(false, 0, XOP.TRANSPORT.TE.ORDERED);
            //api.executeChangeProperties(XopProperties.getBooleanProperty(KEYS.TRANSPORT.RELIABLE), 0 , XopProperties.getBooleanProperty(KEYS.TRANSPORT.ORDERED));
            logger.info(">>> packet: "+packet.toXML());
            String id = packet.getID();
            if( id == null ){
                id = senderID;
            }
			api.executeSend(id, onetoone_id.toLowerCase(), packet.toXML());
		} catch (IOException e) {
			logger.severe("Unable to send message: ==" + ((packet != null) ? packet.toXML() : "null message") + " exception: " + e.getMessage());
		}
	}

	@Override
    public void processIncomingPacket(Packet packet) {

		//only forward to local members if they are on this local XOP instance
        for (JID user : clientManager.getLocalClientJIDs()) {
			if(user.equals(packet.getTo())){
				logger.finer("Incoming XMPP one-to-one message from TransportEngine: "+ packet);
                transportPacketProcessor.processPacket(packet.getFrom(), packet);
                // XOProxy.getInstance().processIncomingPacket(packet, false);
			}
		}
	}

    /**
     * Implements TransportEngine.MessageCallback class for handling messages
     * @param message the JSONObject message
     */
	@Override
	public void processMessage(JSONObject message) {
		if ("end-session".equals(message.get("type"))) {
			logger.finer("end-session message received, no further processing necessary");
			return;
		}
        String payload = null;
        try {
            payload = (String)((JSONObject)message.get("info")).get("payload");
            if(logger.isLoggable(Level.INFO)) logger.info("Processing JSON message: " + message);
            if(logger.isLoggable(Level.FINE)) logger.fine("Processing JSON message: " + message);

			Packet packet = Utils.packetFromString(payload);
            processIncomingPacket(packet);
		} catch (DocumentException e) {
			logger.severe("Unable to build packet from string: " + payload);
		} catch (Exception e) {
			logger.severe("Exception while processing message: " + message.toString());
			e.printStackTrace();
		}
	}

    public String getAddressStr() {
        return XOP.TRANSPORT.TE.ADDRESS;
    }

    public int getPort() {
		return XOP.TRANSPORT.TE.PORT;
	}
}
