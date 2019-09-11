/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.properties;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

/**
 * This class stores reasonable default properties for XOP.
 * 
 * @author David Millar
 */
public class XopDefaultProperties extends Properties {
    private static final long serialVersionUID = 1L;
    private HashMap<String, String> comments = new HashMap<>();
    private final String header = "#XOP Properties File\n# Note: these properties can be overridden by setting system properties at the\n# command line. e.g. java -Dxop.bind.interface=eth0 -jar xop.jar\n# or using the run.sh script: e.g. run.sh -D xop.bind.interface=eth0\n\n";

    public XopDefaultProperties() {
        super();
        init();
    }

    // Put default properties here
    public final void init() {
        // General
        setProperty(XopProperties.DOMAIN, "proxy", "The domain name for this proxy instance.");
        setProperty(XopProperties.PORT, "5222", "The port to run the XMPP server on.");
        setProperty(XopProperties.MUC_SUBDOMAIN, "conference", "Subdomain MUC rooms will be attached to.");
        setProperty(XopProperties.USE_TLS_AUTH, "true", "Should we use TLS authentication? true = always use tls authentication. false = plaintext auth will be used.");
        setProperty(XopProperties.CONTROLLER_TYPE, "text", "'network' to control remotely, and 'text' to control at the command line");
        setProperty(XopProperties.CONFIG_DIR, "./config", "Directory in which to look for config files.");
        setProperty(XopProperties.CLIENT_REAP_MS, "5000", "How often to garbage collect dead client connections, in milliseconds.");
        setProperty(XopProperties.PACKET_HANDLER_CORE_POOL_SIZE, "10", "Size of core thread pool for transporting packets.");
        setProperty(XopProperties.PACKET_HANDLER_MAX_POOL_SIZE, "20", "Max threads to use for the thread pool (0 means unlimited)");
        setProperty(XopProperties.XOP_IQ_TIMEOUT, "5000", "Timeout period for an IQ request, in milliseconds.  An error is sent if an IQ takes longer than this to respond");
        setProperty(XopProperties.BIND_INTERFACE, "lo", "Bind interfaces, separated by commas.  The first interface is the one used to connect local clients.");
        setProperty(XopProperties.PING_SUPPORTED, "true", "Specify if XMPP ping is supported.  Please note some clients will not work if this is disabled.");
        setProperty(XopProperties.COMPONENT_DIR, "plugins/", "Directory in which to search for plugins.");
        //TODO:  change this to all working plugins that won't interfere with general operation
        setProperty(XopProperties.ENABLED_COMPONENTS, "", "Plugins to enable. 'xog', 'ad' and 's2s' are valid plugins.");
        setProperty(XopProperties.MULTICAST_ADDRESS_SPACE, "224.1.128.1-224.1.255.254", "Specify the range of multicast addresses to use for room traffic (separate multiple ranges with commas)");
        setProperty(XopProperties.TRANSPORT_ENGINE_PORT, "1998", "Port transport engine instance is listening on.");
        setProperty(XopProperties.TRANSPORT_ENGINE_ADDRESS, "127.0.0.1", "IP address of transport engine instance");
        setProperty(XopProperties.TRANSPORT_PERSIST_TIME, "0", "Time to persist.  Currently, the TE interprets 1 as persist forever, and 0 as do not persist at all.");
        setProperty(XopProperties.WEB_PORT, "8081", "Port XO webserver is running on.");
        setProperty(XopProperties.WEB_PATH, "./web", "Directory containing XO webserver files.");

        //INDI settings:  are we missing one to specify proactive vs reactive?
        setProperty(XopProperties.INDI_TTL, "60000", "Lifetime (TTL) of the service in milliseconds (long)");
        setProperty(XopProperties.INDI_RAI, "2000", "Service proactive readvertisement interval for sending advertisements in milliseconds (long)");
        setProperty(XopProperties.INDI_NUMRA, "1", "Number of times to readvertise the service (int, -1 = forever)");
        setProperty(XopProperties.INDI_CQI, "0,100,200,400,800,1600", "Times a client queries for a service e.g. 0,100,200 (comma separated list of longs)");
        setProperty(XopProperties.INDI_SCI, "5000", "Proactive service cancel notification interval");
        setProperty(XopProperties.INDI_NUMSCM, "3", "Number of times to send proactive service cancellations (int)");
        setProperty(XopProperties.INDI_LOOPBACK, "true", "sets whether the instances of indi allow loopback or not");
        setProperty(XopProperties.INDI_DUPLICATES, "false", "sets whether the instances of indi allow duplicate adverts or whether duplicate adverts are suppressed.");

        setProperty(XopProperties.MUC_PORT, "5250", "Port to listen on for MUC traffic.");

        //GATEWAY STUFF
        setProperty(XopProperties.GATEWAY_TYPE, "forward", "Type of gateway to use.  'forward', 'unseen', 'delayedunseen', and 'election' are all valid, but only forward works properly at this point");
        setProperty(XopProperties.MULTIPLE_GATEWAYS, "false", "Are we using multiple gateways in the same network?  Don't set to 'true' unless you know what you are doing.");
        setProperty(XopProperties.GATEWAY_SUBDOMAIN, "gatewayX", "What subdomain should this gateway use.  Only used if multiple gateways are selected.");
        setProperty(XopProperties.GATEWAY_FORWARDING_DELAY, "1000", "Gateway message forwarding delay (only for multiple gateways)");
        setProperty(XopProperties.S2S_PREFIX, "conference", "The gateway prefix.  It is unlikely you will need to change this.");
        setProperty(XopProperties.S2S_SERVER, "openfire", "The hostname of the openfire server to which the gateway should connect.");
        setProperty(XopProperties.S2S_PORT, "5269", "The port to connect to on the XOG server.");
        setProperty(XopProperties.S2S_WHITELIST, "openfire,conference.openfire", "Whitelisted server-to-server domains, comma deliminated.  If you have 'hostname', you most likely also need 'conference.hostname");
        setProperty(XopProperties.GATEWAY_CONTROL_CHANNEL, "gatewaycontrolchannel");
        setProperty(XopProperties.GATEWAY_ELECTION_TIME_PERIOD, "10000");


        setProperty(XopProperties.PACKET_HANDLER_KEEP_ALIVE_TIME, "30000", "Discard idle threads beyond core pool size after this many milliseconds");
        setProperty(XopProperties.SD_CLASS, "mil.navy.nrl.protosd.api.imp.indi.INDIInstance", "Service discovery class to use.");
        
        //LOG SERVER
        setProperty(XopProperties.LOG_SERVER_ENABLED, "false", "Whether or not to connect to a log server");
        setProperty(XopProperties.LOG_SERVER_PATH, "localhost", "Where to connect to the log server");
        setProperty(XopProperties.LOG_SERVER_PORT, "1553", "The port on which to connect to the log server");
    }
    public Object setProperty(String key, String value, String comment){
        comments.put(key, comment);
        return setProperty(key, value);
    }

    public String toString(){
        String outString = header + "\n";
        Enumeration props = propertyNames();
        while(props.hasMoreElements()){
            String prop = (String)props.nextElement();
            if(comments.containsKey(prop)){
                outString += "#" + comments.get(prop) + "\n";
            }
            outString += prop + "=" + getProperty(prop) + "\n\n";
        }

        return outString;
    }

    public static void main(String[] args){
        XopDefaultProperties xdp = new XopDefaultProperties();
        System.out.println(xdp.toString());
    }
}

