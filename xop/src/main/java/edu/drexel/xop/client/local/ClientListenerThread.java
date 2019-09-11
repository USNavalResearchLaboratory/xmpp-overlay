package edu.drexel.xop.client.local;

import edu.drexel.xop.client.XOPConnection;
import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.packet.LocalPacketProcessor;
import edu.drexel.xop.util.logger.LogUtils;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Listens for incoming XMPP client connections, and creates a xopConnection when one comes in
 * @deprecated Moving toward ClientConnection.listenForConnections
 */
public class ClientListenerThread implements Runnable {
    private static final Logger logger = LogUtils.getLogger(ClientListenerThread.class.getName());

    private AtomicBoolean killSwitch = new AtomicBoolean(false);
    private ServerSocket ss;
    private ClientManager clientManager;
    private LocalPacketProcessor localPacketProcessor;

    public ClientListenerThread(InetAddress bindAddress, int port, ClientManager clientManager,
                                LocalPacketProcessor localPacketProcessor) throws IOException {
        this.clientManager = clientManager;
        this.localPacketProcessor = localPacketProcessor;
        ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(bindAddress, port));
        logger.info("listening on " + ss.getLocalSocketAddress() + " inet " + ss.getInetAddress());
        logger.info("ClientListenerThread listening on address: " + bindAddress.getHostAddress());
    }

    @Override
    public void run() {
        logger.info("Waiting on socket connections");
        try {
            while (!killSwitch.get()) {
                Socket sock = ss.accept();
                logger.info("Accepted connection from: " + sock.getInetAddress() + ":" + sock.getPort());

                // ClientConnection clientConnection = new ClientConnection(sock);
                // new Thread(clientConnection).start();
                //
                // pass off to the handler
                XOPConnection xopConnection = createNewClientConnection(sock);
                new Thread(xopConnection).start();
            }

            logger.info("closing server socket..");
            ss.close();
            logger.info(".. server socket closed!");
        } catch (SocketTimeoutException ste) {
            logger.warning("Socket Timed out " + ste.getMessage());
            ste.printStackTrace();
        } catch (SocketException se) {
            if ("Socket closed".equals(se.getMessage()) && killSwitch.get()) {
                // swallowing this exception because it happens whenever XO is stopped
                logger.warning("Socket is closed, expected from flipping killSwitch.");
            } else {
                se.printStackTrace();
            }
        } catch (IOException e) {
            logger.severe("Error accepting incoming connection from client.");
            e.printStackTrace();
        }
    }

    public void stopClientListener() {
        killSwitch.set(true);
    }

    private XOPConnection createNewClientConnection(Socket sock) {
        return new LocalClientConnection(sock, clientManager, localPacketProcessor);
    }

}