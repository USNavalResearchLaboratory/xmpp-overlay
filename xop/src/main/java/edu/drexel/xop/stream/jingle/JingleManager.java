package edu.drexel.xop.stream.jingle;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.net.SDListener;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.logging.Logger;

/**
 * Manages all our active jingle sessions
 *
 * @author Rob Taglang
 */
public class JingleManager implements SDListener {
    private final static Logger logger = LogUtils.getLogger(JingleManager.class.getName());

    private HashSet<JingleSession> sessions = new HashSet<>();
    private static JingleManager instance = null;
    private ClientManager clientManager;

    //private constructor to force getInstance()
    private JingleManager(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    private void addSession(JingleSession session) {
        logger.fine("Registered new jingle session: " + session.getId());
        sessions.add(session);
    }

    private void addSession(InetAddress source, JID initiator, JID responder, String sessionId) {
        addSession(new JingleSession(initiator, responder, sessionId, source));
    }

    public void addSession(InetAddress source, IQ p) {
        Element child = p.getChildElement();
        if(child != null) {
            addSession(source, new JID(child.attributeValue("initiator")), new JID(child.attributeValue("responder")), child.attributeValue("sid"));
        }
        else {
            logger.warning("Invalid IQ packet: " + p);
        }
    }

    /**
     * This gets called by IqManager when a session terminate packet comes in
     * @param id the id of the session
     */
    public void removeSession(String id) {
        for(JingleSession session : sessions) {
            if(session.getId().equals(id)) {
                sessions.remove(session);
                return;
            }
        }
    }

    /**
     * This is used to end a session when a remote connection drops
     * @param session the jingle session to terminate
     */
    public void endSession(JingleSession session) {
        //send end session packet
        try {
            if (clientManager.isLocal(session.getInitiator())) {
                Packet pkt = Utils.packetFromString("<iq xmlns='jabber:client' type='set' id='" + session.getId() + "' from='" + session.getResponder() + "' to='" + session.getInitiator() + "'>" +
                                "<jingle xmlns='" + CONSTANTS.DISCO.JINGLE_NAMESPACE + "' action='session-terminate' initiator='" + session.getInitiator() +
                        "' responder='" + session.getResponder() + "' sid =''><reason><success/></reason></jingle></iq>");
                ProxyUtils.sendPacketToLocalClient(pkt, clientManager);
            } else if (clientManager.isLocal(session.getResponder())) {
                Packet pkt = Utils.packetFromString("<iq xmlns='jabber:client' type='set' id='"
                        + session.getId() + "' from='" + session.getInitiator()
                        + "' to='" + session.getResponder() + "'>" +
                        "<jingle xmlns='" + CONSTANTS.DISCO.JINGLE_NAMESPACE + "' action='session-terminate' initiator='"
                        + session.getInitiator() +
                        "' responder='" + session.getResponder()
                        + "' sid =''><reason><success/></reason></jingle></iq>");
                ProxyUtils.sendPacketToLocalClient(pkt, clientManager);
            }
        }
        catch(DocumentException e) {
            logger.severe("Unable to parse packet!");
        }
        sessions.remove(session);
    }

    @Override
    public void gatewayAdded(InetAddress address, JID domain) {
        //do nothing
    }

    @Override
    public void gatewayRemoved(JID domain) {
        //TODO: support gatewayed clients
    }

    @Override
    public void clientDiscovered(Presence presence) {
        //do nothing
    }

    @Override
    public void clientRemoved(Presence presence) {
       //do nothing
    }

    @Override
    public void clientDisconnected(JID clientJID) {

    }

    @Override
    public void clientReconnected(JID clientJID) {

    }

    @Override
    public void clientUpdated(Presence presence) {
    }

    @Override
    public void mucOccupantJoined(Presence presence) {
        //do nothing
    }

    @Override
    public void mucOccupantExited(Presence presence) {
        //do nothing
    }

    @Override
    public void mucOccupantUpdated(Presence presence) {
    }

    @Override
    public void roomAdded(JID roomJID) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void roomRemoved(JID roomJID) {
        // TODO Auto-generated method stub
        
    }
}
