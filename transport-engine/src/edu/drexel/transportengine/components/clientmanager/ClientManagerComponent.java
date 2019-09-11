package edu.drexel.transportengine.components.clientmanager;

import edu.drexel.transportengine.components.Component;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Component which handles incoming client connections and creates <code>ClientHandler</code>s
 * for each.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class ClientManagerComponent extends Component {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private String iface;
    private int port;
    private final Set<ClientHandler> clients;
    private int nextId;

    /**
     * Instantiates a client manager component.
     *
     * @param engine reference to the Transport Engine.
     * @param iface  interface on which to accept for clients.
     * @param port   port on which to accept clients.
     */
    public ClientManagerComponent(TransportEngine engine, String iface, int port) {
        super(engine);
        clients = Collections.synchronizedSet(new HashSet<ClientHandler>());
        nextId = 0;
        this.port = port;
        this.iface = iface;
    }

    /**
     * Creates a <code>ServerSocket</code> for clients to connect to.  For each connection,
     * a new <code>ClientHandler</code> is instantiated.
     */
    @Override
    public void run() {
        ServerSocket socket;
        try {
            if (NetworkInterface.getByName(iface) == null) {
                logger.severe("No interface name " + iface);
                return;
            }

            // TODO: There may be a better way to do this
            Inet4Address bind = null;
            for (Enumeration<InetAddress> addresses = NetworkInterface.getByName(iface).getInetAddresses(); addresses.hasMoreElements(); ) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address) {
                    bind = (Inet4Address) address;
                }
            }
            //

            logger.info("Starting client manager on " + iface);
            if (bind != null) {
                socket = new ServerSocket(this.port, 0, bind);
            } else {
                logger.severe("Unable to find IP address.");
                return;
            }
        } catch (IOException ex) {
            logger.severe("Unable to create client socket.");
            System.exit(1);
            return;
        }

        while (true) {
            try {
                Socket clientSock = socket.accept();
                ClientHandler client = new ClientHandler(this, ++nextId, clientSock);
                logger.info("Adding new client with ID " + nextId);
                clients.add(client);
                client.start();
            } catch (IOException ex) {
                logger.warning("Unable to accept client.");
            }
        }
    }

    @Override
    public void shutdown() {
        synchronized (clients) {
            ClientHandler ch;
            for (Iterator<ClientHandler> i = clients.iterator(); i.hasNext(); ) {
                i.next().handleEndSession(null);
            }
        }
    }

    /**
     * Forwards events coming from the Transport Engine to connected clients
     * via their associated <code>ClientHandler</code>s.
     *
     * @param event the event to process.
     */
    @Override
    public void handleEvent(Event event) {
        synchronized (clients) {
            ClientHandler ch;
            for (Iterator<ClientHandler> i = clients.iterator(); i.hasNext(); ) {
                ch = i.next();
                ch.handleEngineEvent(event);
                if (!ch.isRunning()) {
                    i.remove();
                }
            }
        }
    }
}
