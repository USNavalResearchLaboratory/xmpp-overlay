package edu.drexel.xop.packet;

import org.dom4j.DocumentException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.client.XOPConnection;
import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.core.roster.RosterListManager;
import edu.drexel.xop.packet.iq.IqManager;
import edu.drexel.xop.room.Room;
import edu.drexel.xop.room.RoomManager;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;

abstract class AbstractPacketProcessor implements PacketProcessor {
    private static Logger logger = LogUtils.getLogger(AbstractPacketProcessor.class.getName());

    ClientManager clientManager;

    static IqManager iqManager;
    static RosterListManager rosterListManager;

    AbstractPacketProcessor(ClientManager clientManager) {
        this.clientManager = clientManager;

        if (rosterListManager == null) {
            rosterListManager = new RosterListManager(clientManager);
        }
        if (iqManager == null) {
            iqManager = new IqManager(clientManager, rosterListManager);
        }
    }

    void handleMessageForRoom(JID roomJID, Message message, boolean sendToTransport) {
        logger.fine("Trying to process message for a MUC room: " + roomJID
                + " to:" + message.getTo() + " from: " + message.getFrom());
        RoomManager roomManager = XOProxy.getInstance().getRoomManager(roomJID.getDomain());

        if (roomManager != null) {
            Room room = roomManager.getRoom(roomJID);
            room.sendMessageToRoomAndSendOverGateway(message, sendToTransport);

            // room.handleIncomingMessage(message);

            // roomManager.sendMessageToRoom(message, sendToTransport);
        } else {
            logger.warning("No groupchat exists for any room domain: " + message);
        }
    }


    /**
     * Send XMPP Message to local clients and/or to transport
     *
     * @param p       the message to be delivered
     * @param sendToTransport true if the message originated from a local client, false it came from Transport
     */
    void handleOneToOneMessage(Message p, boolean sendToTransport) {
        logger.info("trying to send a packet, --" + p.toXML() + "--, to: " + p.getTo());
        if (clientManager.isLocal(p.getTo())) {
            XOPConnection connection = clientManager.getConnection(p.getTo());
            if (logger.isLoggable(Level.FINE))
                logger.fine("connection is: " + connection.getAddress() + " when packet is: " + p.toString());
            connection.writeRaw(p.toXML().getBytes());
            logger.fine(" wrote bytes to " + p.getTo());
        } else {
            if (sendToTransport) {
                if (logger.isLoggable(Level.FINE))
                    logger.fine("No connected client for packet, sending to network: " + p.toString());
                XOProxy.getInstance().getXopNet().sendToOneToOneTransport(p);
            }
        }
        logger.fine("exit");
    }

    Packet stripJabberServerNamespace(Packet p) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("strip jabber:server namespace from packet: {{" + p + "}}");
        }

        try {
            //this removes the jabber:server namespace when openfire sends messages to a server
            p = Utils.packetFromString(Utils.stripNamespace(p.toString(), "jabber:server"));
        } catch (DocumentException e) {
            logger.severe("Unable to strip namespace from packet: " + p.toString());
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("new packet: {{" + p + "}}");
        }
        return p;
    }
}
