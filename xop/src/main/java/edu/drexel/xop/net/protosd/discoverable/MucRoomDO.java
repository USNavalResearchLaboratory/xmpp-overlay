package edu.drexel.xop.net.protosd.discoverable;

/*
 * (c) 2013 Drexel University
 */

import edu.drexel.xop.room.Room;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.protosd.api.MDNSTxtField;
import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobjects.DOServiceTypes;
import mil.navy.nrl.protosd.api.distobjects.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobjects.SDObject;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import org.xmpp.packet.JID;

import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Advertisement object for MUC Rooms.
 *
 * @author Duc Nguyen
 */
public class MucRoomDO extends DiscoverableObject {
    private static final Logger logger = LogUtils.getLogger(MucRoomDO.class.getName());

    public static final String SERVICE_TYPE = "_xop_room._udp";
    private static final String ROOM = "_room";
    private static final String DESCRIPTION = "_desc";
    private static final String DOMAIN = "_domain";


    static {
        DOServiceTypes.register(SERVICE_TYPE, MucRoomDO.class);
    }

    private JID roomJID = null;
    private String roomNameStr;
    private String roomDescription;
    private String roomDomain;

    /**
     * Constructor for adverts coming in over the network
     * @param serviceInfo the INDI serviceInfo object
     * @param sdObject the sdobject from protosd
     */
    public MucRoomDO(ServiceInfo serviceInfo, SDObject sdObject) {
        super(serviceInfo, sdObject);
        logger.log(Level.FINE, "MucOccupantDO: info serviceName: " + serviceInfo.getServiceName());

        if (roomJID == null && roomNameStr != null && roomDomain != null) {
            roomJID = new JID(roomNameStr, roomDomain, "");
        }

        if (roomJID == null) {
            logger.fine("Reconstructing roomJID from serviceName " + serviceInfo.getServiceName());
            roomJID = Utils.constructJIDFromServiceName(serviceInfo.getServiceName());
        }
    }


    // TODO: dn 2013-11-14: add room configuration options here.
    public MucRoomDO(SDObject sdObject, Room room) throws ServiceInfoException {
        super(new ServiceInfoEndpoint(
                sdObject.getSDInstance().getProtoSD(),
                        Utils.constructServiceInfoName(room.getRoomJid()),
                        // .replaceAll("[//]", "==").replaceAll("[/.]", "=_=")+"===ROOM",
                        // Replace '/' with '==' and '.' with '=_=' for the muc occupant jid.
                        // Have to replace '.' and '/' since ServiceInfo objects
                        // cannot handle these characters.,
                        SERVICE_TYPE,
                sdObject.getSDPropertyValues().getDomain(),
                        room.getRoomAddressStr(),
                sdObject.getSDPropertyValues().getMulticastPort(),
                        getTxtFieldParams(room)
                ),
              sdObject);

        this.roomJID = room.getRoomJid();

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("MucRoomDO: roomJID jid: " + roomJID.toString());
            logger.fine("  serviceName: " + (this.serviceInfo != null ? this.serviceInfo.getServiceName() : "svcInfo is NULL"));
            logger.fine("  svcInfo: ==[" + this.getServiceInfo() + "]==");
        }
    }

    private static MDNSTxtField getTxtFieldParams(Room room) {
        JID roomName = room.getRoomJid();
        logger.fine("room: {{" + room + "}} roomName " + roomName);
        Hashtable<String, String> txtParams
                = new Hashtable<>();
        txtParams.put(ROOM, roomName.getNode());
        txtParams.put(DOMAIN, roomName.getDomain());
        if (room.getDescription() != null) {
            txtParams.put(DESCRIPTION, room.getDescription());
        }
        return new MDNSTxtField(txtParams);
    }

    public JID getRoomJID() {
        return roomJID;
    }
    
    @Override
    protected void setFromTxtField(Hashtable<String, String> hashtable) {
        roomNameStr = hashtable.get(ROOM);
        roomDomain = hashtable.get(DOMAIN);
        roomDescription = hashtable.get(DESCRIPTION);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("roomNameStr= " + roomNameStr + ", roomDomain= " + roomDomain
                    + "roomDescription= " + roomDescription);
        }
    }
    
    public String toString(){
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("DiscoverableMUCOccupant: [RoomName JID: ").append(roomJID);
        sbuf.append(", roomDescription: ").append(roomDescription);
        sbuf.append(", serviceInfo: ").append(this.serviceInfo.toPrintableString()).append("]");
        
        return sbuf.toString();
    }
}
