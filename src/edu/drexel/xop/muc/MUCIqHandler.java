package edu.drexel.xop.muc;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

import edu.drexel.xop.core.PacketFilter;
import edu.drexel.xop.iq.IqHandler;
import edu.drexel.xop.iq.disco.ProxyDiscoIqHandler;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Handles MUC room feature requests per XEP-0045 (Examples 8 & 9).<br/>
 * Required for multi-user chat rooms to work with Spark.
 * 
 * @author Rob Lass (urlass@cs.drexel.edu)
 * Date: 5/15/13
 */
public class MUCIqHandler extends IqHandler implements PacketFilter {

    private static final Logger logger = LogUtils.getLogger(MUCIqHandler.class.getName());
    private String domain = "";
    private final String MUC_NAMESPACE = "http://jabber.org/protocol/muc";

    private HashSet<String> features = new HashSet<>();

    public MUCIqHandler() {
        this.domain = XopProperties.getInstance().getProperty(XopProperties.MUC_SUBDOMAIN) + "."
            + XopProperties.getInstance().getProperty(XopProperties.DOMAIN);
        features.add(MUC_NAMESPACE);
        features.add("muc_open");
        features.add("muc_unmoderated");
        features.add("muc_nonanonymous");
    }

    @Override
    public IQ handleIq(IQ iq) {
        // return a packet with the correct features (see bottom of this file for an example).
        IQ result = IQ.createResultIQ(iq);
        result.setChildElement("query", ProxyDiscoIqHandler.INFO_NAMESPACE);
        Element child = result.getChildElement();
        child.addElement("identity").addAttribute("category", "conference").addAttribute("name", iq.getTo().toBareJID()).addAttribute("type", "result");
        for (String feature : features) {
            Element e = DocumentHelper.createElement("feature");
            e.addAttribute("var", feature);
            child.addElement("feature").addAttribute("var", feature);
        }
        return iq;
    }

    /**
     * Filters out packets that are session requests
     * 
     * @param packet the packet in question
     * @return true if it is a session request, false otherwise
     */
    public boolean accept(Packet packet) {
        if (!(packet instanceof IQ)) {
            return false;
        }
        IQ iq = (IQ) packet;
        if (iq.getType().equals(IQ.Type.get) && iq.getTo().getDomain().equals(this.domain)) {
            logger.fine("Found an iq info packet for MUC: " + packet.toString());
            return true;
        } else {
            logger.fine("This packet is not an iq MUC info request: " + packet.toString());
            return false;
        }
    }

    public static boolean packetMatches(String string_rep_of_packet) {
        Packet packet;
        try {
            packet = Utils.packetFromString(string_rep_of_packet);
        } catch (org.dom4j.DocumentException e) {
            logger.severe("Document exception while testing: " + string_rep_of_packet);
            return false;
        }
        MUCIqHandler mih = new MUCIqHandler();
        return mih.accept(packet);
    }

    public static void testPacket(String packet, String packet_name) {
        if (packetMatches(packet)) {
            logger.log(Level.INFO, packet_name + "Session IQ Packet successfully filtered.");
        } else {
            logger.log(Level.INFO, packet_name + "Session IQ Packet failed to be filtered!");
        }
    }

    public static void main(String[] args) {

        String spark_packet = "<iq id=\"9Df6m-24\" to=\"foo@conference.proxy\" type=\"get\" from=\"n1@proxy\">\n"
            + "  <query xmlns=\"http://jabber.org/protocol/disco#info\"></query>\n" + "</iq>";
        testPacket(spark_packet, "Spark");
    }
    /*
     * When we try to JOIN a room (that may not exist), Spark sends this message:
     * <iq id="9Df6m-24" to="foo@conference.proxy" type="get" from="n1@proxy">
     * <query xmlns="http://jabber.org/protocol/disco#info"></query>
     * </iq>
     * XOP responds with:
     * <iq type="error" id="9Df6m-24" from="foo@conference.proxy" to="n1@proxy">
     * <error code="404" type="cancel">
     * <item-not-found xmlns="urn:ietf:params:xml:ns:xmpp-stanzas"/>
     * <text xmlns="urn:ietf:params:xml:ns:xmpp-stanzas">Unfortunately, we do not support this feature at this time.</text>
     * </error>
     * </iq>
     * which should actually be something like this (I think?):
     * <iq from="foo@conference.proxy" id="9Df6m-24" to="n1@proxy" type='result'>
     * <query xmlns='http://jabber.org/protocol/disco#info'>
     * <identity
     * category='conference'
     * name='foo'
     * type='text'/>
     * <feature var='http://jabber.org/protocol/muc'/>
     * <feature var='muc_open'/>
     * <feature var='muc_unmoderated'/>
     * <feature var='muc_nonanonymous'/>
     * </query>
     * </iq>
     */

}
