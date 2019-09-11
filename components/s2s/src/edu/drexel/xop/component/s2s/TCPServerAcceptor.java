/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * 
 * This will listen for need incoming connections, and hand a new server object
 * to the XOG object. This is the second connection opened in the dialback process,
 * you can't used this to listen for a server initiating a connection with us.
 * 
 * @author charlesr
 */
public class TCPServerAcceptor extends Thread {

    private static final Logger logger = LogUtils.getLogger(TCPServerAcceptor.class.getName());
    private ServerSocket sock;
    private boolean run = true;
    private PacketRoutingDevice foreignServerHandler;

    public TCPServerAcceptor(PacketRoutingDevice r) {
        super();
        foreignServerHandler = r;

        int port = XopProperties.getInstance().getIntProperty(XopProperties.S2S_PORT);
        try {
            logger.log(Level.INFO, "Attempting to listen for S2S connections on " + port);
            sock = new ServerSocket(port);
            logger.log(Level.INFO, "Accepting incoming server connections");
        } catch (IOException ex) {
            logger.log(Level.INFO, "XOG: Unable to bind to port " + port);
        }
        this.setName("S2S Connection Thread");
    }

    @Override
    public void run() {
        while (run) {
            try {
                // log.log(Level.INFO,"XOG: Waiting for server connections");
                Socket s = sock.accept();
                logger.log(Level.INFO, "XOG: Server Connected (" + s.getRemoteSocketAddress() + ")");
                ServerConnection server = new ServerConnection(s, foreignServerHandler);
                server.init_incoming();
                new Thread(server).start();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "S2S: Server Closing",ex);
            }
        }
    }

    public void close() {
        try {
            run = false;
            sock.close();
        } catch (IOException ex) {
            logger.severe(ex.getLocalizedMessage());
        }
    }
}
