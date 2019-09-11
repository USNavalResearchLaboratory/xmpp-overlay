package edu.drexel.xop.net.discovery;

import java.util.Hashtable;

import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobejcts.DOServiceTypes;
import mil.navy.nrl.protosd.api.distobejcts.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobejcts.SDObject;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import edu.drexel.xop.properties.XopProperties;

/**
 * A XOP Server discoverable object
 */
public class ServerDiscoverableObject extends DiscoverableObject {

    private static final String domain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);

    // TODO: this should be defined as a constant in XopProperties
    private static final int S2S_PORT = 5269;

    // Service names
    private static final String SERVER_ADVERTISEMENT_NAME = "xop-server";

    private static final String MDNS_XMPP_SERVER_TYPE = "xmpp-server";

    static final String serviceType = "_" + MDNS_XMPP_SERVER_TYPE + "._tcp.";

    static {
        DOServiceTypes.register(serviceType, ServerDiscoverableObject.class);
    }

    ServerDiscoverableObject(ServiceInfo serviceInfo, SDObject sdObj) {
        super(serviceInfo, sdObj);
    }

    public ServerDiscoverableObject(SDObject sdObject) throws InitializationException, ServiceInfoException {
        this(new ServiceInfoEndpoint(sdObject.getSDInstance().getProtoSD(), SERVER_ADVERTISEMENT_NAME, serviceType, domain, S2S_PORT), sdObject);
    }

    /*
     * (non-Javadoc)
     * 
     * @see mil.navy.nrl.protosd.api.distobejcts.DiscoverableObject#setFromTxtField(java.util.Hashtable)
     */
    @Override
    protected void setFromTxtField(Hashtable<String, String> keyValues) {
        // TODO Auto-generated method stub

    }

}
