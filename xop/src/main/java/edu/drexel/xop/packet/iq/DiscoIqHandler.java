package edu.drexel.xop.packet.iq;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.core.XMPPClient;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.room.RoomManager;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.xop.util.addressing.NetUtilsKt;

/**
 * Handles XMPP Disco requests for XMPP clients
 *
 */
class DiscoIqHandler {
    private static final Logger logger = LogUtils.getLogger(DiscoIqHandler.class.getName());

    private static final String[] SUPPORTED_FEATURES = {
            CONSTANTS.DISCO.INFO_NAMESPACE,
            CONSTANTS.DISCO.ITEM_NAMESPACE,
            CONSTANTS.DISCO.MUC_NAMESPACE,
            CONSTANTS.DISCO.ROSTER_NAMESPACE,
            // CONSTANTS.DISCO.BYTESTREAM_NAMESPACE,
            // CONSTANTS.DISCO.AUDIO_NAMESPACE,
            "jabber:iq:register",
            "jabber:iq:search",
            "jabber:iq:time",
            "jabber:iq:version"
    };

    private ClientManager clientManager;

    DiscoIqHandler(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * Handles a request from the client, the default behavior is to just send the packet back
     * or send an error if the namespace is not in SUPPORTED_FEATURES
     * @param p the iq packet
     */
    void processIQPacket(IQ p) {
        IQ packet = p.createCopy();
        logger.fine("packet: " + packet);
        IQ responseIQ = new IQ();
        responseIQ.setID(p.getID());
        responseIQ.setTo(packet.getTo());
        responseIQ.setFrom(packet.getFrom());
        responseIQ.setChildElement(packet.getChildElement().createCopy());
        logger.fine("responseIQ: " + responseIQ);

        boolean unsupported = false;
        boolean handled = false;

        if( packet.getTo() != null ) {
            logger.fine("Testing if packet should be handled by the roomManager");
            //test to see if the packet is for any of the room managers
            RoomManager roomManager = XOProxy.getInstance().getRoomManager(responseIQ.getTo().getDomain());
            if (roomManager != null) {

                roomManager.processIQPacket(responseIQ);
                handled = true;
            }
        }
        //handle as a normal request
        if(!handled) {
            Element child = packet.getChildElement();
            if (child != null) {
                String nameSpace = packet.getChildElement().getNamespaceURI();
                switch (nameSpace) {
                    case CONSTANTS.DISCO.INFO_NAMESPACE:
                        // handle disco#info
                        // this is a generic info query
                        if (p.getTo() == null || p.getTo().equals(new JID(XOP.DOMAIN))) {
                            for (String feature : SUPPORTED_FEATURES) {
                                DefaultElement element = new DefaultElement("feature", packet.getChildElement().getNamespace());
                                element.addAttribute("var", feature);
                                responseIQ.getChildElement().add(element);
                            }
                        } else {
                            //this is a query for a specific user
                            //route the packet to that user
                            if (logger.isLoggable(Level.FINE))
                                logger.fine("Rerouting disco IQ: " + p);
                            //XOProxy.getInstance().processOutgoingPacket(p);
                            ProxyUtils.sendPacketToLocalClient(responseIQ, clientManager);
                            return;
                        }
                        break;
                    case CONSTANTS.DISCO.ITEM_NAMESPACE:
                        //handle disco#items XEP-0045 6.1
                        logger.fine("Creating response for disco#items query for subdomains: " + packet.getTo());
                        // TODO dnn 2018-05-31: Support multiple Subdomains either user created or via a configuration interface
                        for (RoomManager manager : XOProxy.getInstance().getRoomManagers()) {
                            DefaultElement itemElement = new DefaultElement("item");
                            itemElement.addAttribute("jid", manager.getDomain());
                            itemElement.addAttribute("name", manager.getDescription());
                            responseIQ.getChildElement().add(itemElement);
                        }
                        break;
                    case CONSTANTS.DISCO.BYTESTREAM_NAMESPACE:
                        //handle byte streams
                        unsupported = true;
                        if (XOP.ENABLE.STREAM) {
                            unsupported = false;
                            String hostAddress
                                    = NetUtilsKt.getBindAddress(XOP.TRANSPORT.SEND_INTERFACE).getHostAddress();

                            DefaultElement host = new DefaultElement("streamhost");
                            host.addAttribute("host", hostAddress);
                            host.addAttribute("jid", XOP.STREAM.JID);
                            host.addAttribute("port", "" + XOP.STREAM.PORT);
                            responseIQ.getChildElement().add(host);
                        }

                        break;
                    case CONSTANTS.DISCO.SERVER_NAMESPACE:
                        if (logger.isLoggable(Level.FINEST))
                            logger.finest("Received ping iq from: " + packet.getFrom());
                        return;
                    case "jabber:iq:last":
                        logger.fine("responding to last activity query");
                        // Support for XEP-0012, caveat:  requesting entitites are always authorized to retrieve activity information
                        IQ respIq = p.createCopy();
                        respIq.setType(IQ.Type.set);
                        respIq.setFrom(p.getTo());
                        respIq.setTo(p.getFrom());
                        JID entity = respIq.getTo();
                        XMPPClient client;
                        if (clientManager.isLocal(entity)) {
                            client = clientManager.getLocalXMPPClient(entity);
                        } else {
                            client = clientManager.getRemoteXMPPClient(entity);
                        }
                        if (client == null) {
                            logger.fine("no client found for " + entity);
                            break;
                        }
                        long lastUpdated = client.getLastUpdated() != null ? client.getLastUpdated().getTime()
                                : System.currentTimeMillis();
                        long lastLogin = (System.currentTimeMillis() - lastUpdated) / 1000;
                        child.addAttribute("seconds", Long.toString(lastLogin));
                        //   Offline User Query, always send last respons
                        //   Online User Query, send last activity by client
                        if (client.getStatus() != null) {
                            child.addText(client.getStatus());
                        }
                        // if( client.getNodeStatus() == XMPPClient.NodeStatus.offline ) {
                        //
                        // } else {
                        //
                        // }
                        ProxyUtils.sendPacketToLocalClient(respIq, clientManager);
                        break;
                    default:
                        //forward it where it should go if it isn't for us to deal with
                        if (p.getTo() != null) {
                            if (logger.isLoggable(Level.FINE))
                                logger.fine("Rerouting iq message: " + p);
                            ProxyUtils.sendPacketToLocalClient(responseIQ, clientManager);
                            return;
                        }
                }
                //send unsupported feature message if that feature isn't supported
                if(unsupported || !Arrays.asList(SUPPORTED_FEATURES).contains(nameSpace)) {
                    PacketError error = new PacketError(PacketError.Condition.bad_request, PacketError.Type.cancel);
                    error.setText("Unfortunately, we do not support that feature at this time.");
                    responseIQ.setError(error);
                }
            }
        }

        //prep the packet before sending to local client
        JID temp = responseIQ.getTo();
        responseIQ.setType(IQ.Type.result);
        responseIQ.setTo(responseIQ.getFrom());
        responseIQ.setFrom(temp);

        if (logger.isLoggable(Level.FINE))
            logger.fine("Responding to disco IQ with: " + responseIQ.toString());
        ProxyUtils.sendPacketToLocalClient(responseIQ, clientManager);
    }
}
