package edu.drexel.xop.room;

import org.dom4j.tree.DefaultElement;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;

import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Holds and manages rooms for a specific domain.
 *
 */
public class RoomManager {
    private static final Logger logger = LogUtils.getLogger(RoomManager.class.getName());

    private Map<JID, Room> rooms = Collections.synchronizedMap(new HashMap<>());
    private HashSet<String> features = new HashSet<>();
    private String domain;
    private String description;

    public RoomManager(String domain, String description) {
        this.domain = domain;
        this.description = description;
        //add default features
        features.add(CONSTANTS.DISCO.MUC_NAMESPACE);
    }

    public String getDomain() {
        return domain;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Process an iq packet
     * @param p copy of the iq packet
     */
    public void processIQPacket(IQ p) {
        logger.fine("RoomManager, " + domain + ", processing IQ: " + p);
        //this is a query for this room manager
        if(p.getTo().getNode() == null) {
            String nameSpace = p.getChildElement().getNamespaceURI();
            switch (nameSpace) {
                case CONSTANTS.DISCO.INFO_NAMESPACE:
                    //respond with information about this manager
                    //add identity
                    DefaultElement identity = new DefaultElement("identity");
                    identity.addAttribute("category", "conference");
                    identity.addAttribute("name", description);
                    identity.addAttribute("type", "text");
                    p.getChildElement().add(identity);

                    //add features
                    for (String feature : features) {
                        DefaultElement element = new DefaultElement("feature");
                        element.addAttribute("var", feature);
                        p.getChildElement().add(element);
                    }
                    break;
                case CONSTANTS.DISCO.ITEM_NAMESPACE:
                    //respond with information about the rooms
                    for (Room room : rooms.values()) {
                        //add each room
                        DefaultElement element = new DefaultElement("item");
                        element.addAttribute("jid", room.getRoomJid().toString());
                        element.addAttribute("name", room.getDescription());
                        p.getChildElement().add(element);
                    }
                    //TODO: support returning only a few items at a time, see: http://xmpp.org/extensions/xep-0045.html
                    break;
            }// end switch(nameSpace)
        } else { //check to see if the request matches any rooms
            logger.fine("Check for room processing");
            Room room = rooms.get(new JID(p.getTo().toBareJID()));
            if(room != null ) {
                room.processIQ(p);
            } else {
                logger.warning("Error received IQ request for room: " + p.getTo() + " which does not exist.");
            }
        }
    }
    // TODO 2019-01-10 remove this since it's not used
    // /**
    //  * sends a chat message to the correct room
    //  * @param p the chat message
    //  * @param sendToTransport true send to transport, false do not send
    //  */
    // public void sendMessageToRoom(Message p, boolean sendToTransport) {
    //     //find the proper destination room for this message
    //     Room room = rooms.get(p.getTo());
    //     if(room != null) {
    //         room.sendMessageToRoomAndSendOverGateway(p, sendToTransport);
    //     } else {
    //         //no matching rooms
    //         logger.warning("There is no room with JID: " + p.getTo());
    //     }
    // }

    public void addRoom(Room room) {
        if (!rooms.containsKey(room.getRoomJid())) {
            logger.info("Adding chat room: " + room.getRoomJid() + " to server: " + getDomain());
            room.setDomain(getDomain());
            rooms.put(room.getRoomJid(), room);
        } else {
            logger.warning("Could not create chat room: " + room.getRoomJid() + ", room already exists");
        }
    }

    public Room getRoom(JID roomName) {
        return rooms.get(roomName);
    }

    public Collection<Room> getRooms() {
        return rooms.values();
    }
}
