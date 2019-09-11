package edu.drexel.xop.net.protosd;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.net.SDListener;
import edu.drexel.xop.net.protosd.discoverable.GatewayDO;
import edu.drexel.xop.net.protosd.discoverable.MucOccupantDO;
import edu.drexel.xop.net.protosd.discoverable.MucRoomDO;
import edu.drexel.xop.net.protosd.discoverable.PresenceDO;
import mil.navy.nrl.protosd.api.DiscoverableObjectListener;
import mil.navy.nrl.protosd.api.distobjects.DiscoverableObject;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

class XopDOListener implements DiscoverableObjectListener {
    private static Logger logger = Logger.getLogger(XopDOListener.class.getName());
    private HashSet<SDListener> listeners;
    private ClientManager clientManager;

    XopDOListener(ClientManager clientManager) {
        listeners = new HashSet<>();
        this.clientManager = clientManager;
    }

    void addListener(SDListener listener){
        listeners.add(listener);
    }

    @Override
    public void discoverableObjectAdded(DiscoverableObject discoverableObject) {
        logger.fine("Adding discoverable object");
        if (logger.isLoggable(Level.FINER)) logger.finer("adding " + discoverableObject);
        if(discoverableObject instanceof PresenceDO) {
            PresenceDO pdo = (PresenceDO) discoverableObject;
            Presence presence = new Presence();  //((PresenceDO)discoverableObject).getPresence();
            presence.setFrom(pdo.getFromJID());
            for(SDListener listener : listeners) {

                if (!clientManager.isLocal(presence.getFrom())) {
                    listener.clientDiscovered(presence);
                } else {
                    logger.warning("Not handling a discovered advert from Local Client: "+presence);
                }
            }
        } else if(discoverableObject instanceof MucOccupantDO) {
            for(SDListener listener : listeners) {
                MucOccupantDO discoveredMucOccupant = ((MucOccupantDO)discoverableObject);
                JID clientJID = discoveredMucOccupant.getClientJID();
                if (!clientManager.isLocal(clientJID)) {
                    logger.fine("discovered a new MUC Occupant. MucOccupantJid: =="
                            + discoveredMucOccupant.getMucOccupantJID()
                            + " From xmpp client: " + discoveredMucOccupant.getClientJID());
                    Presence mucPresence = new Presence();
                    mucPresence.setTo(discoveredMucOccupant.getMucOccupantJID());
                    mucPresence.setFrom(discoveredMucOccupant.getClientJID());
                    mucPresence.addChildElement("x", "http://jabber.org/protocol/muc");
                    listener.mucOccupantJoined(mucPresence);
                } else {
                    logger.fine("discovered LOCAL MucOccupantJid: ==" + discoveredMucOccupant.getMucOccupantJID()
                            + " From xmpp client: " + discoveredMucOccupant.getClientJID());

                }
            }
        } else if(discoverableObject instanceof GatewayDO) {
            for(SDListener listener : listeners) {
                listener.gatewayAdded(((GatewayDO) discoverableObject).getGatewayAddress(),
                        ((GatewayDO) discoverableObject).getGatewayJID());
            }
        } else if(discoverableObject instanceof MucRoomDO){
            for(SDListener listener : listeners) {
                MucRoomDO discoveredRoom = (MucRoomDO) discoverableObject;

                listener.roomAdded(discoveredRoom.getRoomJID());
            }
        }


    }

    @Override
    public void discoverableObjectRemoved(DiscoverableObject discoverableObject) {
        logger.fine("Removing discoverable object");
        logger.finer(""+discoverableObject);
        if(discoverableObject instanceof PresenceDO) {
            for(SDListener listener : listeners) {
                PresenceDO pdo = (PresenceDO) discoverableObject;
                Presence unavailablePresence = new Presence(Presence.Type.unavailable);
                unavailablePresence.setFrom(pdo.getFromJID());
                //((PresenceDO)discoverableObject).getUnavailablePresence();
                if (!clientManager.isLocal(unavailablePresence.getFrom())) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("removing XMPP Client with clientJID: =="
                                + unavailablePresence.getFrom() + " From xmpp client: "
                                + pdo.getFromJID());
                    }
                    listener.clientRemoved(unavailablePresence);
                } else {
                    logger.warning("Not processing remove client from local client: "+unavailablePresence);
                }
            }
        } else if(discoverableObject instanceof MucOccupantDO) {
            for(SDListener listener : listeners) {
                MucOccupantDO discoveredMucOccupant = ((MucOccupantDO)discoverableObject);
                JID clientJID = discoveredMucOccupant.getClientJID();
                if (!clientManager.isLocal(clientJID)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("removing MUCOccupant with MucOccupantJid: =="
                                + discoveredMucOccupant.getMucOccupantJID() + " From xmpp client: "
                                + discoveredMucOccupant.getClientJID());
                    }
                    Presence mucPresence = new Presence(Presence.Type.unavailable);
                    mucPresence.setTo(discoveredMucOccupant.getMucOccupantJID());
                    mucPresence.setFrom(discoveredMucOccupant.getClientJID());

                    listener.mucOccupantExited(mucPresence);
                } else {
                    logger.fine("Removed LOCAL MucOccupantJid: ==" + discoveredMucOccupant.getMucOccupantJID()
                            + " From xmpp client: " + discoveredMucOccupant.getClientJID());
                }
            }
        } else if(discoverableObject instanceof GatewayDO) {
            for(SDListener listener : listeners) {
                listener.gatewayRemoved(((GatewayDO)discoverableObject).getGatewayJID());
            }
        } else if(discoverableObject instanceof MucRoomDO){
            for(SDListener listener : listeners) {
                MucRoomDO discoveredRoom = (MucRoomDO) discoverableObject;

                listener.roomRemoved(discoveredRoom.getRoomJID());
            }
        }
    }

}
