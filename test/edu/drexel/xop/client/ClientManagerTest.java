/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.client;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import edu.drexel.xop.component.ComponentManager;
import edu.drexel.xop.router.PacketRouter;

/**
 * 
 * @author dave
 */
@SuppressWarnings("unused")
public class ClientManagerTest {
    private ClientManager clientManager = null;
    private static final String DAVE_AT_PROXY = "dave@proxy";

    @Before
    public void init() {
        // ClientManager
        clientManager = new ClientManager(new PacketRouter());
        // JID
        JID jid = new JID(DAVE_AT_PROXY);
    }

    @Test
    public void testClientManager() {
        MyClientConnection clientCon = new MyClientConnection();
        clientCon.setJid(new JID(DAVE_AT_PROXY));

        MyClientListener listener = new MyClientListener();
        clientManager.addLocalClientListener(listener);

        clientManager.addLocalUser(clientCon);
        assertTrue(listener.gotDaveAdded);

        clientManager.removeLocalClient(clientCon.getJid().toBareJID());
        assertTrue(listener.gotDaveRemoved);
    }

    @Ignore
    public static class MyClientListener implements LocalClientListener {
        public boolean gotDaveAdded = false;
        public boolean gotDaveRemoved = false;

        @Override
        public void localClientAdded(ClientConnection cc) {
            if (JID.equals(cc.getJid().toBareJID(), DAVE_AT_PROXY))
                gotDaveAdded = true;
        }

        @Override
        public void localClientRemoved(ClientConnection cc) {
            if (JID.equals(cc.getJid().toBareJID(), DAVE_AT_PROXY))
                gotDaveRemoved = true;
        }
    }

    @Ignore
    public class MyClientConnection implements ClientConnection {
        public MyClientConnection() {}

        public void writeRaw(byte[] bytes) {}

        public void stop() {}

        /*
         * (non-Javadoc)
         * @see edu.drexel.xop.core.PacketListener#processCloseStream(java.lang.String)
         */
        public void processCloseStream(String fromJID) {
            // test code, we don't need anything here.
        }

        public void init(PacketRouter pr, ComponentManager cm) {
            // TODO Auto-generated method stub
        }

        /*
         * (non-Javadoc)
         * @see edu.drexel.xop.core.PacketFilter#accept(org.xmpp.packet.Packet)
         */
        public boolean accept(Packet packet) {
            // TODO Auto-generated method stub
            return false;
        }

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

    /**
     * This dialog is expected between the server and client:
     * ******************************************************
     * <stream:stream to='proxy' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>
     * <stream:stream from='proxy' id="multicastX" xmlns="jabber:client" xmlns:stream="http://etherx.jabber.org/streams" version="1.0">
     * 
     * 
     * 
     * <!-- Here, the proxy enumerates available authentication mechanisms to the client -->
     * <stream:features>
     * <mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
     * <mechanism>PLAIN</mechanism>
     * </mechanisms>
     * </stream:features>
     * 
     * 
     * 
     * <!-- Client authenticates -->
     * <auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN' xmlns:ga='http://www.google.com/talk/protocol/auth' ga:client-uses-full-bind-result='true'>AGRhdmlkAGphd24=</auth>
     * <success xmlns="urn:ietf:params:xml:ns:xmpp-sasl"></success>
     * 
     * 
     * 
     * <!-- New stream, sometimes with encryption / compression, etc -->
     * <stream:stream to='proxy' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' version='1.0'>
     * <stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' id='c2s_345' from='proxy' version='1.0'>
     * 
     * ********************************************************
     */
}