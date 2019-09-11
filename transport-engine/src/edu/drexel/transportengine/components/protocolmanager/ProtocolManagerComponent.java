package edu.drexel.transportengine.components.protocolmanager;

import edu.drexel.transportengine.components.Component;
import edu.drexel.transportengine.components.protocolmanager.protocols.UDP;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.addressing.MulticastAddressPool;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manager for transport layer protocols.  This class handles starting and shutting down transport layer instances as
 * necessary.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class ProtocolManagerComponent extends Component {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private List<Protocol> protocols;

    /**
     * Instantiates a new protocol manager.
     *
     * @param engine reference to the Transport Protocol.
     */
    public ProtocolManagerComponent(TransportEngine engine) {
        super(engine);
        protocols = new LinkedList<>();

        // TODO: Do this at runtime
        protocols.add(new UDP(this));
        //protocols.add(new NORM(this));
    }

    /**
     * Handles incoming events that should be sent out over the network.  This method selects a protocol appropriate
     * for the event's transport properties.
     *
     * @param event event from the Transport Engine.
     */
    @Override
    public void handleEvent(Event event) {
        if (event.getDest() != null && event.getEventSrc().equals(getTransportEngine().getGUID())) {
            // TODO: This should not be as strict.
            for (Protocol p : protocols) {
                if (!event.getTransportProperties().reliable || (event.getTransportProperties().reliable && p.isReliable())) {
                    //logger.fine("Using protocol " + p.getClass().getSimpleName() + " for message: " + event.getDest());
                    p.send(event, getAddress(event.getDest()));
                    return;
                }
            }
        } else if (event.getContents() instanceof CreateSocketEvent) {
            for (Protocol p : protocols) {
                CreateSocketEvent cse = (CreateSocketEvent) event.getContents();
                try {
                    p.createSocket(getAddress(cse.getDest()));
                } catch (IOException e) {
                    logger.warning("Unable to create socket for create-socket-event.");
                }
            }
        }
    }

    /**
     * Gets the address for a specified destination by hashing <code>dest</code> into an IP.
     *
     * @param dest destination to hash.
     * @return IP address for <code>dest</code>.
     */
    private InetAddress getAddress(String dest) {
        return MulticastAddressPool.getMulticastAddress(dest,
                Configuration.getInstance().getValueAsString(TEProperties.TRANSPORT_IPS));
    }
}
