/*
 * (c) 2010 Drexel University
 */
package edu.drexel.xop;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.controller.Controller;
import edu.drexel.xop.controller.NetworkControlListener;
import edu.drexel.xop.controller.NetworkController;
import edu.drexel.xop.controller.TextController;
import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.util.logger.LogUtils;

import java.io.File;

/**
 * Entry point for the XO Proxy
 * 
 * @author David Millar, Rob Lass, Duc Nguyen, Dustin Ingram, Rob Taglang
 */
public class Run {
    private static final Logger logger = LogUtils.getLogger(Run.class.getName());

    public static void main(String[] args) {
        logger.info("Changing working directory to the absolute path..."+System.getProperty("xop.path"));
        String xopPathProperty = System.getProperty("xop.path");
        if( xopPathProperty != null ){
            boolean result = (System.setProperty("user.dir", new File(xopPathProperty).getAbsolutePath()) != null);
            if(result) {
            	logger.info("Success!");
            }
            else {
            	logger.info("Failed to change working directory.");
            }
        } else {
            logger.info("xop.path system property was not set");
        }
        logger.info("STARTING XOP!!!!");
        if (args.length > 0 && args[0].equals("-h")) {
            usage();
            System.exit(0);
        } else if (args.length > 0 && args[0].equals("exit")) {
            NetworkController.main(new String[] { "exit" });
        }

        LogUtils.loadLoggingProperties();
        ClientProxy proxy = ClientProxy.getInstance();
        proxy.init();

        Controller c;
        // start the controller
        if (System.getProperty("CONTROLLER", "text").equals("network")) {
            c = new NetworkControlListener();
        } else {
            c = new TextController();
        }
        c.start();

        while (true) {
            Controller.Command command = getNextCommand(c);
            // stop the proxy, and kill this program
            if (command == Controller.Command.EXIT) {
                logger.log(Level.SEVERE, "XOP controller received EXIT command.  Please wait while I shutdown.");
                // stop all the threads
                proxy.stop();
            } else if (command == Controller.Command.COMPONENTS) {
                System.out.println(proxy.getComponents());
            } else if (command == Controller.Command.CLIENTS) {
                System.out.println(proxy.getClients());
            } else if (command == Controller.Command.ROOMS) {
                System.out.println(proxy.getRooms());
            } else if (command == Controller.Command.ROUTES) {
                System.out.println(proxy.getRoutes());
            } else if (command == Controller.Command.ROSTERS) {
                System.out.println(proxy.getRosters());
            }
        }
    }

    private static Controller.Command getNextCommand(Controller c) {
        // wait for a command
        try {
            logger.log(Level.INFO, "controller waiting for a command");
            c.await();
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Interrupted while waiting for a command!");
        }
        return c.getCommand();
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
