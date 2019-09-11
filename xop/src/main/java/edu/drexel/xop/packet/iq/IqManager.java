package edu.drexel.xop.packet.iq;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.core.roster.RosterList;
import edu.drexel.xop.core.roster.RosterListManager;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.Element;
import org.xmpp.packet.*;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages IQ Queries directed at this server coming from connected/local clients
 * 
 */
public class IqManager {
    private static final Logger logger = LogUtils.getLogger(IqManager.class.getName());

    private ClientManager clientManager;
    private RosterListManager rosterListManager;

    public IqManager(ClientManager clientManager, RosterListManager rosterListManager) {
        this.clientManager = clientManager;
        this.rosterListManager = rosterListManager;
    }

    public void processIQPacket(JID fromJID, Packet p) {
        IQ iqPacket = (IQ)p.createCopy();
        logger.finer("IQ Packet: " + iqPacket);
        if (iqPacket.getTo() != null && !iqPacket.getTo().getDomain().endsWith(XOP.DOMAIN)) {
            logger.fine("IQ packet is intended for a remote domain: " + iqPacket.getTo().getDomain());
            if (XOP.ENABLE.GATEWAY) {
                logger.fine("Gateway is enabled, sending to gateway");
                XOProxy.getInstance().sendToGateway(iqPacket);
            } else {
                if (clientManager.isLocal(fromJID)) {
                    logger.fine("Gateway not enabled, sending over one-to-one transport");
                    XOProxy.getInstance().getXopNet().sendToOneToOneTransport(iqPacket);
                } else {
                    logger.fine("Not processing packet from NOT from local instance");
                }
            }
            return;
        }


        logger.finer("Processing IQ packet destined for local domain: " + XOP.DOMAIN);
        switch (iqPacket.getType()) {
            case set:
                handleIQSet(fromJID, iqPacket);
                break;
            case get:
                handleIQGet(p, iqPacket);
                break;
            case result:
                handleIQResult(p, iqPacket);
                break;
            case error:
                handleIQError(iqPacket);
                break;
        }
    } // end bindResource()

    private void handleIQSet(JID fromJID, IQ iqPacket){
        Element childElement = iqPacket.getChildElement();
        if( childElement == null ){
            logger.warning("no child element for IQ: "+iqPacket.toXML());
            return;
        }
        switch(childElement.getName()){
            case "bind":
                // Binds the resource. Simply respond with the resource name,
                BindIqHandler bindHandler = new BindIqHandler(clientManager);
                bindHandler.bindResource(fromJID, iqPacket);
                break;
            case "session":
                // per: https://xmpp.org/rfcs/rfc3921.html#session (not in the latest RFC6120)
                SessionIqHandler sessionHandler = new SessionIqHandler(clientManager);
                sessionHandler.processIQPacket(iqPacket);
                break;
            case "query": // Add item to roster: Per RFC 6121 Section 2.3.1
                if ("jabber:iq:roster".equals(childElement.getNamespaceURI())) {
                    handleRosterSet(fromJID, iqPacket);
                } else {
                    logger.info("unhandled query namespace: " + childElement.getNamespaceURI());
                }
                break; // unsupported IQ operation
            // case CONSTANTS.DISCO.JINGLE_NAMESPACE:
            //     if(childElement.attributeValue("action").equals("session-terminate")) {
            //         JingleManager.getInstance().removeSession(childElement.attributeValue("sid"));
            //     }
            //     //JingleManager.getInstance().addSession(ClientManager.getInstance().getAddress(iqPacket.getFrom()), iqPacket);
            //     break;
        }
    }

    /**
     * Handles IQ type="set", roster
     * Adds an item on a roster. RFC 6121 Sec 2.3.1 (for add)
     * update roster item: 2.4
     * remove roster item: 2.5
     *
     * @param fromJID the source of the iq packet
     * @param iqPacket the roster query iq packet
     */
    private void handleRosterSet(JID fromJID, IQ iqPacket){
        if(iqPacket.getFrom() == null
                || (iqPacket.getTo() != null
                && iqPacket.getTo().toString().equals(XOP.DOMAIN))
                ) {
            logger.finest("No from: or to: is not destined for this domain");
            return;
        }


        Element queryElem = iqPacket.getChildElement();//("query", "jabber:iq:roster");
        Element itemElem = queryElem.element("item");
        String jidStr = itemElem.attributeValue("jid");
        String subscribeAttr = itemElem.attributeValue("subscription");
        JID itemJID = new JID(jidStr);
        RosterList rosterList = rosterListManager.getRosterList(iqPacket);
        IQ rosterResult;

        if( "remove".equals(subscribeAttr ) ){
            logger.fine("removing "+itemJID+" from ");
            List<Packet> packetsToSend = rosterList.removeItem(itemJID);
            for( Packet p : packetsToSend ){
                if (clientManager.isLocal(p.getTo())) {
                    logger.fine("sending response packet: "+p.toXML());
                    ProxyUtils.sendPacketToLocalClient(p, clientManager);
                } else {
                    logger.fine("packet is not local: "+p.toXML());
                }
            }
        } else {
            logger.fine("add/update roster item: "+jidStr);
            String name;
            if( itemElem.attributeValue("name") != null ){
                name = itemElem.attributeValue("name");
            } else {
                name = itemJID.getNode();
            }
            Set<String> groups = new HashSet<>();
            Iterator iter = itemElem.elementIterator("group");
            while(iter.hasNext()){
                Element groupElem = (Element) iter.next();
                groups.add(groupElem.getText());
            }

            // Process the roster add/update
            rosterResult = rosterList.addRosterItem(itemJID, name, groups, "both");
            if (rosterResult.getType() != IQ.Type.error) {
                // RFC 6121 Chap 2.3.2 Success Case
                logger.fine("Adding roster item: "+rosterResult);

                // send a result IQ to the connected resource
                IQ resultIQ = new IQ(IQ.Type.result);
                resultIQ.setID(iqPacket.getID());
                resultIQ.setTo(fromJID);
                ProxyUtils.sendPacketToLocalClient(resultIQ, clientManager);

                // Send roster push to all locally connected relevant parties
                for (JID localClient : clientManager.getLocalClientJIDs()) {
                    rosterResult.setTo(localClient);
                    ProxyUtils.sendPacketToLocalClient(rosterResult, clientManager);
                }
            } else {
                // RFC 6121 Chap 2.3.3 Error Case
                logger.fine("item add/update error case");
                rosterResult.setTo(fromJID);
                ProxyUtils.sendPacketToLocalClient(rosterResult, clientManager);
            }
        }
    }
    /**
     * handle an IQ Get packet
     * @param p the original packet
     * @param iqPacket a copy of this packet cast as an IQ packet
     */
    private void handleIQGet(Packet p, IQ iqPacket){
        Element child = iqPacket.getChildElement();
        if(child == null) {
            logger.warning("no child element! "+iqPacket.toXML());
            return;
        }
        String iqChildElementName = child.getName();
        switch (iqChildElementName) {
            case "query":
                switch( child.getNamespaceURI() ){
                    case "jabber:iq:roster": // handle RFC 6121, 2.2. Retrieving the Roster on Login
                        RosterList rosterList = rosterListManager.getRosterList(iqPacket);
                        IQ rosterListIQ = rosterList.getRosterListIQ();
                        ProxyUtils.sendPacketToLocalClient(rosterListIQ, clientManager);
                        logger.info("responding to roster request with rosterIQ");

                        // If this is an initial available presence from local client also send presences from available clients
                        for (JID availableClientJID : clientManager.getAvailableClientJIDs()) {
                            Presence availablePresence = new Presence();
                            availablePresence.setFrom(availableClientJID);
                            availablePresence.setTo(rosterListIQ.getTo());
                            ProxyUtils.sendPacketToLocalClient(availablePresence, clientManager);
                        }
                        //rosterList.setInitialPresence(false);

                        break;

                    default:
                        logger.info("default handling: "+child.getNamespaceURI());
                        DiscoIqHandler discoHandler = new DiscoIqHandler(clientManager);
                        discoHandler.processIQPacket(iqPacket);
                }

                break;
            case "vCard":
                VCardIqHandler vCardHandler = new VCardIqHandler(clientManager);

                vCardHandler.processPacket(p);
                break;
            case "ping":
                PingIqHandler pingHandler = new PingIqHandler(clientManager);
                pingHandler.processPacket(p);
                break;
            default:
                logger.warning("sending unmodified get IQ back to client");
                ProxyUtils.sendPacketToLocalClient(iqPacket, clientManager);

        }
        logger.finer("Exit");
    }

    private void handleIQResult(Packet p, IQ iqPacket){
        if((iqPacket.getTo() != null && iqPacket.getTo().toString().equals(XOP.DOMAIN))) {
            DiscoIqHandler discoHandler = new DiscoIqHandler(clientManager);
            discoHandler.processIQPacket(iqPacket);
        } else if(iqPacket.getTo() != null) {
            if (clientManager.isLocal(iqPacket.getTo())) {
                logger.finer(iqPacket.getTo() + " is locally connected");
                ProxyUtils.sendPacketToLocalClient(iqPacket, clientManager);
            } else if (XOP.ENABLE.GATEWAY) {
                logger.finer("Gateway enabled! Sending over network to client " + iqPacket.getTo());
                XOProxy.getInstance().getXopNet().sendToOneToOneTransport(iqPacket);
            }
        }
    }

    private void handleIQError(IQ iqPacket){
        if(iqPacket.getType().equals(IQ.Type.error)) {
            logger.warning("handling IQ packet with type error");
        } else {
            logger.warning("unhandled IQ Packet type: "+iqPacket.getType());
        }
    }

    /**
     * Respond to unhandled IQ's with an error
     *
     * @param iq the iq that was not handled
     * @param condition the packet error condition
     * @param type the type of the packet error
     */
    static IQ getErrorForIq(IQ iq, PacketError.Condition condition, PacketError.Type type) {
        IQ response = IQ.createResultIQ(iq);
        response.setType(IQ.Type.error);

        PacketError error = new PacketError(condition, type);
        error.setText("Unfortunately, we do not support this feature at this time.");
        response.setError(error);

        if( logger.isLoggable(Level.FINE) ) logger.fine("Generating an error packet for: " + iq.toString());
        return response;
    }

    /**
     * feature not implemented IQ
     * @param iq the starting IQ message
     * @return a new error IQ indicating the feature is not implemented
     */
    static IQ getFeatureNotImplementedErrorIq(IQ iq) {
        return getErrorForIq(iq, PacketError.Condition.feature_not_implemented, PacketError.Type.cancel);
    }
}
