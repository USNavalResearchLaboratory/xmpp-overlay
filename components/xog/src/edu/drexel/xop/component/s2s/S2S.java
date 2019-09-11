package edu.drexel.xop.component.s2s;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.properties.XopProperties;
import org.xmpp.packet.Packet;

/**
 * This is a work in progress. It does not work ATM.
 * 
 * This class represents a service that listens for incoming S2S connections from XMPP servers that wish to
 * use the server-to-server protocol with us.
 * 
 * @author urlass
 * 
 */
public class S2S extends Thread {
    private static final Logger logger = Logger.getLogger(S2S.class.getName());
    private static int s2sPort = XopProperties.getInstance().getIntProperty(XopProperties.S2S_PORT);
    private static final String xogHostname = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);

    private HashSet<IncomingConnectionHandler> incomingConnectionHandlers;
    private HashSet<OutgoingConnectionHandler> outgoingConnectionHandlers;
	private ServerSocket server_sock;

    public S2S() {
        logger.info("Starting server to server connections");
    	this.setName("S2S Thread");
        incomingConnectionHandlers = new HashSet<>();
        outgoingConnectionHandlers = new HashSet<>();
    }

    public void run() {
        logger.info("Listening for incoming server to server connections");
        server_sock = null;
        try {
            server_sock = new ServerSocket(s2sPort);
            server_sock.setReuseAddress(true);
            while (true) {
                // set up a socket to listen for incoming connections
                Socket s = null;
                try {
                    s = server_sock.accept();
                } catch (IOException e) {
                    logger.log(Level.SEVERE,"Error with incoming socket.",e);
                }
                // hand incoming connection off to ConnectionHandler
                IncomingConnectionHandler c = new IncomingConnectionHandler(s);
                c.start();
                incomingConnectionHandlers.add(c);
            }
        } catch (IOException e1) {
        	logger.severe("unable to set-up new server socket");
            e1.printStackTrace();
        } finally {
        	if( server_sock != null ){
        		try {
					server_sock.close();
				} catch (IOException e) {
					logger.log(Level.SEVERE,"Exception closing S2S server socket",e);
				}
        	}
        }

    }

//    public void sendPacketToIncoming(Packet p) {
//        for (IncomingConnectionHandler ch : incomingConnectionHandlers) {
//            ch.processPacket(p);
//        }
//    }
//

    /**
     * send the packet out to the enterprise
     * @param p the packet to be sent out the outgoingConnection that matches the enterprise server's hostname
     */
    public void sendPacketToEnterprise(Packet p) {
        for (OutgoingConnectionHandler ch : outgoingConnectionHandlers) {
            ch.sendPacket(p);
        }
    }

    public void establishOutgoingS2S() {
        String s2sServer = XopProperties.getInstance().getProperty(XopProperties.S2S_SERVER);
        logger.info("Establishing outgoing connection to "+s2sServer);

        OutgoingConnectionHandler och = new OutgoingConnectionHandler(s2sServer, xogHostname);
        och.start();
        outgoingConnectionHandlers.add(och);
    }

    /**
     * Will close the server socket associated with 
     */
    public void closeS2SConnections(){
    	
    	try {
    		for (IncomingConnectionHandler ch: incomingConnectionHandlers){
    			ch.closeConnections();
    		}
    		
			server_sock.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * test code
     * @param args
     */
    public static void main(String[] args) {
        // For testing purposes
        S2S is = new S2S();
        is.start();
    }
}