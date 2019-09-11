package edu.drexel.xop.core;

import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.gateway.ServerDialbackSession;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.transport.XOPTransportService;
import edu.drexel.xop.packet.LocalPacketProcessor;
import edu.drexel.xop.room.Room;
import edu.drexel.xop.room.RoomManager;
import edu.drexel.xop.stream.StreamListenerThread;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.xop.client.ClientConnectionKt;
import mil.navy.nrl.xop.util.addressing.NetUtilsKt;

/**
 * Facade to the client proxy, also a container for the major components.
 * All clients send all messages to this component.
 */
public class XOProxy {
	private static final Logger logger = LogUtils.getLogger(XOProxy.class.getName());

	private static XOProxy instance = null;
    private Thread slt, serverDialbackThread;
    // private HashSet<PacketProcessor> incoming = new HashSet<>();
    private final Map<String, RoomManager> roomManagers;
    private ServerDialbackSession serverDialbackSession;
    private ClientManager clientManager;
    private LocalPacketProcessor localPacketProcessor;

    private XopNet xopNet;
    // private RosterListManager rosterListManager;

    /**
	 * This is a private constructor to force use of the factory method
	 */
	private XOProxy() {
        roomManagers = Collections.synchronizedMap(new HashMap<>());
        String conferenceDomain = XOP.CONFERENCE_SUBDOMAIN + "." + XOP.DOMAIN;
        roomManagers.put(conferenceDomain,
                new RoomManager(conferenceDomain,
                        "XO Service for " + XOP.CONFERENCE_SUBDOMAIN));

        clientManager = new ClientManager();
	}

	public static XOProxy getInstance() {
		if(instance == null) {
			instance = new XOProxy();
		}
		return instance;
	}

    public XopNet getXopNet(){
        return this.xopNet;
    }

	/**
	 * 
	 * Starts:
	 * - ClientManager as an outgoing XMPP packet handling object
     * - LocalPacketProcessor as an incoming XMPP packets
	 * - ClientListenerThread for locally connected XMPP clients, 
	 * - EXPERIMENTAL: StreamListenerThread: for handling streams
     *
	 * @return The reason for the error. Null if there were no errors.
     */
	public String init(XopNet xopNet) {
		logger.fine("Initializing XOProxy for domain: " + XOP.DOMAIN);
		try {
            this.xopNet = xopNet;
            this.xopNet.init();
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException |
                InitializationException | IllegalAccessException e) {
            e.printStackTrace();
            logger.severe( "ProtoSD failed to initialize! : "+e.getMessage());
			this.stop();
			return e.getMessage();
        } catch (IOException e) {
            e.printStackTrace();
            logger.severe("IOException exception caught! Check if TransportEngine is running!"
                    + e.getMessage());
            this.stop();
            return e.getMessage();
        } catch (Exception e){
            logger.severe("Generic exception: " + e.getMessage());
            e.printStackTrace();
            this.stop();
            return e.getMessage();
        }

        localPacketProcessor = new LocalPacketProcessor(clientManager, xopNet.getSDManager());

        logger.info("Starting client listener thread");

        InetAddress clientBindAddress = NetUtilsKt.getBindAddress(XOP.BIND.INTERFACE);
        Thread clientConnectionThread = ClientConnectionKt.listenForClients(clientBindAddress, XOP.PORT,
                clientManager, localPacketProcessor);
        // try {
        //     clt = new Thread(new ClientListenerThread(clientBindAddress, XOP.PORT));
        //     clt.start();
        // } catch (IOException ioe) {
        //     logger.severe("Unable to start ClientListenerThread. Exiting.");
        //     this.stop();
        //     return "Unable to listen for XMPP Clients";
        // }

		//TODO: This should be more robust, allowing for multiple gateways and retrying connections
		if(XOP.ENABLE.GATEWAY) {
            logger.info("Gateway enabled and initializing");
            serverDialbackSession = new ServerDialbackSession(XOP.GATEWAY.BINDINTERFACE, XOP.GATEWAY.PORT,
                    XOP.GATEWAY.SERVER, XOP.GATEWAY.DOMAIN, XOP.GATEWAY.RECONNECT, localPacketProcessor);
            serverDialbackThread = new Thread(serverDialbackSession);
            serverDialbackThread.start();
		}

		if(XOP.ENABLE.STREAM) {
			logger.info("Starting stream listener thread");
			try {
                slt = new Thread(new StreamListenerThread(InetAddress.getLocalHost(), XOP.STREAM.PORT,
                        clientManager, localPacketProcessor));
                slt.start();
            } catch (IOException e) {
                logger.severe("Unable to start StreamListenerThread. Exiting.");
                this.stop();
                e.printStackTrace();
                return "Unable to listen for Streams";
			}
		}
        logger.info("XOProxy initialized()");


        try {
            clientConnectionThread.join();

            if (XOP.ENABLE.GATEWAY)
                serverDialbackThread.join();
            if (XOP.ENABLE.STREAM)
                slt.join();

        } catch (InterruptedException e) {
            e.printStackTrace();

        }
        return null;
	}

	public synchronized void stop() {
		// Drop all connections to TransportEngineAPI
        synchronized (roomManagers) {
            for (RoomManager manager : roomManagers.values()) {
                for (Room room : manager.getRooms()) {
                    room.close();
                }
            }
            roomManagers.clear();
        }
        // incoming.clear();

		// Send unavailable presence messages
        Set<JID> users = clientManager.getLocalClientJIDs();
        for(JID user : users) {
            announceUnavailableClient(user);
        }

		// Shut down XOP Network
		logger.log(Level.INFO, "Shutting down XopNetImpl...");
		xopNet.close();

		// Stop the ClientListenerThread
        ClientConnectionKt.getRunning().set(false);
        // if(clt != null) {
        //     try {
        //
        //         clt.join();
        //     } catch (InterruptedException e) {
        //         e.printStackTrace();
        //     }
        // }

		//stop the S2SReceivingListenerThread
		if(serverDialbackSession != null) {
			serverDialbackSession.shutdown();
		}

		//stop the StreamListenerThread
		if(slt != null) {
            try {
                slt.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

		logger.log(Level.INFO, "Shut down complete.");
	}

    public ClientManager getClientManager() {
        return clientManager;
    }

    public LocalPacketProcessor getLocalPacketProcessor() {
        return localPacketProcessor;
    }

    /**
     * Adds a discovered room to the RoomManager keyed by roomJID domain
     *
     * @param roomJID
     *         the JID of the MUC room
     */
    public void addMUCRoom(JID roomJID) {
        getRoom(roomJID, false);
    }

    /**
     * @param roomJID The JID of the room to search for
     * @param advertise true if advertising a newly created room
     *
     * @return the room for this JID
     */
    private Room getRoom(JID roomJID, boolean advertise) {
        RoomManager roomManager = roomManagers.get(roomJID.getDomain());
        if (roomManager == null) {
            logger.info(" Room manager does not exist for " + roomJID
                    + ". Creating new RoomManager with domain " + roomJID.getDomain());
            roomManager = new RoomManager(roomJID.getDomain(), "MUC Service for " + roomJID.getDomain());
            roomManagers.put(roomJID.getDomain(), roomManager);
        }
        try {
            Room room = roomManager.getRoom(roomJID);
            if (room == null) {
                logger.info(" Creating new Room, " + roomJID + " for this room domain: " + roomManager.getDomain());
                XOPTransportService transportService = xopNet.createXOPTransportService(roomJID);
                room = new Room(roomJID, clientManager, transportService);
                roomManager.addRoom(room);
                if (advertise) {
                    logger.fine("Advertising new room " + roomJID);
                    xopNet.getSDManager().advertiseMucRoom(room);
                }
            }
            return room;
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, "error retrieving room with roomJID " + roomJID, ioe);
            return null;
        }
    }

    /**
     * @param presence the MUC occupant entering Presence message
     * @param advertiseRoom if true, advertise this room if it is being created for the first time.
     */
    private void addClientToRoomRoot(Presence presence, boolean advertiseRoom) {
        JID clientJID = presence.getFrom();
        JID mucOccupantJID = presence.getTo();
        logger.fine("Adding fullJID: " + clientJID + ", with mucOccupantJID: " + mucOccupantJID
                + ", to room and advertising");

        JID roomJID = new JID(mucOccupantJID.toBareJID());
        Room room = getRoom(roomJID, advertiseRoom);
        room.addMucOccupantToRoom(presence);

        if (clientManager.isLocal(clientJID)) {
			logger.fine("Advertising: client " + clientJID + ", mucOccupantJID: " + mucOccupantJID);
			xopNet.getSDManager().advertiseMucOccupant(presence);
		} else {
            logger.fine("not advertising this muc occupant with clientJID [" + clientJID + "]");
		}
	}

    /**
     * Creates room if doesn't already exist, then adds the muc occupant to this room.
     * Does not send over Gateway
     * @param presence the MUC occupant join presence message.
     * @param advertiseRoom if true, advertise this room if it is being created for the first time.
     */
    public void addClientToRoom(Presence presence, boolean advertiseRoom){
        JID clientJID = presence.getFrom();
        JID mucOccupantJID = presence.getTo();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Adding muc occupant: "
                    + mucOccupantJID + " with fullJID " + clientJID + " to a room");
        }
        addClientToRoomRoot(presence, advertiseRoom);
    }

	/**
	 * Create a new muc room if it doesn't already exist or just add a fullJID with the format:
	 * "someroom@someserver/nick"
     * @param presence the available presence message
     * @param advertiseRoom if true, advertise this room if it is being created for the first time.
     */
    public void addClientToRoomAndSendOverGateway(Presence presence, boolean advertiseRoom) {
	    JID mucOccupantJID = presence.getTo();
        JID clientJID = presence.getFrom();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("adding muc occupant: " + mucOccupantJID + " with fullJID " + clientJID + " to a room");
        }

        addClientToRoomRoot(presence, advertiseRoom);

        // XXX 2016-08-28 rewriting all occupants and sending over gateway
        if (XOP.ENABLE.GATEWAY) { //&& ServerDialbackSession.sessionEstablished() ) {
            if (logger.isLoggable(Level.FINE))
                logger.fine("Gateway enabled, checking if " + mucOccupantJID.getDomain()
                        + ", checking: [" + XOP.GATEWAY.CONFERENCEDOMAIN + "]");

            ServerDialbackSession.remoteMUCOccupants.add(mucOccupantJID);
            if (logger.isLoggable(Level.FINE))
                logger.fine("writing packet to gateway: [[" + presence.toXML() + "]]");
                // logger.fine("writing packet to gateway: [[" + p.toXML() + "]]");
            sendToGateway(presence);
        }
    }

    /**
     * returns the Room of the muc occupant
     * @param mucOccupant the JID of the mucOccupant
     * @return the Room object if there is one, null otherwise
     */
    private Room getRoomForMucOccupant(JID mucOccupant){
        logger.finer("ENTER");
        RoomManager manager = getRoomManager(mucOccupant.getDomain());
        if (manager == null) {
            logger.warning("Attempted to remove: " + mucOccupant.toString() + ", but that server doesn't exist");
            return null;
        }
        Room room = manager.getRoom(new JID(mucOccupant.toBareJID()));
        if (room == null) {
            logger.warning("Attempted to remove: " + mucOccupant.toBareJID() + ", but that room doesn't exist");
        }
        return room;
    }

    /**
     * When the SD Manager receives notification that a MUC occupant has left, the
     * @param presence the presence message of the mucOccupant to remove from rooms
     * @param local True if the calling method is a locally connected client,
     *              False if SDListenerImpl is calling
     */
    public void removeFromRoom(Presence presence, boolean local) {
        JID mucOccupant = presence.getTo();
        Room room = getRoomForMucOccupant(mucOccupant);
        if(room == null) {
            logger.severe("No room was found for MUC Occupant: "+mucOccupant);
            return;
        }
        room.removeMUCOccupant(presence);

        if (local) {
            xopNet.getSDManager().removeMucOccupant(presence);
        }
    }

    /**
     * removes the occupant from the room and sends over gateway if necessary
     * @param presence The unavailable presence from the muc occupant
     * @param removeFromSD if true, also remove from SD system, otherwise,
     *                     do not remove from SD system
     */
    public void removeFromRoomSendOverGateway(Presence presence, boolean removeFromSD) {
        logger.finer("ENTER");
        JID mucOccupant = presence.getTo();
        Room room = getRoomForMucOccupant(mucOccupant);
        if (room == null) {
            logger.severe("Attempted to remove " + mucOccupant + ", but no room found!");
            return;
        }
        JID clientJID = room.getClientJIDForMucOccupant(mucOccupant);

        room.removeMUCOccupant(presence);

        if (removeFromSD) {
            xopNet.getSDManager().removeMucOccupant(presence);
        }
        if (XOP.ENABLE.GATEWAY) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Writing UNAVAILABLE presence message for [" + mucOccupant
                        + "] from: [" + clientJID + "] to the Gateway.");
                logger.fine("PRESENCE: " + presence.toXML());
            }
            sendToGateway(presence);
        }

        logger.finer("EXIT Success!");
    }

    public Collection<RoomManager> getRoomManagers() {
		return this.roomManagers.values();
	}

	public RoomManager getRoomManager(String domain) {
        return roomManagers.get(domain);
	}

    public Set<JID> getRoomIds() {
        HashSet<JID> ids = new HashSet<>();
        for(RoomManager manager : roomManagers.values()) {
            for(Room room : manager.getRooms()) {
                ids.add(room.getRoomJid());
            }
        }
        if(logger.isLoggable(Level.FINER)) logger.finer("room ids: [[" + ids + "]]");
        return ids;
    }

    /**
     * Iterates through all RoomManagers and Rooms to add all the MUC Occupants in all rooms
     * @return a set of all the room occupants across all rooms and room managers.
     */
    public Set<JID> getRoomOccupantIds() {
        HashSet<JID> ids = new HashSet<>();
        for(RoomManager manager : roomManagers.values()) {
            for(Room room : manager.getRooms()) {
                for(JID occupant : room.getMUCOccupants()){
                    ids.add(new JID(occupant.toString()));
                }
            }
        }
        if (logger.isLoggable(Level.FINER)) logger.finer("room occupant jids: [["+ids+"]]");
        return ids;
    }

	/**
	 * close the connection, remove clientRoute, send unavailable presences
	 *
     * @param jidToClose the jid to close
	 */
    public void handleCloseStream(JID jidToClose) {
        if (logger.isLoggable(Level.FINE)) logger.fine("closing connection for JID " + jidToClose);

        // remove from MUC room
        for (RoomManager roomManager : roomManagers.values()) {
            for (Room room : roomManager.getRooms()) {
                logger.fine("Removing muc occupants from room: " + room);
                JID mucOccupantJID = room.getMUCOccupantForClientJID(jidToClose);
                if(mucOccupantJID != null) {
                    Presence mucOccupantLeave = new Presence(Presence.Type.unavailable);
                    mucOccupantLeave.setFrom(jidToClose);
                    mucOccupantLeave.setTo(mucOccupantJID);
                    // removeFromRoom(mucOccupantJID, true);
                    removeFromRoom(mucOccupantLeave, true);
                }
            }
        }
        if (jidToClose != null) {
            announceUnavailableClient(jidToClose);
            clientManager.removeJIDFromAvailableSet(jidToClose);
            clientManager.removeXMPPClient(jidToClose);
        }
    }

    /**
     * Sends unavailable presence to the network
     * @param jid the jid to make unavailable
     */
    private void announceUnavailableClient(JID jid) {
        logger.info("Removing user from SD: " + jid);

        if (xopNet != null) {
            // Remove SD advertisement
            Presence presence = new Presence();
            presence.setType(Presence.Type.unavailable);
            presence.setFrom(jid);
            //notify remote clients
            xopNet.getSDManager().removeClient(presence);
        } else {
            logger.fine("Network not initialized, not sending to SD");
        }
    }

    /**
     * Sends the XMPP Presence or MessageByteBuffer over the gatewayed connection
     * @param p the xmpp packet to send over the gateway
     */
    public void sendToGateway(Packet p) {
        if (!XOP.ENABLE.GATEWAY) {
            logger.finer("Gateway not enabled, exiting");
            return;
        }
        /*
        Need to ensure the packet's from fields are from the XOP.GATEWAY.DOMAIN or CONFERENCE DOMAIN
        and the to field is for the XOP.GATEWAY.SERVER or CONFERENCESERVER. or else
        the federated server may close the connection.
        */
        Utils.rewritePacketToGateway(p);
        String fromDomain = p.getFrom().getDomain();
        String toDomain = p.getTo().getDomain();
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("XOP.GATEWAY.DOMAIN: " + XOP.GATEWAY.DOMAIN);
            logger.finer("XOP.GATEWAY.CONFERENCEDOMAIN: " + XOP.GATEWAY.CONFERENCEDOMAIN);
            logger.finer("p.getFrom().getDomain(): " + fromDomain);
            logger.finer("p.getTo().getDomain(): " + toDomain);
            logger.finer("XOP.GATEWAY.SERVER: " + XOP.GATEWAY.SERVER);
            logger.finer("XOP.GATEWAY.CONFERENCESERVER: " + XOP.GATEWAY.CONFERENCESERVER);
        }

        if ((p.getFrom() != null && (XOP.GATEWAY.DOMAIN.equals(fromDomain)
                || XOP.GATEWAY.CONFERENCEDOMAIN.equals(fromDomain)))
                && (p.getTo() != null && (XOP.GATEWAY.SERVER.equals(toDomain)
                    || XOP.GATEWAY.CONFERENCESERVER.equals(toDomain)) )
                ) {
            serverDialbackSession.sendPacket(p);
        } else {
            logger.fine("PACKET HAS DIFFERENT DOMAINS NOT sending packet to gateway [[[" + p.toString() + "]]]");
        }
    }
}