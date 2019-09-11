/**
 * (c) 2013 Drexel University
 */

package edu.drexel.xop.component.ad;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobejcts.DiscoverableObject;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import mil.navy.nrl.protosd.api.exception.ServiceLifeCycleException;

import org.xmpp.packet.Packet;

import edu.drexel.xop.component.ComponentBase;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.api.DiscoverableObjectListener;
import edu.drexel.xop.net.discovery.SDXOPObject;
import edu.drexel.xop.properties.XopProperties;

/**
 *
 */
public class ADComponent extends ComponentBase implements DiscoverableObjectListener {

    private static final Logger logger = Logger.getLogger(ADComponent.class.getName());

    private final String domain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);

    private static final int S2S_PORT = Integer.parseInt(XopProperties.getInstance().getProperty(XopProperties.S2S_PORT));
    // Service names
    private static final String SERVER_ADVERTISEMENT_NAME = "xop-server";

    // follows these specs: Protocol description: RFC 3920, Discovery Protocol: RFC 2782
    private static final String MDNS_XMPP_SERVER_TYPE = "xmpp-server";

    private static final String SERVICE_TYPE = "_" + MDNS_XMPP_SERVER_TYPE + "._tcp.";

    private HashSet<ServerDiscoverableObject> advertisedServices = new HashSet<>();

    @Override
    public void initialize() {
        super.initialize();
        SDXOPObject sdObject = XopNet.getSDObject();

        int serverPort = S2S_PORT;

        try {
            // Construct server advertisement service info endpoint
            ServiceInfoEndpoint server = new ServiceInfoEndpoint(sdObject.getSDInstance().getProtoSD(), SERVER_ADVERTISEMENT_NAME, SERVICE_TYPE, domain, serverPort);

            ServerDiscoverableObject serverDiscoverableObj = new ServerDiscoverableObject(server, sdObject);

            // Advertise server
            logger.log(Level.INFO, "This server is being advertised over SD.");
            sdObject.advertise(serverDiscoverableObj);

            advertisedServices.add(serverDiscoverableObj);
        } catch (ServiceLifeCycleException ex) {
            logger.log(Level.WARNING, "LifeCycle Error advertising server or client", ex);
        } catch (ServiceInfoException e) {
            logger.log(Level.WARNING, "ServiceInfo Error advertising server or client", e);
            e.printStackTrace();
        }

    }

    public void processPacket(Packet packet) {
        // Do nothing
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.drexel.xop.net.api.DiscoverableObjectListener#discoverableObjectAdded(edu.drexel.xop.net.discovery.DiscoverableObject)
     */
    @Override
    public void discoverableObjectAdded(DiscoverableObject discoverableObject) {
        advertisedServices.add((ServerDiscoverableObject) discoverableObject);
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.drexel.xop.net.api.DiscoverableObjectListener#discoverableObjectRemoved(edu.drexel.xop.net.discovery.DiscoverableObject)
     */
    @Override
    public void discoverableObjectRemoved(DiscoverableObject discoverableObject) {
        advertisedServices.remove(discoverableObject);
    }

    class ServerDiscoverableObject extends DiscoverableObject {

        ServerDiscoverableObject(ServiceInfo serviceInfo, SDXOPObject sdObj) {
            super(serviceInfo, sdObj);
        }

        /*
         * (non-Javadoc)
         * 
         * @see edu.drexel.xop.net.discovery.DiscoverableObject#setFromTxtField(java.util.Hashtable)
         */
        @Override
        protected void setFromTxtField(Hashtable<String, String> keyValues) {
            // we don't do this
        }

    }

    @Override
    public boolean accept(Packet p) {
        try {
            if (p.getTo() != null) {
                if (domain.equals((p.getTo().getDomain()))) {
                    logger.log(Level.FINE, "Accepted a packet");
                    return true;
                } else {
                    logger.log(Level.FINE, "Rejected packet (this.domain=" + domain + ", p.getTo().getDomain()="
                        + p.getTo().getDomain());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
