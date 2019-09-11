package edu.drexel.xop.gateway;

import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.net.SDListener;
import edu.drexel.xop.net.SDManager;
import edu.drexel.xop.packet.LocalPacketProcessor;
import edu.drexel.xop.room.Room;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.xop.util.addressing.NetUtilsKt;

/**
 * A wrapper around the Initiating Gateway Connections and the reference to the
 * ReceivingGatewayThreads. XOProxy will interact with this object
 *
 * Created by duc on 7/13/16.
 */
public class ServerDialbackSession implements Runnable {
    /**
     * (targetDomain, generated hashkey)
     */
    static Map<String,String> initiatingServerKeys
            = Collections.synchronizedMap(new HashMap<>());

    // This variable stipulates whether the next incoming stream is for the authoritative server
    // If it is, then we should ONLY reply with an open stream
    // Else, reply with open stream, then stream features
    static boolean nextIncomingStreamIsForAuthoritativeServer = false;


    private static Logger logger = LogUtils.getLogger(ServerDialbackSession.class.getName());

    static InitiatingGatewayConnection initiatingGatewayConnection;
    private S2SReceivingListenerThread s2SReceivingListenerThread;

    // The set of verified receiving servers
    static Set<String> authenticatedReceivingServerNames =
            Collections.synchronizedSet(new HashSet<>());

    // Maps from hostname to the connection to that hostname
    static Map<String,ReceivingGatewayConnection> receivingGatewayConnections =
            Collections.synchronizedMap(new HashMap<>());

    // Discovered XOP occupants (not located over gateway)
    //public static Set<JID> xopMUCOccupants = Collections.synchronizedSet(new HashSet<JID>());

    public static Set<JID> remoteMUCOccupants = Collections.synchronizedSet(new HashSet<>());

    static Set<JID> gatewayMUC = Collections.synchronizedSet(new HashSet<>());

    // needed for establishing new dialback session for conference.xxx
    static String newFromHost;

    // needed for establishing new dialback for conference.xxx
    static String newToHost;

    private String remoteGatewayDomain;
    private String localGatewayDomain;

    private int gatewayReconnect;


    static GatewayPing gatewayPing;
    private static final ConcurrentLinkedQueue<Packet> outgoingPacketQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Packet> incomingPacketQueue;

    private String bindInterface;
    private int port;

    private LocalPacketProcessor packetProcessor;

    /**
     * default constructor
     */
    public ServerDialbackSession(String bindInterface, int port,
                                 String remoteGatewayDomain, String localGatewayDomain,
                                 int gatewayReconnect, LocalPacketProcessor packetProcessor) {
        // outgoingPacketQueue = new ConcurrentLinkedQueue<>();
        incomingPacketQueue = new ConcurrentLinkedQueue<>();
        this.packetProcessor = packetProcessor;
        this.bindInterface = bindInterface;
        this.port = port;
        this.remoteGatewayDomain = remoteGatewayDomain;
        this.localGatewayDomain = localGatewayDomain;
        newFromHost = localGatewayDomain;
        newToHost = remoteGatewayDomain;
        this.gatewayReconnect = gatewayReconnect;
    }

    /**
     * initialize the remote connections
     */
    private void init() throws IOException {
        logger.info("Starting XO S2SReceivingListenerThread on " + bindInterface);
        InetAddress gatewayBindAddress = NetUtilsKt.getBindAddress(bindInterface);
        s2SReceivingListenerThread = new S2SReceivingListenerThread(gatewayBindAddress, port, packetProcessor);
        s2SReceivingListenerThread.start();

        logger.info("Constructing federated connection");
        logger.info("XOP.GATEWAY.SERVER Remote Server Domain: " + newFromHost);
        logger.info("XOP.GATEWAY.DOMAIN Local Server Domain:  " + newToHost);
        new SessionConnector().start();
    }

    @Override
    public void run() {
        logger.info("Kicking off threads for Dialback connections");
        try {
            init();
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, "Unable to initialize the ServerDialback Session", ioe);
        }
    }

    public void shutdown(){
        s2SReceivingListenerThread.close();
        logger.info("Shutting down any open receiving connections");
        for( ReceivingGatewayConnection conn : receivingGatewayConnections.values() ) {
            logger.info("shutting down incoming connection from "+conn.getHostName());
            conn.stop();
        }
        if(initiatingGatewayConnection != null) {
            logger.info("Shutting down the outgoing connection to "
                    + initiatingGatewayConnection.getHostName());
            initiatingGatewayConnection.stop();
        }
    }

    /**
     * Will send the packet to the initiating gateway connection
     * @param p the packet to be sent over the gateway
     */
    public void sendPacket(Packet p){
        String toDomain = p.getTo().getDomain();
        String fromDomain = p.getFrom().getDomain();
        try {
            if( initiatingGatewayConnection != null
                    && initiatingGatewayConnection.isDomainVerified(toDomain))
            {
                if (logger.isLoggable(Level.FINE))
                    logger.fine("Send packet to gateway: [[[" + p.toString() + "]]]");
                if(initiatingGatewayConnection.isConnected()) {
                    initiatingGatewayConnection.sendPacket(p);
                } else {
                    logger.warning("initiatingGatewayConnection not connected, queuing for future use");
                    outgoingPacketQueue.add(p.createCopy());
                }
            } else {
                logger.fine("Queueing packet because Initiating Gateway connection is not present");
                outgoingPacketQueue.add(p.createCopy());
                initiatingGatewayConnection.initiateDialback(fromDomain, toDomain);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    } // end sendPacket()

    /**
     * A thread that sets up initiatingGateway Connections, will reconnect if ping is not
     * responded to for 1 minute.
     */
    private class SessionConnector extends Thread {
        public void run(){
            InetAddress address;
            Socket socket;
            int reconnectCount = 0;
            while (reconnectCount < gatewayReconnect) {
                try {
                    address = InetAddress.getByName(remoteGatewayDomain);
                    socket = new Socket(address, port);
                    logger.fine("Socket connected to " + socket);
                    initiatingGatewayConnection = new InitiatingGatewayConnection(socket,
                            remoteGatewayDomain,
                            localGatewayDomain,
                            CONSTANTS.GATEWAY.STREAM_ID);
                    gatewayPing = new GatewayPing(initiatingGatewayConnection,
                            remoteGatewayDomain,
                            localGatewayDomain);
                    initiatingGatewayConnection.setGatewayPing(gatewayPing);
                    initiatingGatewayConnection.setPacketQueue(outgoingPacketQueue);
                    initiatingGatewayConnection.init();
                } catch (UnknownHostException uhe) {
                    logger.log(Level.SEVERE, "UnknownHost: " + remoteGatewayDomain, uhe);
                    shutdown();
                    return;
                } catch (IOException ioe) {
                    logger.log(Level.SEVERE, "IOException. trying to reconnect", ioe);

                    if(authenticatedReceivingServerNames
                            .remove(initiatingGatewayConnection.remoteGatewayDomainName)) {
                        logger.fine("Removed: "
                                + initiatingGatewayConnection.remoteGatewayDomainName);
                    } else {
                        logger.fine(initiatingGatewayConnection.remoteGatewayDomainName
                                + " NOT FOUND for removal");
                    }
                    String key = initiatingGatewayConnection.xopGatewayDomainName + "=="
                            + initiatingGatewayConnection.remoteGatewayDomainName;
                    ReceivingGatewayConnection receivingGatewayConnection =
                            receivingGatewayConnections.remove(key);
                    if( receivingGatewayConnection != null ) {
                        logger.info("Shutting down receiving gateway connection");
                        receivingGatewayConnection.stop();
                    } else {
                        logger.fine("Receiving gateway connection with key: "
                                    + key + " not found!");
                    }

                    logger.info("Shutting down initiating gateway connection");
                    initiatingGatewayConnection.stop();
                } // end catch IOException
                reconnectCount++;
                try {
                    logger.info("S2S possibly disconnected, sleep for "+1000*reconnectCount+"ms then reconnect.");
                    Thread.sleep(1000*reconnectCount);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            logger.info("Attempted to reconnect "+reconnectCount+" times. Exiting...");
            shutdown();
        } // end run
    } // end SessionConnector Thread

    /**
     * Mainly for testing purposes without instantiating the whole XO system
     * @param args none here
     */
    public static void main(String[] args){
        if (args.length != 5) {
            System.err.println("Usage: ServerDialbackSession <iface> <port> <remote domain> <local domain> <reconnect ct>");
            return;
        }
        String iface = args[0];
        int port = Integer.parseInt(args[1]);
        String remote = args[2];
        String local = args[3];
        int reconnect = Integer.parseInt(args[4]);

        ClientManager clientManager = new ClientManager();
        SDManager sdManager = new SDManager() {
            @Override
            public void start() {

            }

            @Override
            public void close() {

            }

            @Override
            public void addGateway(InetAddress address, JID domain) {

            }

            @Override
            public void removeGateway(JID domain) {

            }

            @Override
            public void advertiseClient(Presence presence) {

            }

            @Override
            public void removeClient(Presence presence) {

            }

            @Override
            public void updateClientStatus(Presence presence) {

            }

            @Override
            public void advertiseMucOccupant(Presence presence) {

            }

            @Override
            public void removeMucOccupant(Presence presence) {

            }

            @Override
            public void updateMucOccupantStatus(Presence presence) {

            }

            @Override
            public void addSDListener(SDListener listener) {

            }

            @Override
            public void advertiseMucRoom(Room room) {

            }

            @Override
            public void removeMucRoom(Room room) {

            }
        };
        LocalPacketProcessor packetProcessor = new LocalPacketProcessor(clientManager, sdManager);

        LogUtils.loadLoggingProperties(null);
        ServerDialbackSession sds = new ServerDialbackSession(
                iface, port, remote, local, reconnect, packetProcessor
        );
        try {
            sds.init();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} // End ServerDialbackSession