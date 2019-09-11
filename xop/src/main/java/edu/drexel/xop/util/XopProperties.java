package edu.drexel.xop.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads default properties for XOP, these are overwritten by changes in the xop.properties file.
 *
 * You can also override properties by setting them at the command line.
 * For example: java -Dxop.bind.interface=eth0 -jar xop.jar to override the xop.bind.interface property.
 *
 */
public class XopProperties {
	// Don't use LogUtils.getLogger here because LogUtils.getLogger references this class
    private static final Logger logger = Logger.getLogger(XopProperties.class.getName());

    private static final String DEFAULT_FILE_LOCATION = "config/xop.properties";
    
    private static Properties properties = null;
    private static HashMap<String, String> comments = new HashMap<>();
    /**
     * 
     * Description: loads default XOP Properties for android. 
     * Note: it will not look for and load from the DEFAULT_FILE_LOCATION
     */
    public static void loadDefaults() {
        properties = defaultProperties();
    }
    
    public static void loadPropertiesAndroid(){
        System.setProperty("properties.file","/data/local/xop.properties");
        load();
    }

    /**
     * Load the properties file and then override with any system properties
     */
    private static void load() {
        properties = defaultProperties();
        
        /* 
        try {
        	properties.load(XopProperties.class.getClassLoader().getResourceAsStream("xop.properties"));
        	logger.log(Level.INFO, "Properties loaded from xop.properties");
		} catch (IOException e) {
			logger.severe("Error while reading properties file!");
			e.printStackTrace();
		} */
        
        /* TODO: above is better way to do what's below, but xop.properties needs to be added to build path */
        
        //look for a properties file ------------------------------------------------------------------------------------------
        String fileLocation = System.getProperty("properties.file");
        if( fileLocation == null ){
            fileLocation = DEFAULT_FILE_LOCATION;  // this always happens, above is not working
        }
        logger.info("Loading properties from location: " + fileLocation);

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(new File(fileLocation).getAbsoluteFile()));
            try {
                properties.load(reader);
                logger.fine("loaded properties file");
            } catch (IOException e) {
                logger.severe("Error while reading file: " + fileLocation);
            }
        } catch (FileNotFoundException e){
            logger.log(Level.WARNING, "Unable to load properties from: " + fileLocation);  // always happens
        } finally {
            if(reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.severe("Unable to close file: " + fileLocation);
                    //System.exit(1);
                }
            }
        }
       //--------------------------------------------------------------------------------------------------------------------------
        logger.fine("Loading system properties");
        //set any system properties
        for(Object obj : properties.keySet()) {
            String key = ((String)obj).toLowerCase();
            if(System.getProperty(key) != null) {
                logger.fine(key);
                properties.setProperty(key, (System.getProperty(key)).toLowerCase());
            }
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("All properties:");

            for (Object obj : properties.keySet()) {
                String key = ((String) obj).toLowerCase();
                logger.finest(key + ":" + properties.getProperty(key));
            }
        }
    }

    /**
     * Generate the default properties for XOP
     * @return A Properties object containing all of these properties
     */
    private static Properties defaultProperties() {
        logger.finer("setting DefaultProperties");
        Properties props = new Properties();

        props.setProperty(XOPKEYS.DOMAIN, "proxy");
        comments.put(XOPKEYS.DOMAIN, "The domain name for this proxy instance.");

        props.setProperty(XOPKEYS.PORT, "5222");
        comments.put(XOPKEYS.PORT, "The port to run the XMPP server on (for XMPP clients).");

        props.setProperty(XOPKEYS.BIND.INTERFACE, "eth0");
        comments.put(XOPKEYS.BIND.INTERFACE, "The interface to listen for XMPP clients or 'ANY' for all interfaces");

        props.setProperty(XOPKEYS.ENABLE.GATEWAY, "false");
        comments.put(XOPKEYS.ENABLE.GATEWAY, "Set to true to enable gatewaying to another XMPP server via dialback protocol, false to disable");

        props.setProperty(XOPKEYS.ENABLE.COMPRESSION, "false");
        comments.put(XOPKEYS.ENABLE.COMPRESSION, "Enable/disable compressing messages before sending to the Transport system. Default: false (disabled)");

        props.setProperty(XOPKEYS.ENABLE.STREAM, "false");
        comments.put(XOPKEYS.ENABLE.STREAM, "Set to true to enable bytestreams, false to disable");

        props.setProperty(XOPKEYS.ENABLE.ANDROIDLOGGING, "false");
        comments.put(XOPKEYS.ENABLE.ANDROIDLOGGING, "Set to true to enable logging in the app text view, false to disable");
        props.setProperty(XOPKEYS.ENABLE.DEVICELOGGING, "false");
        comments.put(XOPKEYS.ENABLE.DEVICELOGGING, "Set to true to enable text file logging on the android file system, false to disable");

        props.setProperty(XOPKEYS.ENABLE.DELAY, "true");
        comments.put(XOPKEYS.ENABLE.DELAY, "add delay element to messages according to XEP ");

        // props.setProperty(XOPKEYS.ONETOONE.ADDRESS, "225.0.2.187");
        // comments.put(XOPKEYS.ONETOONE.ADDRESS, "Multicast address that XMPP clients will bind to for one-to-one chats. Uses TransportEngine");
        // props.setProperty(XOPKEYS.ONETOONE.LISTENPORT, "6667");
        // comments.put(XOPKEYS.ONETOONE.LISTENPORT, "The port that XOP will listen on when Transport engine is not enabled");

        props.setProperty(XOPKEYS.TLS.AUTH, "false");
        comments.put(XOPKEYS.TLS.AUTH, "set to true if clients must connect via tls, false otherwise");

        props.setProperty(XOPKEYS.STREAM.PORT, "7625");
        comments.put(XOPKEYS.STREAM.PORT, "The port on which clients should connect to open a bytestream");
        String xopDomain = props.getProperty(XOPKEYS.DOMAIN);
        props.setProperty(XOPKEYS.STREAM.JID, "stream." + xopDomain);
        comments.put(XOPKEYS.STREAM.JID, "The JID to identify the bytestream host");

        props.setProperty(XOPKEYS.SSL.KEYSTORE, "config/keystore.jks");
        comments.put(XOPKEYS.SSL.KEYSTORE, "Where the server keystore is located");
        props.setProperty(XOPKEYS.SSL.TRUSTSTORE, "config/cacerts.jks");
        comments.put(XOPKEYS.SSL.TRUSTSTORE, "Where the truststore for server-to-server connections is located");
        props.setProperty(XOPKEYS.SSL.PASSWORD, "xopstore");
        comments.put(XOPKEYS.SSL.PASSWORD, "The password for the keystore and truststore");

        /* ---- SDS Presence Transport ---- */

        props.setProperty(XOPKEYS.SDS.SERVICE, "norm-transport");
        comments.put(XOPKEYS.SDS.SERVICE, "choose which Service discovery system to use (for presence), default 'norm-transport'");

        props.setProperty(XOPKEYS.SDS.INDI.ADDRESS, "225.0.2.186");
        comments.put(XOPKEYS.SDS.INDI.ADDRESS, "The multicast address for INDI to bind to. default 224.0.1.186");
        props.setProperty(XOPKEYS.SDS.INDI.PORT, "5353");
        comments.put(XOPKEYS.SDS.INDI.PORT, "The multicast port for INDI to bind to. default 5353");
        props.setProperty(XOPKEYS.SDS.INDI.TTL, "15000");
        comments.put(XOPKEYS.SDS.INDI.TTL, "Lifetime (TTL) of the service in milliseconds (long)");
        props.setProperty(XOPKEYS.SDS.INDI.RAI, "10000");
        comments.put(XOPKEYS.SDS.INDI.RAI, "Service proactive re-advertisement interval for sending advertisements in milliseconds (long)");
        props.setProperty(XOPKEYS.SDS.INDI.NUMRA, "-1");
        comments.put(XOPKEYS.SDS.INDI.NUMRA, "Number of times to readvertise the service (int, -1 = forever)");
        props.setProperty(XOPKEYS.SDS.INDI.CQI, "0,100,200,400,800,1600");
        comments.put(XOPKEYS.SDS.INDI.CQI, "Times a client queries for a service e.g. 0,100,200 (comma separated list of longs)");
        props.setProperty(XOPKEYS.SDS.INDI.SCI, "12000");
        comments.put(XOPKEYS.SDS.INDI.SCI, "Proactive service cancel notification interval");
        props.setProperty(XOPKEYS.SDS.INDI.NUMSCM, "3");
        comments.put(XOPKEYS.SDS.INDI.NUMSCM, "Number of times to send proactive service cancellations (int)");
        props.setProperty(XOPKEYS.SDS.INDI.ALLOW_LOOPBACK, "false");
        comments.put(XOPKEYS.SDS.INDI.ALLOW_LOOPBACK, "sets whether the instances of indi allow loopback or not");
        props.setProperty(XOPKEYS.SDS.INDI.ALLOW_DUPLICATES, "false");
        comments.put(XOPKEYS.SDS.INDI.ALLOW_DUPLICATES, "sets whether the instances of indi allow duplicate adverts or whether duplicate adverts are suppressed.");
        
        /* ---- Server to Server (i.e. Gatewaying) ---- */
        props.setProperty(XOPKEYS.GATEWAY.SERVER, "openfire");
        comments.put(XOPKEYS.GATEWAY.SERVER, "The hostname of the external XMPP server to which the gateway should connect.");
        props.setProperty(XOPKEYS.GATEWAY.TLSAUTH,"false");
        comments.put(XOPKEYS.GATEWAY.TLSAUTH, "true to use TLS with a gatewayed server, false otherwise.");
        props.setProperty(XOPKEYS.GATEWAY.BINDINTERFACE, "eth1");
        comments.put(XOPKEYS.GATEWAY.BINDINTERFACE, "The bind interface for connecting to the XMPP server.");
        props.setProperty(XOPKEYS.GATEWAY.PORT, "5269");
        comments.put(XOPKEYS.GATEWAY.PORT, "The port to connect to on the Gatewayed XMPP server.");
        props.setProperty(XOPKEYS.GATEWAY.PING, "30000");
        comments.put(XOPKEYS.GATEWAY.PING, "The interval at which this server should send ping messages to the connected server");
        props.setProperty(XOPKEYS.GATEWAY.REWRITEDOMAIN, "false");
        comments.put(XOPKEYS.GATEWAY.REWRITEDOMAIN,
                "True if messages and presences domains are rewritten from the XOP.DOMAIN to the XOP.GATEWAYDOMAIN");

        /* ---- Transport ---- */
        props.setProperty(XOPKEYS.TRANSPORT.SERVICE, "norm-transport");
        comments.put(XOPKEYS.TRANSPORT.SERVICE,
                "Transport service to use: [transport-engine, groupcomms, norm-transport, simple-transport]");

        props.setProperty(XOPKEYS.TRANSPORT.SEND_INTERFACE, "ANY");
        comments.put(XOPKEYS.TRANSPORT.SEND_INTERFACE, "Bound interface to SEND transport messages or 'ANY' for all interfaces");

        props.setProperty(XOPKEYS.TRANSPORT.RECV_INTERFACE, "ANY");
        comments.put(XOPKEYS.TRANSPORT.RECV_INTERFACE, "Bound interface to RECEIVE transport messages or 'ANY' for all interfaces");

        props.setProperty(XOPKEYS.TRANSPORT.ADDRESS, "225.0.87.4");
        comments.put(XOPKEYS.TRANSPORT.ADDRESS, "default multicast group for transport");
        props.setProperty(XOPKEYS.TRANSPORT.PORTRANGE, "10001-10001");
        comments.put(XOPKEYS.TRANSPORT.PORTRANGE,"Port range for sending/receiving");

        // NORM Transport
        // 256*256 send and receive buffer space in bytes
        props.setProperty(XOPKEYS.TRANSPORT.NORM.SENDBUFFERSPACE, "65536");
        comments.put(XOPKEYS.TRANSPORT.NORM.SENDBUFFERSPACE, "Bufferspace for sender threads. default: 256*256=65536");
        props.setProperty(XOPKEYS.TRANSPORT.NORM.RCVBUFFERSPACE, "65536");
        comments.put(XOPKEYS.TRANSPORT.NORM.RCVBUFFERSPACE, "Bufferspace for receiver threads. default: 65536");
        props.setProperty(XOPKEYS.TRANSPORT.NORM.SEGMENTSIZE, "1400");
        comments.put(XOPKEYS.TRANSPORT.NORM.SEGMENTSIZE, "Size of segments in bytes. default: 1400");
        props.setProperty(XOPKEYS.TRANSPORT.NORM.BLOCKSIZE, "64");
        comments.put(XOPKEYS.TRANSPORT.NORM.BLOCKSIZE, "Size NORM blocks in bytes. default: 64");
        props.setProperty(XOPKEYS.TRANSPORT.NORM.NUMPARITY, "15");
        comments.put(XOPKEYS.TRANSPORT.NORM.NUMPARITY, "Parity bits. default: 16");

        props.setProperty(XOPKEYS.TRANSPORT.NORM.SD.INTERVAL, "4000");
        comments.put(XOPKEYS.TRANSPORT.NORM.SD.INTERVAL, "Advertisement message send interval in ms. default: 4000");
        props.setProperty(XOPKEYS.TRANSPORT.NORM.SD.TIMEOUT, "3");
        comments.put(XOPKEYS.TRANSPORT.NORM.SD.TIMEOUT, "Number of messages missed before triggering lost messages. default: 3");

        // Transport Engine properties
        props.setProperty(XOPKEYS.TRANSPORT.TE.ADDRESS, "127.0.0.1");
        comments.put(XOPKEYS.TRANSPORT.TE.ADDRESS, "IP address of transport engine instance");
        props.setProperty(XOPKEYS.TRANSPORT.TE.LISTENPORT, "1998");
        comments.put(XOPKEYS.TRANSPORT.TE.LISTENPORT, "Port transport engine instance is listening on.");

        props.setProperty(XOPKEYS.TRANSPORT.TE.GROUPRANGE, "225.0.2.187-225.0.2.223");
        comments.put(XOPKEYS.TRANSPORT.TE.GROUPRANGE, "The group range for sending/rcving on transport engine.");
        props.setProperty(XOPKEYS.TRANSPORT.TE.PORT, "12601");
        comments.put(XOPKEYS.TRANSPORT.TE.PORT, "Port simple transport  instance is listening on.");

        props.setProperty(XOPKEYS.TRANSPORT.TE.PERSISTTIME, "0");
        comments.put(XOPKEYS.TRANSPORT.TE.PERSISTTIME,
                "Time to persist. TE interprets 1 as persist forever, and 0 as do not persist at all.");
        props.setProperty(XOPKEYS.TRANSPORT.TE.RELIABLE,"false");
        comments.put(XOPKEYS.TRANSPORT.TE.RELIABLE, "Use reliable transport for MUC and OneToOne, default: false");

        // BR&T: Group Comm Transport properties
        props.setProperty(XOPKEYS.TRANSPORT.GC.ROOMS.BESTEFFORT, "*");
        comments.put(XOPKEYS.TRANSPORT.GC.ROOMS.BESTEFFORT, "Best Effort rooms (*=catchall), default=*");

        props.setProperty(XOPKEYS.TRANSPORT.GC.ROOMS.RELIABLE, "");
        comments.put(XOPKEYS.TRANSPORT.GC.ROOMS.RELIABLE, "Reliable rooms (*=catchall), default=''");

        props.setProperty(XOPKEYS.TRANSPORT.GC.ROOMS.ORDERED, "");
        comments.put(XOPKEYS.TRANSPORT.GC.ROOMS.ORDERED, "Ordered (by sender) rooms (*=catchall), default=''");

        props.setProperty(XOPKEYS.TRANSPORT.GC.ROOMS.AGREED, "");
        comments.put(XOPKEYS.TRANSPORT.GC.ROOMS.AGREED, "Agreed ordered rooms (*=catchall), default=''");

        props.setProperty(XOPKEYS.TRANSPORT.GC.ROOMS.SAFE, "");
        comments.put(XOPKEYS.TRANSPORT.GC.ROOMS.SAFE, "Safe ordered rooms (*=catchall), default=''");

        props.setProperty(XOPKEYS.TRANSPORT.GC.DISCOVERY.GROUP, "0");
        comments.put(XOPKEYS.TRANSPORT.GC.DISCOVERY.GROUP, "the group number that GCS will use for discovery messages. The default is 0.");

        props.setProperty(XOPKEYS.TRANSPORT.GC.DISCOVERYDELAY, "5000");
        comments.put(XOPKEYS.TRANSPORT.GC.DISCOVERYDELAY, "The delay for sweeping and signaling XOP SD service (default: 5000ms)");

        props.setProperty(XOPKEYS.TRANSPORT.GC.AGENT.ADDRESS, "127.0.0.1");
        comments.put(XOPKEYS.TRANSPORT.GC.AGENT.ADDRESS, "address of GCS instance. The default is 127.0.0.1");

        props.setProperty(XOPKEYS.TRANSPORT.GC.AGENT.PORT, "56789");
        comments.put(XOPKEYS.TRANSPORT.GC.AGENT.PORT, "GCS instance port (default: 56789)");

        logger.finer("setting DefaultProperties COMPLETE!");

        return props;
    }
    
    /**
     * Add a property to XopProperties
     * @param key the property name
     * @param value the property value
     */
    public static void setProperty(String key, String value) {
    	if(properties == null) {
            load();
        }
        properties.setProperty(key, value);
    }

    /**
     * Retrieve a property from XopProperties
     * @param key the property name
     * @return A string containing the property
     */
    public static String getProperty(String key) {
        //logger.finer("string prop: "+key);
        if(properties == null) {
            load();
        }
        return (String)properties.get(key);
    }

    public static short getShortProperty(String key){
        logger.finest("short prop: " + key);
        if(properties == null){
            load();
        }
        String val = (String) properties.get(key);
        if (val == null || "".equals(val)) {
            return 0;
        }
        return Short.parseShort(val);
    }

    /**
     * Retreive a property from XopProperties, expecting an integer
     * @param key the property name
     * @return An int containing the property value
     */
    public static int getIntProperty(String key) {
        logger.finest("int prop: " + key);
        if(properties == null) {
            load();
        }
        String val = (String) properties.get(key);
        if (val == null || "".equals(val)) {
            return 0;
        }
        return Integer.parseInt(val);
    }

    /**
     * Retrieve a property from XopProperties, expecting a long
     * @param key the property name
     * @return A long containing the property value
     */
    public static long getLongProperty(String key) {
        //logger.finer("long prop: "+key);
        if(properties == null) {
            load();
        }
        String val = (String) properties.get(key);
        if (val == null || "".equals(val)) {
            return 0;
        }
        return Long.parseLong(val);
    }

    /**
     * Retrieve a property from XopProperties, expecting a boolean
     * @param key the property name
     * @return A boolean containing the property value
     */
    public static boolean getBooleanProperty(String key) {
        //logger.finer("bool prop: "+key);
        if(properties == null) {
            load();
        }
        return Boolean.parseBoolean((String)properties.get(key));
    }

    /**
     * Prints all the default properties to std out. This is for generating
     * a xop.properties file if desired.
     */
    private static void printDefaults(){
        Properties props = defaultProperties();
        for( Object obj :props.keySet() ){
            String key = (String)obj;
            String prop = props.getProperty(key);
            if( comments.get(key) != null )
                System.out.println( "# "+comments.get(key));
            System.out.println(key+"="+prop+"\n");
        }

    }

    /**
     * Main method to be called by generate_config.sh script. This will write to std out the default properties in
     * @param args arguments
     */
    public static void main(String[] args){
        XopProperties.printDefaults();
    }
}