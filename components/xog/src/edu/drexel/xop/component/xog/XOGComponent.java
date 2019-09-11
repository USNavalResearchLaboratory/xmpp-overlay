/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.xog;

import java.util.logging.Logger;

import org.xmpp.packet.Packet;

import edu.drexel.xop.component.ComponentBase;
import edu.drexel.xop.component.s2s.S2S;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * @author urlass
 * 
 * This constructor will make the correct concrete Gateway object,
 * and then pass everything through to it.
 * 
 */
public class XOGComponent extends ComponentBase {
    private static final Logger logger = LogUtils.getLogger(XOGComponent.class.getName());
    public static final String ForwardEverything = "forward";
    public static final String ForwardEverythingUnseen = "unseen";
    public static final String DelayedForwardUnseen = "delayedunseen";
    public static final String LeaderElection = "election";
    private Gateway the_gateway = null;
    private boolean multiGateways = false;
    private S2S s2s = null;
    

    public XOGComponent() {
        multiGateways = XopProperties.getInstance().getBooleanProperty(XopProperties.MULTIPLE_GATEWAYS);
        
        if (ForwardEverything.equals(XopProperties.getInstance().getProperty(XopProperties.GATEWAY_TYPE))) {
            the_gateway = new ForwardEverythingGateway();
            logger.info("Using ForwardEverything Gateway.");
        } else if (ForwardEverythingUnseen.equals(XopProperties.getInstance().getProperty(XopProperties.GATEWAY_TYPE))) {
            the_gateway = new ForwardUnseenGateway();
            logger.info("Using ForwardEverythingUnseen Gateway.");
        } else if (DelayedForwardUnseen.equals(XopProperties.getInstance().getProperty(XopProperties.GATEWAY_TYPE))) {
            the_gateway = new DelayedForwardUnseenGateway();
        } else if (LeaderElection.equals(XopProperties.getInstance().getProperty(XopProperties.GATEWAY_TYPE))) {
            the_gateway = new ElectedLeaderGateway();
            logger.info("Using ElectedLeader Gateway.");
        } else {
            logger.severe("Could not instantiate a concrete gateway!  Check the gateway type: "
                + XopProperties.getInstance().getProperty(XopProperties.GATEWAY_TYPE));
        }
        
//        s2s = new S2S();
//        s2s.start();
    }

    // just pass through to the concrete gateway
    @Override
    public void processPacket(Packet p) {
        logger.fine("Packet going to enterprise");
        the_gateway.processPacket(p);
    }

    // just pass through to the concrete gateway
    @Override
    public boolean accept(Packet packet) {
        if( multiGateways ){
            the_gateway.rewriteSubdomainIfNeeded(packet);
        }
        return the_gateway.accept(packet);
    }

    public void stop() {
        logger.info("Unregistering XOG component as a packet interceptor");
        super.componentManager.removeRoute(this);
        
//        s2s.closeS2SConnections();
    }
}