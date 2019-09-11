package edu.drexel.xop.net;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import mil.navy.nrl.protosd.api.distobejcts.SDInstance;
import mil.navy.nrl.protosd.api.distobejcts.SDObjectFactory;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.imp.jmdns.JmDNSInstance;
import mil.navy.nrl.protosd.config.ProtoNetworkInterface;

import org.dom4j.DocumentException;
import org.json.simple.JSONObject;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import edu.drexel.transportengine.api.TransportEngineAPI;
import edu.drexel.transportengine.api.TransportEngineAPI.MessageCallback;
import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.net.discovery.SDXOPObject;
import edu.drexel.xop.net.discovery.XOPPropertyValues;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * This class is an access point to instantiating ProtoSD and one of its underlying service
 * bindings. This is also where messages are sent and received from the network.
 * 
 * @author David Millar, dnguyen, Ian Taylor, urlass
 */
public final class XopNet implements MessageCallback {

    private static SDXOPObject sdObject; // only one discovery instance

    /**
     * A list of addresses to bind to for CombinedSDInterface and Network Transport
     */
    private List<InetAddress> bindAddresses = null;
    private InetAddress bindAddress = null;
    private static final Logger logger = LogUtils.getLogger(XopNet.class.getName());
    public TransportEngineAPI te = null;

    Class<?> serviceDiscoveryClass = null;

    private String[] interfaces;

    // -----[ SINGLETON ]-----

    /**
     * private constructor
     */
    private XopNet() {
        int TRANSPORT_ENGINE_PORT = XopProperties.getInstance().getIntProperty(XopProperties.TRANSPORT_ENGINE_PORT);
        String TRANSPORT_ENGINE_ADDRESS = XopProperties.getInstance().getProperty(XopProperties.TRANSPORT_ENGINE_ADDRESS);
        int TRANSPORT_PERSIST_TIME = XopProperties.getInstance().getIntProperty(XopProperties.TRANSPORT_PERSIST_TIME);
        try {
            bindAddresses = retrieveBindAddresses();
            bindAddress = bindAddresses.get(0);
            logger.log(Level.INFO, "bindAddress: " + bindAddress);
            initSD();

            logger.log(Level.INFO, "Attempting to connect to transport engine at: " + TRANSPORT_ENGINE_ADDRESS + "/"
                + TRANSPORT_ENGINE_PORT);

            // build the transport engine, and register for callback when a message comes in
            te = new TransportEngineAPI(TRANSPORT_ENGINE_ADDRESS, TRANSPORT_ENGINE_PORT);
            te.registerMessageCallback(this);
            te.start();
            te.executeChangeProperties(false, TRANSPORT_PERSIST_TIME, false);

        } catch (ConnectException ce) {
            logger.log(Level.SEVERE, "Error connecting to transport engine.  Is it running on port "
                + TRANSPORT_ENGINE_PORT + "?\nExiting...");
            System.exit(1);
        } catch (Exception ioe) {
            logger.log(Level.SEVERE, "Exiting. Error initializing network: " + ioe.getMessage(), ioe);
            System.exit(1);
        }
        logger.log(Level.INFO, "Successfully connected to transport engine.");
    }

    private static class InstanceHolder {
        private static final XopNet INSTANCE = new XopNet();
    }

    /**
     * @return the SD object instance that Gump has created. This is the instance of the
     * default discovery instance for XOP that is created by default through Gump.
     */
    public static SDXOPObject getSDObject() {
        return sdObject;
    }

    /**
     * @return the XopNet singleton
     */
    public static XopNet getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Initialize the service discovery system.
     * 
     * @throws ClassNotFoundException
     * @throws InitializationException
     */
    private void initSD() throws ClassNotFoundException, InitializationException {
        logger.log(Level.INFO, "Initializing Service Discovery...");
        logger.log(Level.INFO, "java.library.path=" + System.getProperty("java.library.path"));
        XopProperties xopProps = XopProperties.getInstance();

        logger.info("setting SD class!");
        serviceDiscoveryClass = Class.forName(xopProps.getProperty(XopProperties.SD_CLASS));
        logger.info("done setting SD class");

        try {
            XOPPropertyValues propVals = new XOPPropertyValues();
            // logger.log(Level.FINE, "propVals: ttl: "+propVals.getTtl()+" rai:"+propVals.getRai());

            // This conditional is to ensure setting INDI properties are done properly. see artf1485.
            if (serviceDiscoveryClass.equals(JmDNSInstance.class)) {
                logger.info("Using JmDNS for service discovery");
                sdObject = (SDXOPObject) SDObjectFactory.create(SDInstance.SDS_SYSTEM.MDNS, bindAddress, propVals, SDXOPObject.class);
            } else {
                logger.info("Using INDI for service discovery");
                sdObject = (SDXOPObject) SDObjectFactory.create(SDInstance.SDS_SYSTEM.INDI, bindAddress, propVals, SDXOPObject.class);
            }
        } catch (InstantiationException e) {
            logger.severe("Error instantiating service discovery system.");
            e.printStackTrace();
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    public void stop() {
        logger.log(Level.INFO, "Shutting down all Service Discovery subsystems...");
        SDObjectFactory.close();
        logger.log(Level.INFO, "Shutting down Transport Engine...");
        try {
            te.executeEndSession();
        } catch (IOException ex) {
            logger.warning("Could not stop Transport Engine");
        }
        logger.log(Level.INFO, "DONE");
    }

    /**
     * Gets the addresses to bind to for CombinedSDInterface and transport bound to the interface specified in xop.properties
     * 
     * @return a set of addresses
     */
    private List<InetAddress> retrieveBindAddresses() {
        List<InetAddress> retVal = new LinkedList<>();
        try {
            NetworkInterface ni;
            XopProperties xopProps = XopProperties.getInstance();
            String bindInterfaces = xopProps.getProperty(XopProperties.BIND_INTERFACE);
            logger.fine("bindInterfaces: " + bindInterfaces);
            interfaces = bindInterfaces.split(",");
            for (String bindInt : interfaces) {
                ni = NetworkInterface.getByName(bindInt);
                logger.log(Level.INFO, "binding to interface: " + bindInt);
                if (ni != null) {
                    Enumeration<InetAddress> addrs;
                    try {
                        addrs = ni.getInetAddresses();
                        logger.log(Level.INFO, "getting bind addresses for interface: " + ni.getName() + " ni:<<<" + ni
                            + ">>>");
                        while (addrs.hasMoreElements()) {
                            InetAddress addr = addrs.nextElement();
                            if (addr instanceof Inet4Address) { // Sorry we don't support IPv6
                                logger.log(Level.INFO, "adding address: " + addr.getHostAddress());
                                retVal.add(addr);
                            }
                        }
                    } catch (NullPointerException e) {
                        logger.log(Level.SEVERE, "ERROR:  " + bindInt
                            + " is not a valid interface. Please ensure the interface is up and has an IP address.", e);
                        System.exit(1);
                    }
                } else {// the network interface is not found
                    logger.info("Interface, " + bindInt + ", is unavailable attempting to detect local interface");
                    InetAddress nullInetAddr = null;
                    ProtoNetworkInterface protoNetIface = new ProtoNetworkInterface(nullInetAddr);
                    InetAddress addr = protoNetIface.getInterfaceAddressObj();
                    logger.info("found address: " + addr.getHostAddress());
                    retVal.add(addr);
                }
            }
        } catch (SocketException ex) {
            logger.log(Level.SEVERE, "socket exception found!", ex);
        }
        return retVal;
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.api.Network#getBindAddresses()
     */
    public List<InetAddress> getBindAddresses() {
        return bindAddresses;
    }

    public InetAddress getBindAddress() {
        return bindAddress;
    }

    public void sendOutgoingMessage(Message m) {
        try {
            te.executeSend(m.getID(), m.getTo().getNode(), m.toXML());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(String destination) {
        try {
            te.executeSubscription(destination, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    /**
     * Handle incoming messages from the Transport Engine
     */
    public void processMessage(JSONObject message) {
        // get the payload
        String sPacket = ((String) ((JSONObject) message.get("info")).get("payload"));

        // try converting it to an XMPP packet
        Packet incomingPacket = null;
        try {
            incomingPacket = Utils.packetFromString(sPacket);
        } catch (DocumentException e) {
            logger.severe("Unable to create packet from this string from the TE: " + sPacket);
            e.printStackTrace();
        }

        // pass XMPP packet to the packet manager
        ClientProxy.getInstance().processPacket(incomingPacket);
    }
}
