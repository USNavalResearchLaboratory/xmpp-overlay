/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.properties;

import edu.drexel.xop.util.logger.LogUtils;
import java.io.*;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Standard interface to get/fetch XOP related properties and persist them.<br/>
 * You can set this to save the properties to disk when changes are made (default)<br/>
 * or not.
 * <p/>
 * If you want to set a system property from the command line, you need to prepend XOP.* to the property name. e.g. setting the interface would be java -DXOP.xop.bind.interface=eth0
 * <p/>
 * You can also override properties in the xop.properties file by setting them at the command line. For example: java -Dxop.bind.interface=eth0 -jar xop.jar to override the xop.bind.interface property.
 * 
 * @author David Millar
 * @author Trevor Adams
 */
public class XopProperties {
    private static final Logger logger = LogUtils.getLogger(XopProperties.class.getName());

    // Props and file location
    private Properties props;
    private static final Properties defaultProps = new XopDefaultProperties();
    private static final Properties sysProps = System.getProperties();
    private static final String DEFAULT_FILE_LOCATION = "config/xop.properties";
    // Change Listeners
    private Set<PropertyEventListener> propertyEventListeners = new HashSet<>();

    // Some properties and their keys
    public static final String DOMAIN = "xop.domain";
    public static final String PORT = "xop.port";
    public static final String USE_TLS_AUTH = "xop.tls.auth";
    public static final String BIND_INTERFACE = "xop.bind.interface";
    public static final String ENABLED_COMPONENTS = "xop.enabled.components";
    public static final String SAVE_PROPS_ON_CHANGE = "xop.properties.saveOnChange";
    public static final String CLIENT_REAP_MS = "xop.client.connection.reap.period.ms";
    public static final String COMPONENT_DIR = "xop.component.dir";
    public static final String CONFIG_DIR = "xop.config.dir";
    public static final String PROPS_FILE_LOCATION = "properties.file";
    public static final String MULTICAST_ADDRESS_SPACE = "xop.multicast.space";
    public static final String CONTROLLER_TYPE = "controller.gui";
    public static final String PING_SUPPORTED = "xop.ping.supported";

    // Client packet handler
    public static final String PACKET_HANDLER_CORE_POOL_SIZE = "xop.router.core.pool.size";
    public static final String PACKET_HANDLER_MAX_POOL_SIZE = "xop.router.max.pool.size";
    public static final String PACKET_HANDLER_KEEP_ALIVE_TIME = "xop.router.keep.alive.time";

    // the property for iq timeout (in ms)
    public static final String XOP_IQ_TIMEOUT = "xop.router.iq.timeout";

    // INDI configurations
    public static final String INDI_TTL = "xop.sds.indi.ttl";
    public static final String INDI_RAI = "xop.sds.indi.rai";
    public static final String INDI_NUMRA = "xop.sds.indi.numra";
    public static final String INDI_CQI = "xop.sds.indi.cqi";
    public static final String INDI_SCI = "xop.sds.indi.sci";
    public static final String INDI_NUMSCM = "xop.sds.indi.numscm";
    public static final String INDI_LOOPBACK = "xop.sds.indi.loopback";
    public static final String INDI_DUPLICATES = "xop.sds.indi.duplicates";
    public static final String SD_CLASS = "xop.sd.class";

    // MUC
    public static final String MUC_PORT = "xop.muc.port";
    public static final String MUC_SUBDOMAIN = "xop.muc.subdomain";

    // XOG S2S
    public static final String S2S_WHITELIST = "xop.s2s.whitelist";
    public static final String S2S_PREFIX = "xop.s2s.prefix";
    public static final String S2S_SERVER = "xop.s2s.server";
    public static final String S2S_PORT = "xop.s2s.port";
    public static final String GATEWAY_TYPE = "xop.gateway.type";
    public static final String MULTIPLE_GATEWAYS = "xop.gateway.multiple";
    public static final String GATEWAY_SUBDOMAIN = "xop.gateway.subdomain";
    public static final String GATEWAY_FORWARDING_DELAY = "xop.gateway.forwarding.delay";
    public static final String GATEWAY_CONTROL_CHANNEL = "xop.gateway.control.channel";
    public static final String GATEWAY_ELECTION_TIME_PERIOD = "xop.gateway.election.time.period";

    // Transport Engine Properties
    public static final String TRANSPORT_ENGINE_PORT = "xop.transport.port";
    public static final String TRANSPORT_ENGINE_ADDRESS = "xop.transport.address";
    public static final String TRANSPORT_PERSIST_TIME = "xop.transport.persist.time";

    // XO Web Properties
    public static final String WEB_PORT = "xop.webserver.port";
    public static final String WEB_PATH = "xop.webserver.path";
    
    // Log Server Preferences
    public static final String LOG_SERVER_ENABLED = "xop.logserver.enabled";
    public static final String LOG_SERVER_PATH = "xop.logserver.path";
    public static final String LOG_SERVER_PORT = "xop.logserver.port";

    private static final XopProperties instance = new XopProperties();


    private XopProperties() {
        props = new Properties();
        load();

        // sets properties listed in XopDefaultProperties class (note: not comprehensive list of all xop properties)
        for (Object key : defaultProps.keySet()) {
            if (sysProps.containsKey(key)) {
                props.setProperty((String) key, System.getProperty((String) key));
            }
            if (!props.containsKey(key)) {
                props.setProperty((String) key, defaultProps.getProperty((String) key));
            }
        }

        // looks for all system properties that start with xop or gump or xog and sets that as a property
        for (Object key : sysProps.keySet()) {
            if (((String) key).toLowerCase().startsWith("xop.") || ((String) key).toLowerCase().startsWith("gump.")) {
                props.put(key, sysProps.getProperty((String) key));
            }
        }
    }

    public static XopProperties getInstance() {
        return instance;
    }

    /**
     * load the properties file from the system property -Dproperty.file
     */
    public synchronized void load() {
        logger.log(Level.FINE, "Loading properties...");
        InputStream fis = null;

        String fileLocation = sysProps.getProperty("properties.file");
        if( fileLocation == null ){
        	fileLocation = DEFAULT_FILE_LOCATION;
        }
        // First, try to load from the specified or default location.
        File f = new File(fileLocation).getAbsoluteFile();
        if (f.exists()) {
            try {
                fis = new FileInputStream(f);
            } catch (FileNotFoundException ex) {
                logger.log(Level.WARNING, "Properties file does not exist: " + fileLocation
                    + ".  Using Defaults.", ex);
            }
            logger.log(Level.INFO, "Using config file at " + f.getAbsolutePath());
        } else {
            // The file was not there, try looking in the jar
            fis = this.getClass().getResourceAsStream(File.pathSeparator + f.getAbsolutePath());
            if (fis != null) {
                logger.log(Level.INFO, "Using config file in jar at " + f.getAbsolutePath());
            }
        }

        if (fis == null) { // Couldn't find one in the jar either, just keep going, using defaults.
            logger.log(Level.INFO, "No properties file found at " + fileLocation + ", using defaults.");
        } else { // load the config file properties from where ever they were found
            try {
                props.load(fis);
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Unable to read properties file: " + fileLocation
                    + ".  Using Defaults.", ioe);
            } finally {
                try {
                    fis.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Get a property or return a default value
     * 
     * @param property the name of the property to fetch
     * @param defaultValue the default value to return if no such property exists
     * @return the property value, or default value if no property value exists
     */
    @Deprecated
    public synchronized String getProperty(String property, String defaultValue) {
        if (props.containsKey(property)) {
            return props.getProperty(property).trim();
        }
        setProperty(property, defaultValue);
        fireSetEvent(property, null);
        return defaultValue;
    }

    /**
     * Get a property
     * 
     * @param property the key value of the property to fetch
     * @return the property's value
     */
    public synchronized String getProperty(String property) {
        return props.getProperty(property);
    }

    /**
     * Set a property
     * 
     * @param key the key of the property to set
     * @param value the value of the property to set
     */
    public synchronized void setProperty(String key, String value) {
        String oldValue = props.getProperty(key);
        props.setProperty(key, value);
        fireSetEvent(key, oldValue);
    }

    public synchronized boolean getBooleanProperty(String key) {
        String value = props.getProperty(key);
        return (value != null && (value.equalsIgnoreCase("true") || value.equals("1")));
    }

    public synchronized void setBooleanProperty(String key, boolean value) {
        props.setProperty(key, getBooleanStringValue(value));
    }

    private String getBooleanStringValue(boolean bool) {
        return bool ? "true" : "false";
    }

    public synchronized int getIntProperty(String key, int defaultValue) {
        return Integer.parseInt(getProperty(key, String.valueOf(defaultValue)));
    }

    public synchronized int getIntProperty(String key) {
        try {
            return Integer.parseInt(getProperty(key));
        } catch (NumberFormatException nfe) {
            nfe.printStackTrace();
            return 0;
        }
    }

    public synchronized void setIntProperty(String key, int value) {
        props.setProperty(key, String.valueOf(value));
    }

    public synchronized long getLongProperty(String key, long defaultValue) {
        return Long.parseLong(getProperty(key, String.valueOf(defaultValue)));
    }

    public synchronized long getLongProperty(String key) {
        try {
            return Long.parseLong(getProperty(key));
        } catch (NumberFormatException nfe) {
            System.err.println("NFE with key: " + key + " and value: " + getProperty(key));
            nfe.printStackTrace();
            return 0;
        }
    }

    public synchronized void setLongProperty(String key, long value) {
        props.setProperty(key, String.valueOf(value));
    }

    // -----[ Listeners ]-----
    public static void addEventListener(PropertyEventListener listener) {
        getInstance().propertyEventListeners.add(listener);
    }

    public static void removeEventListener(PropertyEventListener listener) {
        getInstance().propertyEventListeners.remove(listener);
    }

    // Fire events to listeners
    private void fireSetEvent(String key, String oldValue) {
        for (PropertyEventListener listener : propertyEventListeners) {
            listener.propertySet(key, oldValue);
        }
    }

    private void fireDeleteEvent(String key, String oldValue) {
        for (PropertyEventListener listener : propertyEventListeners) {
            listener.propertyDeleted(key, oldValue);
        }
    }
}
