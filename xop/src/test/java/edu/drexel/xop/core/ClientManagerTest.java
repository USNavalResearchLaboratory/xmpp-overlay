package edu.drexel.xop.core;

import edu.drexel.xop.client.XOPConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import java.net.InetAddress;

/**
 * Unit test for ClientManager
 * Created by duc on 2/28/17.
 */

public class ClientManagerTest {
    private ClientManager clientManager = new ClientManager();
    private JID localXMPPClientJID = new JID("localxmpp@yahoo.com/resource");
    private JID remoteXMPPClientJID = new JID("remotexmpp@yahoo.com/resource");

    @BeforeEach
    public void setup(){
        clientManager.removeAllClients();

        // create initial clientManager with pre-populated values.
        clientManager.addDiscoveredXMPPClient(
                new XMPPClient(remoteXMPPClientJID, "remotexmpp",
                        null, null, XMPPClient.NodeStatus.online));
        clientManager.addLocalXMPPClient(
                new LocalXMPPClient(
                        localXMPPClientJID,
                        "localxmpp", "status", null,
                        mockXOPConnection
                ) );
    }

    @AfterEach
    public void teardown(){
        // remove all values in the clientManager
        clientManager.removeAllClients();
    }

    @Test
    public void testAddRemoveXMPPClients(){

        Assertions.assertEquals(2, clientManager.getXMPPClients().size(),
                "xmppClients: " + clientManager.getXMPPClients());

        JID jid = new JID("newlocal@yahoo.com/resource");
        LocalXMPPClient newLocalClient = new LocalXMPPClient(
                jid,
                "newlocal", "status", null,
                mockXOPConnection
        );

        clientManager.addLocalXMPPClient(newLocalClient);
        Assertions.assertEquals(3, clientManager.getXMPPClients().size());

        Assertions.assertEquals(1, clientManager.getRemoteClients().size());
        Assertions.assertEquals(1, clientManager.getAvailableClientJIDs().size());
        clientManager.addJIDToAvailableSet(jid);
        Assertions.assertEquals(2, clientManager.getAvailableClientJIDs().size());
        clientManager.removeJIDFromAvailableSet(remoteXMPPClientJID);
        Assertions.assertEquals(1, clientManager.getRemoteClients().size());
        Assertions.assertEquals(1, clientManager.getAvailableClientJIDs().size());
    }


    private XOPConnection mockXOPConnection = new XOPConnection() {
        @Override
        public void writeRaw(byte[] bytes) {

        }

        @Override
        public void processCloseStream() {

        }

        @Override
        public InetAddress getAddress() {
            return null;
        }

        @Override
        public String getHostName() {
            return "mockXOPConnection";
        }

        @Override
        public void run() {

        }
    };
}
