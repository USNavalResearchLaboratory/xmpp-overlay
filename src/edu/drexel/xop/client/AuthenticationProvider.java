/*
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.client;

import java.util.logging.Logger;

import org.xmpp.packet.JID;

import edu.drexel.xop.util.logger.LogUtils;

/**
 * @author David Millar
 * description: authentication provider authenticates everyone regardless of username
 * and password
 */
class AuthenticationProvider {
    private static final Logger logger = LogUtils.getLogger(AuthenticationProvider.class.getName());
    private ClientManager clientManager;

    public AuthenticationProvider(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    public boolean authenticate(JID jid, String password) {
        logger.fine("AUTHENTICATING (" + jid.toBareJID() + ", " + password);
        return !clientManager.userExists(jid);
    }
}
