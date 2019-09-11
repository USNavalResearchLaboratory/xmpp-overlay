package edu.drexel.xop.net;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.XMPPClient;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.packet.TransportPacketProcessor;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * Implements the SDListener methods for handling SD messages from the Transport Service
 *
 * Created by duc on 9/27/16.
 */

public class SDListenerImpl implements SDListener {
    private static Logger logger = LogUtils.getLogger(SDListenerImpl.class.getName());

    private ClientManager clientManager;
    private TransportPacketProcessor transportPacketProcessor;

    SDListenerImpl(ClientManager clientManager, TransportPacketProcessor transportPacketProcessor) {
        this.clientManager = clientManager;
        this.transportPacketProcessor = transportPacketProcessor;
    }

    /**
     * Callback from SDManager to manage when a gateway is added
     * @param address the address of the gateway that is added
     * @param jid domain of the gateway
     */
    public void gatewayAdded(InetAddress address, JID jid) {
        // TODO 2016-06-03 dnn: handle discovered gateways
    }

    /**
     * Callback from SDManager to manage when a gateway is removed
     * @param jid of the gateway to be removed
     */
    public void gatewayRemoved(JID jid) {
        // TODO 2016-06-03 dnn: handle discovered gateways
    }

    /**
     * Present Transport System calls for presence messages from remote clients
     *
     * In order for a local client's roster to be updated
     *
     * @param presence the presence message of the remote client
     */
    public void clientDiscovered(Presence presence) {
        logger.fine("Presence received: ==" + presence.toXML() + "==");
        logger.fine("New REMOTE client found {{" + presence.getFrom() + "}}");
        XMPPClient remoteClient = new XMPPClient(presence.getFrom(), presence.getFrom().toString(),
                presence.getFrom().getResource(),
                "", null, XMPPClient.NodeStatus.online);
        clientManager.addDiscoveredXMPPClient(remoteClient);
        clientManager.addJIDToAvailableSet(presence.getFrom());

        // Determine if the client jid is in everyone's RosterList, add it to the roster list
        transportPacketProcessor.processPacket(presence.getFrom(), presence);
    }

    /**
     * Callback from SDManager for when a presence becomes unavailable
     * @param presence the presence to be removed
     */
    public void clientRemoved(Presence presence) {
        logger.fine("Presence removed: =="+presence.toXML() +"==");
        logger.fine("REMOTE client leaving {{"+presence.getFrom()+"}}");
        clientManager.removeJIDFromAvailableSet(presence.getFrom());
        transportPacketProcessor.processPacket(presence.getFrom(), presence);
    }

    /**
     * Present Transport System calls for presence messages from remote clients
     * changes presence or XOP responds to
     *
     * @param presence updated presence or probe presence
     */
    public void clientUpdated(Presence presence) {
        logger.finer("Update presence ==" + presence.toXML() + "==");
        logger.fine("updated status: " + presence.getStatus() + " show: " + presence.getShow());
        if (presence.getType() != Presence.Type.probe) {
            XMPPClient xmppClient = clientManager.getRemoteXMPPClient(presence.getFrom());
            logger.fine("xmppClient: " + xmppClient);
            xmppClient.setStatus(presence.getStatus());
            xmppClient.setShow(presence.getShow());
        } else {
            logger.fine("presence is probe message, do not update the xmppClient object");
        }
        transportPacketProcessor.processPacket(presence.getFrom(), presence);
    }

    public void clientDisconnected(JID clientJID) {
        logger.fine(clientJID + " is disconnected from network");
        XMPPClient disconnectedClient = clientManager.getRemoteXMPPClient(clientJID);
        disconnectedClient.setStatus("Network partitioned, client disconnected");
        disconnectedClient.setShow(Presence.Show.xa);
        disconnectedClient.setNodeStatus(XMPPClient.NodeStatus.disconnected);

        Presence updatedPresence = disconnectedClient.generateCurrentPresence();
        logger.fine("generated and sending presence: " + updatedPresence);
        transportPacketProcessor.processPacket(updatedPresence.getFrom(), updatedPresence);
    }

    /**
     * Callback from SDManager for when an alias is registered
     * TODO 2018-02-27 dnn Change signature to use the Presence Message. Let ProtoSDListener be the interface that creates the Presence message
     * @param mucPresence the muc occupant joining
     */
    public void mucOccupantJoined(Presence mucPresence) {
        logger.info("registering mucOccupantJID, " + mucPresence.getTo() + " for jid: " + mucPresence.getFrom());
        XOProxy.getInstance().addClientToRoomAndSendOverGateway(mucPresence, false);
    }

    /**
     * Callback from SDmanager for when a MUC occupant is removed
     * i.e. The SD system alerts XOP that a MUC occupant on another XOP instance has left a room.
     * @param mucPresence the mucOccupant's presence
     */
    public void mucOccupantExited(Presence mucPresence) {
        logger.info("unregistering mucOccupantJID, " + mucPresence.getTo() + " for jid: " + mucPresence.getFrom());
        XOProxy.getInstance().removeFromRoomSendOverGateway(mucPresence, false);
    }

    public void mucOccupantUpdated(Presence mucPresence) {
        // TODO 2018-11-21 Implement
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.SDListener#roomAdded(org.xmpp.packet.JID)
     */
    public void roomAdded(JID roomJID) {
        if (!XOProxy.getInstance().getRoomIds().contains(roomJID))
            XOProxy.getInstance().addMUCRoom(roomJID);
        else
            logger.fine("room already added");
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.SDListener#roomRemoved(org.xmpp.packet.JID)
     */
    public void roomRemoved(JID roomJID) {
        throw new UnsupportedOperationException("TODO implement roomRemoved");
    }


}
