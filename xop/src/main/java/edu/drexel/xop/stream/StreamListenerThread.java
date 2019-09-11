package edu.drexel.xop.stream;

import edu.drexel.xop.client.local.ClientListenerThread;
import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.packet.LocalPacketProcessor;

import java.io.IOException;
import java.net.InetAddress;


/**
 * Listens for connections on the designated port for bytestreams
 *
 */
public class StreamListenerThread extends ClientListenerThread {

    public StreamListenerThread(InetAddress bindAddress, int port, ClientManager clientManager,
                                LocalPacketProcessor localPacketProcessor) throws IOException {
        super(bindAddress, port, clientManager, localPacketProcessor);
    }

}
