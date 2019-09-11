package edu.drexel.xop.net.transport;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.util.MessageCompressionUtils;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.Packet;

import java.io.IOException;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple UDP Multicast Socket for one-to-one messages.
 *
 */
abstract class AbstractBasicTransportService implements XOPTransportService {
	private static final Logger logger =
            LogUtils.getLogger(AbstractBasicTransportService.class.getName());
    private UDPSocket udpSocket;
    private String addressStr;
    private int port;
    protected ClientManager clientManager;

    AbstractBasicTransportService(String ifname, InetAddress group, int port,
                                  ClientManager clientManager) throws IOException {
        this.clientManager = clientManager;
        udpSocket = new UDPSocket(ifname, group, port);
        udpSocket.start();
        this.port = port;
        logger.info("created AbstractBasicTransportService");
        addressStr = group.getHostAddress();
	}

	public void close() {
        logger.info("Closing UDPSocket");
		udpSocket.close();
	}

	public void sendPacket(Packet packet) {
        // logger.fine("Sending string message: {{{"+packet.toString()+"}}}");
        logger.fine("Sending xml message: {{{"+packet.toXML()+"}}}");
        String pktStr = packet.createCopy().toString();
        byte[] bytes = pktStr.getBytes();
		udpSocket.send(bytes);
    }

    public String getAddressStr() {
        return addressStr;
    }

    public int getPort(){
        return port;
    }

    /**
     * Wrapper around a <code>MulticastSocket</code>.
     */
    private class UDPSocket extends Thread {

        private InetAddress address;
        private int port;
        private MulticastSocket socket;
        private DatagramSocket sendingSocket;
        private boolean running;
        // private int originalTtl;
        private int origRcvBufferSize;

        /**
         * Instantiates a new UdpMulticast instance.
         *
         * @param ifname
         *            the interface name to bind to.
         * @param address
         *            the multicast group address
         * @param port
         *            Port on which to multicast.
         * @throws IOException
         *             if the socket could not be created.
         */
        UDPSocket(String ifname, InetAddress address, int port) throws IOException {
            this.running = true;
            this.setDaemon(true);
            this.address = address;
            this.port = port;
            socket = new MulticastSocket(port);

            logger.info(
                    "open multicast socket on interface, " + ifname);
            NetworkInterface iface = NetworkInterface.getByName(ifname);

            InetAddress ifaceInetAddress = iface.getInetAddresses().nextElement();
            logger.info("ifaceInetAddress: " + ifaceInetAddress);
            // InetAddress inetAddress = InetAddress.getByName("0.0.0.0");
            // socket.setInterface(ifaceInetAddress);
            socket.setLoopbackMode(false); // i.e. set to false, we receive traffic we send.
            socket.setReceiveBufferSize(4096);
            socket.setInterface(ifaceInetAddress);
            int originalTtl = socket.getTimeToLive();
            logger.info("ttl:" + originalTtl);
            socket.setTimeToLive(XOP.TRANSPORT.TTL);
            originalTtl = socket.getTimeToLive();
            logger.info("new ttl:" + originalTtl);

            origRcvBufferSize = socket.getReceiveBufferSize();
            logger.finer("socket.getReceiveBufferSize(): "+origRcvBufferSize);

            logger.info("Joining group: " + address + ":" + port);
            socket.joinGroup(new InetSocketAddress(address, port), iface);

            sendingSocket = new DatagramSocket();
            logger.info("Sending Socket: " + sendingSocket);
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
         * @param data
         *            data to send.
         */
        public void send(byte[] data) {
            try {
                data = MessageCompressionUtils.compressBytes(data);
                DatagramPacket p = new DatagramPacket(data, data.length, address, port);
                logger.finest( "sending data to address "+address);
                socket.send(p);
                logger.finest("SENT data to address " + address + " out interface " + socket.getInterface());
                // sendingSocket.send(p);

            } catch (IOException ex) {
                logger.warning(
                        "Unable to send packet: \n\t" + ex.getMessage());
            }
        }

        /**
         * Starts the socket's listener.
         */
        @Override
        public void run() {
            while (running) {
                try {
                    byte[] buf = new byte[4095];
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);

                    byte[] byteData;
                    int start;
                    int end;
                    if( XOP.ENABLE.COMPRESSION ){
                        byteData = MessageCompressionUtils.decompressBytes(p.getData());
                        start = 0;
                        end = byteData.length;
                    } else {
                        byteData = p.getData();
                        start = p.getOffset();
                        end = p.getLength();
                    }

                    String dataStr = new String(byteData, start, end);
                    if (logger.isLoggable(Level.FINEST))
                        logger.finest("received data: len: " + dataStr.length() + " [[[" + dataStr + "]]]");
                    Packet xmppPacket = Utils.packetFromString(dataStr);
                    processIncomingPacket(xmppPacket);
                } catch (Exception ex) {
                    logger.warning(
                            "Unable to receive packet. \n\t" + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        }
    }
}
