/*
 * (c) 2010 Drexel University
 */
package edu.drexel.xop;

import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.XopNetImpl;
import edu.drexel.xop.util.logger.LogUtils;

import java.io.File;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Entry point for the XO Proxy on linux servers (e.g. XO Gateway)
 * 
 */
public class Run {
    private static final Logger logger = LogUtils.getLogger(Run.class.getName());

    public static void main(String[] args) {
        logger.info("Changing working directory to the absolute path..."+System.getProperty("xop.path"));
        String xopPathProperty = System.getProperty("xop.path");
        if( xopPathProperty != null ){
            boolean result = (System.setProperty("user.dir", new File(xopPathProperty).getAbsolutePath()) != null);
            if(!result) {
            	logger.info("Failed to change working directory.");
            }
        } else {
            logger.info("xop.path system property was not set");
        }
        logger.info("STARTING XOP!!!!");
        if (args.length > 0 && args[0].equals("-h")) {
            usage();
            System.exit(0);
        }
        
        LogUtils.loadLoggingProperties(null);

        XOProxy proxy = XOProxy.getInstance();
        XopNet xopNet = new XopNetImpl(proxy.getClientManager());
        String errorMessage = proxy.init(xopNet);
        if( errorMessage != null ){
            logger.severe("XOP unable to start!!");
            logger.severe("Error MessageByteBuffer: "+errorMessage);
        }
    }

    public static void shutdown() {

        XOProxy.getInstance().stop();
        
        // cleans up lock files created to prevent multiple threads from editing log files
        for (Handler h : logger.getHandlers()) {
        	  h.close();
        }
    }

    private static void usage() {
        System.out.println("---[ XOP - System properties (abridged list, see xop.properties for full list) ]---");
        System.out.println("PROPERTY\tDEFAULT\tDESCRIPTION");
        System.out.println("domain\t[proxy]\tdomain name");
        System.out.println("config.dir\t[./config]\tthe directory containing config files");
        System.out.println("properties.file\t[./config/xop.properties]\tthe XOP properties file location");
        System.out.println("xop.port\t[5222]\tserver port to use");
        System.out.println("xop.muc.port\t[5250]\toutgoing multicast port for muc");
        System.out.println("xop.debug.force.plain.auth\ttrue\tallows plaintext auth if true, SSL only if false");
    }
}
