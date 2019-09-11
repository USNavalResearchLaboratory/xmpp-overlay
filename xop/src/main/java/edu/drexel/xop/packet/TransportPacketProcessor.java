package edu.drexel.xop.packet;

import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.core.XMPPClient;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.net.SDManager;
import edu.drexel.xop.util.logger.LogUtils;

import static org.xmpp.packet.Presence.Type.probe;

/**
 * accept XMPP messages (Messate, IQ, Presence) coming from Transport System deliver to XOP for handling
 */
public class TransportPacketProcessor extends AbstractPacketProcessor {
    private static Logger logger = LogUtils.getLogger(TransportPacketProcessor.class.getName());
    private SDManager sdManager;
    private TransportPresenceManager transportPresenceManager;

    public TransportPacketProcessor(ClientManager clientManager) {
        super(clientManager);
    }

    public void processPacket(JID fromJID, Packet p) {
        p = stripJabberServerNamespace(p);

        if (p instanceof IQ) {
            iqManager.processIQPacket(fromJID, p);
        } else if (p instanceof Presence) {
            processPresencePacketFromNetwork((Presence) p);
        } else if (p instanceof Message) {
            Message m = (Message) p;
            if (isForMUC((m))) {
                JID roomJID = m.getTo().asBareJID();

                if (!XOProxy.getInstance().getRoomIds().contains(roomJID)) {
                    roomJID = p.getFrom().asBareJID();
                }
                logger.fine("roomJID: " + roomJID);
                logger.fine("element.asXML: " + m.getElement().asXML());
                logger.fine("element: " + m.getElement().getNamespace());
                handleMessageForRoom(roomJID, m, false);

            } else {
                handleOneToOneMessage(m, false);
            }
        }
    }

    private boolean isForMUC(Message message) {
        return (message.getType() == Message.Type.groupchat
                || XOProxy.getInstance().getRoomOccupantIds().contains(message.getTo()));
    }

    /**
     * note: this must be called prior to usage.
     *
     * @param sdManager the sdmanager for sending messages back to transport when needed
     */
    public void addSDManager(SDManager sdManager) {
        this.sdManager = sdManager;
        transportPresenceManager = new TransportPresenceManager(clientManager, sdManager, rosterListManager);
    }


    /**
     * process one-to-one presence message from the network
     */
    private void processPresencePacketFromNetwork(Presence presence) {
        if (logger.isLoggable(Level.FINER)) logger.finer("processing presence: " + presence);

        if( transportPresenceManager == null || sdManager == null) {
            logger.severe("Presence Transport not initialized yet!");
            return;
        }

        JID contact = presence.getTo();
        JID userJID = presence.getFrom();
        // 2018-11-27 dnn support NormPresenceTransport presence probes

        if (contact != null && clientManager.isLocal(contact)) {
            logger.fine("Presence has a destination and is local, responding");
            if (presence.getType() == probe) {
                // Follow RFC6121 Ch 4.3

                XMPPClient localXMPPClient = clientManager.getLocalXMPPClient(contact);

                // RFC6121 Ch4.3.2
                //   item 1: Irrelevant, all contacts default subscription of both
                //   item 2: not supported at this time, assumes contact will be on this XOP
                //   item 3: If the user is not available, respond with an unavailable and last seen
                //   item 4: send the full XML stanza of the last
                Presence responsePresence = localXMPPClient.generateCurrentPresence();

                logger.fine("sending probe response to network: " + responsePresence);
                sdManager.updateClientStatus(responsePresence);
            }
            logger.fine("exiting handling probe");
            return;
        }
        // From SDListenerImpl TODO 2018-12-05 make sure this works
        rosterListManager.addDiscoveredClientToRosterLists(presence.getFrom());

        transportPresenceManager.sendPresenceToLocalUsersWithDifferentDomain(presence.getFrom(), presence.getType());

        for (JID user : clientManager.getLocalClientJIDs()) {
            if (logger.isLoggable(Level.FINE)) logger.fine("local toJID: " + user);
            if (!ProxyUtils.compareJIDs(user, presence.getFrom())) {
                Presence p = new Presence(presence.getType());
                p.setFrom(presence.getFrom());
                p.setTo(user);
                p.setStatus(presence.getStatus());
                p.setPriority(presence.getPriority());
                p.setShow(presence.getShow());
                logger.info("sending packet ==" + p.toXML() + "== to local client");
                ProxyUtils.sendPacketToLocalClient(p, clientManager);
            }
        }

        logger.finer("exiting.");
    }

}
