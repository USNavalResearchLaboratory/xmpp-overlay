/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.client;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import edu.drexel.xop.core.PacketListener;

/**
 *
 * @author dave
 */
public class StanzaUnmarshallerTest {

    private MyPacketListener listener = new MyPacketListener();

    private String to = "john@proxy";
    private String from = "jane@proxy";

    @Before
    public void init() {
        listener = new MyPacketListener();
    }

    @Test
    public void testUnmarshalling() {
 
        // Check message
        try {
            StanzaUnmarshaller stanzaUnmarshaller = new StanzaUnmarshaller();
            String message = "<message from='jane@proxy' to='john@proxy'><body>this is some text for the body of the message</body></message>";
            stanzaUnmarshaller.processStanza(message, from);
	        assertTrue("Message parsed correctly", listener.gotMessage);

	        // Check iq
            String iq = "<iq type='get' id='351-0' to='john@proxy' from='jane@proxy'/>";
            stanzaUnmarshaller.processStanza(iq, from);
	        assertTrue("IQ unmarshalled properly", listener.gotIq);

	        // Check presence
            String presence = "<presence from='jane@proxy' to='john@proxy' type='unavailable'/>";
            stanzaUnmarshaller.processStanza(presence, from);
	        assertTrue("Presence unmarshalled correctly", listener.gotPresence);
	        
	        // check close stream tag
            String closeStream1 = "</stream>";
            stanzaUnmarshaller.processStanza(closeStream1, from);

            String closeStream2 = "</stream:stream>";
            stanzaUnmarshaller.processStanza(closeStream2, from);
		} catch (ParserException e) {
			e.printStackTrace();
			fail("Exception caught");
		}
    }
    
    @Ignore
    public class MyPacketListener implements PacketListener {

        public boolean gotMessage = false;
        public boolean gotPresence = false;
        public boolean gotIq = false;
        public boolean gotClose = false;

        @Override
        public void processPacket(Packet packet) {
        	System.out.println("processPacket: "+packet.toString());
            if(packet instanceof Presence) {
                checkPresence((Presence) packet);
            } else if(packet instanceof Message) {
                checkMessage((Message) packet);
            } else if(packet instanceof IQ) {
                checkIq((IQ) packet);
            } else {
            	System.out.println("packet: "+packet.toString());
            	
            }
        }

        private void checkMessage(Message m) {
            String body = "this is some text for the body of the message";
            if(m.getBody().equals(body)
                    && m.getTo().toBareJID().equals(to)
                    && m.getFrom().toBareJID().equals(from))
                gotMessage = true;
        }

        private void checkIq(IQ iqPkt) {
            if(iqPkt.getFrom().toBareJID().equals(from)
                    && iqPkt.getTo().toBareJID().equals(to)
                    && iqPkt.getType().equals(IQ.Type.get))
                gotIq = true;
        }

        private void checkPresence(Presence presencePkt) {
            if(presencePkt.getFrom().toBareJID().equals(from)
                    && presencePkt.getTo().toBareJID().equals(to)
                    && presencePkt.getType().equals(Presence.Type.unavailable))
                gotPresence = true;
        }  
    }
}
