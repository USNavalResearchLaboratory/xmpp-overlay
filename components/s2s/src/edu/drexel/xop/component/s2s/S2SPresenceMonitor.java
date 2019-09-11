package edu.drexel.xop.component.s2s;

import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.net.api.DiscoverableGroupListener;
import edu.drexel.xop.net.api.DiscoverableMembershipListener;
import edu.drexel.xop.net.api.DiscoverableUserListener;
import edu.drexel.xop.net.discovery.GroupDiscoverableObject;
import edu.drexel.xop.net.discovery.MembershipDiscoverableObject;
import edu.drexel.xop.net.discovery.UserDiscoverableObject;
import edu.drexel.xop.properties.XopProperties;
import org.dom4j.Element;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * XOGPresenceMonitor: 
 * 
 * @author duc
 *
 */
class S2SPresenceMonitor implements DiscoverableGroupListener, DiscoverableMembershipListener,
        DiscoverableUserListener {
    private static final Logger logger = Logger.getLogger(S2SPresenceMonitor.class.getName());
    
    /**
     * map of roomNames to a list of MucOccupants by full jid
     * e.g. [roomname : openfireuser@openfire, xoguser@proxy]
     */
    private Map<String, RoomOccupantStore> roomOccupantMap;
    private Set<String> manetClients;
    private Set<String> whitelistDomains;
    private static final String xopMUCDomain = XopProperties.getInstance().getProperty(XopProperties.MUC_SUBDOMAIN);
    private static final String s2sDomain = XopProperties.getInstance().getProperty(XopProperties.S2S_SERVER);
    private static final String s2sMUCDomain = "conference."+s2sDomain;
    private static final String MUC_USER_NAMESPACE = "http://jabber.org/protocol/muc#user";

    /**
     * default constructor
     */
    public S2SPresenceMonitor(Set<String> whitelistDomains){
        this.whitelistDomains = whitelistDomains;
        roomOccupantMap = Collections.synchronizedMap(new HashMap<String, RoomOccupantStore>());
        manetClients = Collections.synchronizedSet(new HashSet<String>());
    }
    
    /**
     * 
     * @param packet determine if XOG should modify the packet
     * @return
     */
    public boolean shouldModifyAndForwardPacket(Packet packet){
        boolean retVal = false;
        JID toJid = packet.getTo();
        if( toJid != null ){
            String domain = toJid.getDomain();
            if( domain.contains(xopMUCDomain)){
                String roomName = toJid.getNode();
                RoomOccupantStore roomOccupantStore = roomOccupantMap.get(roomName);
                logger.fine("toJid: "+toJid.toString()+" roomName: "+roomName+" ");
                if (roomOccupantStore == null || roomOccupantStore.isJidFirstItem(toJid.getResource())){
                    retVal = true;
                }
            } else { // this is a message to a specific client (probably a presence)
                logger.fine("not destined for a chatroom.");
                retVal = true;
            }
        }
        logger.fine("should modify and forward packet? "+retVal);
        return retVal;
    }

    /**
     * return true if this presence message is from the Enterprise side and is also
     * not a MUC room presence message.
     * @param packet
     * @return
     */
    public boolean isPresenceFromEnterpriseClient(Presence packet){
        boolean retVal = true;

        JID fromJid = packet.getFrom();
        String domain = fromJid.getDomain();

        if( whitelistDomains.contains(domain) ){
            if( domain.startsWith("conference") ){
                retVal = false;
            }
        }

        if( manetClients.contains(fromJid.toBareJID() ) )
            retVal = false;

        logger.fine("isPresenceFromEnterpriseClient: "+retVal);
        return retVal;
    }

    /**
     *
     * @param message
     * @return
     */
    public boolean isMessageFromEnterpriseMUCOccupant(Message message){
        boolean retVal = true;

        JID fromJid = message.getFrom();
        String domain = fromJid.getDomain();

        if( whitelistDomains.contains(domain) ){
            if( domain.startsWith("conference")  ){
                retVal = false;
            }
        }
        if( manetClients.contains(fromJid.toBareJID())){
            retVal = false;
        }
        logger.fine("isMessageFromEnterprise: "+retVal);
        return retVal;
    }

    /**
     *
     * @param packet from the Enterprise side
     * @return true if this is a MUC Room Presence and the from-Nickname is not from the MANET. False otherwise
     */
    public boolean isEnterpriseMUCOccupant(Packet packet){
        boolean retVal = false;
        if( packet.getTo() != null &&  packet.getTo().getDomain().contains(xopMUCDomain) ){
            String roomName = packet.getTo().getNode();
            RoomOccupantStore roomOccupantStore = roomOccupantMap.get(roomName);
            if( roomOccupantStore != null ) {
                Level logLevel = logger.getLevel();
                if( ( logLevel != null && logLevel.intValue() <= Level.FINE.intValue()) ){
                    logger.fine("checking if packet came from a client on the enterprise "+packet.toString());
                    String[] jids = roomOccupantStore.getJids();
                    logger.fine("roomOccupantStore size: "+jids.length);
                    for(String jid : jids){
                        logger.fine("jid: "+jid);
                    }
                    String[] nicks = roomOccupantStore.getNicks();
                    for(String nick : nicks){
                        logger.fine("nick: "+nick);
                    }
                }
                String fromNick = packet.getFrom().getResource();
                String fromDomain = packet.getFrom().getDomain();
                logger.fine("fromNick:"+fromNick+" roomOccupantStore.isManetClient "+roomOccupantStore.isManetClientNick(fromNick));
                // We make the assumption: If a nickname is not from the MANET then it is from the Enterprise.
                if( whitelistDomains.contains(fromDomain) && !roomOccupantStore.isManetClientNick(fromNick) ){
                    retVal = true;
                    roomOccupantStore.add(packet.getFrom().toString());
                } else {
                    retVal = false;
                }
            }
        } else
            logger.fine("not a MUC message");
        logger.fine("isEnterpriseMUCOccupant: "+retVal);
        return retVal;
    }

    @Override
    public void membershipAdded(MembershipDiscoverableObject membershipDiscoverableObject) {
        String mucJid = membershipDiscoverableObject.getMUCOccupantJid();
        String userJid = membershipDiscoverableObject.getUserJid();
        String roomName = membershipDiscoverableObject.getRoomName();

        JID tempJid = new JID(mucJid);
        String nick = tempJid.getResource();
        logger.fine("Adding discovered member: "+mucJid+" with nick: "+nick+" to room: "+roomName);
        RoomOccupantStore roomOccupantStore = roomOccupantMap.get(roomName);
        if ( roomOccupantStore == null ){
            logger.fine("Room not discovered yet, adding");
            roomOccupantStore = roomOccupantMap.put(roomName, new RoomOccupantStore());
        }
        roomOccupantStore.add(mucJid);
        if( !userJid.contains(s2sDomain)){
            roomOccupantStore.addManetClientNick(nick);
            logger.fine("discovered member is a manet client");
        } else
            logger.fine("discovered member is an enterprise client!");
        
    }

    @Override
    public void membershipRemoved(MembershipDiscoverableObject membershipDiscoverableObject) {
        String mucJid = membershipDiscoverableObject.getMUCOccupantJid();
        String userJid = membershipDiscoverableObject.getUserJid();
        String roomName = membershipDiscoverableObject.getRoomName();

        RoomOccupantStore roomOccupantStore = roomOccupantMap.get(roomName);
        logger.fine("removing "+mucJid+" from "+roomName);
        roomOccupantStore.remove(mucJid);

        logger.fine("sending presences to enterprise");
        ClientProxy proxy = ClientProxy.getInstance();
        // Also construct a presence messages for muc occupants on the enterprise
        Presence exitPresence = createExitPresence(userJid, roomName, mucJid);
        proxy.processPacket(exitPresence);
    }

    /**
     * Create a presence message indicating the MUCOccupant for the given mucJidStr is leaving rewrite the mucJid muc domain to the s2s server muc domain.
     *
     * This presence message is going to look like the presence sent from the client to XOP, except the MUC domain will be the domain of the enterprise server.
     *
     * @param userJid
     * @param roomName
     * @param mucJidStr
     * @return
     */
    private Presence createExitPresence(String userJid, String roomName, String mucJidStr){
        Presence retVal = new Presence();
        JID mucJID = new JID(mucJidStr);
        JID toEnterpriseJID = new JID( mucJID.getNode(), s2sMUCDomain, mucJID.getResource());
        JID manetFromJID = new JID(userJid);
        JID fromJID = new JID(roomName, s2sMUCDomain, manetFromJID.getResource());

        retVal.setFrom(mucJID);
        retVal.setTo(toEnterpriseJID);
        retVal.setType(Presence.Type.unavailable);
//        Element e = addMucAttributes(retVal);
//        e.element("item").attribute("role").setValue("none");

        logger.fine("Exit Presence: "+retVal);
        return retVal;
    }

    private Element addMucAttributes(Presence presence) {
        logger.finest("Getting MUC attributes (or setting)");
        Element e = presence.getChildElement("x", MUC_USER_NAMESPACE);
        if (e == null) {
            e = presence.addChildElement("x", MUC_USER_NAMESPACE);
            e.addElement("item").addAttribute("affiliation", "member").addAttribute("role", "participant").addAttribute("jid", presence.getTo().toString());
        }
        return e;
    }



    @Override
    public void groupAdded(GroupDiscoverableObject groupDiscoverableObject) {
        String roomName = groupDiscoverableObject.getRoomName();
        roomOccupantMap.put(roomName,new RoomOccupantStore());

        logger.fine("Added new MUC Room to track");
    }

    @Override
    public void groupRemoved(GroupDiscoverableObject groupDiscoverableObject) {
        String roomName = groupDiscoverableObject.getRoomName();
        roomOccupantMap.remove(roomName);
        
        logger.fine("Removed "+roomName+" from S2SPresenceMonitor");
        // TODO dnguyen 2013-06-10: implement removing room on the enterprise side?
    }

    @Override
    public void userAdded(UserDiscoverableObject udo){
        String jidStr = udo.getJid();
        if( !jidStr.contains(s2sDomain)){
            manetClients.add(jidStr);
            logger.fine("User Added as a manet client: "+jidStr);
        } else
            logger.fine("user is not a manet client: "+jidStr);
    }

    @Override
    public void userRemoved(UserDiscoverableObject udo){
        String jidStr = udo.getJid();
        manetClients.remove(jidStr);
        logger.fine("User removed: "+jidStr);
    }


    /**
     * 
     * @author duc
     * Description: data structure for storing nicknames and mucOccupantJids for MANET-based clients and
     * enterprise-based clients.
     */
    private class RoomOccupantStore{
        private Set<String> mucOccupantJids;
        private String firstJid;
        private Set<String> manetClientNicks;
        
        public RoomOccupantStore(){
            mucOccupantJids = Collections.synchronizedSet(new TreeSet<String>());
            manetClientNicks = Collections.synchronizedSet(new TreeSet<String>());
        }

        public void remove(String mucJid) {
            synchronized(mucOccupantJids){
                mucOccupantJids.remove(mucJid);
                if( mucJid.equals(firstJid) ){
                    Iterator<String> en = mucOccupantJids.iterator();
                    String first = en.next();
                    firstJid = first;
                }
            }
            
            JID tempJid = new JID(mucJid);
            String nick = tempJid.getResource();
            
            synchronized(manetClientNicks){
                if( !"".equals(nick) ){
                    manetClientNicks.remove(nick);
                }
            }
        }

        public boolean isJidFirstItem(String mucJid) {
            if( firstJid != null ){
                logger.fine("firstJid: "+firstJid+" mucJid: "+mucJid);
                return mucJid.equals(firstJid);
            } else {
                logger.fine("first item: "+mucJid);
                mucOccupantJids.add(mucJid);
                firstJid = mucJid;
                return true;
            }
        }

        /**
         * 
         * @param nick
         * @return true if this client is from the manet, false if it's from the enterprise.
         */
        public boolean isManetClientNick(String nick){
            return manetClientNicks.contains(nick);
        }

        public void add(String mucJid) {
            mucOccupantJids.add(mucJid);

        }
        
        public void addManetClientNick(String nick){
            manetClientNicks.add(nick);
            if( firstJid == null ){
                firstJid = nick;
            }
        }
        
        public String[] getJids(){
            return mucOccupantJids.toArray(new String[0]);
        }

        public String[] getNicks(){
            return manetClientNicks.toArray(new String[0]);
        }
    }
}
