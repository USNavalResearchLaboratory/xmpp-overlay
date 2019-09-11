/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq;

import edu.drexel.xop.core.PacketFilter;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles session requests per RFC 3921.<br/>
 * Required for messaging and presence.  After a session is established, a client <br/>
 * is considered an "active resource."
 * <p/>
 * TODO: implement sessions to the letter.  See RFC 3921.
 *
 * @author David Millar
 */
public class SessionIqHandler extends IqHandler implements PacketFilter {

    private static final Logger logger = LogUtils.getLogger(SessionIqHandler.class.getName());
    private static final String SESSION_NS = "urn:ietf:params:xml:ns:xmpp-session";

    @Override
    public IQ handleIq(IQ iq) {
        // TODO: Verify that client is authenticated and bound to a resource
        // TODO: handle resource collisions, etc.
        return IQ.createResultIQ(iq);
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
        Element e = iq.getChildElement();
        if (iq.getType().equals(IQ.Type.set) && e.getNamespaceURI().equals(SESSION_NS)) {
            logger.log(Level.FINE, "Found a session request packet: " + packet.toString());
            return true;
        } else {
            logger.log(Level.FINE, "This packet is not a session request: " + packet.toString());
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
        SessionIqHandler sih = new SessionIqHandler();
        return sih.accept(packet);
    }

    public static void testPacket(String packet, String packet_name) {
        if (packetMatches(packet)) {
            logger.log(Level.INFO, packet_name + "Session IQ Packet successfully filtered.");
            /*
            logger.log(Level.INFO, "Result is :");
            try{
                logger.log(Level.INFO, 
                    IQ.createResultIQ((IQ)(Utils.packetFromString(packet))).toString());
            }catch(org.dom4j.DocumentException e){
                logger.severe("Document exception while testing response of " + packet);
            }
            */
        } else {
            logger.log(Level.INFO, packet_name + "Session IQ Packet failed to be filtered!");
        }
    }

    public static void main(String[] args) {

        String pidgin_packet = "<iq type='set' id='purple4a966369'><session xmlns='urn:ietf:params:xml:ns:xmpp-session'/></iq>";
        testPacket(pidgin_packet, "Pidgin");

        String oneteam_packet = "<iq xmlns=\"jabber:client\" to=\"127.0.0.1\" type=\"set\" id=\"sess_1\"><session xmlns=\"urn:ietf:params:xml:ns:xmpp-session\"/></iq>";
        testPacket(oneteam_packet, "OneTeam");

        //from the XMPP RFC
        String xmpp_standard_example = "<iq to='example.com' type='set' id='sess_1'> <session xmlns='urn:ietf:params:xml:ns:xmpp-session'/></iq>";
        testPacket(xmpp_standard_example, "XMPP Standard Example");


    }
//    ---[ From RFC 3921 ]---
//
//    Step 1: Client requests session with server:
//
//   <iq to='example.com'
//       type='set'
//       id='sess_1'>
//     <session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>
//   </iq>
//
//   Step 2: Server informs client that session has been created:
//
//   <iq from='example.com'
//       type='result'
//       id='sess_1'/>
//
//
//    ---[ From session with openfire server ]---
//
//    2009.08.26 11:37:54
//ID mJEyS-1
//-> out
//Processed: true
//null -> test1@davebox/spark
//<?xml version="1.0" encoding="UTF-8"?>
//<iq type="result" id="mJEyS-1" to="test1@davebox/spark">
//  <session xmlns="urn:ietf:params:xml:ns:xmpp-session"/>
//</iq>
//
//
//2009.08.26 11:37:54
//ID mJEyS-1
//<- in
//Processed: true
//test1@davebox/spark -> null
//<?xml version="1.0" encoding="UTF-8"?>
//<iq id="mJEyS-1" type="set" from="test1@davebox/spark">
//  <session xmlns="urn:ietf:params:xml:ns:xmpp-session"/>
//</iq>
}
