package edu.drexel.xop.net;

import edu.drexel.xop.net.transport.XOPTransportService;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * interface
 * Created by duc on 11/30/16.
 */
public interface XopNet {
    void init() throws InvocationTargetException, NoSuchMethodException,
            InstantiationException, InitializationException, IllegalAccessException,
            IOException // Exceptions thrown by SDSystem and TransportEngine initialization
    ;

    void sendToOneToOneTransport(Packet packet);

    void close();

    SDManager getSDManager();

    /**
     * TODO: This is used by protosd, we're remomving protosd though 20180928
     * @return the host IP address as a String
     */
    String getHostAddrStr();

    /**
     * TODO: This is used by protosd, we're removing protosd though 20180928
     * @return the port created for the one-to-one transport system
     */
    int getOneToOnePort();

    /**
     * @param roomJID the room for which this transport service is created. If null, then assumes this is a one-to-one transport
     * @return the XOPTransportService object created for the room
     * @throws IOException thrown if no multicast address is availabe or unable to create a room
     */
    XOPTransportService createXOPTransportService(JID roomJID) throws IOException;
}
