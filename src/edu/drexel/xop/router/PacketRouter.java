package edu.drexel.xop.router;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

import edu.drexel.xop.client.ClientConnection;
import edu.drexel.xop.client.LocalClientListener;
import edu.drexel.xop.component.VirtualComponent;
import edu.drexel.xop.core.PacketListener;
import edu.drexel.xop.interceptor.DuplicatePacketInterceptor;
import edu.drexel.xop.interceptor.PacketInterceptor;
import edu.drexel.xop.interceptor.SelfAddressedPacketInterceptor;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Maintains a list of PacketInterceptors. If any of them "intercept" the packet, it stops processing the packet.
 * 
 * Maintains a list of components which must implement PacketFilter. If the packet was not intercepted, it tests every
 * incoming packet against the components' accept() case.
 * 
 * @author di
 */
public class PacketRouter implements LocalClientListener, PacketListener {

    private static final Logger logger = LogUtils.getLogger(PacketRouter.class.getName());

    // Queues & Reapers
    private ThreadedPacketDeliveryQueue deliveryQueue;
    private IQReaper reaper;

    // Sets
    private Set<PacketInterceptor> packetInterceptors = Collections.synchronizedSet(new HashSet<PacketInterceptor>());
    private final Map<String, VirtualComponent> routes = new ConcurrentHashMap<String, VirtualComponent>();

    /**
     * Constructor
     */
    public PacketRouter() {
        logger.fine("Creating the PacketRouter");
        this.addPacketInterceptor(new DuplicatePacketInterceptor());
        this.addPacketInterceptor(new SelfAddressedPacketInterceptor());
        deliveryQueue = new ThreadedPacketDeliveryQueue(this);
        reaper = new IQReaper();
        Thread t = new Thread(reaper, "IQ Timeout Thread");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Send a new packet into the PacketRouter for processing
     * 
     * @param packet
     */
    // Do not use! Instead use ClientProxy.getInstance().processPacket(p)
    public void processPacket(Packet packet) {
        logger.fine("Received a packet to process: " + packet.toString());
        if (packet instanceof IQ) {
            reaper.handleDelayedIQ((IQ) packet);
        }
        deliveryQueue.enqueuePacket(packet);
    }

    @Override
    public void localClientAdded(ClientConnection clientConnection) {
        logger.fine("Adding route due to new ClientConnection: " + clientConnection.getJid());
        addRoute(clientConnection);
    }

    @Override
    public void localClientRemoved(ClientConnection clientConnection) {
        logger.fine("Removing route due to closed ClientConnection: " + clientConnection.getJid());
        removeRoute(clientConnection);
    }

    /**
     * Add a PacketInterceptor
     * 
     * @param packetInterceptor
     */
    public void addPacketInterceptor(PacketInterceptor packetInterceptor) {
        logger.fine("Adding a PacketInterceptor" + packetInterceptor.toString());
        packetInterceptors.add(packetInterceptor);
    }

    /**
     * Add a new component (and thus, a route) to the router
     * 
     * @param vc
     */
    public synchronized void addRoute(VirtualComponent vc) {
        logger.fine("Adding a generic route for a " + vc.getClass().getSimpleName());
        routes.put(vc.getClass().getSimpleName(), vc);
    }

    /**
     * Remove a component and corresponding route from the router
     * 
     * @param pl
     */
    public synchronized void removeRoute(VirtualComponent pl) {
        logger.fine("Removing a generic route for VirtualComponent: " + pl.getClass().getSimpleName());
        routes.remove(pl.getClass().getSimpleName());
    }

    /**
     * For printing to the terminal, etc.
     */
    public String toString() {
        String rval = "PacketRouter: available routes:";
        for (VirtualComponent pl : routes.values()) {
            rval += "\n > " + pl.getClass().getSimpleName();
        }
        return rval;
    }

    /**
     * Tests the packet against added components' test cases, and handles
     * processing accordingly
     * Note, this is only called in ThreadedPacketDeliveryQueue
     * 
     * @param p
     */
    synchronized void route(Packet p) {
        logger.fine("Processing packet: " + p);
        if (handleInterceptors(p)) {
            Boolean packetHandled = false;
            for (VirtualComponent vc : routes.values()) {
                logger.finer("Trying " + vc.getClass().getSimpleName());
                if (vc.accept(p)) {
                    logger.fine("Accepted by " + vc.getClass().getSimpleName());
                    packetHandled = true;
                    vc.processPacket(p.createCopy());
                }
            }
            if (!packetHandled) {
                logger.warning("The following packet was NOT accepted by any filters: \n " + p);
            }
        }
    }

    /**
     * Will iterate through all VirtualComponents (routes) and call the processCloseStream() method
     * 
     * @param fromJID
     */
    public void handleCloseStream(String fromJID) {
        synchronized (routes) {
            for (VirtualComponent vc : routes.values()) {
                logger.fine("Closing stream for: " + fromJID + " in VirtualComponent: " + vc.getClass().getSimpleName());
                vc.processCloseStream(fromJID);
            }
        }
    }

    /**
     * Test the packet against all added interceptors
     * 
     * @param p
     * @return
     */
    private boolean handleInterceptors(Packet p) {
        logger.fine("Passing the packet through the interceptors");
        for (PacketInterceptor interceptor : packetInterceptors) {
            if (!interceptor.interceptPacket(p)) {
                logger.fine("Packet rejected by interceptor!");
                return false;
            }
        }
        logger.fine("Packet accepted by interceptors");
        return true;
    }
}