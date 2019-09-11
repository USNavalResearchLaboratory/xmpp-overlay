package edu.drexel.xop.client;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.JID;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Authentication provider loads information about users from a sqlite database
 * This is used for client authentication
 *
 * This database also contains stored roster information for groups
 *
 */
public class AuthenticationProvider {
    private static final Logger logger = LogUtils.getLogger(AuthenticationProvider.class.getName());
    private ClientManager clientManager;

    public AuthenticationProvider(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    public boolean authenticate(JID jid, String password) {
        if( logger.isLoggable(Level.FINE) ) logger.fine("Authenticate: (" + jid.toBareJID() + ", [password masked])");
        boolean retVal = !clientManager.clientExists(jid) || !clientManager.getAvailableClientJIDs().contains(jid);
        logger.fine("authenticated? "+retVal
                + "; !clientManager.clientExists(jid) " + (!clientManager.clientExists(jid))
                + "; !clientManager.getAvailableClientJIDs().contains(jid)"
                + (!clientManager.getAvailableClientJIDs().contains(jid)));
        return retVal;
    }

}
