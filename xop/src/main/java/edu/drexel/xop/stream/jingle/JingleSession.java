package edu.drexel.xop.stream.jingle;

/**
 * (c) 2013 Drexel University
 */

import org.xmpp.packet.JID;

import java.net.InetAddress;

/**
 * Holds information about our active jingle sessions
 *
 * @author Rob Taglang
 */
public class JingleSession {
    private JID initiator, responder;
    private String sessionId;
    private InetAddress source;

    JingleSession(JID initiator, JID responder, String sessionId, InetAddress source) {
        this.source = source;
        this.initiator = initiator;
        this.responder = responder;
        this.sessionId = sessionId;
    }

    JID getInitiator() {
        return initiator;
    }

    JID getResponder() {
        return responder;
    }

    public String getId() {
        return sessionId;
    }

    public InetAddress getSource() {
        return source;
    }

    public boolean equals(JingleSession session) {
        return session != null &&
               getInitiator() != null && getInitiator().equals(session.getInitiator()) &&
               getResponder() != null &&  getResponder().equals(session.getResponder()) &&
               getId() != null && getId().equals(session.getId());
    }
}
