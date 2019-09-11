package edu.drexel.xop.room;

import org.dom4j.DocumentException;
import org.json.simple.JSONObject;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.io.IOException;
import java.util.logging.Logger;

import edu.drexel.transportengine.api.TransportEngineAPI;
import edu.drexel.transportengine.api.TransportEngineAPI.MessageCallback;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Uses the transport engine to send and receive messages from a group
 */
class TransportManager implements MessageCallback {
	private static final Logger logger = LogUtils.getLogger(TransportManager.class.getName());

    private Room room;

    // transport engine api
	private TransportEngineAPI api;
    // private boolean reliable = false;
    // private boolean ordered = false;

    TransportManager(Room room) throws IOException{
        this.room = room;

        api = new TransportEngineAPI(XOP.TRANSPORT.TE.ADDRESS, XOP.TRANSPORT.TE.LISTENPORT);
        api.start();

        long persistTime = 0;
        if (XOP.TRANSPORT.TE.PERSISTTIME != 0){
            persistTime = (System.currentTimeMillis() /1000L) + XOP.TRANSPORT.TE.PERSISTTIME;
        }
        api.executeChangeProperties(XOP.TRANSPORT.TE.RELIABLE, persistTime , XOP.TRANSPORT.TE.ORDERED);
        api.executeSubscription(room.getRoomJid().toString().toLowerCase(), true);
        api.registerMessageCallback(this);
        logger.info("created TransportManager for room: " + this.room.getRoomJid());
    }

	public void close() {
		try {
			api.executeEndSession();
		} catch (IOException e) {
			logger.severe("Unable to end transport engine session gracefully: " + e.getMessage());
			e.printStackTrace();
		}
	}

    void sendMessage(Message m) {
        try {
			// long persistTime = 0;
			// if (XOP.TRANSPORT.PERSISTTIME != 0){
			// 	persistTime = (System.currentTimeMillis() /1000L) + XOP.TRANSPORT.PERSISTTIME;
			// }
            logger.info(">> Sending message to " + room.getRoomJid() + " with reliability=" + XOP.TRANSPORT.TE.RELIABLE);
			// api.executeChangeProperties(XOP.TRANSPORT.RELIABLE, 0 , XOP.TRANSPORT.ORDERED);

            String idStr = m.getID();
            if( idStr == null ){
                idStr = Utils.generateID(12);
                logger.warning("m.getID() is null, using random string: "+idStr);
            }
            api.executeSend(idStr, room.getRoomJid().toString().toLowerCase(), m.toString());

        } catch (IOException e) {
            logger.severe("Unable to send message: ==" + m.toXML() + " exception: " + e.getMessage());
        }
    }

	public void processMessage(JSONObject message) {
        if ("end-session".equals(message.get("type"))) {
            logger.info("end-session message received, no further processing necessary");
            return;
        }

        String payload = (String) ((JSONObject) message.get("info")).get("payload");
        //if( logger.isLoggable(Level.FINE) ) logger.fine("Processing incoming (from network) JSON message: " + message);
        logger.info("Processing incoming (from network) JSON message: " + message);
        try {
            Packet packet = Utils.packetFromString(payload);
            room.handleIncomingMessage(packet);
            // edu.drexel.xop.net.transport.IncomingPacketProcessor.processPacketForRoom(room.getRoomJid(), packet);
        } catch (DocumentException e) {
            logger.severe("Unable to build packet from string: " + payload);
        }
    }
}
