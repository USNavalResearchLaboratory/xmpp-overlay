/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq;

import java.util.Iterator;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import edu.drexel.xop.core.PacketFilter;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Clients must bind to a resource in order to become "connected resources"<br/>
 * TODO: ensure unique resource, handle already bound resources of the same node
 * 
 * @author David Millar
 */
public class BindIqHandler extends IqHandler implements PacketFilter {
    private static final String BIND_XMLNS = "urn:ietf:params:xml:ns:xmpp-bind";
    private Random random;

    private static final Logger logger = LogUtils.getLogger(BindIqHandler.class.getName());

    public BindIqHandler() {
        logger.fine("BindIqHandler being constructed");
        random = new Random(System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    @Override
    public IQ handleIq(IQ iq) {
        logger.finest("handle iq packet: " + iq.toXML());
        IQ result = IQ.createResultIQ(iq);
        String id = iq.getID();
        Element e = iq.getChildElement();
        Iterator<Element> es = e.elementIterator();

        // Get Resource
        String resource;
        if (es.hasNext()) {
            Element child = es.next();
            resource = child.getText();
        } else {
            resource = getUniqueResource();
        }

        // Set the clients resource
        JID jid = iq.getFrom();
        JID newJid = new JID(jid.getNode(), jid.getDomain(), resource, true);

        // Respond with an "ok"
        String response = "<iq type='result' id='" + id + "' to='" + iq.getFrom()
            + "'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><jid>" + newJid.toFullJID() + "</jid></bind></iq>";
        try {
            result = (IQ) Utils.packetFromString(response);
        } catch (DocumentException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return result;
    }

    public boolean accept(Packet packet) {
        if ((packet instanceof IQ)) {
            IQ iq = (IQ) packet;
            if (iq.getType() == IQ.Type.set) {
                Element e = iq.getChildElement();
                String ns = e.getNamespaceURI();
                return ns.equals(BIND_XMLNS);
            }
        }
        return false;
    }

    // TODO: check for uniqueness per bare JID
    private String getUniqueResource() {
        int n = random.nextInt(99999);
        return String.valueOf(n);
    }
}