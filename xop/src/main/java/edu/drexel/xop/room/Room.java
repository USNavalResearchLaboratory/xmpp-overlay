package edu.drexel.xop.room;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.tree.BaseElement;
import org.dom4j.tree.DefaultElement;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.net.transport.BasicTransportService;
import edu.drexel.xop.net.transport.XOPTransportService;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.addressing.MulticastAddressPool;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Holds information about an XMPP chatroom, the occupants, the clients, name and the domain.
 * Handles sending MUC Presence and Messages to occupants in this room on this local XOP instance.
 */
public class Room {
    private static final Logger logger = LogUtils.getLogger(Room.class.getName());

    // private Map<JID, String> members; // map of clientJIDs to nick names
    private Map<String, JID> occupantNickToMucOccupantJID;
    private Map<JID, String> mucOccupantToNick;
    private Map<String, JID> occupantNickToClientJID;
    private Map<JID, String> clientJIDToNickMap;
    // private Map<String, JID> occupantNickToFullJID;

    private HashSet<String> features = new HashSet<>();
    private TransportManager transportManager;

    // Built-in transport
    private XOPTransportService transportService;

    private JID mucRoomJID;
    private String roomName;
    private String domain;
    private String description;

    private ClientManager clientManager;

    public Room(JID mucRoomJID, ClientManager clientManager, XOPTransportService transportService) throws IOException {
        this.mucRoomJID = mucRoomJID;
        this.roomName = mucRoomJID.getNode();
        this.domain = mucRoomJID.getDomain();
        this.description = "XO Room named: " + roomName;
        //add default features
        features.add(CONSTANTS.DISCO.MUC_NAMESPACE);

        // members = Collections.synchronizedMap(new HashMap<JID, String>());
        occupantNickToMucOccupantJID = new ConcurrentHashMap<>();
        clientJIDToNickMap = Collections.synchronizedMap(new HashMap<>());
        mucOccupantToNick = Collections.synchronizedMap(new HashMap<>());
        occupantNickToClientJID = Collections.synchronizedMap(new HashMap<>());
        // occupantNickToFullJID = Collections.synchronizedMap(new HashMap<String, JID>());
        this.clientManager = clientManager;

        logger.info("Constructing transport - type:" + XOP.TRANSPORT.SERVICE + ", room: " + mucRoomJID);
        switch(XOP.TRANSPORT.SERVICE){
            case "transport-engine":
                transportManager = new TransportManager(this); // or roomName?
                break;
            case "norm-transport":
                logger.info("Constructing reliable transport for room "+mucRoomJID);
                this.transportService = transportService;
                break;
            default:
                logger.info("Constructing simple transport mechanism for joining room");
                InetAddress multicastGroupAddress =
                        MulticastAddressPool.getMulticastAddress(this.mucRoomJID.toString(),
                                XOP.TRANSPORT.TE.GROUPRANGE);
                if (logger.isLoggable(Level.FINE)) {
                    if (multicastGroupAddress != null) {
                        String hostAddress = multicastGroupAddress.getHostAddress();
                        logger.fine("Using multicast group: " + hostAddress);
                    } else {
                        logger.warning("multicast group is NULL!");
                    }
                }
                this.transportService = new BasicTransportService(XOP.BIND.INTERFACE,
                        multicastGroupAddress, XOP.TRANSPORT.TE.PORT, this, clientManager);
        }

        logger.info("Advertising new room: " + mucRoomJID);
    }

    public Set<JID> getMUCOccupants(){
        return new HashSet<>(mucOccupantToNick.keySet());
    }

    public Collection<JID> getMemberClientJids() {
        return clientJIDToNickMap.keySet();
    }

    /**
     * @param jid the ClientJID
     * @return the nick name associated with this clientJID
     */
    private String getNickForClientJID(JID jid) {
        return clientJIDToNickMap.get(jid);
    }

    /**
     * Get the MUC occupant for a corresponding nickname
     * @param nick the mucOccupant nickname
     * @return the matching mucOccupant for this nick name
     */
    public JID getMUCOccupantJidForNick(String nick) {
        return occupantNickToMucOccupantJID.get(nick);
    }

    /**
     * get the client JID for the supplied room nickname
     *
     * @param mucOccupantJID the nickname for the room
     * @return the Client JID
     */
    public JID getClientJIDForMucOccupant(JID mucOccupantJID) {
        String nick = mucOccupantToNick.get(mucOccupantJID);
        if (nick == null) {
            return null;
        }
        return occupantNickToClientJID.get(nick);
    }

    public JID getMUCOccupantForClientJID(JID clientJID) {
        String nick = clientJIDToNickMap.get(clientJID);
        if (nick != null)
            return occupantNickToMucOccupantJID.get(nick);
        else
            logger.warning("No nickname found for " + clientJID);
        return null;
    }

    /**
     * Modify the packet with the proper IQ information and allow it to pass back to DiscoIqHandler
     * @param p the IQ packet to be modified
     */
    void processIQ(IQ p) {
        //TODO: support querying contacts
        String nameSpace = p.getChildElement().getNamespaceURI();
        if(nameSpace.equals(CONSTANTS.DISCO.INFO_NAMESPACE)) {
            //add identity
            DefaultElement identity = new DefaultElement("identity");
            identity.addAttribute("category", "conference");
            identity.addAttribute("name", roomName);
            identity.addAttribute("type", "text");
            p.getChildElement().add(identity);

            //add features
            for(String feature : features) {
                DefaultElement element = new DefaultElement("feature");
                element.addAttribute("var", feature);
                p.getChildElement().add(element);
            }
            //TODO: support extended info result, see: http://xmpp.org/extensions/xep-0045.html
        } else if(nameSpace.equals(CONSTANTS.DISCO.ITEM_NAMESPACE)) {
            //compile the list of users
            for (String nick : clientJIDToNickMap.values()) {
                DefaultElement item = new DefaultElement("item");
                item.addAttribute("roomName", getRoomJid() + "/" + nick);
                p.getChildElement().add(item);
            }
        }
    }

    /**
     * Sends MUC message to transport and to locally connected occupants in the room.
     *
     * @param m the XMPP group chat message
     * @param sendToTransport send to transport if true, do not send otherwise
     */
    public void sendMessage(Message m, boolean sendToTransport) {
        //m.setID(UUID.randomUUID().toString());
        if (sendToTransport) {
            switch (XOP.TRANSPORT.SERVICE) {
                case "transport-engine":
                    transportManager.sendMessage(m.createCopy());
                    break;
                default:
                    transportService.sendPacket(m.createCopy());
            }
        }
        // Sends the message to local connected Occupants
        sendMessageToLocalOccupants(m);
    }

    /**
     * Sends the given XMPP message to locally connected MUC occupants
     *
     * @param m the group chat message to send to locally connected occupants
     */
    private void sendMessageToLocalOccupants(Message m) {
        JID fromJID = getMUCOccupantForClientJID(m.getFrom());
        if (fromJID == null) {
            // We need to ensure that if the gateway is enabled, messages from the gateway
            // get delivered to locally connected clients.
            logger.fine("No clientJID found. from:" + m.getFrom());
            JID toMucOccupant = getMUCOccupantForClientJID(m.getTo());
            JID fromClient = getMUCOccupantForClientJID(m.getFrom());
            logger.fine("working with toMucOccupant: " + toMucOccupant + " to: " + m.getTo());
            // if (XOP.ENABLE.GATEWAY
            //         && clientManager.isLocal(m.getTo())
            //
            if (clientManager.isLocal(m.getTo())
                    && !m.getFrom().equals(toMucOccupant)) {
                logger.fine(m.getTo() + " != " + fromClient + "sending to local client.");
                // Message serviceToOccupantMessage = new Message();
                // serviceToOccupantMessage.setType(Message.Type.groupchat);
                // serviceToOccupantMessage.setTo(m.getTo());
                // serviceToOccupantMessage.setFrom(m.getFrom());
                // serviceToOccupantMessage.setBody(m.getBody());
                // serviceToOccupantMessage.setID(UUID.randomUUID().toString());

                Message serviceToOccupantMessage = Utils.removeJabberNamespace(m);
                serviceToOccupantMessage.setID(UUID.randomUUID().toString());
                ProxyUtils.sendPacketToLocalClient(serviceToOccupantMessage, clientManager);
            } else {
                logger.fine("Not sending message to local clients");
            }
            return;
        }

        // Iterate through the list of locally connected clients and send to them
        // Only for when XO gateway is not enabled
        for (JID clientJID : getMemberClientJids()) {
            logger.finer("Trying to send to "+clientJID+" from "+m.getFrom());
            // handle messages for logged in clients
            if (clientManager.isLocal(clientJID)) {
                // Message serviceToOccupantMessage = new Message();
                // serviceToOccupantMessage.setType(Message.Type.groupchat);
                // serviceToOccupantMessage.setTo(clientJID);
                // serviceToOccupantMessage.setBody(m.getBody());
                // serviceToOccupantMessage.setFrom(fromJID);

                Message serviceToOccupantMessage = Utils.removeJabberNamespace(m);
                serviceToOccupantMessage.setID(UUID.randomUUID().toString());
                serviceToOccupantMessage.setTo(clientJID);
                serviceToOccupantMessage.setFrom(fromJID);

                logger.fine("Sending groupchat to local client: " + clientJID + ", from: " + fromJID);
                ProxyUtils.sendPacketToLocalClient(serviceToOccupantMessage, clientManager);
            } else {
                if (logger.isLoggable(Level.FINE))
                    logger.fine(clientJID + " is not locally connected.");
            }
        }
    }

    /**
     * Call processIncomingPacket() to send XMPP Group chat message to the network and to local clients
     * Then send over the gateway if it's enabled
     *
     * @param m the XMPP group chat message to be sent
     * @param sendToTransport true if message originated from local client
     */
    public void sendMessageToRoomAndSendOverGateway(Message m, boolean sendToTransport) {
        logger.fine("Room: " + roomName + " sending msg from:" + m.getFrom() + " msg ==" + m.getBody() + "==");

        sendMessage(m, sendToTransport);

        if(XOP.ENABLE.GATEWAY){
            XOProxy.getInstance().sendToGateway(m.createCopy());
        } else {
            logger.fine("Gateway not enabled.");
        }
    }


    /**
     * process incoming packets from GCS Transport.
     *
     * @param packet incoming message packet
     */
    public void handleIncomingMessage(Packet packet) {
        for (JID clientJID : getMemberClientJids()) {
            Message msg = (Message) packet.createCopy();
            logger.finer("msg.getTo(): " + msg.getTo());
            logger.finer("msg.getFrom(): " + msg.getFrom());

            Message m = Utils.removeJabberNamespace(msg);

            logger.finer(" attempting to send to clientJID: " + clientJID);
            if (clientManager.isLocal(clientJID)
                    && (clientManager.isLocal(m.getTo()) || m.getTo().equals(getRoomJid()))
                    && !m.getFrom().equals(clientJID)) {

                m.setTo(clientJID);
                // test if this is from the muc Occupant
                JID possibleClientJID = m.getFrom();
                if (getMemberClientJids().contains(possibleClientJID)) {
                    JID newFrom = new JID(getRoomJid().getNode(), getDomain(), getNickForClientJID(m.getFrom()));
                    logger.finer("rewriting OLD from: " + possibleClientJID + " NEW from: " + newFrom);
                    m.setFrom(newFrom);
                } else {
                    JID newFrom = new JID(getRoomJid().getNode(), getDomain(), possibleClientJID.getNode());
                    logger.finer(possibleClientJID + " NOT FOUND in rewriting OLD from: " + possibleClientJID + ", NEW from: " + newFrom);
                    m.setFrom(newFrom);

                }
                m.setID(UUID.randomUUID().toString());

                logger.fine("sending to local client: " + clientJID + ", from: " + m.getFrom());
                ProxyUtils.sendPacketToLocalClient(m, clientManager);
            } else {
                if (logger.isLoggable(Level.FINE))
                    logger.finer("not sending this message to fullJID: " + clientJID);
            }
        } // end for loop local members

        // TODO 2019-01-10: GCS and whatever else uses this method will not have messages sent over
        //   The s2s connection if it's enabled.
        // if(XOP.ENABLE.GATEWAY){
        //     XOProxy.getInstance().sendToGateway(packet.createCopy());
        // } else {
        //     logger.fine("Gateway not enabled.");
        // }
    }

    /**
     * Sends Presence Messages to occupants signaling this client is joining as a MUC Occupant.
     * @param presence the available presence message from the muc occupant
     */
    public void addMucOccupantToRoom(Presence presence){
        JID clientJID = presence.getFrom();
        JID mucOccupantJID = presence.getTo();
        logger.info("adding new muc occupant with occupant JID: " + mucOccupantJID
                + " from fullJID: " + clientJID);
        mucOccupantToNick.put(mucOccupantJID, mucOccupantJID.getResource());
        clientJIDToNickMap.put(clientJID, mucOccupantJID.getResource());
        occupantNickToClientJID.put(mucOccupantJID.getResource(), clientJID);
        occupantNickToMucOccupantJID.put(mucOccupantJID.getResource(), mucOccupantJID);

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ClientJIDs of room members: " + clientJIDToNickMap);
            logger.fine("mucOccupantToNick: " + mucOccupantToNick);
            logger.fine("occupantNickToClientJID: " + occupantNickToClientJID);
            logger.fine("occupantNickToMucOccupantJID: " + occupantNickToMucOccupantJID);
        }
        // sendPresenceToLocalOccupants(clientJID, mucOccupantJID);
        sendPresenceToLocalOccupants(presence);
    }

    /**
     * Sends the given presence to locally connected muc occupants
     *
     * @param presence the presence to send
     */
    private void sendPresenceToLocalOccupants(Presence presence) {
        JID clientJID = presence.getFrom();
        JID mucOccupantJID = presence.getTo();

        if (clientManager.isLocal(clientJID)) {
            logger.fine( "Send presences from existing occupants to new occupant if fullJID is local");
            // logger.finer("getMemberNicknames(): " + getMemberNicknames());
            // for(String occupant : getMemberNicknames()) {
            for(JID occupant : getMUCOccupants()) {
                String stringPresence = Utils.stripNamespace(presence.toString(), "jabber:client");
                try {
                    Presence presenceCopy = (Presence) Utils.packetFromString(stringPresence);
                    // JID fromMUCOccupant = new JID(roomName, domain, occupant);
                    presenceCopy.setFrom(occupant);
                    presenceCopy.setTo(clientJID);
                    addMUCUserElement(mucOccupantJID, occupant, presenceCopy);
                    if (logger.isLoggable(Level.FINER))
                        logger.finer("Sending packet: [[" + presenceCopy.toString() + "]]");
                    ProxyUtils.sendPacketToLocalClient(presenceCopy, clientManager);
                } catch (DocumentException e) {
                    logger.severe("Unable to marshall Presence Packet from string: "+stringPresence);
                }
            }
        }

        logger.info("Send presences from the new occupant to all local members");
        for(JID jid : clientJIDToNickMap.keySet()) {
            if (clientManager.isLocal(jid)) {
                Presence p = new Presence();
                p.setTo(jid);
                p.setFrom(mucOccupantJID);

                addMUCUserElement(clientJID, jid, p);
                ProxyUtils.sendPacketToLocalClient(p, clientManager);
            }
        }
    }

    private void addMUCUserElement(JID clientJID, JID jid, Presence p) {
        Element elem = p.addChildElement("x", "http://jabber.org/protocol/muc#user");
        elem.addElement("item").addAttribute("affiliation", "member").addAttribute("role", "participant");
        if (jid.equals(clientJID)) {
            elem.addElement("status").addAttribute("code", "110");
        }
    }

    /**
     * Removes the muc occupant for this associated client JID and sends Unavailable Presence
     * to any locally connected client
     * @param presence the unavailable presence of the MUC occupant
     */
    public void removeMUCOccupant(Presence presence) {
        JID mucOccupantJID = presence.getTo();
        JID clientJID = presence.getFrom();

        //send presences from the user to all local members
        for(JID jid : clientJIDToNickMap.keySet()) {
            if (clientManager.isLocal(jid)) {
                logger.finer("Creating unavailable presence message to send to "+jid);
                Presence p = new Presence(Presence.Type.unavailable);
                p.setFrom(mucOccupantJID);
                p.setTo(jid);
                Element e = p.addChildElement("x", "http://jabber.org/protocol/muc#user");
                Element itemElement = new BaseElement("item");
                itemElement.addAttribute("affiliation", "member");
                itemElement.addAttribute("jid", clientJID.toString());
                itemElement.addAttribute("role", "none");
                if (jid.equals(clientJID)) {
                    logger.finer("Adding 110 for presence to " + jid);
                    Element statusCode = new BaseElement("status");
                    statusCode.addAttribute("code", "110");
                }
                e.add(itemElement);
                ProxyUtils.sendPacketToLocalClient(p, clientManager);
            }
        }

        String nick;
        if (mucOccupantJID == null) {
            logger.finer("No MUC Occupant passed in. removing: " + clientJID);
            nick = clientJIDToNickMap.remove(clientJID);
        } else {
            logger.finer("Removing with MUC Occupant JID: " + mucOccupantJID);
            nick = mucOccupantToNick.remove(mucOccupantJID);
        }
        logger.finer("==== NICK IS " + nick);
        JID removedClientJID = occupantNickToClientJID.remove(nick);

        if (nick != null && occupantNickToClientJID.containsKey(nick)) {
            if (removedClientJID != null && clientJIDToNickMap.containsKey(removedClientJID)) {
                logger.finer("Removed clientJID: " + clientJID
                        + " from clientJIDToNickMap");
                clientJIDToNickMap.remove(removedClientJID);
            }
        }

        if (nick != null && occupantNickToMucOccupantJID.containsKey(nick)) {
            JID removedMucOccupantJID = occupantNickToMucOccupantJID.remove(nick);
            if (removedMucOccupantJID != null && mucOccupantToNick.containsKey(removedMucOccupantJID)) {
                logger.finer("Removed removedMucOccupantJID: " + removedMucOccupantJID
                        + " from mucOccupantToNick");
                mucOccupantToNick.remove(removedMucOccupantJID);
            }
        }
    }

    public JID getRoomJid() {
        return mucRoomJID;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Used for the source address in the protosd advert
     * @return the multicast address string for this MUC room, different for each transport
     */
    public String getRoomAddressStr() {
        String addressStr;
        switch (XOP.TRANSPORT.SERVICE) {
            case "transport-engine":
                // a bit of a hack since we can't assume we're using the same algorithm
                InetAddress address;
                try {
                    address = MulticastAddressPool.getMulticastAddress(
                            mucRoomJID.toString(), XOP.TRANSPORT.TE.GROUPRANGE);
                    addressStr = address != null ? address.getHostAddress() : "none";
                } catch (UnknownHostException | NullPointerException e) {
                    logger.warning("Unable to set addressStr. " + e.getMessage());
                    addressStr = "unknown";
                }
                break;
            case "groupcomms":
                addressStr = XOP.TRANSPORT.ADDRESS;
                break;
            default: // simple-transport and norm-transport
                addressStr = transportService.getAddressStr();
        }

        return addressStr;
    }

    public String toString() {
        return "MUCRoomJID: "+getRoomJid();
    }

    public void close() {
        switch (XOP.TRANSPORT.SERVICE) {
            case "transport-engine":
                if (transportManager != null) {
                    logger.fine("Closing transportManager");
                    transportManager.close();
                }
                break;
            case "groupcomms":
                // TODO: dnn 2017-07-10 implement
                // gcs.unsubscribe(this.getRoomJid());
                break;
            default:
                logger.fine("Closing transportService");
                this.transportService.close();
        }
    }
}
