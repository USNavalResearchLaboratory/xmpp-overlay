package edu.drexel.xop.component.s2s;

import java.net.InetAddress;
import java.net.Socket;

/**
 * This is a ConnectionHandler that does not communicate with the authoritative server
 * using DNS. Instead, it sends the messages meant for the authoritative server to the
 * same IP address from which it received the S2S initiation request.
 * 
 * This deviates from the standard, but allows multiple XMPP servers with the same domain
 * to connect to us.
 * 
 * @author urlass
 * 
 */
public class NonAuthoritativeConnectionHandler extends IncomingConnectionHandler {
    public NonAuthoritativeConnectionHandler(Socket s) {
        super(s);
    }

    public InetAddress getOriginatingServerAddress(Socket incomingSocket, String authoritativeServer) {
        return incomingSocket.getInetAddress();
    }
}