package edu.drexel.xop.core;

import edu.drexel.xop.client.XOPConnection;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Set of static utility functions
 * Created by duc on 11/1/16.
 */

public class ProxyUtils {
    private static Logger logger = LogUtils.getLogger(ProxyUtils.class.getName());
    /**
     * Sends packet to the locally connected client
     * @param p the packet to be sent to the client. must have a to-field
     * @param clientManager client manager
     */
    public static void sendPacketToLocalClient(Packet p, ClientManager clientManager) {
        if( p.getTo() == null ){
            logger.warning("packet has no to field, dropping");
            return;
        }

        XMPPClient xmppClient = clientManager.getXMPPClient(p.getTo());
        if (xmppClient instanceof LocalXMPPClient) {
            XOPConnection connection = ((LocalXMPPClient) xmppClient).getXopConnection();
            if (logger.isLoggable(Level.FINER))
                logger.finer("connection is: " + connection.getAddress() + ", packet to send: {{" + p.toString() + "}}");
            connection.writeRaw(p.toXML().getBytes());
        } else {
            logger.warning("unable to find xmppClient: " + xmppClient + " packet: " + p);
        }
    }

    /**
     * compares two JIDs. and bare JIDs.
     * @param user1 jid1
     * @param user2 jid2
     * @return true if they are the same or bare jids are the same, false otherwise
     */
    public static boolean compareJIDs(JID user1, JID user2) {
        if (logger.isLoggable(Level.FINER)) logger.finer("user1: " + user1 + " user2: " + user2);
        if ((user1 == null && user2 != null)
                || (user1 != null && user2 == null)) {
            return false;
        } else {
            if (user1 == null) { //&& user2 == null
                logger.warning("user1 and user2 are both null!");
                return true;
            }
        }
        return user1.equals(user2) || user1.toBareJID().equals(user2.toBareJID());
    }
}
