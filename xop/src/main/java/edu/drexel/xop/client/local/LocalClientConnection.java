package edu.drexel.xop.client.local;

import edu.drexel.xop.client.XMLProcessor;
import edu.drexel.xop.client.XOPConnection;
import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.packet.LocalPacketProcessor;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import org.xml.sax.InputSource;
import org.xmpp.packet.JID;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements a xopConnection and responsible for passing data to and from attached client connections
 * @deprecated Moving to ClientConnection.kt
 */
public class LocalClientConnection implements XOPConnection {
    private XMLProcessor processor = null;
    private static final Logger logger = LogUtils.getLogger(LocalClientConnection.class.getName());
    private Socket clientSocket;
    private InputStream clientInputStream = null;
    private OutputStream clientOutputStream = null;
    private static SSLSocketFactory socketFactory = null;
    private boolean stopping;
    private ClientManager clientManager;
    private LocalPacketProcessor localPacketProcessor;

    /**
     * @param s the socket between XOP and the XMPP client
     */
    LocalClientConnection(Socket s, ClientManager clientManager, LocalPacketProcessor localPacketProcessor) {
        clientSocket = s;
        this.clientManager = clientManager;
        this.localPacketProcessor = localPacketProcessor;
        logger.info("Initialized a localClientHandler for socket: "+clientSocket);
    }

    public boolean isLocal() {
        return true;
    }

    // public Socket getSocket() {
    //     return clientSocket;
    // }

    // These are raw bytes destined for the client
    public void writeRaw(byte[] bytes) {
        logger.finer("writing raw bytes to "
                + ((clientSocket != null)?clientSocket.getInetAddress().getHostAddress():"null address")
                + ": ===" + new String(bytes) + "===");
        try {
            if (clientSocket.isConnected())
                clientOutputStream.write(bytes, 0, bytes.length);
            else
                logger.info("not writing socket because not connected!");
        } catch (IOException e) {
            logger.severe("Error writing raw bytes to client! " + e.getMessage());
            // e.printStackTrace();
        } finally {
        	if (stopping) {
        		try {
                    logger.info("client socket is closing!");
                    clientOutputStream.close();
                    clientInputStream.close();
                	clientSocket.close(); 
                 } catch (IOException e) {
                    logger.severe("Unable to close socket!");
                }
        	}
        }
    }

    public void processCloseStream() {
        if(!stopping) {
            JID jid = clientManager.getJIDForLocalConnection(this);

            XOProxy.getInstance().handleCloseStream(jid);
        }
    }

    public InetAddress getAddress() {
        return clientSocket.getInetAddress();
    }

    public String getHostName() {
        if( logger.isLoggable(Level.FINE) ) logger.fine("getting hostname: " + clientSocket.getLocalAddress().toString());
        return clientSocket.getLocalAddress().toString();
    }

    private XMLProcessor getXMLProcessor() {
        return new ClientXMLProcessor(this, clientManager, localPacketProcessor);
    }

    private boolean getClientMode() {
        return false;
    }

    public void run() {
        processor = getXMLProcessor();
        try {
            clientInputStream = clientSocket.getInputStream();
            clientOutputStream = clientSocket.getOutputStream();
            XMLProcessor xmlProcessor = new ClientXMLProcessor(this, clientManager, localPacketProcessor);
            InputSource is = new InputSource(clientInputStream);
            xmlProcessor.setInputSource(is);
            xmlProcessor.beginProcessing(xmlProcessor.getHandler());
        } catch (IOException e) {
            logger.severe("Error while getting input and output streams from socket: " + e.getMessage());
        }
    }

    public void stop() {
        stopping = true;
        writeRaw(CONSTANTS.AUTH.STREAM_CLOSE.getBytes());
        // TODO dnn 2016-06-06: set flag stating close stream was sent (so when we receive the close stream,
        //  we can close the connection.
    }

    boolean enableSSL() {
        String clientKey = "nothing";
        logger.info("Converting socket for " + clientKey + " into SSL");

        try {
            SSLSocket sock = transformToSSLSocket(clientSocket);
            if( sock == null ){
                throw new Exception("socket was null");
            }
            sock.setUseClientMode(getClientMode());
            logger.finer("Starting ssl handshake");
            sock.startHandshake();
            logger.finer("Completed ssl handshake");
            // get the SSL versions from the new socket ...
            clientInputStream = sock.getInputStream();
            clientOutputStream = sock.getOutputStream();
            clientSocket = sock;
            processor.setInputSource(new InputSource(clientInputStream));
        } catch (IOException e) {
            logger.severe("Error getting input / output stream from client socket (SSL).");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            logger.severe("Error transforming socket to SSL.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static void setKeyStore(InputStream storeLocation, InputStream trustLocation, String pass) {
        setKeyStore(storeLocation, trustLocation, pass, pass);
    }

    /**
     * Call this externally to set the keystore used for the SSLSocketFactory
     * @param storeLocation the location of the keystore
     * @param trustLocation the location of the truststore
     * @param storePass keystore password
     * @param trustPass trustsore password
     */
    private static void setKeyStore(InputStream storeLocation, InputStream trustLocation,
                                    String storePass, String trustPass) {
        try {
            logger.fine("Setting up keystore...");
            SSLContext context = SSLContext.getInstance("TLS");
            KeyManagerFactory keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            TrustManagerFactory trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            KeyStore keyStore = loadKeyStore(storeLocation, storePass);
            KeyStore trustStore = loadKeyStore(trustLocation, trustPass);

            keyManager.init(keyStore, XOP.SSL.PASSWORD.toCharArray());
            trustManager.init(trustStore);

            context.init(keyManager.getKeyManagers(), trustManager.getTrustManagers(), new SecureRandom());

            socketFactory = context.getSocketFactory();
            logger.fine("Finished setting up keystore.");
        } catch (NoSuchAlgorithmException e) {
            logger.severe("Error while loading KeyManagerFactory: " + e.getMessage());
        } catch (KeyStoreException e) {
            logger.severe("Error while setting KeyStore: " + e.getMessage());
        } catch (UnrecoverableKeyException e) {
            logger.severe("Could not recover key: " + e.getMessage());
        } catch (KeyManagementException e) {
            logger.severe("Error with KeyManager: " + e.getMessage());
        }
    }

    /**
     * Do not use this, it is just a utility method for setting the keystore
     * @param location the location of the keystore
     * @return a keystore object
     */
    private static KeyStore loadKeyStore(InputStream location, String password) {
        KeyStore store = null;
        try {
            store = KeyStore.getInstance(KeyStore.getDefaultType());
            if(location != null) {
                store.load(location, password.toCharArray());
            }
            else {
                logger.severe("Couldn't load null keystore");
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            logger.severe("Unable to load keystore: " +e.getMessage());
        } finally {
            try {
                if( location != null ) {
                    location.close();
                }
            } catch (IOException e) {
                logger.severe("Could not close file stream: " + e.getMessage());
            }
        }
        return store;
    }

    /**
     * Transforms a conventional socket into a SSL socket.
     * 
     * @param socket socket to be turned into an SSL socket
     * @return a new SSL socket or null if unable to
     */
    private static SSLSocket transformToSSLSocket(Socket socket) throws IOException {
        if(socketFactory == null) {
            File keystorePath = new File(XOP.SSL.KEYSTORE).getAbsoluteFile();
            File trustStorePath = new File(XOP.SSL.TRUSTSTORE).getAbsoluteFile();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("keystorePath: " + keystorePath.getAbsolutePath());
            }
            setKeyStore(
                    new FileInputStream(keystorePath),
                    new FileInputStream(trustStorePath),
                    XOP.SSL.PASSWORD);
            if( logger.isLoggable(Level.FINE) ) {
                logger.fine("using keystore: " + XOP.SSL.KEYSTORE);
                logger.fine("using truststore: " + XOP.SSL.TRUSTSTORE);
            }
        }

        if(socketFactory != null) {
            InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
            logger.info("creating SSL Socket");
            logger.info("socket address: "+remoteAddress.getAddress().getHostAddress());
            SSLSocket sock = (SSLSocket) (socketFactory.createSocket(socket, remoteAddress.getHostName(), socket.getPort(), true));
            sock.setEnabledProtocols(sock.getSupportedProtocols());
            sock.setEnabledCipherSuites(sock.getSupportedCipherSuites());
            return sock;
        } else {
            logger.severe("Couldn't open SSL Socket, the SSLServerSocketFactory was null");
            //Run.shutdown();
            return null;
        }
    }
}
