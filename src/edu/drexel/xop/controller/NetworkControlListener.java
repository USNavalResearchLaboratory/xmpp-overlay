/**
 * (c) 2011 Drexel University
 *
 * A controller that listens on the network for connections.  When one comes
 * in, it passes it to NetworkCommandHandler, which processes commands.
 *
 * @author Rob Lass<urlass@cs.drexel.edu>
 *
 */
package edu.drexel.xop.controller;

import edu.drexel.xop.util.logger.LogUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetworkControlListener extends Controller {

    public static final int DEFAULT_PORT = 8585;
    private int port = DEFAULT_PORT;

    public NetworkControlListener() {
        this(DEFAULT_PORT);
    }

    public NetworkControlListener(int port) {
        super(LogUtils.getLogger(NetworkControlListener.class.getName()));
        this.port = port;
    }

    @SuppressWarnings("unused")
    public void run() {

        //set up a server socket
        ServerSocket server_socket;
        try {
            server_socket = new ServerSocket(port);
            //when a connection comes in, pass it off to NetworkCommandHandler
            while (true) {
                try {
                    logger.log(Level.SEVERE, "Listening for commands on port: " + this.port);
                    Socket socket = server_socket.accept();
                    new NetworkCommandHandler(socket, this);
                } catch (IOException e) {
                    logger.log(Level.WARNING,
                            "IOException when handling incoming connection.");
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "IOException when trying to listen on port "
                    + port + " for a command connection.");
        }
    }
}

class NetworkCommandHandler extends Thread {

    private static final Logger logger = LogUtils.getLogger(NetworkCommandHandler.class.getName());
    protected Socket socket = null;
    protected Controller controller = null;

    public NetworkCommandHandler(Socket socket, Controller controller) {
        this.socket = socket;
        this.controller = controller;
        Thread t = new Thread(this);
        t.start();
    }

    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String s;
            while ((s = reader.readLine()) != null) {
                controller.sendRawCommand(s);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "IOException reading from incoming command socket.");
        }
    }
}
