package edu.drexel.xop.component.s2s;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.properties.XopProperties;
import org.dom4j.Element;
import org.xmpp.packet.Packet;

import edu.drexel.xop.util.XMLLightweightParser;

/**
 * This class handles a single incoming S2S request. It can also handle a request meant for the authoritative server.
 * 
 * @author urlass
 * 
 */
public class IncomingConnectionHandler extends ConnectionHandler {
    private static final Logger logger = Logger.getLogger(IncomingConnectionHandler.class.getName());

    private Socket sock;
    private InputStream inStream;
    // OutputStream outStream;
    final static int S2S_PORT = XopProperties.getInstance().getIntProperty(XopProperties.S2S_PORT);
    private String remoteServerName = null;

    IncomingConnectionHandler(Socket s) {
        sock = s;
        try {
            inStream = sock.getInputStream();
            // outStream = sock.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        if (negotiateConnection()) {
            logger.info("Successfully negotiated S2S!");
        } else {
            logger.warning("Unable to negotiate dialback connection.");
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.component.s2s.ConnectionHandler#closeConnections()
     */
    public void closeConnections(){
    	try {
    		if( sock != null ){
        		logger.fine("Closing socket");
    			sock.close();
    		}
		} catch (IOException e) {
			logger.log(Level.WARNING, "Exception closing incoming connection handler's socket", e);
		}
    }
    
    /**
     * The numbers in parenthesis indicate the step number in the XMPP Dialback standards document. See http://xmpp.org/rfcs/rfc3920.html#dialback
     * 
     * TODO: Cleanly finish if this is an authenticated server request.
     * 
     * @return true if negotiation was successful, false if something went wrong
     */
    public boolean negotiateConnection() {
        // set up the parser & related variables
        XMLLightweightParser parser = new XMLLightweightParser("UTF-8");

        // read the first packet from the socket, make sure it's to establish a connection (1 & 2)
        waitForMessage(parser, inStream);

        String msg = msg_buffer.poll();
        if (!msg.startsWith("<stream:stream")) {// it's not a start stream, give up
            handleBadMessage("Incoming S2S socket received invalid input (not a <stream...> message).", sock);
            return false;
        }

        // respond with a unique ID (3)
        String streamId = getNextStreamID();
        String response = "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' xmlns:db='jabber:server:dialback' id='" + streamId + "' version='1.0'>";

        // if it's version 1.0, we need to add this, or OF will close the stream? charlesr seemed to think so...
        Element elem = edu.drexel.xop.util.Utils.elementFromString(msg);
        if (elem.attribute("version") != null && elem.attributeValue("version").equals("1.0")) {
            response += "<stream:features> <dialback xmlns='urn:xmpp:features:dialback'> <required/>  <errors/> </dialback> </stream:features>";
        }

        sendMessage(response, sock);
        /*
         * try {
         * outStream.write(response.getBytes());
         * } catch (IOException e1) {
         * logger.severe("Unable to send message to remote server!");
         * e1.printStackTrace();
         * return false;
         * }
         */

        // wait for a dialback key (4)
        waitForMessage(parser, inStream);
        msg = msg_buffer.poll();

        // we need the to, from, and originatingID for step 8, below
        String from = getField(msg, "to");
        String to = getField(msg, "from");
        remoteServerName = to;
        // /////////////////////we are now acting as an authoritative server///////////////////////////////
        if (msg.startsWith("<db:verify")) {
            // TODO: actually verify this, the code below will accept anything
            String validityMessage = "<db:verify from='" + from + "' to='" + to + 
            		"' type='valid' id='" + getField(msg, "id") + "'/>";
            sendMessage(validityMessage, sock);
            return true;
        }

        // ////////////////////////////////CONTINUE NORMAL OPERATION//////////////////////////////////////
        if (!msg.startsWith("<db:result")) {
            handleBadMessage("Expecting dialback key, received: " + msg, sock);
            return false;
        }
        String originatingId = getIdFromDbMessage(msg);

        // open a new socket to the initiating server, and send a stream header (5 & 6)
        String authoritativeServer = getFromFieldFromMessage(msg);
        Socket authoritativeSocket = null;
        try {
            authoritativeSocket = new Socket(getOriginatingServerAddress(sock, 
            		authoritativeServer), S2S_PORT);
        } catch (IOException e) {
            logger.severe("Unable to connect to authoritative server: " + authoritativeServer);
            e.printStackTrace();
        }

        /*
         * OutputStream authoritativeOutput = null;
         * try {
         * authoritativeOutput = authoritativeSocket.getOutputStream();
         * } catch (IOException e) {
         * logger.severe("Unable to open socket to authoritative server.");
         * e.printStackTrace();
         * }
         */
        String request_dialback_message = "<stream:stream\nxmlns:stream='http://etherx.jabber.org/streams'\nxmlns='jabber:server'\nxmlns:db='jabber:server:dialback'>";
        sendMessage(request_dialback_message, authoritativeSocket);
        /*
         * try {
         * authoritativeOutput.write(request_dialback_message.getBytes());
         * } catch (IOException e1) {
         * logger.severe("Unable to send stream header to authoritative server.");
         * e1.printStackTrace();
         * }
         */

        // wait for a response over the new socket (7)
        XMLLightweightParser authoritativeParser = new XMLLightweightParser("UTF-8");
        InputStream authoritativeInput = null;
        try {
            authoritativeInput = authoritativeSocket.getInputStream();
        } catch (IOException e1) {
            logger.severe("Unable to get input stream from authoritative server.");
            e1.printStackTrace();
        }
        waitForMessage(authoritativeParser, authoritativeInput);
        msg = msg_buffer.poll();
        if (!msg.startsWith("<stream:stream")) {
            handleBadMessage("Incoming S2S socket received invalid input (not a <stream...> message).", sock);
            return false;
        }

        // send a request for verification of the key received in step 4 over the new socket (8)

        String verify_request_message = "<db:verify\nto='" + to + "'\nfrom='" + from + "'\nid='" + streamId + "'>\n" + originatingId + "\n</db:verify>";
        sendMessage(verify_request_message, authoritativeSocket);
        /*
         * try {
         * authoritativeOutput.write(verify_request_message.getBytes());
         * } catch (IOException e1) {
         * logger.severe("Unable to send request for verification of key to authoritative server.");
         * e1.printStackTrace();
         * }
         */

        // wait for a 'valid' or 'invalid' response from the new socket (9)
        waitForMessage(parser, authoritativeInput);
        msg = msg_buffer.poll();
        if (!msg.startsWith("<db:verify")) {
            handleBadMessage("Expected <db:verify ..> message, received this: " + msg, sock);
            return false;
        }

        boolean worked = isValidFromPacket(msg);

        // send the result, 'valid' or 'invalid', over the original socket
        if (worked) {
            String validity_response_message = "<db:result\nfrom='Receiving Server'\nto='Originating Server'\ntype='valid'/>";
            sendMessage(validity_response_message, sock);
            /*
             * try {
             * outStream.write(validity_response_message.getBytes());
             * } catch (IOException e) {
             * logger.severe("Unable to send key verification result to authoritative server.");
             * e.printStackTrace();
             * }
             */
            return true;
        }

        // if we're here, it failed
        try {
            sock.close();
        } catch (IOException e) {
            logger.severe("Unable to close socket.");
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Returns true if this message says the packet is valid, false otherwise.
     */
    private boolean isValidFromPacket(String msg) {
        String type = getField(msg, "type");
        if (type.equals("valid")) {
            return true;
        }
        return false;
    }

    public InetAddress getOriginatingServerAddress(Socket incomingSocket, String authoritativeServer) {
        try {
            return InetAddress.getByName(authoritativeServer);
        } catch (UnknownHostException e) {
            logger.severe("Unable to resolve hostname: " + authoritativeServer);
            e.printStackTrace();
        }
        return null;
    }

    public static String getFromFieldFromMessage(String msg) {
        return getField(msg, "from");
    }

    protected String getNextStreamID() {
        return "082092908209109120921";
    }

    public void processPacket(Packet p) {
        sendMessage(p.toString(), sock);
        // outStream.write(p.toString().getBytes());
    }
}
