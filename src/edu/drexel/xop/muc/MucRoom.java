package edu.drexel.xop.muc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import mil.navy.nrl.protosd.api.distobejcts.SDObject;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import mil.navy.nrl.protosd.api.exception.ServiceLifeCycleException;

import org.dom4j.Element;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.PacketError.Condition;
import org.xmpp.packet.PacketError.Type;
import org.xmpp.packet.Presence;

import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.iq.disco.DiscoIdentity;
import edu.drexel.xop.iq.disco.DiscoItem;
import edu.drexel.xop.iq.disco.DiscoProvider;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.api.DiscoverableMembershipListener;
import edu.drexel.xop.net.discovery.Affiliation;
import edu.drexel.xop.net.discovery.GroupDiscoverableObject;
import edu.drexel.xop.net.discovery.MembershipDiscoverableObject;
import edu.drexel.xop.net.discovery.OccupantStatus;
import edu.drexel.xop.net.discovery.Role;
import edu.drexel.xop.net.discovery.RoomType;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Muc Room
 * 
 * @author Duc Nguyen
 * @author David Millar
 */
public class MucRoom implements DiscoProvider, DiscoverableMembershipListener {

    private static final Logger logger = LogUtils.getLogger(MucRoom.class.getName());

    private String domain;
    private String subdomain;
    private String roomDomain;
    private String roomName;
    private String roomSubject;
    private String mucPrefix;
    private String roomDescription = "XO MUC Room";
    private Date creationDate;
    private Date modificationDate;
    private JID roomJid;

    private GroupDiscoverableObject mucRoomDiscoverableObject;
    private boolean firstUser = true;

    private XopNet xopNetwork;
    private String roomMcastAddrString;
    private InetAddress roomGroupAddr;
    private int roomPort;

    private Set<String> features = new HashSet<>();
    private Set<DiscoIdentity> identities = new HashSet<>();
    private ClientProxy proxy = ClientProxy.getInstance();

    private final Map<String, MucOccupant> occupantsByNick = new ConcurrentHashMap<String, MucOccupant>();
    private final Map<String, MucOccupant> occupantsByJID = new ConcurrentHashMap<String, MucOccupant>();

    /**
     * constructor: creates a room and a multicast address for this room
     * @param roomName
     * @param roomType
     * @param roomOwner
     * @param roomMsg
     * @param subdomain
     * @param openMCastGroup
     */
    public MucRoom(String roomName, RoomType roomType, String roomOwner, String roomMsg, String subdomain, boolean openMCastGroup) {
        logger.fine("constructing a MUC room with name: " + roomName + " roomOwner: " + roomOwner + " openMCastGroup:"
            + openMCastGroup);
        this.creationDate = new Date();
        this.roomName = roomName;
        this.subdomain = subdomain;
        this.domain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN); // e.g. proxy
        this.mucPrefix = XopProperties.getInstance().getProperty(XopProperties.S2S_PREFIX); // e.g. conference

        // Disco Features
        features.add(MucProperties.MUC_NAMESPACE);
        features.add("muc_public");
        features.add("muc_open");
        features.add("muc_unmoderated");
        features.add("muc_nonanonymous");
        features.add("muc_unsecured");
        features.add("http://jabber.org/protocol/disco#info");

        // Disco Identities
        identities.add(new DiscoIdentity("conference", "text", roomName));

        roomDomain = this.subdomain + "." + this.domain;
        roomJid = new JID(this.roomName + "@" + this.roomDomain);
        roomSubject = roomMsg;
        try {
            mucRoomDiscoverableObject = new GroupDiscoverableObject(XopNet.getSDObject(), roomName, roomMsg, roomType, roomOwner);
            roomMcastAddrString = mucRoomDiscoverableObject.getMulticastAddress();
            roomPort = mucRoomDiscoverableObject.getPort();
        } catch (IOException | InitializationException | ServiceInfoException e1) {
            e1.printStackTrace();
        }
        try {
            roomGroupAddr = InetAddress.getByName(roomMcastAddrString);
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Subscribe to messages from the room via the TransportationEngine
        xopNetwork = XopNet.getInstance();
        xopNetwork.subscribe(this.roomName);
    }

    /**
     * Processes Presence Packets from locally connected clients. and remote clients
     * 
     * @param isLocal
     */
    public void processPresencePacket(Presence p, boolean isLocal) {
        String toNick = p.getTo().getResource();
        if (toNick == null) {
            String fromNick = (p.getFrom() != null) ? p.getFrom().getResource() : null;
            if (fromNick == null) {
                logger.warning("Error: No nickname specified. Sending error packet. " + p);
                sendError(PacketError.Condition.jid_malformed, PacketError.Type.modify);
            }
        } else if (occupantsByNick.containsKey(toNick)) {
            // this Presence packet is directed to a specific MUC Occupant
            MucOccupant oc = occupantsByNick.get(toNick);
            if (p.getType() != null && p.getType().equals(Presence.Type.unavailable)) {
                logger.fine("Recieved unavailable packet");
                leaveRoom(oc, p);
            } else if (oc.getJid().equals(p.getFrom())) {
                logger.fine("Error: Nickname Conflict. Sending error packet." + p);
                sendError(PacketError.Condition.conflict, PacketError.Type.cancel);
            } else if (p.getType() != null && p.getType().equals(Presence.Type.error)) {
                logger.fine("Recieved error packet");
                handleError(oc, p);
            } else if (!occupantsByNick.containsKey(p.getFrom().getResource())) {
                logger.fine("Sending message directly to local user");
                setMucAttributes(p);
                p.setFrom(new JID(roomName, roomDomain, p.getFrom().getResource()));
                sendToLocalUser(p, oc);
            } else if (!p.equals(oc.getPresence().getPresence())) {
                // this is a new Presence Message for this occupant
                MucRoomPresence newMucRoomPresence = new MucRoomPresence(p);
                logger.fine("Setting a new presence message for occupant: " + oc.getJid() + " with presence: "
                    + newMucRoomPresence);
                oc.setPresence(newMucRoomPresence);
            } else {
                logger.fine("Ignored presence:" + p);
            }
        } else {
            logger.info("User:" + toNick + " entering room: " + roomName);
            joinRoom(toNick, p, isLocal);
        }
    }

    /**
     * Processes a message packet. Here is where we rewrite locally sent
     * messages
     * 
     * @param m
     */
    public void processMessagePacket(Message m) {
        logger.fine("Got message, broadcasting to room occupants: " + m.toString());
        String fromJidStr = m.getFrom().toString();
        MucOccupant occupantFrom = occupantsByJID.get(fromJidStr);
        if (occupantFrom != null && occupantFrom.isLocal()) {
            logger.fine("Occupant '" + occupantFrom.getResource() + "' found, rewriting & sending to net...");
            m.setFrom(new JID(roomName, roomDomain, occupantFrom.getResource()));
            logger.fine("Sending message to network!!! " + m.toXML());
            // Send the message to the network via the Transport Engine
            xopNetwork.sendOutgoingMessage(m);
        } else if (roomName.equals(m.getFrom().getNode()) && roomDomain.equals(m.getFrom().getDomain())) {
            // The message is for this room and the from field is not local (msg is from another XOP instance).
            // The message is also for this domain (e.g. proxy)
            sendToLocalUsers(m);
        } else if (roomName.equals(m.getFrom().getNode()) && m.getFrom().getDomain().startsWith(mucPrefix)) {
            // the message is from the room and the
            String resource = m.getFrom().getResource();
            if (resource == null) {
                m.setFrom(new JID(roomName, roomDomain, ""));
                sendToLocalUsers(m);
            } else if (!occupantsByNick.containsKey(resource)) {
                m.setFrom(new JID(roomName, roomDomain, m.getFrom().getResource()));
                sendToLocalUser(m, occupantsByNick.get(m.getTo().getResource()));
            }
        } else {
            logger.fine("User {" + m.getFrom() + "} tried to send a message to a room, " + m.getTo().getResource()
                + ", they are not a member of\n" + m);
            sendError(PacketError.Condition.not_acceptable, PacketError.Type.cancel);
        }
    }

    public MucOccupant joinRoom(String nickName, Presence presence, boolean isLocal) {
        logger.info("User:{" + nickName + "} is trying to join room: {" + roomName + "}" + presence);
        MucOccupant occupant = new MucOccupant(presence.getFrom(), isLocal);
        occupant.setPresence(new MucRoomPresence(presence));
        occupant.setResource(nickName);
        occupant.setJid(presence.getFrom());

        logger.fine("Sending joining users presence to other users.");
        sendToLocalUsers(occupant.getPresence().getPresence().createCopy());
        if (occupant.isLocal()) {
            logger.fine("Sending occupants presence to joining user");
            sendExistingPresences(presence.getFrom());
        }
        // Adding to our data store
        occupantsByNick.put(nickName, occupant);
        if (presence.getFrom() != null) {
            occupantsByJID.put(presence.getFrom().toString(), occupant);
        } else {
            logger.fine("there is no From field for this presence message! Not putting occupant into occupantsByJID datastructure.");
        }

        // Send joining user's presence to the client
        MucRoomPresence mrp = occupant.getPresence();
        if (occupant.isLocal() || !occupant.getJid().getDomain().endsWith(domain)) {
            logger.fine("Sending users presence to himself");
            proxy.processPacket(mrp.getJoinPresence(firstUser));
        }
        firstUser = false;

        if (occupant.isLocal()) {
            logger.fine("Occupant is local, advertising MDO");
            try {
                SDObject sdObj = XopNet.getSDObject();
                JID mucOccupantJid = new JID(roomName, roomDomain, nickName);
                JID occupantJid = occupant.getJid();
                MembershipDiscoverableObject memberPresenceSDObj = 
                        new MembershipDiscoverableObject(sdObj, occupantJid.toString(), mucOccupantJid.toFullJID(), nickName,
                                roomName, Affiliation.OWNER, Role.PARTICPANT, 
                                OccupantStatus.AVAILABLE);
                logger.fine("advertising member: " + memberPresenceSDObj);
                occupant.setMembershipDiscoverableObject(memberPresenceSDObj);
                sdObj.advertise(memberPresenceSDObj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return occupant;
    }

    /**
     * Tries to remove the occupant in two different ways based on a String JID.
     * Sends unavailable presence message to locally connected clients
     * TODO: this method and leaveRoom(MucOccupant, Presence) are essentially performing the same task, except leaveRoom is private and only used by close() and processPresencePacket() They should merge functionalities
     * 
     * @param fromJIDStr
     */
    public void removeOccupantByJID(String fromJIDStr) {
        logger.fine("Attempting to remove occupant: " + fromJIDStr);
        MucOccupant occupantToRemove = occupantsByJID.get(fromJIDStr);
        if (occupantToRemove == null) {
            String nick = fromJIDStr.substring(0, fromJIDStr.indexOf("@"));
            logger.fine("occupant by Jid not found, removing by nick: " + nick);
            occupantToRemove = occupantsByNick.get(nick);
        }

        if (removeOccupant(occupantToRemove)) {
            sendToLocalUsers(occupantToRemove.getPresence().getExitPresence(false));

            if (occupantToRemove.isLocal()) {
                logger.fine("Removing advertisement for local user " + occupantToRemove.getJid());
                SDObject sdObject = XopNet.getSDObject();
                try {
                    MembershipDiscoverableObject membershipObj = occupantToRemove.getMembershipDiscoverableObject();
                    sdObject.remove(membershipObj);
                    // membershipObj.updateStatus(OccupantStatus.UNAVAILABLE);
                    // sdObject.advertise(membershipObj);
                } catch (ServiceLifeCycleException | ServiceInfoException e) {
                    e.printStackTrace();
                }
            }
            logger.fine("Successfully removed user: " + fromJIDStr);
        } else {
            logger.warning("Unable to fully remove user: " + fromJIDStr);
        }
    }

    /**
     * Close this room, and remove all occupants
     */
    public void close() {
        for (MucOccupant occupant : getOccupants()) {
            Presence p = occupant.getOutgoingPresence().createCopy();
            p.setType(Presence.Type.unavailable);
            leaveRoom(occupant, p);
        }
    }

    /**
     * Takes a message and forms it for any locally connected clients,
     * then sends it into the PacketRouter for processing
     * 
     * @param p
     */
    private void sendToLocalUsers(Packet p) {
        logger.fine("Sending packet to locally connected clients");
        for (MucOccupant user : occupantsByNick.values()) {
            if (user.isLocal()) {
                sendToLocalUser(p.createCopy(), user);
            } else {
                logger.fine("Not routing to non-local user: " + user.getJid());
            }
        }
    }

    /**
     * Takes a message and forms it for any locally connected clients,
     * then sends it into the PacketRouter for processing
     * 
     * @param p
     * @param oc
     */
    private void sendToLocalUser(Packet p, MucOccupant oc) {
        logger.fine("Routing to: " + oc.getJid());
        p.setTo(oc.getJid());
        proxy.processPacket(p);
    }

    /**
     * A bit of a hack -- this makes sure that a packet has the appropriate
     * MUC attributes and elements. This also happens in MucRoomPresence if
     * you use that, but it also does wacky stuff like switch the to and from
     * fields, so we have this here instead.
     * 
     * @param p
     */
    private void setMucAttributes(Presence p) {
        logger.finest("Setting MUC Presence attributes");
        Element e = p.getChildElement("x", MucProperties.MUC_NAMESPACE);
        if (e == null) {
            e = p.addChildElement("x", MucProperties.MUC_NAMESPACE);
        }
        e = p.getChildElement("x", MucProperties.MUC_USER_NAMESPACE);
        if (e == null) {
            e = p.addChildElement("x", MucProperties.MUC_USER_NAMESPACE);
            e.addElement("item");
        }
        e.element("item").addAttribute("affiliation", "member").addAttribute("role", "participant").addAttribute("jid", p.getFrom().toString());
    }

    /**
     * Sends the presence messages of existing occupants to a newly joined
     * occupant
     * 
     * @param joiner
     */
    private void sendExistingPresences(JID joiner) {
        logger.fine("Sending presences to newly joined occupant: " + joiner);
        for (MucOccupant occupant : occupantsByNick.values()) {
            logger.finer("Presence broadcast to: " + occupant.getJid());
            Presence p = occupant.getOutgoingPresence().createCopy();
            p.setTo(joiner);
            proxy.processPacket(p);
            logger.finer("Sent presence:" + p);
        }
    }

    /**
     * Handles received error messages
     * 
     * @param oc
     * @param p
     */
    private void handleError(MucOccupant oc, Presence p) {
        logger.fine("Handling error presence");
        PacketError error = p.getError();
        if (error != null) {
            if (error.getType() == PacketError.Type.cancel) {
                if (error.getCondition() == PacketError.Condition.not_acceptable) {
                    logger.fine("Resending the occupants latest presence");
                    proxy.processPacket(oc.getOutgoingPresence());
                } else if (error.getCondition() == PacketError.Condition.conflict) {
                    logger.warning("The resource requested, '" + oc.getResource() + "', has already been taken");
                }
            } else {
                logger.warning("Unhandled error presence: " + p);
            }
        } else {
            logger.fine("Got presence of type error without PacketError!");
        }
    }

    /**
     * Sends various Error messages to clients
     * 
     * @param type
     * @param condition
     */
    private void sendError(Condition condition, Type type) {
        Presence error_packet = new Presence(Presence.Type.error);
        error_packet.setError(new PacketError(condition, type));
        proxy.processPacket(error_packet);
    }

    /**
     * Attempts to completely remove the occupant, returns if successful
     * 
     * @param occupant
     * @return
     */
    private boolean removeOccupant(MucOccupant occupant) {
        if (occupant == null) {
            logger.fine("Cannot remove null occupant");
            return false;
        }
        if (occupantsByNick == null) {
            logger.severe("Somehow, occupants is null!  This should not happen!");
            return false;
        }
        logger.fine("occupant: " + occupant);
        if (occupant.isLocal() && occupantsByNick.remove(occupant.getJid().getNode()) == null) {
            logger.severe("Occupant was not in resource table: " + occupant.getJid().getNode());
            return false;
        }

        if (!occupant.isLocal() && occupantsByNick.remove(occupant.getResource()) == null) {
            logger.severe("occupant with resource: " + occupant.getResource() + " not in table");
            return false;
        }

        if (occupantsByJID.remove(occupant.getJid().toString()) == null) {
            logger.severe("Occupant was not in JID table: " + occupant.getJid());
            return false;
        }
        logger.fine("Removed " + occupant.getJid() + " from occupants.");
        return true;
    }

    /**
     * When occupant leaves the room, send unavailable presence to all locally
     * connected clients and then remove the membership from the SD system
     * 
     * TODO: this method and removeOccupantByJID(String) are essentially performing the same task, except leaveRoom is private and only used by close() and processPresencePacket() They should merge functionalities
     * 
     * @param occupant
     * @param presence
     */
    private void leaveRoom(MucOccupant occupant, Presence presence) {
        if (occupant.isLocal()) {
            removeOccupant(occupant);// remove the occupant from the hashmaps

            proxy.processPacket(occupant.getPresence().getExitPresence(true));
            // dnguyen: Why are we doing this if we're also calling processPacket above it?
            // sendToLocalUsers(occupant.getPresence().getExitPresence(true));
            // Presence p = occupant.getPresence().getExitPresence(false);
            // JID from = p.getFrom();
            // p.setFrom(occupant.getJid());
            // p.setTo(from);

            logger.fine("Occupant is local, removing MDO advertisement");
            SDObject sdObject = XopNet.getSDObject();
            try {
                MembershipDiscoverableObject membershipObj = occupant.getMembershipDiscoverableObject();
                sdObject.remove(membershipObj);
                // membershipObj.updateStatus(OccupantStatus.UNAVAILABLE);
                // sdObject.advertise(membershipObj);
            } catch (ServiceLifeCycleException | ServiceInfoException e) {
                e.printStackTrace();
            }
        }
    }

    // **********[ OVERRIDES ] ***********************************
    // ----------[ DiscoverableGroupListener Overrides ]----------
    @Override
    public Set<String> getFeatures() {
        return Collections.unmodifiableSet(features);
    }

    @Override
    public Set<DiscoIdentity> getIdentities() {
        return Collections.unmodifiableSet(identities);
    }

    @Override
    public Set<DiscoItem> getItems() {
        Set<DiscoItem> roomInfo = new HashSet<>();
        for (MucOccupant occupant : occupantsByNick.values()) {
            roomInfo.add(new DiscoItem(occupant.getJid(), null, null, null));
        }
        return roomInfo;
    }

    // ----------[ Other Overrides ]----------
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("\n----------[ ROOM - " + roomName + " ]----------\n");
        for (MucOccupant o : getOccupants()) {
            sb.append("__________________________\n");
            sb.append(o.toString()).append("\n");
        }
        return sb.toString();
    }

    // **********[ UNUSED/UNIMPLEMENTED/HELPERS ] **************************

    public Date getCreationDate() {
        return (Date) creationDate.clone();
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = (Date) creationDate.clone();
    }

    public Set<MucOccupant> getOccupants() {
        return new HashSet<>(occupantsByNick.values());
    }

    public JID getRoomJid() {
        return roomJid;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getSubject() {
        return roomSubject;
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate() {
        this.modificationDate = new Date();
    }

    public String getDescription() {
        return this.roomDescription;
    }

    public void setDescription(String description) {
        // TODO: Handle description change
        throw new UnsupportedOperationException("Not supported: Changing room description");
    }

    public void nickNameChanged(MucOccupant occupant, Presence newPresence, String oldNick, String newNick) {
        // TODO: Handle nickname change
        throw new UnsupportedOperationException("Not supported: Changing resource/nick name");
    }

    public void changeSubject(Message packet, MucOccupant occupant) {
        // TODO: Advertise a change in the subject and change the subject, using SD system
        throw new UnsupportedOperationException("Not supported: Changing the room subject");
    }

    /**
     * Advertise this room via the SD system.
     */
    public void advertise() {
        logger.fine("Advertising the MUC room: " + mucRoomDiscoverableObject);
        try {
            XopNet.getSDObject().advertise(mucRoomDiscoverableObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ----------[ DiscoverableMembershipListener Overrides ]----------

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.api.DiscoverableMembershipListener#membershipAdded(edu.drexel.xop.net.discovery.MembershipDiscoverableObject)
     */
    @Override
    public void membershipAdded(MembershipDiscoverableObject membershipDiscoverableObject) {
        logger.fine("Discovered new membershipDiscoverableObj: " + membershipDiscoverableObject);
        String roomName = membershipDiscoverableObject.getRoomName();
        membershipDiscoverableObject.getAffiliation();
        String mucOccupantJid = membershipDiscoverableObject.getMUCOccupantJid();
        String userJid = membershipDiscoverableObject.getUserJid();
        String role = membershipDiscoverableObject.getRole().name();
        String user = membershipDiscoverableObject.getServiceInfo().getServiceName();

        String nick = (new JID(mucOccupantJid)).getResource(); // gets the nick

        if (!occupantsByJID.containsKey(userJid) && !occupantsByNick.containsKey(nick)) {
            logger.fine("Remotely discovered client");
            Presence presence = new Presence();
            // String fromRoom = room.getRoomJid().toBareJID();
            JID memberJid = new JID(mucOccupantJid);
            presence.setTo(memberJid);
            presence.setFrom(user);
            presence.addChildElement("x", MucProperties.MUC_NAMESPACE);
            logger.fine("Created join room presence for discovered MUC occupant");
            logger.fine("Presence message to be processed" + presence);

            proxy.processPacket(presence);// this will send the presence packet through the PacketRouter and hence direct it out the network (if necessary), over the gateway (if necessary), and send to locally connected clients (if necessary).
        } else {
            logger.fine(userJid + " already in room: " + roomName);
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.api.DiscoverableMembershipListener#membershipRemoved(edu.drexel.xop.net.discovery.MembershipDiscoverableObject)
     */
    @Override
    public void membershipRemoved(MembershipDiscoverableObject membershipDiscoverableObject) {
        logger.fine("MUC occupant leaving room. member disc obj:" + membershipDiscoverableObject);
        String roomName = membershipDiscoverableObject.getRoomName();
        String mucOccupantJid = membershipDiscoverableObject.getMUCOccupantJid();
        String userJid = membershipDiscoverableObject.getUserJid();

        if (this.occupantsByJID.containsKey(userJid)) {
            // String nick = (new JID(mucOccupantJid)).getResource();
            this.removeOccupantByJID(userJid);
        } else {
            logger.fine(userJid + " not in room: " + roomName);
        }
    }
}