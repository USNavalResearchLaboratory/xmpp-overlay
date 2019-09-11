package edu.drexel.transportengine.components.protocolmanager.protocols;

import edu.drexel.transportengine.components.protocolmanager.Protocol;
import edu.drexel.transportengine.components.protocolmanager.ProtocolManagerComponent;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Concrete implementation of the UDP transport layer.  This class also handles starting up and shutting down UDP
 * sockets.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class UDP extends Protocol {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private List<UDPSocket> sockets;

    /**
     * Instantiates a new <code>UDP</code> class.
     *
     * @param protocolManager reference to the protocol manager.
     */
    public UDP(ProtocolManagerComponent protocolManager) {
        super(protocolManager);
        sockets = new LinkedList<>();
    }

    /**
     * Determines if the protocol is reliable.
     *
     * @return if the protocol is reliable.
     */
    @Override
    public boolean isReliable() {
        return false;
    }

    /**
     * The protocol name.
     *
     * @return name of the protocol.
     */
    @Override
    public String getProtocolName() {
        return "udp";
    }

    /**
     * Sends an message unreliably via UDP.
     *
     * @param event   event to send.
     * @param address destination of event.
     */
    @Override
    public void send(Event event, InetAddress address) {
        UDPSocket sock;
        try {
            sock = createSocket(address);
        } catch (IOException ex) {
            logger.warning("Unable to create UDP socket for send().");
            return;
        }
        try {
            logger.fine("Sending event of type: " + event.getContentType() + " (dest " + event.getDest() + ")");
            sock.send(event.pack());
        } catch (IOException ex) {
            logger.warning("Unable to send UDP packet.");
        }
    }

    @Override
    protected UDPSocket createSocket(InetAddress address) throws IOException {
        for (UDPSocket sock : sockets) {
            if (sock.getInetAddress().equals(address) && sock.getPort() == getPort()) {
                return sock;
            }
        }

        logger.fine("Creating socket on " + address + ":" + getPort());

        UDPSocket sock = new UDPSocket(address, getPort());
        sockets.add(sock);
        sock.start();
        return sock;
    }

    /**
     * Wrapper around a <code>MulticastSocket</code>.
     */
    private class UDPSocket extends Thread {

        private final Logger logger = LogUtils.getLogger(this.getClass().getName());
        private InetAddress address;
        private int port;
        private MulticastSocket socket;
        private boolean running;

        /**
         * Instantiates a new UdpMulticast instance.
         *
         * @param address IP or hostname on which to multicast.
         * @param port    Port on which to multicast.
         * @throws IOException if the socket could not be created.
         */
        public UDPSocket(InetAddress address, int port) throws IOException {
            this.running = true;

            this.address = address;
            this.port = port;
            socket = new MulticastSocket(port);
            socket.setLoopbackMode(false);
            socket.joinGroup(address);
            socket.setTimeToLive(Configuration.getInstance().getValueAsInt(TEProperties.PROTOCOL_UDP_TTL));
        }

        /**
         * Gets the address of the socket.
         *
         * @return address of the socket.
         */
        public InetAddress getInetAddress() {
            return address;
        }

        /**
         * Gets the port of the socket.
         *
         * @return port of the socket.
         */
        public int getPort() {
            return port;
        }

        /**
         * Stops the socket.
         */
        public void close() {
            running = false;
            socket.close();
        }

        /**
         * Sends a packet.
         *
         * @param data data to send.
         */
        public void send(byte[] data) {
            try {
                DatagramPacket p = new DatagramPacket(data, data.length, address, port);
                socket.send(p);
            } catch (IOException ex) {
                logger.warning("Unable to send packet.");
            }
        }

        /**
         * Starts the socket's listener.
         */
        @Override
        public void run() {
            while (running) {
                try {
                    byte[] buf = new byte[2048];
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    handleIncoming(buf);
                } catch (Exception ex) {
                    logger.warning("Unable to receive packet.");
                    ex.printStackTrace();
                }
            }
        }
    }
}
