/**
 * 
 */
package edu.drexel.xop.component.xog;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.muc.MucProperties;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * @author urlass
 * 
 */
abstract class Gateway {

    protected static final Logger logger = LogUtils.getLogger(Gateway.class.getName());
    protected final ClientProxy proxy = ClientProxy.getInstance();
    protected Set<String> usersSeen = new HashSet<>();

    protected String mucPrefix;
    protected String serverName;
    protected String subdomain;
    protected String domain;
    protected boolean multiGateways;

    Gateway() {
        logger.info("Initializing XOG Component");
        mucPrefix = XopProperties.getInstance().getProperty(XopProperties.S2S_PREFIX);
        serverName = XopProperties.getInstance().getProperty(XopProperties.S2S_SERVER);
        subdomain = XopProperties.getInstance().getProperty(XopProperties.GATEWAY_SUBDOMAIN);
        domain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);
        multiGateways = Boolean.parseBoolean(XopProperties.getInstance().getProperty(XopProperties.MULTIPLE_GATEWAYS));

        logger.info("domain: " + domain);
        logger.info("subdomain: " + subdomain);
        logger.info("multiGateways: " + multiGateways);
        logger.info("serverName: " + serverName);
        logger.info("mucPrefix: " + mucPrefix);
    }

    /**
     * 
     * This function is intended to be over written in derived classes that do
     * further processing to determine whether or not to accept a packet.
     * 
     * @param p the packet
     * @return Always returns true if this packet should be forwarded, false otherwise
     */
    public abstract boolean shouldForward(Packet p);

    /**
     * Will send message over the gatewayed connection to the enterprise. If the message
     * is from a MANET client that hasn't been "seen" yet, then a presence message is
     * constructed and sent to the enterprise.
     * @param p
     */
    public void processPacket(Packet p) {
        logger.fine("Sending packet out to the enterprise:" + p);
        if (p instanceof Message) {
            Message m = (Message) p;
            // TODO dnguyen 20130604: this data structure should be merged or used with the S2SPresenceMonitor, it's performing similar work as this class
            if (usersSeen.add(m.getFrom().toString())) { // Send a presence message to the GW if we haven't seen this user
                proxy.processPacket(makeGatewayPresence(m.getFrom()));
            }
            m.deleteExtension("x", MucProperties.MUC_USER_NAMESPACE);
            if (multiGateways) { // If we're multi-gwing, add the subdomain
                m.setFrom(addSubdomain(p.getFrom()));
            }
            JID newToJid = new JID(p.getTo().getNode(), mucPrefix + "." + serverName, "");
            logger.fine("re-writing to: field:" + newToJid);
            m.setTo(newToJid);
            proxy.processPacket(m);
        } else if (p instanceof Presence) {
            usersSeen.add(p.getFrom().toString()); // We've now seen a presence from this user, add them
            proxy.processPacket(makeGatewayPresence(p.getTo()));
        }
    }

    /**
     * Will construct a presence message with the
     * @param jid the jid to be set as the to: field
     * @return a new presence message with the MUC prefix and the enterprise server name as the domain.
     */
    private Presence makeGatewayPresence(JID jid) {
        Presence pres = new Presence();
        if (multiGateways) { // If we're multi-gwing, add the subdomain
            logger.fine("adding subdomain " + subdomain);
            pres.setFrom(addSubdomain(jid));
            pres.setTo(new JID(jid.getNode(), mucPrefix + "." + serverName, jid.getResource() + "(" + subdomain + ")"));
        } else { // Otherwise, just make a regular presence
            logger.fine("not adding subdomain");
            pres.setFrom(jid);
            pres.setTo(new JID(jid.getNode(), mucPrefix + "." + serverName, jid.getResource()));
        }

        pres.addChildElement("x", "http://jabber.org/protocol/muc");
        return pres;
    }

    public static boolean hasSubdomain(Packet p) {
        return ((p.getFrom() != null && p.getFrom().getResource() != null && p.getFrom().getResource().matches("\\([^()]*\\)")) || (p.getTo() != null
            && p.getTo().getResource() != null && p.getTo().getResource().matches("\\([^()]*\\)")));
    }

    private JID addSubdomain(JID jid) {
        return new JID(jid.getNode(), mucPrefix + "." + subdomain + "." + domain, jid.getResource() + "(" + subdomain
            + ")");
    }

    private JID removeSubdomain(JID jid) {
        return new JID(jid.getNode(), mucPrefix + "." + domain, jid.getResource().replaceAll("\\([^()]*\\)", ""));
    }

    /**
     * 
     * @param packet
     * @return True if this packet should be accepted for processing, false otherwise
     */
    public abstract boolean accept(Packet packet);

    // we need this for multiple gateways to work. Messages coming back from the server look like this:
    // <message type="groupchat" id="purple6d3418e5" to="enterpriseuser@conference.gateway1.proxy" from="room@conference.fireopen/swat"><body>msg</body></message>
    // but we need them to look like this:
    // <message type="groupchat" id="purple6d3418e5" to="enterpriseuser@conference.proxy" from="room@conference.fireopen/swat"><body>msg</body></message>
    void rewriteSubdomainIfNeeded(Packet packet) {
        if (!multiGateways) {
            return;
        }
        if (hasSubdomain(packet)) {
            Packet newPacket = packet.createCopy();
            newPacket.setTo(removeSubdomain(packet.getTo()));
            newPacket.setFrom(removeSubdomain(packet.getFrom()));
            proxy.processPacket(newPacket);
        }
    }

    // Utility functions derived objects may use
    protected boolean isMessageFromServer(Packet p) {
        return (p.getTo() != null // There is a TO field
            && p.getTo().getDomain().startsWith(mucPrefix) // The message is to the MUC (e.g., "conference" subdomain)
            && p.getTo().getDomain().endsWith(domain) // The message is for the XO subdomain (e.g., "proxy")
            && p.getFrom().getDomain().startsWith(mucPrefix) // The message is from the MUC (e.g., "conference" subdomain)
        && p.getFrom().getDomain().endsWith(this.serverName));// It's from the enterprise XMPP server
    }

    protected boolean isPresenceFromServer(Packet p) {
        return (p.getTo() != null // There is a TO field
            && p.getTo().getDomain().startsWith(mucPrefix) // The message is to the MUC
            && p.getTo().getDomain().endsWith(domain) // The message is for the XO subdomain
        && p.getFrom().getDomain().equals(this.serverName));// It's from the enterprise XMPP server
    }

    protected boolean isMessageFromManet(Packet p) {
        return (p.getTo() != null // There is a TO field
            && p.getTo().getDomain().startsWith(mucPrefix) // The message is to the MUC
            && p.getTo().getDomain().endsWith(domain) // The message is for the XO subdomain
            && p.getFrom().getDomain().startsWith(mucPrefix) // The message is to the MUC
        && p.getFrom().getDomain().endsWith(domain));
    }

    protected boolean isPresenceFromManet(Packet p) {
        return (p.getTo() != null // There is a TO field
            && p.getTo().getDomain().startsWith(mucPrefix) // The message is to the MUC
            && p.getTo().getDomain().endsWith(domain) // The message is on the local node
        && p.getFrom().getDomain().equals(domain));
    }
}
