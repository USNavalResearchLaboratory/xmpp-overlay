package edu.drexel.xop.packet.iq;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.IQ;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles session requests per RFC 3921.
 * Required for messaging and presence.  After a session is established, a client
 * is considered an "active resource."
 * https://xmpp.org/rfcs/rfc3921.html#session
 *
 */
class SessionIqHandler {

    private static final Logger logger = LogUtils.getLogger(SessionIqHandler.class.getName());

    private ClientManager clientManager;

    SessionIqHandler(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * sends a resultIQ message based on the iq to the client
     *
     * @param iq the iq session packet
     */
    void processIQPacket(IQ iq) {
        if( logger.isLoggable(Level.FINE) ) logger.fine("Responding to session IQ with: " + iq.toString());
        ProxyUtils.sendPacketToLocalClient(IQ.createResultIQ(iq), clientManager);
    }
}
