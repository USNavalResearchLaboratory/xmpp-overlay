package edu.drexel.xop.net;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.net.basicsds.BasicSDManager;
import edu.drexel.xop.net.protosd.ProtoSDManager;
import edu.drexel.xop.net.transport.BasicOneToOneTransportService;
import edu.drexel.xop.net.transport.OneToOneTransportService;
import edu.drexel.xop.net.transport.XOPTransportService;
import edu.drexel.xop.packet.TransportPacketProcessor;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.protosd.api.distobjects.SDInstance;
import mil.navy.nrl.protosd.api.distobjects.SDObjectFactory;
import mil.navy.nrl.protosd.api.distobjects.SDPropertyValues;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.xop.transport.reliable.XopNormService;
import mil.navy.nrl.xop.util.addressing.GroupPortPoolKt;
import mil.navy.nrl.xop.util.addressing.NetUtilsKt;
import mil.navy.nrl.xop.util.addressing.PortRange;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

/**
 * Get information about the network and send items through service discovery
 *
 */
public class XopNetImpl implements XopNet {
    private static final Logger logger = LogUtils.getLogger(XopNetImpl.class.getName());

    private SDManager sdManager;
    private String hostAddrStr;

    private XOPTransportService oneToOneTransport;
    private int oneToOnePort;

    // private GcsXoTransport gcs;
    private XopNormService xopNormService; // will be null if the service is not enabled
    private TransportPacketProcessor transportPacketProcessor;
    private ClientManager clientManager;

    public XopNetImpl(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    @Override
    public void init() throws InvocationTargetException, NoSuchMethodException,
            InstantiationException, InitializationException, IllegalAccessException,
            IOException // Exceptions thrown by SDSystem and TransportEngine initialization
    {
        InetAddress transportAddress = NetUtilsKt.getBindAddress(XOP.TRANSPORT.INTERFACE);
        this.hostAddrStr = transportAddress.getHostAddress();
        logger.info("initializing network, client binding to address: " + transportAddress);

        transportPacketProcessor = new TransportPacketProcessor(clientManager);
        initTransport(transportPacketProcessor);
        SDListener sdListener = new SDListenerImpl(clientManager, transportPacketProcessor);

        //initialize service discovery
        logger.info("XOP.SDS.SERVICE: " + XOP.SDS.SERVICE);
        switch(XOP.SDS.SERVICE){
            case "protosd":
                initProtoSD();
                break;
            case "norm-transport":

                if (xopNormService == null) {
                    xopNormService = initXopNormService(transportPacketProcessor);
                }

                sdManager = xopNormService.enablePresenceTransport(XOP.TRANSPORT.NORM.SD.INTERVAL,
                        XOP.TRANSPORT.NORM.SD.TIMEOUT,
                        sdListener);
                // 2019-02-26: This is unnecessary. PresenceTransport will construct, initialize, and return an sdManager
                // logger.fine("Adding sdListener to SDManager");
                // sdManager.addSDListener(sdListener);
                // sdManager.start();
                break;
            default:
                initSimpleSDService();
        }

        // TODO 2018-12-07 refactor this. We shouldn't have to initialize sdManager, do something, then addSDManager() to
        //  transportPacketProcessor
        transportPacketProcessor.addSDManager(sdManager);
        logger.info("XopNetImpl initialized");
    }

    /**
     * Initialize the transport system
     * @throws IOException when transport is not initialized
     */
    private void initTransport(TransportPacketProcessor transportPacketProcessor) throws IOException {
        logger.info("XOP.TRANSPORT.SERVICE: " + XOP.TRANSPORT.SERVICE);
        String ONETOONE = "ONETOONE";
        switch (XOP.TRANSPORT.SERVICE) {
            case "transport-engine":
                logger.info("Done. Initializing one-to-one TransportManager");
                oneToOneTransport = new OneToOneTransportService(clientManager, transportPacketProcessor);
                oneToOnePort = oneToOneTransport.getPort();
                logger.info("One-to-One TransportManager initialized");
                break;
            case "norm-transport":
                xopNormService = initXopNormService(transportPacketProcessor);
                oneToOneTransport = xopNormService.createNormTransport(null,
                        XOP.ENABLE.COMPRESSION);
                oneToOnePort = oneToOneTransport.getPort();
                logger.info("One-to-One NORM for Reliable Transport initialized");
                break;
            default:
                InetAddress oneToOneGroupAddress = InetAddress.getByName(XOP.TRANSPORT.ADDRESS);
                PortRange portRange = GroupPortPoolKt.extractPortRange(XOP.TRANSPORT.PORTRANGE);
                oneToOnePort = GroupPortPoolKt.getPort(ONETOONE, portRange.getStartPort(),
                        portRange.getEndPort());
                logger.info("Initializing Simple Transport");
                oneToOneTransport = new BasicOneToOneTransportService(XOP.BIND.INTERFACE,
                        oneToOneGroupAddress, oneToOnePort, clientManager, transportPacketProcessor);
                logger.info("Simple Transport initialized");
        }
    }

    private XopNormService initXopNormService(TransportPacketProcessor transportPacketProcessor) throws IOException {
        logger.info("Initializing NORM for Reliable Transport on iface:"
                + XOP.TRANSPORT.INTERFACE + ", port range: " + XOP.TRANSPORT.PORTRANGE);
        InetAddress multicastGroup = InetAddress.getByName(XOP.TRANSPORT.ADDRESS);

        return new XopNormService(XOP.TRANSPORT.INTERFACE, multicastGroup,
                XOP.TRANSPORT.PORTRANGE, transportPacketProcessor);
    }

    private void initProtoSD() throws InvocationTargetException,
            NoSuchMethodException, InstantiationException, InitializationException,
            IllegalAccessException {
        logger.info("Using ProtoSD for SD management. address: "+XOP.SDS.INDI.ADDRESS);
        System.setProperty("net.mdns.address", XOP.SDS.INDI.ADDRESS);
        SDPropertyValues values = new SDPropertyValues();
        // 2018-05-24 remove . from the XOP domain for listening on stuff
        values.setDomain(XOP.DOMAIN.replace(".", ""));
        //values.setHostName(XOP.HOSTNAME); // Does nothing
        values.setMulticastAddress(XOP.SDS.INDI.ADDRESS);
        values.setMulticastPort(XOP.SDS.INDI.PORT);
        values.setTtl(XOP.SDS.INDI.TTL);
        values.setRai(XOP.SDS.INDI.RAI);
        values.setNumra(XOP.SDS.INDI.NUMRA);
        values.setCgiStr(XOP.SDS.INDI.CQI);
        values.setSci(XOP.SDS.INDI.SCI);
        values.setNumscm(XOP.SDS.INDI.NUMSCM);
        values.setLoopback(XOP.SDS.INDI.LOOPBACK);
        values.setDuplicates(XOP.SDS.INDI.DUPLICATES);

        logger.info("ProtoSD property value - multicastAddress: "+values.getMulticastAddress());
        InetAddress sdBindAddress;
        if (XOP.SDS.INTERFACE != null) {
            sdBindAddress = NetUtilsKt.getBindAddress(XOP.SDS.INTERFACE);
            logger.info("INTERFACE: " + XOP.SDS.INTERFACE + " using " + sdBindAddress);
        } else {
            sdBindAddress = NetUtilsKt.getBindAddress(XOP.TRANSPORT.INTERFACE);
        }
        logger.info("SD bind address" + sdBindAddress);

        sdManager = (ProtoSDManager) SDObjectFactory.create(SDInstance.SDS_SYSTEM.INDI,
                sdBindAddress,
                values,
                ProtoSDManager.class);
        SDListener sdListener = new SDListenerImpl(clientManager, transportPacketProcessor);
        sdManager.addSDListener(sdListener);
        sdManager.start();

        logger.info("ProtoSDManager started!");
    }

    private void initSimpleSDService() throws UnknownHostException{
        logger.info("Initializing Simple Transport");
        InetAddress sdsGroup = InetAddress.getByName(XOP.SDS.SIMPLE.ADDRESS);
        int sdsPort = XOP.SDS.SIMPLE.PORT;
        sdManager = new BasicSDManager(XOP.BIND.INTERFACE, sdsGroup, sdsPort);
        logger.info("Simple Transport initialized");

    }

    /**
     * Uses the One-to-One TransportManager to send an xmpp packet. This transport manager will
     * handle incoming packets by sending them to XOProxy.processIncomingPacket(p).
     * @param packet the packet to be sent to the network
     */
    @Override
    public void sendToOneToOneTransport(Packet packet){
        oneToOneTransport.sendPacket(packet);
    }

    @Override
    public void close() {
        //stop service discovery
        if (sdManager != null) {
            sdManager.close();
    	}

        logger.info("Shutting down One-to-One Transport Manager");
        if( oneToOneTransport != null ){
            oneToOneTransport.close();
        }

        logger.info("Shutting down transport");
        switch(XOP.TRANSPORT.SERVICE){
            case "transport-engine":
                logger.info("... TransportEngine");
                // transportEngine.shutdown();
                break;
            case "ndn":
                logger.info("... NDN Transport");

                break;
            case "simple-transport":
                logger.info("... Simple Best-effort Transport");

                break;
            case "norm-transport":
                logger.info("... NORM Transport");
                xopNormService.shutdown();
                break;
        }

    	logger.info("XOPNet shutdown!");
    }

    @Override
    public SDManager getSDManager() {
        return sdManager;
    }

    @Override
    public XOPTransportService createXOPTransportService(JID roomJID) {
        XOPTransportService retVal = null;
        logger.info("Creating XOPTransportService of type: " + XOP.TRANSPORT.SERVICE);
        switch(XOP.TRANSPORT.SERVICE){
            case "transport-engine":
                break;
            case "groupcomms":
                break;
            case "ndn":
                break;
            case "simple-transport":
                break;
            case "norm-transport":
                retVal = xopNormService.createNormTransport(roomJID, XOP.ENABLE.COMPRESSION);
                break;
        }
        return retVal;
    }

    @Override
    public String getHostAddrStr(){
        return hostAddrStr;
    }

    @Override
    public int getOneToOnePort() {
        return oneToOnePort;
    }
}
