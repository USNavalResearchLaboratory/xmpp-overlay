package edu.drexel.xop.gateway;

import edu.drexel.xop.packet.LocalPacketProcessor;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens for incoming connections from a federated S2S
 * Created by duc on 5/12/16.
 */
class S2SReceivingListenerThread extends Thread {
    private static final Logger logger = LogUtils.getLogger(S2SReceivingListenerThread.class.getName());

    private AtomicBoolean killSwitch = new AtomicBoolean(false);
    private ServerSocket ss;
    private LocalPacketProcessor packetProcessor;

    S2SReceivingListenerThread(InetAddress bindAddress, int port, LocalPacketProcessor packetProcessor) throws IOException {
        this.packetProcessor = packetProcessor;
        try {
            ss = new ServerSocket();
            // true = enables the SO_REUSEADDR option so a socket in timeout state can be reused
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(bindAddress,port));
            logger.info("S2S bound on address "+bindAddress.getHostAddress());
        } catch (BindException e) {
            logger.log(Level.SEVERE,"Unable to bind XOP instance to port " + port
                    + ", is XOP already running?", e);
            this.interrupt();
            throw e;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error setting up server socket on port " + port
                    + " to listen for client connections.", e);
            throw e;
        }
    }

    @Override
    public void run() {
        logger.info("S2SReceivingListenerThread is listening on address: "
                + ss.getInetAddress() );
        while (!killSwitch.get()) {
            Socket sock;
            try {
                if (ss != null) {
                    sock = ss.accept();
                    logger.info("Accepted connection from: "
                            + sock.getInetAddress() + ":" + sock.getPort());
                    if (XOP.GATEWAY.TIMEOUT != -1) {
                        sock.setSoTimeout(XOP.GATEWAY.TIMEOUT);
                        // sock.setSoLinger(false, 0);
                        sock.setSoLinger(true, XOP.GATEWAY.TIMEOUT);
                    } else {
                        logger.info("GATEWAY TIMEOUT DISABLED");
                    }
                    sock.setKeepAlive(true);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Socket Timeout: " + sock.getSoTimeout());
                        logger.fine("Socket linger: " + sock.getSoLinger());
                        logger.fine("Socket keepalive: " + sock.getKeepAlive());
                    }

                    // pass off to the handler
                    ReceivingGatewayConnection ch = new ReceivingGatewayConnection(sock, packetProcessor);
                    new Thread(ch).start();
                }
            } catch (SocketTimeoutException ste) {
                logger.log(Level.WARNING,"Socket Timed out " + ste.getMessage(), ste);
            } catch (SocketException se) {
                if("Socket closed".equals(se.getMessage()) && killSwitch.get()) {
                    // swallowing this exception because it happens whenever XO is stopped
                    logger.info("Socket is closed, expected from flipping killSwitch.");
                } else {
                    logger.log(Level.WARNING,"SocketException " + se.getMessage(), se);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE,"Error accepting incoming connection from client.", e);
            }
        }
        logger.fine("S2SReceivingListenerThread exiting.");
    }

    public void close() {
        killSwitch.set(true);
        // TODO ng 11/25 this code is not being called at the right time, seemingly too early
        if(ss != null) {
            try {
                logger.info("closing server socket..");
                ss.close();
                logger.info(".. server socket closed!");
            } catch (IOException e) {
                logger.severe("Unable to close server socket");
            }
        }
    }
}
