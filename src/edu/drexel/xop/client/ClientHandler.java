/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import edu.drexel.xop.component.ComponentManager;
import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.router.PacketRouter;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * handles passing data to and from attached client connections
 * 
 * @author David Millar
 * @author Duc Nguyen
 * @author Rob Lass
 * <p/>
 * Some of this code taken from GUMP, written by Ian Taylor
 */
public class ClientHandler implements Runnable, ClientConnection {
    private boolean isLocal = true;
    private StanzaUnmarshaller stanzaUnmarshaller = null;
    private ClientAuthenticator auth = null;
    private boolean userAuthenticated;
    protected JID jid = null;
    private static final Logger logger = LogUtils.getLogger(ClientHandler.class.getName());
    private Socket clientSocket = null;
    private InputStream clientInputStream = null;
    private OutputStream clientOutputStream = null;
    private static final String CLIENTKEYSTORE = new File("config/clientcerts.jks").getAbsolutePath();
    private static final String KEYSTOREPW = "gumpstore";
    private ClientManager clientManager;

    public ClientHandler(ClientManager clientManager, Socket s) {
        this.isLocal = s.getInetAddress().isLoopbackAddress();
        this.clientManager = clientManager;
        this.init();
        clientSocket = s;
    }

    @Override
    public final void init() {
        auth = new ClientAuthenticator(this.clientManager, this);
        stanzaUnmarshaller = new StanzaUnmarshaller();
    }

    @Override
    public boolean isLocal() {
        return isLocal;
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.client.ClientConnection#handleClientInput(byte[])
     */
    @Override
    public void handleClientInput(byte[] bytes) {
        // Here, we handle raw bytes from a client, and in most cases produce a packet
        // That gets handed off to the handlePacketFromClient(...) method

        // Now we have a UTF-8 string
        String string = new String(bytes);

        // Pass off the data to our stanzaListener
        String fromJid = (getJid() == null) ? "" : getJid().toBareJID();

        try {
            logger.finer("processing stanza fromJid: (" + fromJid + "), string: ----" + string + "----");
            if (userAuthenticated) {
                logger.finer("User is authenticated, using the stanza unmarshaller");
                stanzaUnmarshaller.processStanza(string, fromJid);
                auth = null; // set to null so we can set auth to be garbage collected. (Well, this might not work)
            } else {
                logger.finer("User is not authenticated, doing something else");
                auth.processStanza(string, fromJid);
                userAuthenticated = auth.isAuthenticated();
            }
        } catch (ParserException e) {
            logger.log(Level.FINE, "Closing connection for user " + fromJid);
            ClientProxy cp = ClientProxy.getInstance();
            cp.handleCloseStream(fromJid);
        }
    }

    @Override
    public void processPacket(Packet p) {
        logger.finer("process packet, sending raw bytes to client p" + p);
        if (p instanceof Message) { // without this, oneteam does not work
            try {
                logger.finer("removing x extension for muc user");
                p.deleteExtension("x", "http://jabber.org/protocol/muc#user");
            } catch (Exception e) {
                e.printStackTrace();
                // incase tinder throws an exception as it is known to do
            }
        }
        String string = p.toXML();
        byte[] bytes = string.getBytes();
        writeRaw(bytes);
    }

    @Override
    public JID getJid() {
        return jid;
    }

    @Override
    public void setJid(JID jid) {
        logger.fine("setting jid to: " + jid);
        this.jid = jid;
    }

    /**
     * ugly hack to send SASL start streams after turning on SSL.
     */
    public void sslPostProcessing() {
        String SASL_START_STREAM = "<stream:stream from='" + ClientAuthenticator.DOMAIN + "' id='multicastX' to='"
            + ((this.jid != null) ? this.jid.toFullJID() : "")
            + "' version='1.0' xml:lang='en' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>";
        auth.send(SASL_START_STREAM);
        auth.send(ClientAuthenticator.STREAM_FEATURES_PLAIN);
    }

    // These are raw bytes destined for the client
    @Override
    public void writeRaw(byte[] bytes) {
        logger.log(Level.FINE, "writing raw bytes to client: <<<" + new String(bytes) + ">>>");
        try {
            clientOutputStream.write(bytes, 0, bytes.length);
        } catch (IOException e) {
            logger.severe("Error writing raw bytes to client!");
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        logger.log(Level.FINE, "stop() cleanup");
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.core.PacketListener#processCloseStream(java.lang.String)
     */
    @Override
    public void processCloseStream(String fromJID) {
        logger.finer("handling processCloseStream(" + fromJID + ") for jid.toBareJID(): " + jid.toBareJID());
        if (fromJID.equals(jid.toBareJID())) {
            logger.finer("closing stream for " + jid.toBareJID());
        }
        this.clientManager.removeLocalClient(fromJID);
    }

    @Override
    public boolean accept(Packet p) {
        try {
            if (p.getTo() != null && jid.toBareJID().equals(p.getTo().toBareJID())) {
                logger.log(Level.FINE, "Accepted a packet");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.component.VirtualComponent#init(edu.drexel.xop.component.ComponentManager)
     */
    @Override
    public void init(PacketRouter pr, ComponentManager cm) {
        // do nothing
    }

    @Override
    public void run() {
        // watch the socket for incoming data from the client, and relay it to the appropriate functions
        boolean closed = false;
        int RECEIVEBUFFER = 65535; // 64 K input buffer because datagram sockets only can hold 64K
        byte[] dataBuffer = new byte[RECEIVEBUFFER];
        try {
            clientInputStream = clientSocket.getInputStream();
            clientOutputStream = clientSocket.getOutputStream();
        } catch (IOException e) {
            logger.severe("Error getting input and output streams from client socket!");
            e.printStackTrace();
        }

        Thread cur = Thread.currentThread();
        while (!closed) {
            try {
                int bytesread = clientInputStream.read(dataBuffer);
                if (bytesread <= 0)
                    break;
                byte[] data = new byte[bytesread];
                System.arraycopy(dataBuffer, 0, data, 0, bytesread);
                this.handleClientInput(data);

            } catch (Exception e) {
                cur.getUncaughtExceptionHandler().uncaughtException(cur, e);
                try {
                    clientOutputStream.close();
                    e.printStackTrace();
                } catch (IOException e2) {
                    // We tried our best .... and the error is above
                }
                System.err.println("Socket Closed abnormally - see above error for more information");
                closed = true;
            }
        }
    }

    // //////////Everything below here modified from Ian's code///////////

    public void enableSSL() {
        String clientKey = "nothing";
        logger.info("Converting socket for " + clientKey + " into SSL");

        try {
            SSLSocket sock = transformToSSLSocket(clientSocket);
            // get the SSL versions from the new socket ...
            clientInputStream = sock.getInputStream();
            clientOutputStream = sock.getOutputStream();
            clientSocket = sock;
        } catch (IOException e) {
            logger.severe("Error getting input / output stream from client socket (SSL).");
            e.printStackTrace();
        } catch (Exception e) {
            logger.severe("Error transforming socket to SSL.");
            e.printStackTrace();
        }
    }

    /**
     * Creates a client SSL socket for testing
     * 
     * @return
     */
    private static SSLSocketFactory getSSLSocketFactory() {
        if (System.getProperty("javax.net.ssl.keyStore") == null
            && System.getProperty("javax.net.ssl.keyStorePassword") == null) {
            logger.fine("Using default CLIENTKEYSTORE=" + CLIENTKEYSTORE);
            System.setProperty("javax.net.ssl.keyStore", CLIENTKEYSTORE);
            System.setProperty("javax.net.ssl.keyStorePassword", KEYSTOREPW);
        } else {
            logger.fine("Using user defined CLIENTKEYSTORE=" + CLIENTKEYSTORE);
        }
        return (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    /**
     * Transforms a conventional socket into a SSL socket.
     * 
     * @param socket
     * @return
     * @throws Exception
     */
    private static SSLSocket transformToSSLSocket(Socket socket) throws Exception {
        InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
        SSLSocketFactory sf = getSSLSocketFactory();
        SSLSocket sock = (SSLSocket) (sf.createSocket(socket, remoteAddress.getHostName(), socket.getPort(), true));
        sock.setUseClientMode(false);
        sock.setEnabledProtocols(sock.getSupportedProtocols());
        sock.setEnabledCipherSuites(sock.getSupportedCipherSuites());
        sock.startHandshake();
        return sock;
    }
}
