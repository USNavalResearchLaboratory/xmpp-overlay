package edu.drexel.xop.net.protosd.discoverable;

import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.util.Hashtable;
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
 * Used by ProtoSDManager to get information about XMPP presences
 * The status message of the xmpp presence. 
 *
 */
public class PresenceDO extends DiscoverableObject {
    private static final Logger logger = LogUtils.getLogger(PresenceDO.class.getName());

    public static final String SERVICE_TYPE = "_xop_presence._udp";
    private static final String TXTFIELD_PRESENCE_TYPE = "_presence_type";
    // public static final String TXTFIELD_PRESENCE_STATUS = "_statusmsg";

    static {
        DOServiceTypes.register(SERVICE_TYPE, PresenceDO.class);
    }

    // private Presence presence = null;
    // private Presence unavailablePresence = null;
    private String fromNode;
    private String fromDomain;
    private String fromResource;
    private String status;
    private JID fromJID;

    public PresenceDO(ServiceInfo info, SDObject sdObject){
        super(info, sdObject);
        logger.fine("Info serviceName: " + info.getServiceName());

        // presence = new Presence();
        if( fromDomain != null ) {
            logger.fine("setting presence from: "+fromNode+" "+fromDomain+" "+fromResource);
            // presence.setFrom(new JID(fromNode, fromDomain, fromResource));
            fromJID = new JID(fromNode, fromDomain, fromResource);
            logger.fine("fromJID: " + fromJID);
        } else {
            logger.fine("null fromDomain! setting using serviceName: " + info.getServiceName());
            // presence.setFrom(Utils.constructJIDFromServiceName(info.getServiceName()));
            fromJID = Utils.constructJIDFromServiceName(info.getServiceName());
        }
    }


    /**
     * constructor for PresenceDO objects used for
     * @param sdObject the SDObject
     * @param presence the generated presence object
     * @throws ServiceInfoException if unable to create a serviceInfoEndpoint
     */
    public PresenceDO(SDObject sdObject, Presence presence) throws ServiceInfoException {
        super(new ServiceInfoEndpoint(
                        sdObject.getSDInstance().getProtoSD(),
                        Utils.constructServiceInfoName(new JID(presence.getFrom().toBareJID())),
                        SERVICE_TYPE,
                        sdObject.getSDPropertyValues().getDomain(),
                        XOProxy.getInstance().getXopNet().getHostAddrStr(),
                        XOProxy.getInstance().getXopNet().getOneToOnePort(),
                        getTxtFieldParams(presence)
                ),
                sdObject);

        fromJID = presence.getFrom();
        logger.fine("PresenceDO: fromJID: " + this.fromJID);
        logger.fine("  serviceName: " + (this.serviceInfo != null ? this.serviceInfo.getServiceName() : "svcInfo is NULL"));
        logger.fine("  svcInfoEndpoint: ==" + this.getServiceInfo() + "==");

    }

    private static MDNSTxtField getTxtFieldParams(Presence presence) {
        Hashtable<String, String> txtField = new Hashtable<String, String>();
        logger.fine("presence from field: " + presence.getFrom());
        txtField.put(CONSTANTS.PROTOSD.JID_NODE, presence.getFrom().getNode());
        txtField.put(CONSTANTS.PROTOSD.JID_DOMAIN, presence.getFrom().getDomain());
        if(presence.getFrom().getResource() != null) {
            txtField.put(CONSTANTS.PROTOSD.JID_RESOURCE, presence.getFrom().getResource());
        }
        String status = presence.getStatus();
        if (status == null || "".equals(status)) {
            status = "status";
        }
        txtField.put(CONSTANTS.PROTOSD.STATUS, status);
        if( presence.getType() != null ) {
            txtField.put(TXTFIELD_PRESENCE_TYPE, presence.getType().name());
        }
        logger.fine("txtField: " + txtField);
        return new MDNSTxtField(txtField);
    }

    public JID getFromJID(){
        return fromJID;
    }

    @Override
    protected void setFromTxtField(Hashtable<String, String> textfields) {
        logger.info("setting node, domain, resource, and status fields");
        fromNode = textfields.get(CONSTANTS.PROTOSD.JID_NODE);
        fromDomain = textfields.get(CONSTANTS.PROTOSD.JID_DOMAIN);
        fromResource = textfields.get(CONSTANTS.PROTOSD.JID_RESOURCE);
        status = textfields.get(CONSTANTS.PROTOSD.STATUS);
    }
    
    public String toString(){
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("PresenceDO: [fromJID: ").append(fromJID).append(", ");
        sbuf.append("serviceInfo: ").append(this.serviceInfo.toPrintableString()).append("]");
        return sbuf.toString();
    }
}
