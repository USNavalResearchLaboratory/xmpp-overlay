package edu.drexel.xop.core;

import edu.drexel.xop.client.XOPConnection;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * Local XMPP clients
 * Created by duc on 2/27/17.
 */

public class LocalXMPPClient extends XMPPClient {
    private XOPConnection xopConnection;

    public LocalXMPPClient(JID clientJID, String displayName, String status, Presence.Show show,
                           XOPConnection xopConnection) {
        super(clientJID, displayName, status, show, NodeStatus.online);
        this.xopConnection = xopConnection;
    }

    public XOPConnection getXopConnection() {
        return xopConnection;
    }

}
