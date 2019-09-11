package edu.drexel.xop.packet.iq;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.core.XMPPClient;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Clients must bind to a resource in order to become "connected resources"
 * TODO: ensure unique resource, handle already bound resources of the same node
 */
class BindIqHandler {
    private static final String BIND_XMLNS = "urn:ietf:params:xml:ns:xmpp-bind";

    private static final Logger logger = LogUtils.getLogger(BindIqHandler.class.getName());

    private ClientManager clientManager;

    BindIqHandler(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * Creates and sends a result IQ packet to the client according to RFC6120 section 9.1.3 "Step 16"
     * http://www.xmpp.org/rfcs/rfc6120.html#examples-c2s-bind
     *
     * @param iq the iq packet received from the client
     */
    void bindResource(JID fromJID, IQ iq) {

        IQ setIQ = iq.createCopy();
        IQ resultIQ = IQ.createResultIQ(setIQ);
        try{
            // Create result IQ per
            resultIQ.setTo(fromJID);

            //set the resource if the client did not specify one
            Element bind = resultIQ.setChildElement("bind", BIND_XMLNS);

            //create a new bind Element
            Element resource = setIQ.getChildElement().element("resource");
            JID jid;
            if(resource == null) {
                jid = new JID(fromJID.toBareJID() + "/" + getUniqueResource());
            } else {
                jid = new JID(fromJID.toBareJID() + "/" + resource.getText());
            }

            Element resultIQJid = new DefaultElement("jid");
            resultIQJid.setText(jid.toString());

            bind.add(resultIQJid);

        } catch( Exception | Error e){
            logger.log(Level.SEVERE,"Error encountered, returning error result IQ.",e);
            resultIQ = IqManager.getErrorForIq(resultIQ, PacketError.Condition.conflict, PacketError.Type.cancel);
        }

        //send it to the client
        if( logger.isLoggable(Level.FINE) ) logger.fine("Responding to bind IQ with: " + resultIQ.toString());
        ProxyUtils.sendPacketToLocalClient(resultIQ, clientManager);
        clientManager.addJIDToAvailableSet(resultIQ.getTo());
        XMPPClient xmppClient = clientManager.getLocalXMPPClient(resultIQ.getTo());
        logger.info("Bound xmppClient: " + xmppClient);
        xmppClient.bindResource(resultIQ.getTo().getResource());
    }

    private String getUniqueResource() {
        return Utils.generateID(8);
    }

}