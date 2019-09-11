package edu.drexel.xop.packet;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.XMPPClient;
import edu.drexel.xop.core.roster.RosterListManager;
import edu.drexel.xop.net.SDManager;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.Presence;

import java.util.logging.Logger;

/**
 * used by TransportSystem to advertise
 */
class TransportPresenceManager extends AbstractPresenceManager {
    private static Logger logger = LogUtils.getLogger(LocalPresenceManager.class.getName());

    private SDManager sdManager;

    TransportPresenceManager(ClientManager clientManager, SDManager sdManager, RosterListManager rosterListManager) {
        super(clientManager, rosterListManager);
        this.sdManager = sdManager;
    }

    /**
     * Process presence packets from Transport System
     *
     * @param presence packet from local/connected clients
     */
    void processPresencePacket(Presence presence) {
        // TODO 2018-12-17 ensure these are handled properly
        if (isForMUC(presence)) {
            //the presence is for a chat room
            logger.fine("presence message destined for MUC room");
            processMUCPresence(presence);
            return;
        }

        // presence with destination
        if (presence.getTo() != null) {
            logger.fine("Presence has a destination, handle appropriately");

            //this packet should be sent somewhere specific
            handlePresenceWithDestination(presence);
            return;
        }

        // if (Utils.isOnDifferentDomain(presence.getFrom())) {
        //     ProxyUtils.sendPacketToLocalClient(presence, clientManager);
        // }
        //

        XMPPClient client = clientManager.getLocalXMPPClient(presence.getFrom());
        client.update(presence);

        if (presence.isAvailable()) {
            logger.fine("Available Presence");
            logger.finer("presence: " + presence.toXML());
            /// this block from handlePresence(presence)
            // JID fromJID = new JID(presence.getFrom().toBareJID());

            sendPresenceToLocalUsersWithDifferentDomain(presence.getFrom(), presence.getType());
            sendAvailablePresenceToLocalClients(presence.getFrom());

            //notify remote clients
            // sdManager.advertiseClient(presence);
        } else { // presence is an unavailable presence
            clientManager.removeJIDFromAvailableSet(presence.getFrom());

            //notify remote clients
            // sdManager.removeClient(presence);
        }

    }
}
