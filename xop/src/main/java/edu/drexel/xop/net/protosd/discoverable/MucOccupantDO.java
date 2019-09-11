package edu.drexel.xop.net.protosd.discoverable;

import org.xmpp.packet.JID;

import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.protosd.api.MDNSTxtField;
import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobjects.DOServiceTypes;
import mil.navy.nrl.protosd.api.distobjects.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobjects.SDObject;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;

/**
 * Used by ProtoSDManager to get information about registered aliases
 *
 */
public class MucOccupantDO extends DiscoverableObject {
    private static final Logger logger = LogUtils.getLogger(MucOccupantDO.class.getName());
    public static final String SERVICE_TYPE = "_xop_mucoccupant._udp";

    private JID clientJID = null; // where the message is from. e.g., user123@proxy TODO: assumes non-anonymous rooms. make it generic so users can be anonymous
    private JID mucOccupantJID = null; // the occupant JID. e.g., room@conference.proxy/user123
    private String jidNode;
    private String jidDomain;
    private String jidResource;
    private String alias;


    static {
        DOServiceTypes.register(SERVICE_TYPE, MucOccupantDO.class);
    }

    /**
     * Constructor for searching for discoverable objects.
     * @param info the INDI serviceInfo object
     * @param sdObject the sdobject from protosd
     */
    public MucOccupantDO(ServiceInfo info, SDObject sdObject) {
        super(info, sdObject);
        logger.log(Level.INFO, "MucOccupantDO: info serviceName: "+info.getServiceName());

        if(alias != null) {
            this.mucOccupantJID = new JID(alias);
        } else {
            logger.warning("Reconstructing mucOccupantJID (alias) from serviceInfo because alias is null. serviceInfo: "+serviceInfo);
            this.mucOccupantJID = Utils.constructJIDFromServiceName(info.getServiceName());
        }
        if(jidDomain != null) {
            this.clientJID = new JID(jidNode, jidDomain, jidResource);
        } else {
            logger.warning( "fullJID from serviceInfo is null because jidDomain is null. serviceInfo: "+info);
        }
    }

    /**
     * Constructor for when an XMPP client joins a room.
     * @param sdObject the SDObject from protosd
     * @param clientJID the JID of the occupant entering the room
     * @param alias the occupant name
     * @throws ServiceInfoException
     */
    public MucOccupantDO(SDObject sdObject, JID clientJID, JID alias) throws ServiceInfoException {
        super(new ServiceInfoEndpoint(
                sdObject.getSDInstance().getProtoSD(),
                Utils.constructServiceInfoName(alias),
                        // Replace '/' with '==' and '.' with '=_=' for the muc occupant jid. Have to replace '.' and '/' since ServiceInfo objects cannot handle these characters.
                        //Utils.generateID(8),
                        SERVICE_TYPE,
                        sdObject.getSDPropertyValues().getDomain(),
                        XOProxy.getInstance().getXopNet().getHostAddrStr(),
                        35550,
                //sdObject.getSDPropertyValues().getMulticastAddress(),
                //sdObject.getSDPropertyValues().getMulticastPort(),
                        getTxtFieldParams(clientJID, alias)
                        ),
                sdObject);


        this.clientJID = clientJID;
        this.mucOccupantJID = alias;
        logger.fine("MucOccupantDO: original jid: " + this.clientJID + " alias jid: " + mucOccupantJID);
        logger.fine("  serviceName: " + (this.serviceInfo != null ? this.serviceInfo.getServiceName() : "svcInfo is NULL"));
        logger.fine("  svcInfoEndpoint: =={" + this.getServiceInfo() + "}==");
    }

    private static MDNSTxtField getTxtFieldParams(JID clientJID, JID mucRoomJID){
        Hashtable<String, String> txtField = new Hashtable<>();

        txtField.put(CONSTANTS.PROTOSD.JID_NODE, clientJID.getNode());
        txtField.put(CONSTANTS.PROTOSD.JID_DOMAIN, clientJID.getDomain());
        if(clientJID.getResource() != null) {
            txtField.put(CONSTANTS.PROTOSD.JID_RESOURCE, clientJID.getResource());
        }
        txtField.put(CONSTANTS.PROTOSD.ALIAS, mucRoomJID.toString());
        logger.fine("txtField: " + txtField);
        return new MDNSTxtField(txtField);
    }

    public JID getMucOccupantJID() {
        return mucOccupantJID;
    }

    public JID getClientJID() {
        return clientJID;
    }


    @Override
    protected void setFromTxtField(Hashtable<String, String> hashtable) {
        if( logger.isLoggable(Level.FINE) ) logger.fine("MucOccupantDO setting fields from txt parameters");

        jidNode = hashtable.get(CONSTANTS.PROTOSD.JID_NODE);
        jidDomain = hashtable.get(CONSTANTS.PROTOSD.JID_DOMAIN);
        jidResource = hashtable.get(CONSTANTS.PROTOSD.JID_RESOURCE);
        alias = hashtable.get(CONSTANTS.PROTOSD.ALIAS);
        if (logger.isLoggable(Level.FINE)) logger.fine("alias: " + alias);
   }
    
    public String toString(){

        return "MucOccupantDO: [fromFullJID: " + clientJID +
                ", mucOccupantJID: " + mucOccupantJID + ", " +
                ", serviceInfo: " + this.serviceInfo.toPrintableString() + "]";
    }
}
