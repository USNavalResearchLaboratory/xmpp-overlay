package edu.drexel.xop.util;

import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.dom4j.tree.DefaultElement;
import org.xmpp.packet.*;

import java.io.StringReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility functions for XMPP
 */
public class Utils {
    private static final Logger logger = LogUtils.getLogger(Utils.class.getName());

    private static SecureRandom random = new SecureRandom();
    private static String idChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";

    public static boolean hasComponentSubdomain(JID j, String serverDomain) {
        if (j == null || j.getDomain() == null || j.getDomain().length() <= 0) {
            return false;
        }
        String domain = j.getDomain();
        if (!domain.contains(".")) {
            return false;
        }
        String component = domain.substring(0, domain.indexOf(".") - 1);
        return !component.equals(serverDomain);
    }

    /**
     * Parses stanzas. This function assumes that you will never receive more
     * than one top level tag at a time.
     *
     * @param s (not a close stream)
     * @return a message, presence, or iq packet
     * @throws DocumentException
     */
    public static synchronized Packet packetFromString(String s) throws DocumentException {
        SAXReader saxReader = new SAXReader();
        StringReader sr = new StringReader(s);
        Document document = saxReader.read(sr);
        sr.close();
        if (logger.isLoggable(Level.FINEST))
            logger.finest("document.toString(): " + document.toString());
        // Construct our packet
        String pType = document.getRootElement().getName();
        if (pType.equalsIgnoreCase("message")) {
            Message msg = new Message(document.getRootElement(), true);
            return msg.createCopy();
        } else if (pType.equalsIgnoreCase("iq")) {
            return new IQ(document.getRootElement(), true);
        } else if (pType.equalsIgnoreCase("presence")) {
            return new Presence(document.getRootElement(), true);
        } else {
            throw new DocumentException("Error parsing packet.  Invalid type: " + s);
        }
    }

    /**
     * @param x extension xml element
     * @return an XMPP packet
     * @throws DocumentException if can't parse this element because it is an unhandled packet type
     */
    public static Packet packetFromElement(Element x) throws DocumentException {
        String pType = x.getName();
        if (pType.equalsIgnoreCase("message")) {
            return new Message(x, true);
        } else if (pType.equalsIgnoreCase("iq")) {
            return new IQ(x, true);
        } else if (pType.equalsIgnoreCase("presence")) {
            return new Presence(x, true);
        } else {
            throw new DocumentException("Error parsing packet.  Invalid type: " + pType);
        }
    }

    /**
     * Add a delay element in accordance to XEP-0203
     *
     * @param packet    XMPP Packet to add the delay element to
     * @param timestamp the timestamp in milliseconds since the epoch of the stated timestamp
     * @param from      the jid of the from= attribute of the delay element
     * @param text      if there is text for this delay element
     */
    public static void addDelay(Packet packet, Long timestamp, JID from, String text) {
        Element e = packet.getElement();
        addDelay(e, timestamp, from, text);
    }

    /**
     * Add a delay element in accordance to XEP-0203
     *
     * @param e         the XML element representation of an XMPP Packet
     * @param timestamp the timestamp in milliseconds since the epoch of the stated timestamp
     * @param from      the jid of the from= attribute of the delay element
     * @param text      if there is text for this delay element
     * @return the passed in element with the delay element added to it
     */
    public static Element addDelay(Element e, Long timestamp, JID from, String text) {
        Element delay = new DefaultElement(new QName("delay", new Namespace(null, CONSTANTS.MISC.DELAY_NAMESPACE)));
        DateFormat df = new SimpleDateFormat(CONSTANTS.MISC.XMPP_DATETIME_FORMAT, Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        delay.addAttribute("stamp", df.format(new Date(timestamp)));

        if (from != null) {
            delay.addAttribute("from", from.toString());
        }
        if (text != null) {
            delay.addText(text);
        }

        e.add(delay);
        return e;
    }

    /**
     * legacy addDelay element used by clientConnection when XOP.ENABLE.DELAY = true
     *
     * @param e the XML Element representation of Packet
     * @return the passed in element with the
     */
    public static Element addDelay(Element e) {
        return addDelay(e, System.currentTimeMillis(), null, null);
    }


    private static byte[] hashText(String text, String algorithm) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(text.getBytes(StandardCharsets.ISO_8859_1), 0, text.length());
        return md.digest();
    }

    private static byte[] MD5(String text) {
        try {
            return hashText(text, "MD5");
        } catch (NoSuchAlgorithmException e) {
            logger.severe("No such algorithm: " + e.getMessage());
        }
        return null;
    }

    public static String Md5Base64(String text) {
        return Base64.encodeToString(MD5(text)).trim();
    }

    /**
     * Description: generates a random set of characters of a given length
     *
     * @param length the length of the random set of characters
     * @return the random id
     */
    public static String generateID(int length) {
        String id = "";
        for (int i = 0; i < length; i++) {
            id += idChars.charAt(random.nextInt(idChars.length()));
        }
        return id;
    }


    public static Message removeJabberNamespace(Message msg) {
        // hack to remove xmlns='jabber:server' (also removes 'jabber:client')
        Message m = new Message();
        m.setBody(msg.getBody());
        m.setFrom(msg.getFrom());
        m.setTo(msg.getTo());
        m.setType(msg.getType());
        m.setID(msg.getID());
        return m;
    }

    public static String stripNamespace(String input, String namespace) {
        String ret = "";
        for (String part : input.split(" ")) {
            if (!part.contains("xmlns") || !part.contains(namespace)) {
                ret += part + " ";
            } else if (part.contains("/>")) {
                ret = ret.trim();
                ret += "/>" + " ";
            } else if (part.contains(">")) {
                ret = ret.trim();
                ret += "/>" + " ";
            }
        }
        ret = ret.trim();
        return ret;
    }

    /**
     * Tests if the supplied JID is not on the local domain specified in XOP.DOMIN.
     *
     * @param jid the jid to test
     * @return true if this jid is not on the local domain (e.g. conference.openfire), false if it is (e.g. XOP.DOMAIN)
     */
    public static boolean isOnDifferentDomain(JID jid) {

        return !(jid.getDomain().endsWith(XOP.DOMAIN));
    }

    public static JID rewriteDomain(JID oldJID, String newDomain) {
        JID newJID = new JID(oldJID.getNode(), newDomain, oldJID.getResource());
        return newJID;
    }

    public static void rewritePacketToGateway(Packet p) {
        logger.finer("ENTER");
        if (!XOP.GATEWAY.REWRITEDOMAIN) {
            logger.finer("NOT rewriting domain");
            return;
        }

        JID fromJID = p.getFrom();
        if (fromJID != null && !fromJID.getDomain().contains(XOP.GATEWAY.DOMAIN)) {
            JID newFrom = new JID(fromJID.getNode(), XOP.GATEWAY.DOMAIN, fromJID.getResource());
            if (fromJID.getDomain().contains("conference"))
                newFrom = new JID(fromJID.getNode(), XOP.GATEWAY.CONFERENCEDOMAIN, fromJID.getResource());
            p.setFrom(newFrom);
        } else {
            logger.fine("NOT REWRITING FROM FIELD FOR packet going over gateway: [[[" + p + "]]] has no from.");
        }

        JID toJID = p.getTo();
        if (toJID != null && !toJID.getDomain().contains(XOP.GATEWAY.SERVER)) {
            JID newTo = new JID(toJID.getNode(), XOP.GATEWAY.SERVER, toJID.getResource());
            if (toJID.getDomain().contains("conference"))
                newTo = new JID(toJID.getNode(), XOP.GATEWAY.CONFERENCESERVER, toJID.getResource());
            p.setTo(newTo);
        } else {
            logger.fine("NOT REWRITING TO FIELD FOR packet going over gateway: [[[" + p + "]]].");
        }
        logger.fine("EXIT");
    }

    /**
     * rewrite the packet to and from fields coming from the gateway.
     *
     * @param p
     */
    public static void rewritePacketFromGateway(Packet p) {
        logger.finer("ENTER");
        if (!XOP.GATEWAY.REWRITEDOMAIN) {
            logger.finer("NOT rewriting domain");
            return;
        }
        JID oldTo = p.getTo();
        if (oldTo != null) {
            String domain = "";
            if (oldTo.getDomain().contains("conference")) {
                domain += "conference.";
            }
            // only messages directed to this client on the network.
            domain += XOP.DOMAIN;

            JID newTo = new JID(oldTo.getNode(), domain, oldTo.getResource());
            p.setTo(newTo);
            logger.fine("Old to: " + oldTo + ", rewrote to field, " + p);
        }
        JID oldFrom = p.getFrom();
        if (oldFrom != null) {
            String domain = "";
            if (oldFrom.getDomain().contains("conference")) {
                domain += "conference.";
            }
            // only messages directed to this client on the network.
            domain += XOP.DOMAIN;

            JID newFrom = new JID(oldFrom.getNode(), domain, oldFrom.getResource());
            p.setFrom(newFrom);
            logger.fine("Old from: " + oldFrom + ", rewrote from field, " + p);
        }
        logger.finer("EXIT");
    }

    public static String getPingMessageString(String to, String from, String id) {
        String PING_MESSAGE = "<iq from='" + from + "' to='" + to + "'"
                + " id='" + id + "'"
                + " type='get'><ping xmlns='" + CONSTANTS.GATEWAY.PING_NAMESPACE + "'/></iq>";

        return PING_MESSAGE;
    }


    public static List<InetAddress> filterForIPV4(List<InetAddress> addresses) {
        if (addresses != null) {
            List<InetAddress> filtered = new ArrayList<>();
            for (InetAddress address : addresses) {
                if (address instanceof Inet4Address) {
                    filtered.add(address);
                }
            }
            return filtered;
        }
        return null;
    }

    public static List<InetAddress> getAddressesForInterface(String net) throws SocketException {
        if (net != null && NetworkInterface.getByName(net) != null) {
            return Collections.list(NetworkInterface.getByName(net).getInetAddresses());
        }
        return null;
    }

    public static void main(String[] args) {
        JID jid = new JID("node", "domain", "resource");

        System.out.println("jid: FULL {" + jid.toFullJID() + "}");
        System.out.println("jid: BARE {" + jid.toBareJID() + "}");
        System.out.println("jid: 2STR {" + jid.toString() + "}");
        jid = new JID("node", "domain", null);

        System.out.println("jid: BARE {" + jid.toBareJID() + "}");
        System.out.println("jid: 2STR {" + jid.toString() + "}");
        System.out.println("jid: FULL {" + jid.toFullJID() + "}");
    }

    public static JID constructJIDFromServiceName(String serviceName) {
        //alias.toString().replaceAll("[//]", "==").replaceAll("[/.]", "=_=")
        String mucOccupantJIDStr = serviceName.replaceAll("==", "/").replaceAll("=_=", ".");
        return new JID(mucOccupantJIDStr);
    }

    public static String constructServiceInfoName(JID jid) {
        String serviceName = jid.toString();
        return serviceName.replaceAll("[//]", "==").replaceAll("[/.]", "=_=");
    }
}