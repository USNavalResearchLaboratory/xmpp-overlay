package edu.drexel.xop.net.transport;

import org.xmpp.packet.Packet;

/**
 * Interface defining Transport Mechanism functionality
 * Created by duc on 9/15/16.
 */
public interface XOPTransportService {
    /**
     * Sends XMPP packet to the transport service (to be sent to the network)
     * @param packet the packet to send to the transport service
     */
    void sendPacket(Packet packet);

    /**
     * Process an incoming (from the network) XMPP MessageByteBuffer. Send to locally connected XMPP clients
     * @param packet the incoming XMPP Packet
     */
    void processIncomingPacket(Packet packet);

    /**
     * close connection to the TransportService
     */
    void close();

    /**
     * @return the service address of this transport mechanism
     */
    String getAddressStr();

    /**
     * @return the port for this transport
     */
    int getPort();
}
