package edu.drexel.xop.component.s2s;

import edu.drexel.xop.util.XMLLightweightParser;
import org.xmpp.packet.Packet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents a connection to a remote XMPP server such as OpenFire.
 * 
 * 
 * 
 * @author urlass
 * 
 */
public class OutgoingConnectionHandler extends ConnectionHandler {
    private static final Logger logger = Logger.getLogger(OutgoingConnectionHandler.class.getName());

    private static final int S2S_PORT = 5269;
    private String remoteHostname;
    private String myHostname;
    private Socket sock;
    private InputStream inStream = null;
    private OutputStream outStream = null;

    OutgoingConnectionHandler(String remoteHostname, String myHostname) {
        this.remoteHostname = remoteHostname;
        this.myHostname = myHostname;
    }

    /**
     * The step number from the dialback standard are in parenthesis
     */
    public void run() {
        // Establish TCP connection to the Receiving Server (1)
        logger.fine("Trying to establish a connection.");
        try {
            sock = new Socket(InetAddress.getByName(remoteHostname), S2S_PORT);
            logger.fine("Established connection.");
            // The Originating Server sends a stream header to the Receiving Server (2)
            String initiate_stream_message = "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' xmlns:db='jabber:server:dialback'>";
            sendMessage(initiate_stream_message, sock);
            logger.fine("Sent stream initiation message.");

            // The Receiving Server SHOULD send a stream header back to the Originating Server, including a unique ID for this interaction (3)
            XMLLightweightParser parser = new XMLLightweightParser("UTF-8");
            inStream = sock.getInputStream();
            logger.fine("Waiting for response from server.");
            waitForMessage(parser, inStream);
            String msg = msg_buffer.poll();
            logger.fine("Received response from server: " + msg);

            // The Originating Server sends a dialback key to the Receiving Server (4)
            String key = getNextDialbackKey();
            String dialback_key_message = "<db:result to='" + remoteHostname + "' from='" + myHostname + "' id='" + key + "'>" + key + "</db:result>";
            logger.fine("Sending dialback key to server.");
            sendMessage(dialback_key_message, sock);

            /*
             * All of these steps are covered by IncomingConnectionHandler
             * // The Receiving Server establishes a TCP connection back to the domain name asserted by the Originating Server (5)
             * // The Authoritative Server sends the Receiving Server a stream header (6)
             * // The Receiving Server sends the Authoritative Server a request for verification of a key (7)
             * // The Authoritative Server verifies whether the key was valid or invalid (8)
             */

            // The Receiving Server informs the Originating Server of the result (9)
            logger.fine("Waiting for validity response from server.");
            waitForMessage(parser, inStream);
            msg = msg_buffer.poll();
            if (getField(msg, "type").toLowerCase().equals("valid")) {
                logger.info("Successfully negotiated S2S with: " + remoteHostname);
            } else {
                logger.severe("UNABLE to negotiated S2S with: " + remoteHostname);
            }

        } catch ( IOException e) {
            logger.log(Level.SEVERE,"Unable to connect to remote host: " + remoteHostname, e);
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.component.s2s.ConnectionHandler#closeConnections()
     */
    public void closeConnections(){
    	try {
    		if( sock != null){
    			logger.fine("closing OutgoingConnection socket");
    			sock.close();
    		}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Exception while closing outgoing connection sockets");
		}
    }


    public void sendPacket(Packet p){
        sendMessage(p.toXML(),sock);
    }

    protected String getNextDialbackKey() {
        return "9023099023030";
    }

    /**
     * test code
     * 
     * @param args hosts to connect to using S2S
     */
    public static void main(String[] args) {
        // start the listener, so that it can verify the key from us
        S2S is = new S2S();
        is.start();

        String myHostname = args[0];
        for (int i = 1; i < args.length; i++) {
            OutgoingConnectionHandler och = new OutgoingConnectionHandler(args[i], myHostname);
            och.start();
        }
    }
}
