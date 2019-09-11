package edu.drexel.xop.net;

import edu.drexel.xop.net.transport.XOPTransportService;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * Mock object for XopNet
 * Created by duc on 11/30/16.
 */
public class MockXopNet implements XopNet {
    private SDManager mockSDManager;

    @Override
    public void init() { // throws InvocationTargetException, NoSuchMethodException, InstantiationException, InitializationException, IllegalAccessException, IOException {
        mockSDManager = new MockSDManager();
    }

    @Override
    public void sendToOneToOneTransport(Packet packet) {

    }

    @Override
    public void close() {

    }

    @Override
    public SDManager getSDManager() {
        return mockSDManager;
    }

    @Override
    public String getHostAddrStr() {
        return null;
    }

    @Override
    public int getOneToOnePort(){
        return 0;
    }

    @Override
    public XOPTransportService createXOPTransportService(JID roomJID) { //throws IOException {
        return null;
    }
}
