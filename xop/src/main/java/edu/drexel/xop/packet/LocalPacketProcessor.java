package edu.drexel.xop.packet;

import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketExtension;
import org.xmpp.packet.Presence;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.gateway.ServerDialbackSession;
import edu.drexel.xop.net.SDManager;
import edu.drexel.xop.room.Room;
import edu.drexel.xop.room.RoomManager;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Parses packet and redirects it to the proper location
 * INCOMING (from clients and network) packets.
 */
public class LocalPacketProcessor extends AbstractPacketProcessor {
    private static final Logger logger = LogUtils.getLogger(LocalPacketProcessor.class.getName());

    // private static LocalPacketProcessor instance = null;
    private LocalPresenceManager localPresenceManager;

    /**
     * private constructor that creates the iqManager (for handling iq packets)
     * and AbstractPresenceManager for processing presence messages
     */
    public LocalPacketProcessor(ClientManager clientManager, SDManager sdManager) {
        super(clientManager);
        localPresenceManager = new LocalPresenceManager(clientManager, sdManager, rosterListManager);
    }

    /**
     * directs IQ, Presence, and Message packets to their respective IQManager, localPresenceManager,
     * Room, or OneToOneTransport
     *
     * @param fromJID the source of the packet
     * @param p       the packet
     */
    public void processPacket(JID fromJID, Packet p) {
        logger.fine("Processing packet from local client " + fromJID);
        if (logger.isLoggable(Level.FINER)) logger.finer("Packet: " + p);
        if (p == null) {
            logger.warning("Discarded NULL packet");
            return;
        }
        p = stripJabberServerNamespace(p);

        if (p instanceof IQ) {
            iqManager.processIQPacket(fromJID, p);
        } else if (p instanceof Presence) {
            localPresenceManager.processPresencePacket((Presence) p);
        } else if (p instanceof Message) {
            Message msg = (Message) p;

            PacketExtension composingChatStates = msg.getExtension("composing", CONSTANTS.DISCO.CHATSTATES_NAMESPACE);
            PacketExtension activeChatStates = msg.getExtension("active", CONSTANTS.DISCO.CHATSTATES_NAMESPACE);
            logger.fine("compose: " + composingChatStates);
            logger.fine("active: " + activeChatStates);
            if (composingChatStates == null) {
                msg.deleteExtension("active", CONSTANTS.DISCO.CHATSTATES_NAMESPACE);
                //determine if the message was destined for groupchat
                String type = msg.getElement().attributeValue("type");
                if (type != null && type.equals("groupchat")) {
                    JID roomJID = msg.getTo().asBareJID();
                    handleMessageForRoom(roomJID, msg, true);
                } else {
                    //handle normally
                    if (logger.isLoggable(Level.FINE))
                        logger.fine("handleOneToOneMessage: " + p.toString());
                    handleOneToOneMessage(msg, true);
                }
            } else {
                logger.fine("discarding composing chatstates");
            }
        } else {
            logger.warning("Dropping unknown packet type: [[[" + p + "]]]");
        }
        logger.finer("Exit.");
    }


    public void processPacketFromGateway(JID fromJID, Packet p) {

        // TODO: 2018-12-06

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("packet to send to room: " + p);
            logger.fine("searching for room: " + fromJID);
        }

        // retrieve destination room from the ClientManager from field
        RoomManager rmgr = XOProxy.getInstance().getRoomManager(p.getFrom().getDomain());
        if (rmgr == null) {
            logger.warning("unable to find room, " + p.getFrom() + ", for this message");
            return;
        }

        Room room = rmgr.getRoom(new JID(fromJID.toBareJID()));
        if (p.getFrom().getResource() == null) {
            logger.fine("Processing Packet with no resource in from: field and destined " +
                    "for MUC room. e.g. active typing messages");
            room.sendMessage((Message) p, true);
            return;
        }

        // We only want to process packet from clients connected to the gatewayed server
        // i.e. ofuser@openfire or room@conference.openfire/ofuser
        String fromNick = fromJID.getResource();
        JID fromMucOccupantJID = room.getMUCOccupantJidForNick(fromNick);

        JID fromClientJID = room.getClientJIDForMucOccupant(fromMucOccupantJID);
        logger.fine("ClientManager.getInstance().getRemoteClients(): ["
                + clientManager.getRemoteClients() + "]");
        logger.fine("remoteMUCOccupants: "
                + ServerDialbackSession.remoteMUCOccupants);
        logger.fine("room.getMemberClientJids(): "
                + room.getMemberClientJids());
        logger.fine("fromMucOccupantJID: " + fromMucOccupantJID
                + ", fromClientJID: " + fromClientJID
                + ", fromNick: " + fromNick
                + ", p.getFrom(): " + p.getFrom()
                + ", p.getTo(): " + p.getTo());
        if (fromMucOccupantJID != null
                && ( // the m
                !clientManager.getRemoteClients().contains(fromClientJID)
                        && ServerDialbackSession.remoteMUCOccupants.contains(fromMucOccupantJID)
                        && room.getMemberClientJids().contains(fromClientJID)
                        && room.getMemberClientJids().contains(p.getTo()))
        ) {
            if (!clientManager.isLocal(fromClientJID)) {
                logger.fine(" processing packet from remote client on gatewayed server");
                room.sendMessage((Message) p, true);
            } else {
                logger.fine("message is not from a local client, do not send to room");
            }
        } else {
            logger.fine("packet is not from a recognized gateway client. ie. originated from XOP or not from OF user");
        }
    }
    //
    // /**
    //  * Processes packets coming from the transport system and sends to the muc occupants in the chat
    //  * room
    //  *
    //  * @param roomJID the MUC room object to send these packets
    //  * @param packet  the packet to be sent. (assumed this is an XMPP Message)
    //  * @deprecated Should not be used anymore
    //  */
    // public void processPacketForRoom(JID roomJID, Packet packet) {
    //     try {
    //         logger.finer("only forward to local members and only if 'from' is not the sender");
    //         // logger.fine("room.getMemberClientJids(): " + room.getMemberClientJids());
    //
    //         JID dest = packet.getTo();
    //         if (roomJID.equals(dest)) {
    //             // room.processIncomingPacket((Message) packet);
    //             // // The MUC to: field was the generic room
    //             RoomManager roomManager = XOProxy.getInstance().getRoomManager(roomJID.getDomain());
    //             Room room = roomManager.getRoom(roomJID);
    //             handleClientMUCMessage(room, packet);
    //         } else {
    //             logger.fine("possibly from Gateway: " + packet);
    //             Message msg = (Message) packet.createCopy();
    //
    //             if (clientManager.isLocal(msg.getTo())) {
    //                 Message m = Utils.removeJabberNamespace(msg);
    //                 logger.fine("sending to local client: " + msg.getTo() + ", from: " + m.getFrom());
    //                 ProxyUtils.sendPacketToLocalClient(m, clientManager);
    //             }
    //         }
    //     } catch (Exception e) {
    //         logger.severe("Exception while processing message: " + packet.toString());
    //         e.printStackTrace();
    //     }
    // }
    //
    // /**
    //  * Re-write the packet to remove the xmlns='jabber:server' or 'jabber:client' then send
    //  * to muc occupants of the given room.
    //  * TODO 2018-02-13 move this to Room.
    //  *
    //  * @param room   the room to send this message
    //  * @param packet the packet to be sent
    //  * @deprecated moved to Room
    //  */
    // private void handleClientMUCMessage(Room room, Packet packet) {
    //     for (JID clientJID : room.getMemberClientJids()) {
    //         Message msg = (Message) packet.createCopy();
    //
    //         // hack to remove xmlns='jabber:server' (also removes 'jabber:client')
    //         Message m = Utils.removeJabberNamespace(msg);
    //
    //
    //         logger.finer(" attempting to send to clientJID: " + clientJID);
    //         if (clientManager.isLocal(clientJID)
    //                 && (clientManager.isLocal(m.getTo()) || m.getTo().equals(room.getRoomJid()))
    //                 && !m.getFrom().equals(clientJID)) {
    //
    //             m.setTo(clientJID);
    //             // test if this is from the muc Occupant
    //             JID possibleClientJID = m.getFrom();
    //             if (room.getMemberClientJids().contains(possibleClientJID)) {
    //                 JID newFrom = new JID(room.getRoomJid().getNode(), room.getDomain(), room.getNickForClientJID(m.getFrom()));
    //                 logger.finer("rewriting OLD from: " + possibleClientJID + " NEW from: " + newFrom);
    //                 m.setFrom(newFrom);
    //             } else {
    //                 JID newFrom = new JID(room.getRoomJid().getNode(), room.getDomain(), possibleClientJID.getNode());
    //                 logger.finer(possibleClientJID + " NOT FOUND in rewriting OLD from: " + possibleClientJID + ", NEW from: " + newFrom);
    //                 m.setFrom(newFrom);
    //
    //             }
    //             m.setID(UUID.randomUUID().toString());
    //
    //             logger.fine("sending to local client: " + clientJID + ", from: " + m.getFrom());
    //             ProxyUtils.sendPacketToLocalClient(m, clientManager);
    //         } else {
    //             if (logger.isLoggable(Level.FINE))
    //                 logger.finer("not sending this message to fullJID: " + clientJID);
    //         }
    //     } // end for loop local members
    //
    //     if (XOP.ENABLE.GATEWAY) {
    //         if (logger.isLoggable(Level.FINER)) {
    //             logger.finer("----DETERMINING SENDING TO GATEWAY");
    //             logger.finer("to: " + packet.getTo() + " from: " + packet.getFrom());
    //             logger.finer("!ClientManager.getInstance().isLocal(packet.getTo()) " + !clientManager.isLocal(packet.getTo()));
    //             logger.finer("!ClientManager.getInstance().isLocal(packet.getFrom()" + !clientManager.isLocal(packet.getFrom()));
    //             logger.finer("ServerDialbackSession.gatewayMUC: " + ServerDialbackSession.gatewayMUC);
    //
    //             logger.finer("ServerDialbackSession.remoteMUCOccupants: [[" + ServerDialbackSession.remoteMUCOccupants + "]]");
    //         }
    //         if (
    //             // drop messages received from network that are for locally connected clients
    //                 !clientManager.isLocal(packet.getTo())
    //
    //                         // drop messages received from network that were originating from locally connected clients
    //                         && (!clientManager.isLocal(packet.getFrom())
    //                         && ServerDialbackSession.gatewayMUC.contains(packet.getTo())
    //                         && !ServerDialbackSession.gatewayMUC.contains(packet.getFrom())
    //                 )
    //         ) {
    //             logger.fine(" This is a gatewayed XOP instance (ie XOG), send to over Gateway");
    //             XOProxy.getInstance().sendToGateway(packet);
    //         } else {
    //             if (logger.isLoggable(Level.FINER))
    //                 logger.finer("not sending packet to gateway [[[" + packet + "]]]");
    //         }
    //     }
    // }
}
