package edu.drexel.xop.net.protosd;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.net.SDListener;
import edu.drexel.xop.net.SDManager;
import edu.drexel.xop.net.protosd.discoverable.GatewayDO;
import edu.drexel.xop.net.protosd.discoverable.MucOccupantDO;
import edu.drexel.xop.net.protosd.discoverable.MucRoomDO;
import edu.drexel.xop.net.protosd.discoverable.PresenceDO;
import edu.drexel.xop.room.Room;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobjects.DiscoverableLookUp;
import mil.navy.nrl.protosd.api.distobjects.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobjects.SDInstance;
import mil.navy.nrl.protosd.api.distobjects.SDObject;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import mil.navy.nrl.protosd.api.exception.ServiceLifeCycleException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uses ProtoSD for service discovery on the network
 * Supports the SDManager and SDListener architecture
 */
public class ProtoSDManager extends SDObject implements SDManager {
    private static final Logger logger = LogUtils.getLogger(ProtoSDManager.class.getName());

    // These synchronized sets are needed for advertising and removing the clients. retrieving the keySet() is not threadsafe.
    private final Set<JID> clientJIDs;
    private final Set<JID> mucOccupants;
    private final Set<JID> mucRooms;

    private ConcurrentMap<JID, PresenceDO> presenceDiscoverableObjects;
    private ConcurrentMap<JID, MucOccupantDO> mucOccupantDiscoverableObjects;
    private ConcurrentMap<JID, GatewayDO> gatewayDiscoverableObjects;
    private ConcurrentMap<JID,MucRoomDO> mucRoomDiscoverableObjects;
    private SDInstance sdInstance;

    private XopDOListener xopDOListener;
    private XopDiscoverableLookup xopDiscoverableLookup;

    private ClientManager clientManager;

    public ProtoSDManager(SDInstance sdInstance, ClientManager clientManager) {
        super(sdInstance);
        this.sdInstance = sdInstance;
        this.clientManager = clientManager;

        // These synchronized sets are needed for advertising and removing the clients.
        // Retrieving the keySet() is not threadsafe.
        clientJIDs = Collections.synchronizedSet(new HashSet<JID>());
        mucOccupants = Collections.synchronizedSet(new HashSet<JID>());
        mucRooms = Collections.synchronizedSet(new HashSet<JID>());

        presenceDiscoverableObjects = new ConcurrentHashMap<>();
        gatewayDiscoverableObjects = new ConcurrentHashMap<>();
        mucOccupantDiscoverableObjects = new ConcurrentHashMap<>();
        mucRoomDiscoverableObjects = new ConcurrentHashMap<>();

        xopDOListener = new XopDOListener(clientManager);

        xopDiscoverableLookup = new XopDiscoverableLookup();
    }

    @Override
    public void start() {
        addDiscoverableObjectListener(xopDOListener);
        try {
            sdInstance.addServiceListener(this,
                    PresenceDO.SERVICE_TYPE, sdInstance.getSDPropertyValues().getDomain());
            sdInstance.addServiceListener(this,
                    MucOccupantDO.SERVICE_TYPE, sdInstance.getSDPropertyValues().getDomain());
            sdInstance.addServiceListener(this,
                    GatewayDO.SERVICE_TYPE, sdInstance.getSDPropertyValues().getDomain());
            sdInstance.addServiceListener(this,
                    MucRoomDO.SERVICE_TYPE, sdInstance.getSDPropertyValues().getDomain());
        } catch (ServiceInfoException e) {
            logger.severe("Unable to advertise connection: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        logger.fine("Removing the advertisements for all clients");

        presenceDiscoverableObjects.clear();
        mucOccupantDiscoverableObjects.clear();
        gatewayDiscoverableObjects.clear();
        mucRoomDiscoverableObjects.clear();

        logger.fine("shutting down the protoSD instance");
        sdInstance.close();
            
    }

    @Override
    public void addSDListener(SDListener listener) {
        xopDOListener.addListener(listener);
    }


    @Override
    public void addGateway(InetAddress address, JID domain) {
        logger.fine("Adding gateway with address: "+address.getHostAddress()+" and domain: "+domain);
        try {
            GatewayDO disc = new GatewayDO(this, address, domain);
            gatewayDiscoverableObjects.put(domain, disc);
            advertise(disc);
        } catch (ServiceInfoException | ServiceLifeCycleException e) {
            logger.severe("Unable to advertise client: " + address.toString() + ", " + domain.toString() + " : " + e.getMessage());
        }
    }

    @Override
    public void removeGateway(JID domain) {
        logger.fine("Removing gateway with domain: "+domain);
        try {
            if(domain != null) {
                GatewayDO dg = gatewayDiscoverableObjects.get(domain);
                if(dg != null) {
                    remove(dg);
                }
                gatewayDiscoverableObjects.remove(domain);
            }
        } catch (ServiceLifeCycleException | ServiceInfoException e) {
            logger.severe("Unable to remove client advertisement for: " + domain.toString() + " : " + e.getMessage());
        }
    }

    @Override
    public void advertiseClient(Presence presence) {
        JID from = presence.getFrom();
        logger.fine("adding: " + from + ", presenceDiscoverableObjects "
                + presenceDiscoverableObjects.keySet());
        logger.fine("clientJIDs: " + clientJIDs);
        // Set<JID> jids = presenceDiscoverableObjects.keySet();
        if (clientJIDs.contains(from)) {
            logger.info("Client with JID " + from + " already advertised");
            return;
        }

        logger.fine("Advertising presence: " + presence);
        try {
            PresenceDO disc = new PresenceDO(this, presence);
            advertise(disc);
            presenceDiscoverableObjects.put(from, disc);
            clientJIDs.add(from);
            logger.info("ProtoSDManager has advertised this client: " + disc);

        } catch (ServiceInfoException | ServiceLifeCycleException e) {
            logger.severe("Unable to advertise presence: " + presence.toString() + " : " + e.getMessage());
        }
    }

    @Override
    public void updateClientStatus(Presence presence) {
        logger.fine("Protosd does not support updating status");
    }

    @Override
    public void removeClient(Presence presence) {
        logger.info("presence.getFrom(): " + presence.getFrom()
                + ", Removing presence from ProtoSD: " + presence);
        logger.fine("presenceDiscoverableObjects: " + presenceDiscoverableObjects.keySet());
        logger.fine("clientJIDs: "+clientJIDs);
        JID clientJID = presence.getFrom();
        try {
            if(clientJID != null) {
                PresenceDO dp = presenceDiscoverableObjects.get(clientJID);
                if(dp != null) {
                    remove(dp);
                    logger.fine("presence removed from ProtoSD: " + presenceDiscoverableObjects.keySet());
                } else {
                    logger.warning("presence from " + presence.getFrom() + " not removed");
                }
                presenceDiscoverableObjects.remove(clientJID);
                clientJIDs.remove(clientJID);
            }
        } catch (ServiceLifeCycleException | ServiceInfoException e) {
            logger.severe("Unable to remove presence advertisement for: " + presence.toString() + " : " + e.getMessage());
        }
    }

    @Override
    public void advertiseMucOccupant(Presence presence) {
        JID clientJID = presence.getFrom();
        JID mucOccupantJID = presence.getTo();
        logger.fine("mucRooms: " + mucOccupants);
        if(mucOccupants.contains(mucOccupantJID)) {
            logger.info("MUC occupant for " + mucOccupantJID + " already advertised. "+mucOccupants);
            return;
        }

        logger.info("ProtoSDManager is advertising MUC Occupant: " + mucOccupantJID + " for client: " + clientJID);
        try {
            MucOccupantDO disc = new MucOccupantDO(this, clientJID, mucOccupantJID);
            advertise(disc);
            mucOccupantDiscoverableObjects.put(mucOccupantJID, disc);
            mucOccupants.add(mucOccupantJID);
            logger.info("ProtoSDManager has advertised this occupant: " + disc);
        } catch (ServiceInfoException e) {
            logger.severe("ServiceInfoException thrown! Unable to advertise mucOccupant! fullJID:" + clientJID.toString() + ", mucOccupantJID:" + mucOccupantJID.toString() + " : " + e.getMessage());
        } catch (ServiceLifeCycleException e) {
            logger.severe("Unable to advertise mucOccupant! fullJID:" + clientJID.toString() + ", mucOccupantJID:" + mucOccupantJID.toString() + " : " + e.getMessage());
        }
    }


    @Override
    public void removeMucOccupant(Presence presence) {
        JID mucOccupantJID = presence.getTo();
        if(logger.isLoggable(Level.FINER)) logger.finer("mucOccupant Presence: "+presence);
        if(logger.isLoggable(Level.FINE))
            logger.fine(">>>>>>>>>>>>> Removing mucOccupantJID: "+mucOccupantJID);
        if (logger.isLoggable(Level.FINE)) logger.fine("mucRooms: " + mucOccupants);
        if(logger.isLoggable(Level.FINE))
            logger.fine("mucOccupantDiscoverableObjects: " +
                mucOccupantDiscoverableObjects.keySet());

        try {
            if(mucOccupantJID != null) {
                MucOccupantDO da = mucOccupantDiscoverableObjects.get(mucOccupantJID);
                logger.fine(">>>>>>>>>>>>>>>>>> removing MucOccupantDO: " + da);
                if(da != null) {
                    remove(da);
                } else {
                    logger.warning("did not remove: " + mucOccupantJID);
                }
                mucOccupants.remove(mucOccupantJID);
                mucOccupantDiscoverableObjects.remove(mucOccupantJID);
            }
        } catch (ServiceLifeCycleException | ServiceInfoException e) {
            logger.severe("Unable to remove mucOccupantJID advertisement for: " + mucOccupantJID + " : " + e.getMessage());
        }
    }

    @Override
    public void updateMucOccupantStatus(Presence presence) {
        logger.fine("Protosd does not support updating status");
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.SDManager#advertiseMucRoom(org.xmpp.packet.JID)
     */
    @Override
    public void advertiseMucRoom(Room room) {
        // TODO dnguyen 20140815 currently, not advertising rooms since they aren't being used in the system.
        logger.fine("adding room");
		try {
		    MucRoomDO discRoom = new MucRoomDO(this, room);
            mucRoomDiscoverableObjects.put(room.getRoomJid(), discRoom);
            advertise(discRoom);
		} catch (ServiceInfoException e) {
			logger.severe("Unable to construct Room advertisement!"+e.getMessage());
        } catch (ServiceLifeCycleException e) {
			logger.severe("Unable to advertise room!"+e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.SDManager#removeMucRoom(org.xmpp.packet.JID)
     */
    @Override
    public void removeMucRoom(Room room) {
    	logger.fine("removing room");
        MucRoomDO roomAdvert = mucRoomDiscoverableObjects.remove(room);

        try {
            remove(roomAdvert);
        } catch (ServiceLifeCycleException e) {
            logger.severe("Service Life Cycle Exception: unable to remove room: "
                    + (roomAdvert != null ? roomAdvert.getRoomJID().toString() : "Room " + room
                    + " not found!"));
            e.printStackTrace();
        } catch (ServiceInfoException e) {
            logger.severe("Service Info Exception: unable to remove room: "
                    + (roomAdvert != null ? roomAdvert.getRoomJID().toString() : "Room " + room
                    + " not found!"));
            e.printStackTrace();
        }
    }
    
    /*
    dnn 2016-06-03: These methods don't add any value.
    */
    @Override
    public void serviceAdded(ServiceInfo info){
        logger.fine("ProtoSDManager: Added a service: " + info);

        String svcName = info.getServiceName();
        logger.fine("serviceName: [" + svcName + "]");
        JID jid = Utils.constructJIDFromServiceName(svcName); //this.constructJID(info);
        switch(info.getServiceType()){
            case MucOccupantDO.SERVICE_TYPE:
                MucOccupantDO da = new MucOccupantDO(info, this);
                if (clientManager.isLocal(jid)) {
                    logger.info(jid+" is LOCAL, do nothing");
                    return;
                }
                logger.fine("reconstructed jid: " + jid);
                mucOccupantDiscoverableObjects.put(jid, da);
                mucOccupants.add(jid);
                logger.fine("Added " + jid + " and saved saved MucOccupantDO: " + da);
                break;
            case PresenceDO.SERVICE_TYPE:
                PresenceDO dp = new PresenceDO(info, this);
                if (clientManager.isLocal(jid)) {
                    logger.info(jid+" is LOCAL, do nothing");
                    return;
                }
                logger.fine("svcName: " + svcName + " jid: " + jid);
                presenceDiscoverableObjects.put(jid, dp);
                clientJIDs.add(jid);
                logger.fine("Added PresenceDO from presenceDiscoverableObjects: "
                        + presenceDiscoverableObjects.keySet());
                break;
            case MucRoomDO.SERVICE_TYPE:
                MucRoomDO dm = new MucRoomDO(info, this);
                logger.fine("svcName: " + svcName + " jid: " + jid);
                mucRoomDiscoverableObjects.put(jid, dm);
                mucRooms.add(jid);
                logger.fine("Added PresenceDO from presenceDiscoverableObjects: "
                        + mucRoomDiscoverableObjects.keySet());
                break;
            default:
                logger.severe("Unknown service type: " + info.getServiceType());
        }

        super.serviceAdded(info);
    }

    @Override
    public void serviceResolved(ServiceInfoEndpoint info) {
        logger.info("ProtoSDManager: Resolved a service:"+info);
        super.serviceResolved(info);
    }

    @Override
    public void serviceRemoved(ServiceInfo info ){
        logger.fine("ProtoSDManager: removed service: " + info);

        ServiceInfo savedServiceInfo = info;
        JID jid = Utils.constructJIDFromServiceName(info.getServiceName()); //this.constructJID(info);
        switch(info.getServiceType()){
            case MucOccupantDO.SERVICE_TYPE:
                if (clientManager.isLocal(jid)) {
                    logger.info(jid+" is LOCAL, do nothing");
                    return;
                }
                logger.fine("reconstructed jid: "+jid);
                MucOccupantDO da = mucOccupantDiscoverableObjects.get(jid);
                mucOccupants.remove(jid);
                logger.fine("Retrieving saved MucOccupantDO: "+da);
                if( da != null ) {
                    savedServiceInfo = da.getServiceInfo();
                    mucOccupantDiscoverableObjects.remove(jid);
                } else {
                    logger.info("MucOccupantDO is null!");
                }
                break;
            case PresenceDO.SERVICE_TYPE:
                // jid = Utils.constructJIDFromServiceName(info.getServiceName()); //new JID(info.getServiceName());
                if (clientManager.isLocal(jid)) {
                    logger.fine(jid+" is LOCAL, do nothing");
                    return;
                }
                logger.fine("svcName: " + info.getServiceName() + " reconstructed jid: " + jid);
                PresenceDO dp = presenceDiscoverableObjects.get(jid);
                clientJIDs.remove(jid);
                logger.fine("Retrieving PresenceDO: " + dp + " from presenceDiscoverableObjects: " + presenceDiscoverableObjects.keySet());
                if( dp != null ) {
                    savedServiceInfo = dp.getServiceInfo();
                    presenceDiscoverableObjects.remove(jid);
                } else {
                    logger.info("PresenceDO is null!");
                }
                break;
            case MucRoomDO.SERVICE_TYPE:
                break;
            default:
                logger.severe("Unknown service type: "+info.getServiceType());
        }

        super.serviceRemoved(savedServiceInfo);
    }
    //
    // private JID constructJID( ServiceInfo info ){
    //     //alias.toString().replaceAll("[//]", "==").replaceAll("[/.]", "=_=")
    //     String svcName = info.getServiceName();
    //     String mucOccupantJIDStr = svcName.replaceAll("==","/").replaceAll("=_=",".");
    //     return new JID(mucOccupantJIDStr);
    // }


    @Override
    public DiscoverableLookUp getDiscoverableLookup() {
        return xopDiscoverableLookup;
    }

    private class XopDiscoverableLookup extends DiscoverableLookUp {
        @Override
        public DiscoverableObject getDiscoverableFor(SDObject sdObject, ServiceInfo serviceInfo) {
            logger.info("generating DiscoverableObject for svcInfo: "+serviceInfo);
            switch (serviceInfo.getServiceType()) {
                case PresenceDO.SERVICE_TYPE:
                    PresenceDO dp = new PresenceDO(serviceInfo, sdObject);
                    logger.fine("adding XMPP Entity with key: " + dp.getFromJID());
                    presenceDiscoverableObjects.put(dp.getFromJID(), dp);
                    return dp;
                case MucOccupantDO.SERVICE_TYPE:
                    MucOccupantDO da = new MucOccupantDO(serviceInfo, sdObject);
                    logger.fine("adding MUC Occupant with key: " + da.getClientJID());
                    mucOccupantDiscoverableObjects.put(da.getMucOccupantJID(), da);
                    return da;
                case GatewayDO.SERVICE_TYPE:
                    GatewayDO dg = new GatewayDO(serviceInfo, sdObject);
                    gatewayDiscoverableObjects.put(dg.getGatewayJID(), dg);
                    return dg;
                case MucRoomDO.SERVICE_TYPE:
                    MucRoomDO dr = new MucRoomDO(serviceInfo, sdObject);
                    mucRoomDiscoverableObjects.put(dr.getRoomJID(), dr);
                    return dr;
                default:
                    logger.warning("Unknown service type, returning null");
                    return null;
            }
        }
    }
} // End ProtoSDManager
