/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.client;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import edu.drexel.xop.component.ComponentManager;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.router.PacketRouter;
import edu.drexel.xop.util.XMLLightweightParser;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * 
 * @author David Millar
 */
@SuppressWarnings("unused")
public class ClientAuthenticationTest {

    private static final Logger logger = LogUtils.getLogger(ClientAuthenticationTest.class.getName());
    private static final String DAVE_AT_PROXY = "dave@proxy";

    public static final String firstStream = "<stream:stream to='proxy' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>";
    public static final String auth = "<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN' xmlns:ga='http://www.google.com/talk/protocol/auth' ga:client-uses-full-bind-result='true'>AGRhdmlkAGphd24=</auth>";

    @Before
    public void init() {
        // ClientManager
        ClientManager clientManager = new ClientManager(new PacketRouter());

        // Authenticated Stanza Listenre - if this gets data, authentication succeeded
        AuthenticatedStanzaListener listener = new AuthenticatedStanzaListener();

        // JID
        JID jid = new JID(DAVE_AT_PROXY);

        // Auth Factory
        AuthenticationProvider authProvider = new AuthenticationProvider(clientManager);
    }

    @Test
    public void testPlainTextAuthentication() {
        // Setup client
        MyClientConnection clientCon = new MyClientConnection();
        XopProperties.getInstance().setProperty(XopProperties.USE_TLS_AUTH, "true");
        // Now we have a client connection
        // It is pumping data it receives to it's authenticator
        // Once it is authenticated, it starts pumping data to the Authenticated
        // Stanza listener (per the factory configuration above)

        // Fake client input here
        clientCon.handleClientInput(firstStream.getBytes());
        assertTrue("Fail: first server stream", clientCon.gotFirstStream);
        assertTrue("Fail: stream features", clientCon.gotStreamFeatures);

        // Feed the auth module a new stream
        clientCon.handleClientInput(firstStream.getBytes());

        // Send auth data
        clientCon.handleClientInput(auth.getBytes());
        assertTrue("Fail: auth message", clientCon.gotSuccess);
    }

    @Ignore
    public class AuthenticatedStanzaListener implements StanzaProcessor {
        public boolean gotData = false;

        @Override
        public void processStanza(String string, String fromJid) {
            gotData = true;
        }
    }

    @Ignore
    public class MyClientConnection implements ClientConnection {
        public boolean gotFirstStream = false;
        public boolean gotStreamFeatures = false;
        public boolean gotSuccess = false;
        XMLLightweightParser parser = new XMLLightweightParser("UTF-8");

        public MyClientConnection() {
            super();
        }

        @Override
        public void writeRaw(byte[] bytes) {
            try {
                parser.read(bytes);
                if (parser.areThereMsgs()) {
                    for (String msg : parser.getMsgs()) {
                        checkMsg(msg);
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }

        public void checkMsg(String s) {
            // NOTE: ORDER MATTERS HERE!
            if (s.contains("features"))
                gotStreamFeatures = true;
            else if (s.startsWith("<stream"))
                gotFirstStream = true;
            else if (s.startsWith("<success"))
                gotSuccess = true;
        }

        @Override
        public void stop() {}

        /*
         * (non-Javadoc)
         * @see edu.drexel.xop.core.PacketListener#processCloseStream(java.lang.String)
         */
        @Override
        public void processCloseStream(String fromJID) {

        }

        @Override
        public void init(PacketRouter pr, ComponentManager cm) {
            // TODO Auto-generated method stub
        }

        /*
         * (non-Javadoc)
         * @see edu.drexel.xop.core.PacketFilter#accept(org.xmpp.packet.Packet)
         */
        @Override
        public boolean accept(Packet packet) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void enableSSL() {
            // TODO Auto-generated method stub
        }

        @Override
        public void processPacket(Packet p) {
            // TODO Auto-generated method stub
        }

        @Override
        public JID getJid() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void init() {
            // TODO Auto-generated method stub
        }

        @Override
        public void setJid(JID jid) {
            // TODO Auto-generated method stub
        }

        @Override
        public void handleClientInput(byte[] bytes) {
            // TODO Auto-generated method stub
        }

        @Override
        public boolean isLocal() {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void sslPostProcessing() {
            // TODO Auto-generated method stub
        }
    }
}