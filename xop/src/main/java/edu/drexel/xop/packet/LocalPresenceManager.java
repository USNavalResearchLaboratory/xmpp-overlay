package edu.drexel.xop.packet;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.core.XMPPClient;
import edu.drexel.xop.core.roster.RosterListManager;
import edu.drexel.xop.net.SDManager;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.DocumentException;
import org.xmpp.packet.Presence;

import java.util.logging.Logger;

class LocalPresenceManager extends AbstractPresenceManager {
    private static Logger logger = LogUtils.getLogger(LocalPresenceManager.class.getName());

    LocalPresenceManager(ClientManager clientManager, SDManager sdManager, RosterListManager rosterListManager) {
        super(clientManager, rosterListManager, sdManager);
    }

    /**
     * Process presence packets from local/connected clients
     *
     * @param presence packet from local/connected clients
     */
    void processPresencePacket(Presence presence) {
        if (isForMUC(presence)) {
            //the presence is for a chat room
            logger.fine("presence message destined for MUC room");
            processMUCPresence(presence);
            return;
        }
        if (presence.getTo() != null) {
            logger.fine("Presence has a destination, handle appropriately");

            //this packet should be sent somewhere specific
            handlePresenceWithDestination(presence);
            return;
        }

        if (Utils.isOnDifferentDomain(presence.getFrom())) {
            ProxyUtils.sendPacketToLocalClient(presence, clientManager);
        }
        XMPPClient client = clientManager.getLocalXMPPClient(presence.getFrom());
        client.setShow(presence.getShow());
        client.setStatus(presence.getStatus());
        client.setLastPresenceId(presence.getID());

        Presence sentPresence;
        try {
            sentPresence = (Presence) Utils.packetFromString(
                    Utils.stripNamespace(presence.toXML(), "jabber:client"));
        } catch (DocumentException e) {
            sentPresence = presence;
        }

        if (sentPresence.isAvailable()) {
            logger.fine("Available Presence");
            logger.finer("presence: " + sentPresence);
            /// this block from handlePresence(presence)
            // JID fromJID = new JID(presence.getFrom().toBareJID());

            sendPresenceToLocalUsersWithDifferentDomain(sentPresence.getFrom(), sentPresence.getType());
            sendAvailablePresenceToLocalClients(sentPresence.getFrom());

            //notify remote clients
            sdManager.advertiseClient(sentPresence);
        } else { // presence is an unavailable presence
            clientManager.removeJIDFromAvailableSet(sentPresence.getFrom());

            //notify remote clients
            sdManager.removeClient(sentPresence);
        }

    }
}
