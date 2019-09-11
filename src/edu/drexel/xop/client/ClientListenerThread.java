package edu.drexel.xop.client;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;

import edu.drexel.xop.core.Stoppable;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * // listens for incoming client connections, and creates a ClientConnection when one comes in
 * 
 * @author Rob Lass
 */
public class ClientListenerThread extends Thread implements Stoppable {

    private static final Logger logger = LogUtils.getLogger(ClientListenerThread.class.getName());
    boolean keepListening = true;
    ServerSocket ss = null;
    int port = 5222;
    private ClientManager clientManager;

    public ClientListenerThread(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    public boolean init() {
        try {
            port = XopProperties.getInstance().getIntProperty(XopProperties.PORT, port);
            ss = new ServerSocket(port);
        } catch (BindException e) {
            logger.severe("Unable to bind XOP instance to port " + port + ", is XOP already running?");
            e.printStackTrace();
            this.interrupt();
            return false;
        } catch (NumberFormatException e) {
            logger.severe("Error converting " + port + " to an integer (this was specified as the port).");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            logger.severe("Error setting up server socket on port " + port + " to listen for client connections.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        while (keepListening) {
            Socket sock = null;
            try {
                if (ss != null) {
                    ss.setSoTimeout(1000);
                    sock = ss.accept();
                }
            } catch (SocketTimeoutException ste) {
                // do nothing
            } catch (IOException e) {
                logger.severe("Error accepting incoming connection from client.");
                e.printStackTrace();
            }
            // pass the socket off to the client handler
            if (sock != null) {
                // do authentication

                // pass off to the client handler
                ClientHandler ch = new ClientHandler(this.clientManager, sock);
                new Thread(ch).start();
            }
        }
    }

    public void stopMe() {
        this.keepListening = false;
    }
}