/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.core;

import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import mil.navy.nrl.protosd.api.distobejcts.SDObject;

import org.xmpp.packet.Packet;

import edu.drexel.xop.client.ClientListenerThread;
import edu.drexel.xop.client.ClientManager;
import edu.drexel.xop.client.PacketManager;
import edu.drexel.xop.component.ComponentManager;
import edu.drexel.xop.component.VirtualComponent;
import edu.drexel.xop.iq.IqManager;
import edu.drexel.xop.iq.RosterManager;
import edu.drexel.xop.muc.MUCComponent;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.presence.PresenceManager;
import edu.drexel.xop.presence.SDPresenceManager;
import edu.drexel.xop.router.PacketRouter;
import edu.drexel.xop.util.logger.LogUtils;
import edu.drexel.xop.web.WebServer;

/**
 * Facade to the client proxy, also a container for the major components.<br/>
 * All clients send all messages to this component.<br/>
 * 
 * @author David Millar, Rob Lass
 */
public class ClientProxy {
    // Various managers
    @SuppressWarnings("unused")
    private SDObject xopSD;
    private IqManager iqManager;
    private RosterManager rosterManager;
    private ComponentManager componentManager;
    private PacketRouter packetRouter;
    private PresenceManager presenceManager;
    private ClientManager clientManager;
    private PacketManager packetManager;
    private SDPresenceManager remotePresenceManager;
    private HashSet<Stoppable> threadsToStop = new HashSet<>();

    private static final Logger logger = LogUtils.getLogger(ClientProxy.class.getName());

    private static final String MUC_COMPONENT_NAME = "edu.drexel.xop.component.muc.MUCComponent";

    private static class ClientProxyHolder {
        public static final ClientProxy INSTANCE = new ClientProxy();
    }

    public static ClientProxy getInstance() {
        return ClientProxyHolder.INSTANCE;
    }

    public void init() {
        @SuppressWarnings("unused")
        XopNet xopNet = XopNet.getInstance();
        xopSD = XopNet.getSDObject();

        // Create the Packet Router
        packetRouter = new PacketRouter();

        // Create the various managers
        clientManager = new ClientManager(packetRouter);
        packetManager = new PacketManager();
        componentManager = new ComponentManager(packetRouter);
        rosterManager = new RosterManager(clientManager);
        iqManager = new IqManager(packetRouter, componentManager, rosterManager);
        presenceManager = new PresenceManager(packetRouter, clientManager);
        remotePresenceManager = new SDPresenceManager(clientManager);

        // Remote Presence Manager
        packetRouter.addRoute(remotePresenceManager);

        // Packet Listeners
        packetManager.addPacketListener(new WebServer());
        packetManager.addPacketListener(packetRouter);

        // MUC Component
        componentManager.addComponent(new MUCComponent(clientManager, iqManager));

        ClientListenerThread clt = new ClientListenerThread(clientManager);
        if (clt.init()) {
            clt.start();
            this.addThreadToStop(clt);
        } else {
            logger.severe("Unable to start ClientListenerThread. Exiting.");
            this.stop();
        }
    }

    /**
     * -----[ LifeCycle ]-----
     */
    public synchronized void stop() {
        // Send everyone unavailable messages
        logger.log(Level.INFO, "Sending user unavailable messages...");
        if (presenceManager != null) {
            presenceManager.sendUnavailablePresenceMessages();
        }

        // Shutdown Components
        logger.log(Level.INFO, "Shutting down component manager...");
        if (componentManager != null) {
            componentManager.shutDown();
        }

        // Shut down XOP Network
        logger.log(Level.INFO, "Shutting down XopNet...");
        XopNet.getInstance().stop();

        // Shutdown threads
        for (Stoppable s : threadsToStop) {
            s.stopMe();
        }

        logger.log(Level.INFO, "Shut down complete.");
        System.exit(0);
    }

    /**
     * Sends the packet to PacketListeners for distribution
     * 
     * @param p
     */
    public void processPacket(Packet p) {
        this.packetManager.processPacket(p);
    }

    /**
     * close the connection, remove clientRoute, send unavailable presences
     * 
     * @param fromJID
     */
    public void handleCloseStream(String fromJID) {
        logger.fine("processing close stream from ClientProxy for fromjid:" + fromJID);
        packetRouter.handleCloseStream(fromJID);
    }

    // //////////////////////////////
    // informational get functions //
    // //////////////////////////////

    public String getRooms() {
        VirtualComponent comp = componentManager.getComponent(MUC_COMPONENT_NAME);
        if (comp != null) {
            return comp.toString();
        }
        return "The MUC service does not seem to be running.";
    }

    public String getComponents() {
        return componentManager.toString();
    }

    public String getClients() {
        return clientManager.toString();
    }

    public String getRosters() {
        return rosterManager.toString();
    }

    public String getRoutes() {
        return packetRouter.toString();
    }

    public void addThreadToStop(Stoppable s) {
        threadsToStop.add(s);
    }
}