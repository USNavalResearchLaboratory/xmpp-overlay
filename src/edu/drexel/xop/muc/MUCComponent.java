package edu.drexel.xop.muc;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError.Condition;
import org.xmpp.packet.PacketError.Type;
import org.xmpp.packet.Presence;

import edu.drexel.xop.client.ClientManager;
import edu.drexel.xop.component.ComponentBase;
import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.core.XOPException;
import edu.drexel.xop.iq.IqManager;
import edu.drexel.xop.iq.disco.DiscoIdentity;
import edu.drexel.xop.iq.disco.DiscoItem;
import edu.drexel.xop.iq.disco.DiscoProvider;
import edu.drexel.xop.iq.disco.ProxyDiscoIqHandler;
import edu.drexel.xop.iq.disco.ServerDiscoProvider;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.api.DiscoverableGroupListener;
import edu.drexel.xop.net.api.DiscoverableMembershipListener;
import edu.drexel.xop.net.discovery.GroupDiscoverableObject;
import edu.drexel.xop.net.discovery.MembershipDiscoverableObject;
import edu.drexel.xop.net.discovery.RoomType;
import edu.drexel.xop.net.discovery.SDXOPObject;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Muc Component - delegates incoming packets, handles component and Room Disco.
 * urlass (Jan 17, 2013): We don't currently use this object, but if we implement XEP-0055 or (more likely) XEP-0059, we may want to use it.
 * 
 * @author dnguyen
 * @author David Millar
 */
public class MUCComponent extends ComponentBase implements DiscoverableGroupListener, DiscoverableMembershipListener, DiscoProvider, ServerDiscoProvider {
    private static final Logger logger = LogUtils.getLogger(MUCComponent.class.getName());
    private final String domain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);
    private final String subdomain = XopProperties.getInstance().getProperty(XopProperties.MUC_SUBDOMAIN);

    // DISCO
    private Set<String> features = new HashSet<>();
    private Set<DiscoIdentity> identities = new HashSet<>();
    private DiscoItem discoServerItem = null;

    Map<JID, JID> mucAliasToFullJid = new ConcurrentHashMap<JID, JID>();
    private final Map<String, MucRoom> rooms = new ConcurrentHashMap<String, MucRoom>();

    /*
     * INCREDIBLE HACK ALERT:
     * Sometimes, the MembershipDiscoverableObject reaches XOP before the GroupDiscoverableObject
     * for that member's room, or the room is not finished being created before the MDO arrives.
     * This queue holds MDOs that we have seen for which we do not have a corresponding room. When
     * we create rooms, we try to distribute these MDOs to the correct room, as if they were arriving
     * right on time.
     */
    private Set<MembershipDiscoverableObject> orphanMDOs = new HashSet<>();
    private ClientManager clientManager;
    private IqManager iqManager;

    public MUCComponent(ClientManager clientManager, IqManager iqManager) {
        this.clientManager = clientManager;
        this.iqManager = iqManager;
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.component.ComponentBase#initialize()
     */
    @Override
    public void initialize() {
        logger.info("Initializing MucComponent...");

        // Disco Features
        features.add(ProxyDiscoIqHandler.INFO_NAMESPACE);
        features.add(ProxyDiscoIqHandler.ITEM_NAMESPACE);
        features.add(MucProperties.MUC_NAMESPACE);
        // TODO: Support XEP-0055
        // features.add("jabber:iq:search");
        // TODO: Support XEP-0059
        // features.add("http://jabber.org/protocol/rsm");

        // Disco Identities
        identities.add(new DiscoIdentity("conference", "text", "Public Chatrooms"));
        // TODO: Support XEP-0055
        // identities.add(new DiscoIdentity("directory", "chatroom",
        // "Public Chatroom Search"));

        SDXOPObject discoverySystem = XopNet.getSDObject();
        logger.info("adding MucComponent as Listener to Discovery system");
        discoverySystem.addDiscoverableGroupListener(this);
        discoverySystem.addDiscoverableMembershipListener(this);

        // Tell the IQManager about the MUC IQ handler
        this.iqManager.addFilterHandler(new MUCIqHandler());

        logger.info("Initialized MUC: " + this.subdomain + "." + this.domain);
    }

    public Set<MucRoom> getRooms() {
        return Collections.synchronizedSet(new HashSet<>(rooms.values()));
    }

    private MucRoom getRoom(String roomName) {
        return rooms.get(roomName);
    }

    private boolean roomExists(String roomName) {
        return rooms.containsKey(roomName);
    }

    private MucRoom createRoom(String roomName, RoomType roomType, String roomOwner, String msg, Boolean openMcastGroup, boolean advertise) {
        logger.fine("Checking for room '" + roomName + "'");
        if (roomExists(roomName)) {
            logger.fine("Room '" + roomName + "' has already been created");
            return getRoom(roomName);
        } else {
            logger.fine("Creating MUC room '" + roomName + "', with owner '" + roomOwner + "'");
            MucRoom room;
            if (roomOwner == null) {
                roomOwner = "noowner";
            }
            room = new MucRoom(roomName, roomType, roomOwner, msg, this.subdomain, openMcastGroup);
            if (advertise) { // If the room is being created from an ad, we don't need to re-advertise
                room.advertise();
            }
            logger.fine("registering room " + roomName + " to listen for room adverts");
            XopNet.getSDObject().addDiscoverableMembershipListener(room);
            return room;
        }
    }

    // TODO: Never used
    public void destroyRoom(String roomName) {
        MucRoom room = rooms.remove(roomName);
        room.close();
    }

    private void processPresencePacket(Presence p) {
        logger.fine("Processing presence packet");
        MucRoom room = createRoom(p.getTo().getNode(), new RoomType(), p.getTo().getResource(), "hello world", true, true);
        rooms.put(room.getRoomName(), room);
        if (p.getFrom().getResource() == null) { // This is a user joining a room
            registerMucAlias(p.getTo(), p.getFrom());
        }
        room.processPresencePacket(p, clientManager.isLocalClient(p.getFrom().toBareJID()));
    }

    private void processMessagePacket(Message m) {
        logger.fine("Processing message packet");
        String roomName = m.getTo().getNode();
        String nick = m.getTo().getResource();
        addMucUserInfo(m);

        if (roomExists(roomName)) {
            logger.fine("Room " + roomName + " found, send message to room!");
            getRoom(roomName).processMessagePacket(m);
        } else {
            logger.severe("User '" + nick + "' tried to send a message to a room '" + roomName
                + "' they aren't a member of");
        }
    }

    private void processIqPacket(IQ iq) {
        IQ response;
        String roomName = iq.getTo().getNode();
        if (roomName != null) { // There is a node in the "to" field
            if (roomExists(roomName)) {
                response = ProxyDiscoIqHandler.getComponentInfo(iq, getRoom(roomName));
            } else {
                response = IqManager.getErrorForIq(iq, Condition.item_not_found, Type.cancel);
            }
            ClientProxy.getInstance().processPacket(response);
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.iq.disco.ServerDiscoProvider#getServerItem()
     */
    @Override
    public DiscoItem getServerItem() {
        if (discoServerItem == null) {
            discoServerItem = new DiscoItem(new JID(this.subdomain + "." + this.domain), "XOP MUC Service", null, null);
        }
        return discoServerItem;
    }

    public String toString() {
        String rval = "This proxy (\"" + this.domain + "\") knows about the following rooms: [";
        String delim = "";
        for (MucRoom mr : getRooms()) {
            rval += delim + mr.getRoomName();
            delim = ", ";
        }
        return rval + "]\n";
    }

    public JID getMucAliasToFullJid(JID mucAlias) {
        if (!mucAliasToFullJid.containsKey(mucAlias)) {
            logger.fine("No MUC alias found for: " + mucAlias);
            return mucAlias;
        }
        return mucAliasToFullJid.get(mucAlias);
    }

    public void registerMucAlias(JID roomNick, JID fullJid) {
        mucAliasToFullJid.put(roomNick, fullJid);
    }

    /**
     * Adds the x MUC user-namespace element
     * 
     * @param m
     */
    private void addMucUserInfo(Message m) {
        Element e = m.getChildElement("x", MucProperties.MUC_USER_NAMESPACE);
        if (e == null) {
            e = m.addChildElement("x", MucProperties.MUC_USER_NAMESPACE);
            e.addElement("item").addAttribute("affiliation", "member").addAttribute("role", "participant").addAttribute("jid", m.getFrom().toString());
        }
    }

    // **********[ OVERRIDES ] ***********************************
    // ----------[ DiscoverableGroupListener Overrides ]----------

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.api.DiscoverableGroupListener#groupAdded(edu.drexel.xop.net.discovery.GroupDiscoverableObject)
     */
    @Override
    public void groupAdded(GroupDiscoverableObject gdo) {
        logger.fine("Discovered new Room: " + gdo);
        MucRoom room = createRoom(gdo.getRoomName(), new RoomType(gdo.getRoomType()), gdo.getOwner(), "hello world", true, false);
        rooms.put(room.getRoomName(), room);
        // Let's see if this room can claim any orphaned MDOs
        for (MembershipDiscoverableObject mdo : orphanMDOs) {
            if (mdo.getRoomName().equals(room.getRoomName())) {
                // Success! An orphaned MDO now has a home
                logger.fine("Sending previously orphaned MDO to room: " + room.getRoomName());
                orphanMDOs.remove(mdo);
                room.membershipAdded(mdo);
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.api.DiscoverableGroupListener#groupRemoved(edu.drexel.xop.net.discovery.GroupDiscoverableObject)
     */
    @Override
    public void groupRemoved(GroupDiscoverableObject groupDiscoverableObject) {
        // TODO: Doesn't do anything
    }

    // ----------[ DiscoverableMembershipListener Overrides ]----------

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.api.DiscoverableMembershipListener#membershipAdded(edu.drexel.xop.net.discovery.MembershipDiscoverableObject)
     */
    @Override
    public void membershipAdded(MembershipDiscoverableObject mdo) {
        if (!roomExists(mdo.getRoomName())) {
            // We got a MDO but the room doesn't exist yet!
            logger.fine("Found orphaned MDO, adding to queue");
            orphanMDOs.add(mdo);
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.api.DiscoverableMembershipListener#membershipRemoved(edu.drexel.xop.net.discovery.MembershipDiscoverableObject)
     */
    @Override
    public void membershipRemoved(MembershipDiscoverableObject mdo) {
        // Do nothing, if we have the room, it will take care of this.
    }

    // ----------[ DiscoProvider Overrides ]----------

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.iq.disco.DiscoProvider#getFeatures()
     */
    @Override
    public Set<String> getFeatures() {
        return Collections.unmodifiableSet(features);
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.iq.disco.DiscoProvider#getIdentities()
     */
    @Override
    public Set<DiscoIdentity> getIdentities() {
        return Collections.unmodifiableSet(identities);
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.iq.disco.DiscoProvider#getItems()
     */
    @Override
    public Set<DiscoItem> getItems() {
        Set<DiscoItem> roomInfo = new HashSet<>();
        for (MucRoom room : getRooms()) {
            roomInfo.add(new DiscoItem(room.getRoomJid(), room.getRoomName(), null, null));
        }
        return roomInfo;
    }

    // ----------[ PacketListener Overrides ]----------

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.component.VirtualComponent#processPacket(org.xmpp.packet.Packet)
     */
    @Override
    public void processPacket(Packet p) {
        // Process packets from the PacketRouter
        logger.fine("processing packet from PacketRouter. to:" + p.getTo() + " from: " + p.getFrom());
        try {
            if (p instanceof Presence) {
                processPresencePacket((Presence) p);
            } else if (p instanceof Message) {
                processMessagePacket((Message) p);
            } else if (p instanceof IQ) {
                processIqPacket((IQ) p);
            } else {
                throw new XOPException("Invalid packet: " + p.toString());
            }
        } catch (Exception e) {
            logger.severe("Error in ProcessPacket " + p.toString());
            e.printStackTrace();
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.core.PacketFilter#accept(org.xmpp.packet.Packet)
     */
    @Override
    public boolean accept(Packet p) {
        try {
            if (p.getTo() != null) { // check to make sure it is to this domain, and that it does not contain the multiple gateway subdomain
                String gatewaySubdomain = XopProperties.getInstance().getProperty(XopProperties.GATEWAY_SUBDOMAIN);
                if (p.getTo().getDomain().equals(subdomain + "." + domain)
                    && !p.getTo().getDomain().contains(gatewaySubdomain)) {
                    logger.fine("Accepted a packet");
                    logger.finer("packet: ========" + p.toString() + "\n=========");
                    return true;
                } else {
                    logger.finer("Rejected packet (this.domain=" + domain + ", p.getTo().getDomain()="
                        + p.getTo().getDomain());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.component.ComponentBase#processCloseStream(java.lang.String)
     */
    @Override
    public void processCloseStream(String fromJID) {
        for (DiscoIdentity di : identities) {
            logger.fine("Ignoring Disco Identity: " + di.toString());
        }

        logger.info("Removing user: " + fromJID);
        // need to delete all references to this user
        Set<MucRoom> rooms = getRooms();
        for (MucRoom room : rooms) {
            logger.fine("Removing " + fromJID + " from " + room.toString());
            room.removeOccupantByJID(fromJID);
        }
    }
}