package edu.drexel.xop.gateway;

import org.xmpp.packet.Packet;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Establishes and maintains a socket connection with an XMPP server
 *
 */
class InitiatingGatewayConnection extends GatewayConnection {
    private static final Logger logger =
            LogUtils.getLogger(InitiatingGatewayConnection.class.getName());

    private AtomicBoolean dialbackComplete = new AtomicBoolean(false);
    private ConcurrentLinkedQueue<Packet> packetQueue = new ConcurrentLinkedQueue<>();

    private Set<String> verifiedDomains = Collections.synchronizedSet(new HashSet<String>());
    private AtomicBoolean sessionInitializing = new AtomicBoolean(false);

    String xopGatewayDomainName;
    String remoteGatewayDomainName;
    private String sessionId;

    // private S2SReceivingListenerThread receivingListenerThread;
    private InitiatingGatewayXMLProcessor initiatingGatewayXMLProcessor;

    InitiatingGatewayConnection(Socket socket,
                                String remoteGatewayDomainName,
                                String xopGatewayDomainName,
                                String sessionId) {
        super();
        // receivingListenerThread = glt;
        this.remoteGatewayDomainName = remoteGatewayDomainName;
        this.xopGatewayDomainName = xopGatewayDomainName;
        // this.port = port;
        this.socket = socket;
        this.sessionId = sessionId;
        this.clientMode = true;
        // this.socket = new Socket(address, port);
        logger.info( "Using socket: "+ socket);
        initiatingGatewayXMLProcessor =
                new InitiatingGatewayXMLProcessor(this,
                        remoteGatewayDomainName, xopGatewayDomainName, sessionId);
        sessionInitializing.set(true);
        logger.info("Constructed InitiatingGatewayConnection.");
    }

    boolean isDomainVerified(String domain){
        return verifiedDomains.contains(domain);
    }

    void addVerifiedDomain(String domain){
        verifiedDomains.add(domain);
    }

    // void processCloseStream() {
    //     // handle the close stream for xop
    //     stopping=true;
    //     killSwitch.set(true);
    //     try{
    //         gatewayInputStream.close();
    //         gatewayOutputStream.close();
    //
    //         socket.close();
    //     } catch (IOException e) {
    //         logger.severe("Exception closing streams and socket " + e.getMessage());
    //     }
    //
    // }


    public void stop() {
        try {
            writeRaw(CONSTANTS.AUTH.STREAM_CLOSE.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopping = true;
        killSwitch.set(true);
    }

    @Override
    public String toString() {
        return "InitiatingGatewayConnection";
    }

    public GatewayXMLProcessor getXMLProcessor()    {
        return initiatingGatewayXMLProcessor;
    }

    public boolean getClientMode() {
        return clientMode;
    }

    public void init() throws IOException {
        super.init();
        //only do this if we are initiating the connection
        logger.info("Sending open stream. Domain: "+xopGatewayDomainName
                + ", Remote gateway domain: "+remoteGatewayDomainName +", Session id: "+sessionId); //S2S Step 1
        String streamOpen = GatewayUtil.generateS2SOpenStream( xopGatewayDomainName, remoteGatewayDomainName, sessionId);
        logger.fine("streamOpen: " + streamOpen);
        this.writeRaw(streamOpen.getBytes());
    }


    /**
     *
     * @param dialbackComplete set the atomic flag to true when dialback is complete
     */
    void setDialbackComplete(boolean dialbackComplete, String domain ) {
        this.dialbackComplete.set(dialbackComplete);
        this.verifiedDomains.add(domain);
        this.sessionInitializing.set(false);
        try {
            sendQueuedPackets();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initiates dialback if not initialized yet.
     * @param fromDomain the from domain
     * @param toDomain to to domain
     */
    void initiateDialback(String fromDomain, String toDomain) throws IOException {
        if( !sessionInitializing.get() ) {
            sessionInitializing.set(true);
            String streamId = Utils.generateID(8);
            logger.fine("Initiating dialback with streamId "+streamId
                    + " from: " + fromDomain + " to: " + toDomain);
            initiatingGatewayXMLProcessor.initiateDialback(fromDomain, toDomain, streamId);
        }
    }

    void setPacketQueue(ConcurrentLinkedQueue<Packet> packetQueue){
        this.packetQueue = packetQueue;
    }

    /**
     * called only once after dialback is complete
     */
    void sendQueuedPackets() throws IOException {
        logger.fine("Writing " + packetQueue.size()
                + " queued outgoing packets to initiating connection");
        int ct = 0;
        while(!packetQueue.isEmpty()){
            Packet p = packetQueue.remove();
            this.writeRaw(p.toXML().getBytes());
            ct++;
        }
        logger.fine("Wrote "+ct+" packets to the initiating connection");
    }

    boolean hasQueuedPackets(){
        return !packetQueue.isEmpty();
    }

    /**
     * @param p
     *         packet to be written to the output stream
     *
     * @throws IOException
     *         if there
     */
    void sendPacket(Packet p) throws IOException{
        String domain = p.getTo().getDomain();
        if( isDomainVerified(domain) ){
            if( logger.isLoggable(Level.FINEST))
                logger.finest("domain "+domain+" is verified, writing to socket");
            writeRaw(p.toString().getBytes());
        } else {
            if( logger.isLoggable(Level.FINE))
                logger.fine("domain "+domain+" is NOT verified, queueing packet");
            packetQueue.add(p.createCopy());
        }
    }

    // // TODO 2018-02-26 remove
    // public void authorizeMultiplexedDomain(String remoteHost, String xopGatewayDomainName,
    //                                        String sessionId, String hashKey) {
    //     initiatingGatewayXMLProcessor.authorizeMultiplexedDomain(remoteHost,
    //                                          xopGatewayDomainName, sessionId, hashKey);
    // }
}
