/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

import edu.drexel.xop.component.ComponentBase;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.discovery.MembershipDiscoverableObject;
import edu.drexel.xop.net.discovery.OccupantStatus;
import edu.drexel.xop.net.discovery.SDXOPObject;
import edu.drexel.xop.net.discovery.UserDiscoverableObject;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.protosd.api.distobejcts.SDObject;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import mil.navy.nrl.protosd.api.exception.ServiceLifeCycleException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author dnguyen
 * @author tradams
 * 
 * The Component to send messages to a foreign server
 */
public class S2SComponent extends ComponentBase implements PacketRoutingDevice {

    private static final Logger logger = LogUtils.getLogger(S2SComponent.class.getName());
    private TCPServerAcceptor tsa;
    public static final String DELIMITER = ",";
    Buffered outgoing;
    StreamManager manager;
    private final Set<String> whitelistDomains;

    private static final String xopDomain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);
    private static final String xopMUCDomain = XopProperties.getInstance().getProperty(XopProperties.MUC_SUBDOMAIN);
    private static final String xopMucFullDomain = xopMUCDomain +"."+xopDomain;

    private S2SPresenceMonitor presenceMonitor;

    private final Map<String,MembershipDiscoverableObject> enterpriseMDOs;
    private final Map<String,UserDiscoverableObject> enterpriseClients;


    public S2SComponent() {
        tsa = new TCPServerAcceptor(this);
        manager = new StreamManager(this);
        outgoing = new Buffered(manager);

        whitelistDomains = initWhitelist(XopProperties.getInstance().getProperty(XopProperties.S2S_WHITELIST));
        tsa.start();
        
        presenceMonitor = new S2SPresenceMonitor(whitelistDomains);
        enterpriseMDOs = new ConcurrentHashMap<>();
        enterpriseClients = new ConcurrentHashMap<>();
        SDXOPObject sdObject = XopNet.getSDObject();
        sdObject.addDiscoverableGroupListener(presenceMonitor);
        sdObject.addDiscoverableMembershipListener(presenceMonitor);
        sdObject.addDiscoverableUserListener(presenceMonitor);
    }

    private Set<String> initWhitelist(String list) {
        Set<String> whitelistDomains = Collections.synchronizedSet(new HashSet<String>());
        if (list != null) {
            String[] temp = list.split(DELIMITER);
            whitelistDomains.clear();
            whitelistDomains.addAll(Arrays.asList(temp));
        }
        return whitelistDomains;
    }

    /**
     * Stop the TCPServerAcceptor
     */
    public void stop() {
        tsa.close();
    }

    public void routePacket(Packet p) {
        logger.fine("Routing Packet from ServerConnection (incoming packet): " + p);
        if ( (whitelistDomains.contains(p.getTo().getDomain()) || p.getTo().getDomain().endsWith(xopDomain)) ){
            // only forwards MUC presence and MUC messages after rewriting

            if( p instanceof Message && presenceMonitor.shouldModifyAndForwardPacket(p)
                    && presenceMonitor.isEnterpriseMUCOccupant(p) ){
                JID newTo = new JID( p.getTo().getNode(), p.getTo().getDomain(),null );
                JID newFrom = new JID( p.getFrom().getNode(),xopMucFullDomain,p.getFrom().getResource() );
                p.setTo(newTo);
                p.setFrom(newFrom);
                logger.fine("modified Message with new to: "+newTo.toString()+". sending to network");
                XopNet.getInstance().sendOutgoingMessage((Message)p);
            } else if (p instanceof Presence){
                Presence presence = (Presence) p;
                SDObject sdObject = XopNet.getSDObject();
                // S2S advertise this client if he's from the enterprise side
                // Ignore presence messages that were generated in response to
                // presence messages from the MANET side.
                if( presenceMonitor.isEnterpriseMUCOccupant(presence)
                        && presenceMonitor.shouldModifyAndForwardPacket(p) ){
                    // NOTE: this will still advertise that the enterprise muc occupant is in
                    // this room.
                    String mucdomain = p.getFrom().getDomain(); // the second part after the '.' eg. conference.n4gw returns n4gw
                    String[] splitdomain = mucdomain.split("\\.");
                    String domain;
                    if(splitdomain.length >1)
                        domain = splitdomain[1];
                    else
                        domain = splitdomain[0];
                    String nickName = p.getFrom().getResource();
                    String roomName = p.getTo().getNode();
                    JID fromMucOccupantJid = new JID(roomName,xopMucFullDomain,nickName);
                    String userJid =  nickName+"@"+domain;
                    String status = ((Presence)p).getStatus();
                    Presence.Type type = presence.getType();
                    if( type != null) {
                        status = type.toString();
                    }
                    OccupantStatus occStatus = OccupantStatus.AVAILABLE;
                    if( status != null ){
                        occStatus = OccupantStatus.valueOf(status.toUpperCase());
                    }

                    logger.fine("domain: "+domain+"; nickName: "+nickName+"; userJid: "+
                            userJid+"; roomName: "+roomName+"; occStatus: "+occStatus);
                    if( occStatus == OccupantStatus.AVAILABLE){

                        MembershipDiscoverableObject mdo;
                        logger.fine("presence message from Enterprise MUC Occupant client, advertise using SD system");
                        try {
                            mdo = new MembershipDiscoverableObject(sdObject, userJid,fromMucOccupantJid.toFullJID(),nickName, roomName, occStatus);
                            enterpriseMDOs.put(nickName, mdo);
                            logger.fine("advertising mdo:"+mdo);
                            sdObject.advertise(mdo);
                        } catch (IOException | InitializationException | ServiceInfoException | ServiceLifeCycleException e) {
                            logger.log(Level.SEVERE,"Unable to advertise enterprise client from Presence:"+p, e);
                        }
                    } else {// occupant is leaving room, remove Advert
                        logger.fine("Enterprise client is leaving, remove advert for: "+nickName);
                        MembershipDiscoverableObject mdo = enterpriseMDOs.get(nickName);
                        if( mdo != null){
                            try {
                                sdObject.remove(mdo);
                            } catch (ServiceLifeCycleException | ServiceInfoException e) {
                                logger.log(Level.SEVERE, "Unable to remove enterprise client MembershipDiscoverableObject: "+mdo, e);
                            }
                        } else {
                            logger.warning("unable to find advertisement for nick:"+nickName);
                        }
                    }
                    logger.fine("end isEnterpriseMUCOccupant()");
                } else if( presenceMonitor.isPresenceFromEnterpriseClient(presence)){
                    logger.fine("beginning isPresenceFromEnterpriseClient()!!!");
                    // Create a UserDiscoverableObject and determine if this is an
                    // enterprise client joining or leaving.
                    try{
                        if( presence.getStatus() == null ) {
                            String resource = presence.getFrom().getResource();
                            String node = presence.getFrom().getNode();
                            String fromBareJid = presence.getFrom().toBareJID();
                            String nick = (resource == null) ? node : resource;
                            String ver = (presence.getID() == null) ? "1" : presence.getID(); // TODO: 2012-03-27 need to generate version based on XEP-0115
                            // String hash = "hash"; // TODO: 2012-03-27 need to define hashing function based on XEP-0115
                            String firstName = "fname";
                            String email = "fname@lastn.com";
                            String lastName = "lastn";
                            String msg = "enterprise client";
                            logger.log(Level.FINE, "userDiscoverableObject does not exist, creating... ");
                            UserDiscoverableObject user = new UserDiscoverableObject(sdObject, node, fromBareJid, node, "AVAILABLE", ver, firstName, email, lastName, msg);// "Ian", "ian.j.taylor@gmail.com", "Taylor", "In Jacksonville");
                            sdObject.advertise(user);
                            enterpriseClients.put(fromBareJid, user);
                        } else if( Presence.Type.unavailable.equals(presence.getType()) ){
                            String fromBareJid = presence.getFrom().toBareJID();
                            logger.fine("Enterprise client is going offline: "+fromBareJid);
                            UserDiscoverableObject user = enterpriseClients.remove(fromBareJid);
                            sdObject.remove(user);
                        }
                    } catch( InitializationException | ServiceLifeCycleException | ServiceInfoException | IOException e ){
                        logger.log(Level.SEVERE,"Exception while creating or removing and SD advert: "+p,e);
                    }
                } else {
                    logger.fine("Presence message is not originating from the enterprise");
                }
            }
        } else {
            logger.log(Level.WARNING, "Not routing packet with domain {" + p.getTo().getDomain() + "} Packet: {"
                + p + "}");
        }
    }

    // ----------[ PacketListener Overrides ]----------

    @Override
    public void processCloseStream(String fromJID) {
        // TODO Auto-generated method stub
    }

    @Override
    public void processPacket(Packet p) {
        // Sends the packet out on the connection
        logger.fine("Sending on outgoing connection." + p);
        outgoing.addPacket(p);
    }

    @Override
    public boolean accept(Packet p) {
        if (p instanceof Message || p instanceof Presence) { // Does not accept IQ messages
            logger.finer("Trying S2S: " );
            if (p.getTo() != null && p.getTo().getDomain() != null
                && whitelistDomains.contains(p.getTo().getDomain())) {
                logger.log(Level.FINE,"Accepted packet "+ p);
                return true;
            } else {
                logger.log(Level.FINER,"Rejected. Not initiating dialback with domain: "+ ((p.getTo()!=null)?p.getTo().getDomain():" (oops no To field!)"));
                return false;
            }
        } else {
            logger.log(Level.FINER,"Rejected, (got an IQ message)");
            return false;
        }
    }
}