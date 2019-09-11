/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.util;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import edu.drexel.xop.util.logger.LogUtils;

/**
 * Utility functions for XMPP
 * 
 * @author David Millar
 */
public class Utils {
    private static final Logger logger = LogUtils.getLogger(Utils.class.getName());

    public static String getComponentSubDomain(JID j, String serverDomain) {
        if (!hasComponentSubdomain(j, serverDomain)) {
            return "";
        }

        String domain = j.getDomain();
        return domain.substring(0, domain.indexOf("."));
    }

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
     * @param msg
     * @return true if this is a close stream tag, false otherwise
     */
    public static boolean isCloseStream(String msg) {
        return (msg.contains("/stream>") || msg.contains("/flash>") || msg.contains("/stream:stream"));
    }

    /**
     * Parses stanzas. This function assumes that you will never receive more
     * than one top level tag at a time.
     * 
     * @param s (not a close stream)
     * @return a message, presence, or iq packet
     * @throws DocumentException
     */
    public static Packet packetFromString(String s) throws DocumentException {
        SAXReader saxReader = new SAXReader();
        StringReader sr = new StringReader(s);
        Document document = saxReader.read(sr);
        // Construct our packet
        String pType = document.getRootElement().getName();
        if (pType.equalsIgnoreCase("message")) {
            return new Message(document.getRootElement(), true);
        } else if (pType.equalsIgnoreCase("iq")) {
            return new IQ(document.getRootElement(), true);
        } else if (pType.equalsIgnoreCase("presence")) {
            return new Presence(document.getRootElement(), true);
        } else {
            throw new DocumentException("Error parsing packet.  Invalid type: " + s);
        }
    }

    /**
     * @param x
     * @return
     * @throws DocumentException
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

    public static Element elementFromString(String input_string) {

        String x = input_string;
        if (x.startsWith("<stream:stream")) {
            x += "</stream:stream>";
        } else if (x.startsWith("</stream:stream")) {
            x = "<stream:stream xmlns:stream='http://etherx.jabber.org/streams'>" + x;
        }
        if (x.startsWith("<db")) {}

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(false);

        SAXParser parser;
        try {
            parser = factory.newSAXParser();

            XMLReader reader = parser.getXMLReader();
            InputSource s = new InputSource(new StringReader(x));
            SAXXOPHandler handler = new SAXXOPHandler();
            reader.setContentHandler(handler);
            reader.parse(s);
            return handler.getElement();

        } catch (ParserConfigurationException | IOException | SAXException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public static byte[] hashText(String text, String algorithm) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        md.update(text.getBytes("iso-8859-1"), 0, text.length());
        return md.digest();
    }

    public static byte[] MD5(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return hashText(text, "MD5");
    }
}