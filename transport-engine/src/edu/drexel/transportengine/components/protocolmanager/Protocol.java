package edu.drexel.transportengine.components.protocolmanager;

import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * An abstraction of a transport layer protocol.  This handles sending and receiving of data through this layer.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public abstract class Protocol extends Thread {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private ProtocolManagerComponent protocolManager;

    /**
     * Instantiates a new protocol.
     *
     * @param protocolManager reference to the protocol manager.
     */
    public Protocol(ProtocolManagerComponent protocolManager) {
        this.protocolManager = protocolManager;
    }

    /**
     * Gets the port the protocol should use.
     *
     * @return the port number to use.
     */
    public final int getPort() {
        // TODO: This is a messy way of getting the port
        return Configuration.getInstance().getValueAsInt(
                TEProperties.fromKey("protocol." + getProtocolName() + ".port"));
    }

    protected abstract Object createSocket(InetAddress address) throws IOException;

    /**
     * Gets the protocol manager specified at instantiation.
     *
     * @return the protocol manager.
     */
    protected ProtocolManagerComponent getProtocolManager() {
        return protocolManager;
    }

    /**
     * Handles incoming data from the transport layer by converting it to an event and injecting it into the
     * Transport Engine's event bus.
     *
     * @param data data to convert to an event.
     */
    protected void handleIncoming(byte[] data) {
        Event event = new Event(data);
        if (!event.getEventSrc().equals(protocolManager.getTransportEngine().getGUID())) {
            logger.fine("Got event of type " + event.getContents().getName() + ".  Executing...");
            getProtocolManager().getTransportEngine().executeEvent(event);
        }
    }

    /**
     * The protocol name.
     *
     * @return name of the protocol.
     */
    public abstract String getProtocolName();

    /**
     * Determines if the protocol is reliable.
     *
     * @return if the protocol is reliable.
     */
    public abstract boolean isReliable();

    /**
     * Sends an event to this transport layer implementation.
     *
     * @param event   event to send.
     * @param address destination of event.
     */
    public abstract void send(Event event, InetAddress address);
}
