package edu.drexel.xop.net.protosd.discoverable;

/**
 * (c) 2013 Drexel University
 */

import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.protosd.api.MDNSTxtField;
import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobjects.DOServiceTypes;
import mil.navy.nrl.protosd.api.distobjects.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobjects.SDObject;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import org.xmpp.packet.JID;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used by ProtoSDManager to get information about XMPP Gateways
 *
 * @author Rob Taglang
 */
public class GatewayDO extends DiscoverableObject {
    private static final Logger logger = LogUtils.getLogger(GatewayDO.class.getName());

    public static final String SERVICE_TYPE = "_xop_gateway._udp";
    public static final String JID_NODE = "_jid_node";
    public static final String JID_DOMAIN = "_jid_domain";
    public static final String JID_RESOURCE = "_jid_resource";
    public static final String HOST_ADDRESS = "_address";

    static {
        DOServiceTypes.register(SERVICE_TYPE, GatewayDO.class);
    }

    private InetAddress gatewayAddress;
    private JID gatewayJID;
    private String hostAddress;
    private String jidNode;
    private String jidDomain;
    private String jidResource;

    public GatewayDO(ServiceInfo info, SDObject sdObject) {
        super(info, sdObject);
        if(hostAddress != null) {
            try {
                gatewayAddress = InetAddress.getByName(hostAddress);
            } catch (UnknownHostException e) {
                logger.severe("Unable to resolve hostname: " + hostAddress);
            }
        }
        if(jidDomain != null) {
            this.gatewayJID = new JID(jidNode, jidDomain, jidResource);
        } else {
            logger.severe("Gateway JID is null due to null domain for serviceInfo:"+serviceInfo);
        }
    }

    public GatewayDO(SDObject sdObject, InetAddress gatewayAddress, JID gatewayJID) throws ServiceInfoException {
        super(new ServiceInfoEndpoint(
                        sdObject.getSDInstance().getProtoSD(),
                        gatewayJID.toString(),
                        SERVICE_TYPE,
                        sdObject.getSDPropertyValues().getDomain(),
                        sdObject.getSDPropertyValues().getMulticastPort(),
                        getTextParams(gatewayAddress, gatewayJID)
                ),
                sdObject);

        this.gatewayAddress = gatewayAddress;
        this.gatewayJID = gatewayJID;
    }

    // TODO (Sbever 8/6/14): GatewayDO doesn't really matter for Agile Bloodhound, so I'm commenting this
    // TODO (Sbever 8/6/14): out for now. We should turn it back on/fix it later (we didn't have values and hashtables
    // TODO (Sbever 8/6/14): won't take null).
    private static MDNSTxtField getTextParams(InetAddress gatewayAddress, JID gatewayJID){
        Hashtable<String, String> txtFields = new Hashtable<String, String>();
        //txtFields.put(JID_NODE, gatewayJID.getNode());
        txtFields.put(JID_DOMAIN, gatewayJID.getDomain());
        //txtFields.put(JID_RESOURCE, gatewayJID.getResource());
        txtFields.put(HOST_ADDRESS, gatewayAddress.getHostAddress());
        return new MDNSTxtField(txtFields);
    }

    public InetAddress getGatewayAddress() {
        return gatewayAddress;
    }

    public JID getGatewayJID() {
        return gatewayJID;
    }

    @Override
    protected void setFromTxtField(Hashtable<String, String> txtFields) {
        if( logger.isLoggable(Level.FINE) ) logger.fine("DiscoverableGateway setting fields from MDNSTxtField");
        hostAddress = txtFields.get(HOST_ADDRESS);
        jidNode = txtFields.get(JID_NODE);
        jidDomain = txtFields.get(JID_DOMAIN);
        jidResource = txtFields.get(JID_RESOURCE);
    }
    
    /**
     * for debugging purposes, print out the contents of the DiscoverableClient object
     */
    public String toString(){
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("DiscoverableGateway: [JID: ").append(gatewayJID);
        sbuf.append(", address: ").append(gatewayAddress.getHostAddress()).append(", ");
        sbuf.append("serviceInfo: ").append(this.serviceInfo.toPrintableString()).append("]");
        
        return sbuf.toString();
    }
}
