/*
 * Copyright (C) Drexel University 2012
 */
package edu.drexel.xop.client;

import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import edu.drexel.xop.net.discovery.UserDiscoverableObject;

/**
 * @author duc
 *         <p/>
 *         Description: a representation of an XMPP client that is discovered through the SD system
 * @since Mar 9, 2012
 */
public class RemoteClient {
    private UserDiscoverableObject userSDObj;
    private JID jid;
    private Presence presence;

    /**
     * @param userSDObj
     */
    public RemoteClient(UserDiscoverableObject userSDObj) {
        super();
        this.userSDObj = userSDObj;

        this.jid = new JID(userSDObj.getJid());

        presence = generatePresence(userSDObj);
    }

    /**
     * @param user
     * @return
     */
    private Presence generatePresence(UserDiscoverableObject user) {
        Presence p = new Presence();

        p.setFrom(jid);
        p.setID("1");
        p.setStatus(user.getStatus());

        return p;
    }

    /**
     * @return the userSDObj
     */
    public UserDiscoverableObject getUserSDObj() {
        return userSDObj;
    }

    /**
     * @return the jid
     */
    public JID getJid() {
        return jid;
    }

    /**
     * @return the presence
     */
    public Presence getPresence() {
        return presence;
    }

}
