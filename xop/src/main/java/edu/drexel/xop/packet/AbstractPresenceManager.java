package edu.drexel.xop.packet;

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.tree.DefaultElement;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.core.roster.RosterList;
import edu.drexel.xop.core.roster.RosterListManager;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;

import static org.xmpp.packet.Presence.Type.unavailable;

/**
 * Called from LocalPacketProcessor and handles presence messaging for the INCOMING Packets from
 * locally connected clients
 */
abstract class AbstractPresenceManager {
    private static final Logger logger = LogUtils.getLogger(AbstractPresenceManager.class.getName());

    protected ClientManager clientManager;
    private RosterListManager rosterListManager;

    AbstractPresenceManager(ClientManager clientManager, RosterListManager rosterListManager) {
        this.clientManager = clientManager;
        this.rosterListManager = rosterListManager;
    }

    abstract void processPresencePacket(Presence presence);

    /**
     * Sends an available presence of the fromJID to locally connected clients
     * @param fromJID the originator of the presence message
     */
    void sendAvailablePresenceToLocalClients(JID fromJID) {
        for (JID user : clientManager.getLocalClientJIDs()) {
            if( logger.isLoggable(Level.FINE) ) logger.fine("local toJID: "+user);
            if(!ProxyUtils.compareJIDs(user, fromJID)) {
                Presence p = new Presence();
                p.setFrom(user);
                p.setTo(fromJID);
                logger.info("sending outgoing packet: "+p.toXML());
                ProxyUtils.sendPacketToLocalClient(p, clientManager);
            }
        }
    }


    /**
     * Sends this presence message to local users
     * TODO 2018-12-04 why do we need to do this?
     * @param from the from JID.
     * @param type the type of presence to send to local users
     */
    void sendPresenceToLocalUsersWithDifferentDomain(JID from, Presence.Type type) {
        Presence presence = new Presence(type);
        presence.setFrom(from);

        HashSet<JID> usersOnDiffDomain = new HashSet<>();
        for (JID user : clientManager.getLocalClientJIDs()) {
            if(Utils.isOnDifferentDomain(user)) {
                usersOnDiffDomain.add(user);
            }
        }
        for(JID user : usersOnDiffDomain) {
            if(!ProxyUtils.compareJIDs(presence.getFrom(), user)) {
                logger.fine("sending presence "+(presence.getType()==null?"":"of type "+presence.getType())
                        +"to "+user);
                Packet p = presence.createCopy();
                p.setTo(user);
                ProxyUtils.sendPacketToLocalClient(p, clientManager);
            }
        }
    }

    /**
     *
     * @param presence the presence with a to: field coming from local connected client.
     */
    void handlePresenceWithDestination(Presence presence) {
        switch(presence.getType()){
            case subscribed:
                handleSubscribedPresence(presence);
                break;
            case probe:
                handleProbePresence(presence);
                break;
            case subscribe: // RFC6121 ch 3.1.1
                handleSubscribePresence(presence);
                break;
            default:
                ProxyUtils.sendPacketToLocalClient(presence, clientManager);
        }
    }

    /**
     * handle subscribe according to RFC6121 3.1
     * @param presence the presence with a destination and a type of "subscribe"
     */
    private void handleSubscribePresence(Presence presence) {
        // TODO 2016-11-15 fix according to RFC6121 Ch3.1

        IQ rosterPushUpdate = new IQ(IQ.Type.set);
        logger.fine("from: "+presence.getFrom());
        rosterPushUpdate.setTo(presence.getFrom());
        if (presence.getFrom() != null) {// handle messages coming from local client or from client over s2s
            rosterPushUpdate.setFrom(presence.getTo());
        }
        Element queryElem = rosterPushUpdate.setChildElement("query","jabber:iq:roster");
        Element itemElem = queryElem.addElement("item");
        Element askAttr = itemElem.addAttribute("ask","subscribe");
        itemElem.addAttribute("jid",presence.getTo().toString());
        Element subscrAttr = itemElem.addAttribute("subscription","none");
        sendPacketLocalTransportGateway(clientManager, rosterPushUpdate);

        RosterList rosterList = rosterListManager.getRosterList(presence.getFrom());
        //if the buddy is already added, don't forward
        if (rosterList != null
                && !rosterList.getAllRosterMemberJIDs().contains(presence.getFrom())) {
            Presence subscribedPresence = new Presence(Presence.Type.subscribed);
            JID temp = presence.getFrom();
            subscribedPresence.setFrom(presence.getTo());
            subscribedPresence.setTo(temp);
            //loop the subscribed packet as if it came from a client
            sendPacketLocalTransportGateway(clientManager, subscribedPresence);
        } else {
            sendPacketLocalTransportGateway(clientManager, presence);
        }

        itemElem.remove(askAttr);
        itemElem.remove(subscrAttr);
        itemElem.addAttribute("approved","true");
        itemElem.addAttribute("subscription","both");
        logger.fine("sending new IQ roster push update: "+rosterPushUpdate.createCopy());
        sendPacketLocalTransportGateway(clientManager, rosterPushUpdate);
    }

    private void sendPacketLocalTransportGateway(ClientManager clientManager, Packet packet) {
        if (clientManager.isLocal(packet.getTo())) {
            ProxyUtils.sendPacketToLocalClient(packet, clientManager);
        } else {
            logger.fine("roster push update not destined to a local entity: " + packet.toXML());
            // check if should send to gateway
            sendPacketTransportGateway(packet);
        }
    }

    private void sendPacketTransportGateway(Packet packet) {
        if (XOP.ENABLE.GATEWAY
                && packet.getTo().getDomain().contains(XOP.GATEWAY.SERVER)) {
            logger.fine("sending roster push update to the gateway");
            XOProxy.getInstance().sendToGateway(packet);
        } else {
            logger.fine(" sending roster push update to the transport system");
            XOProxy.getInstance().getXopNet().sendToOneToOneTransport(packet);
        }
    }

    private void handleProbePresence(Presence presence) {
        logger.fine("Handling Presence Probe. presence packet " + presence.toXML());
        // TODO 2016-11-15 fix according to RFC6121 Ch4.3

        //forward the probe if it is for a remote client
        if (!clientManager.isLocal(presence.getTo())) {
            sendPacketTransportGateway(presence);
        } else {
            logger.fine("return to local client");
            //send an available presence message back
            Presence localProbe = presence.createCopy();
            localProbe.setFrom(presence.getTo());
            localProbe.setTo(presence.getFrom());
            ProxyUtils.sendPacketToLocalClient(localProbe, clientManager);
        }
    }

    /**
     *
     * process a message that looks like this:
     *
     * <presence id='pg81vx64'
     *           from='fromclient@example.com' // NOTE: THIS IS ADDED BY THE LocalClientHandler
     *           to='juliet@example.com'
     *           type='subscribed'/>
     *
     * from locally connected client.
     * @param presence the subscribed presence
     */
    private void handleSubscribedPresence(Presence presence) {
        // TODO 2016-11-15 fix according to RFC6121 Ch3.4

        //add buddy
        RosterList rosterList = rosterListManager.getRosterList(presence.getFrom());
        IQ rosterPushIQ = rosterList.registerLocalClient(presence.getTo(), presence.getTo().getNode());

        // send roster pushIQ as per Ch 3.4.2 with the
        sendPacketLocalTransportGateway(clientManager, rosterPushIQ);
        // ProxyUtils.sendPacketToLocalClient(rosterPushIQ, clientManager);

        //send an available presence message
        Presence subscribedPresence = new Presence( );
        subscribedPresence.setTo(presence.getFrom().toBareJID());
        subscribedPresence.setFrom(presence.getTo().toBareJID());
        subscribedPresence.setType(Presence.Type.subscribed);

        sendPacketLocalTransportGateway(clientManager, subscribedPresence);
        // ProxyUtils.sendPacketToLocalClient(subscribedPresence, clientManager);
    }

    private Presence generateError(Packet p, String type, String error) {
        Presence presence = (Presence)p.createCopy();
        DefaultElement element = new DefaultElement("error");
        element.addAttribute("by", presence.getTo().toString());
        element.addAttribute("type", type);
        DefaultElement err = new DefaultElement(error, new Namespace("", CONSTANTS.DISCO.XML_NAMESPACE));
        element.add(err);
        presence.getElement().add(element);

        //prepare the packet to be sent back
        presence.setFrom(presence.getTo().toBareJID());
        presence.setTo(presence.getFrom());
        presence.setType(Presence.Type.error);

        return presence;
    }

    boolean isForMUC(Presence presence) {
        return (presence.getChildElement("x", CONSTANTS.DISCO.MUC_NAMESPACE) != null
                || XOProxy.getInstance().getRoomOccupantIds().contains(presence.getTo()));
    }

    void processMUCPresence(Presence presence) {
        logger.fine("Processing MUC Presence. presence: "+presence);
        //see if a nickname was specified, it was not, respond with an error
        if(presence.getTo().getResource() == null) {
            //XOProxy.getInstance().processOutgoingPacket(generateError(presence, "modify", "jid-malformed"));
            ProxyUtils.sendPacketToLocalClient(generateError(presence, "modify", "jid-malformed"), clientManager);
        }

        //available
        if(XOP.ENABLE.GATEWAY) {
            if(presence.getType() == null) {
                XOProxy.getInstance()
                        .addClientToRoomAndSendOverGateway(presence, true);
                        // .addClientToRoomAndSendOverGateway(presence.getFrom(), presence.getTo());
            } else if(presence.getType().equals(unavailable)) {
                XOProxy.getInstance().removeFromRoomSendOverGateway(presence, true);
            }
        } else {
            if(presence.getType() == null) {
                XOProxy.getInstance().addClientToRoom(presence, true);
            } else if(presence.getType().equals(unavailable)) {
                XOProxy.getInstance().removeFromRoom(presence, true);
            }
        }
    }
}