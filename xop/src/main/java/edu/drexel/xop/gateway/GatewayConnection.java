package edu.drexel.xop.gateway;

import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import edu.drexel.xop.Run;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Abstract gateway connection for Initiating and Receiving Gateway Connections
 * Created by duc on 6/6/16.
 * update 2018-03-27 moving away from XOPConnection to try and compartmentalize
 * the connection and socket handling
 */
abstract class GatewayConnection implements Runnable {
    private static Logger logger = LogUtils.getLogger(GatewayConnection.class.getName());

    private static SSLSocketFactory socketFactory = null;
    protected Socket socket = null;

    boolean clientMode = true;
    AtomicBoolean killSwitch = new AtomicBoolean(false);

    private GatewayXMLProcessor processor = null;
    private InputStream gatewayInputStream = null;
    private OutputStream gatewayOutputStream = null;
    boolean stopping;

    private GatewayPing gatewayPing;

    void setGatewayPing(GatewayPing gatewayPing){
        this.gatewayPing = gatewayPing;
    }

    GatewayPing getGatewayPing(){
        return this.gatewayPing;
    }

    /**
     * Stops the connection from this end
     */
    abstract void stop();

    /**
     * Call this externally to set the keystore used for the SSLSocketFactory
     * @param storeLocation The input stream at for the keystore location
     * @param trustLocation the input strum of the trust store
     * @param storePass the password for the keystore
     * @param trustPass the passwor dfor the trust store
     */
    private static void setKeyStoreAndTrustStore(InputStream storeLocation,
                                                 InputStream trustLocation,
                                                 String storePass, String trustPass)
    {
        try {
            logger.fine("Setting up keystore...");
            SSLContext context = SSLContext.getInstance("TLS");
            KeyManagerFactory keyManager =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = loadStore(storeLocation, storePass);
            keyManager.init(keyStore, XOP.GATEWAY.STOREPASSWORD.toCharArray());

            TrustManager[] trustManagers;
            if( XOP.GATEWAY.ACCEPT_SELF_SIGNED_CERTS ){
                logger.info("Gateway connections allowing Self-signed certificates");
                trustManagers = new TrustManager[]{
                        new X509TrustManager(){
                            public java.security.cert.X509Certificate[] getAcceptedIssuers(){
                                return null;
                            }
                            public void checkClientTrusted(
                                    java.security.cert.X509Certificate[] certs, String authType){}
                            public void checkServerTrusted(
                                    java.security.cert.X509Certificate[] certs, String authType){}
                        }
                };
            } else {
                logger.info("Gateway connection using trust store located at "+trustLocation);
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                KeyStore trustStore = loadStore(trustLocation, trustPass);

                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            }

            context.init(keyManager.getKeyManagers(), trustManagers, new SecureRandom());

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
     * Utility method for setting the keystore/truststore
     * @param location input stream for the keystore/truststore
     * @param password the password for the keystore/truststore
     * @return a KeyStore object
     */
    private static KeyStore loadStore(InputStream location, String password) {
        KeyStore store = null;
        try {
            store = KeyStore.getInstance(KeyStore.getDefaultType());
            if(location != null) {
                store.load(location, password.toCharArray());
            }
            else {
                logger.severe("Couldn't load null keystore");
            }
        } catch (KeyStoreException e) {
            logger.severe("KeyStoreException: Unable to load keystore: " +e.getMessage());
        } catch (CertificateException e) {
            logger.severe("CertificateException: Unable to load keystore: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            logger.severe("NoSuchAlgorithmException: Unable to load keystore: " + e.getMessage());
        } catch (IOException e) {
            logger.severe("IOException: Unable to load keystore: " + e.getMessage());
        } finally {
            if (location != null) {
                try {
                    location.close();
                } catch (IOException e) {
                    logger.warning("Could not close file stream: " + e.getMessage());
                }
            }
        }
        return store;
    }

    /**
     * Transforms a conventional socket into a SSL socket.
     *
     * @param socket the socket to be transformed into SSO
     * @return an encrypted SSL socket
     * @throws IOException for missing keystore and trust store or host not found exceptions
     */
    private static SSLSocket transformToSSLSocket(Socket socket) throws IOException {
        if(socketFactory == null) {
            File keystorePath = new File(XOP.GATEWAY.KEYSTORE).getAbsoluteFile();
            File truststorePath = new File(XOP.GATEWAY.TRUSTSTORE).getAbsoluteFile();
            if( logger.isLoggable(Level.FINE) ) logger.fine("keystorePath: " + keystorePath.getAbsolutePath());
            if( logger.isLoggable(Level.FINE) ) logger.fine("truststorePath: " + truststorePath.getAbsolutePath());
            setKeyStoreAndTrustStore(
                    new FileInputStream(keystorePath),
                    new FileInputStream(truststorePath),
                    XOP.GATEWAY.STOREPASSWORD,
                    XOP.GATEWAY.TRUSTSTOREPASSWORD);
            if( logger.isLoggable(Level.FINE) ) {
                logger.fine("using keystore: " + XOP.GATEWAY.KEYSTORE);
                logger.fine("using truststore: " + XOP.GATEWAY.TRUSTSTORE);
            }
        }
        if(socketFactory != null) {
            InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
            logger.fine("creating SSL Socket");
            logger.fine("socket address: "+remoteAddress.getAddress().getHostAddress());
            SSLSocket sock = (SSLSocket) (socketFactory.createSocket(socket, remoteAddress.getHostName(), socket.getPort(), true));
            logger.finer("Supported Cipher Suites: " + Arrays.toString(sock.getSupportedCipherSuites()));
            logger.finer("Supported Protocols: " + Arrays.toString(sock.getSupportedProtocols()));
            String[] enabledProtocols = new String[]{"SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2"};
            sock.setEnabledProtocols(enabledProtocols);
            sock.setEnabledCipherSuites(sock.getSupportedCipherSuites());
            logger.finer("Enabled Protocols: " + Arrays.toString(sock.getEnabledProtocols()));
            logger.fine("returning new SSL Socket");
            return sock;
        }
        else {
            logger.severe("Couldn't open SSL Socket, the SSLServerSocketFactory was null");
            Run.shutdown();
        }
        return null;
    }

    public void writeRaw(byte[] bytes) throws IOException{

        if( logger.isLoggable(Level.FINER) ) {
            logger.finer("writing raw bytes to "
                    + ((socket != null) ? socket.getInetAddress().getHostAddress() : "null address")
                    + ": ===" + new String(bytes) + "===");
            logger.finer("socket.isBound()"+socket.isBound());
            logger.finer("socket.isClosed()"+socket.isClosed());
            logger.finer("socket.isConnected()"+socket.isConnected());
        }

        try {
            gatewayOutputStream.write(bytes, 0, bytes.length);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing raw bytes to client!", e);
            stopping=true;
            throw e;
        } finally {
            if (stopping) {
                try {
                    logger.info("client socket is closing!");
                    gatewayOutputStream.close();
                    gatewayInputStream.close();
                    socket.close();
                } catch (IOException e) {
                    logger.severe("Unable to close socket!");
                }
            } else {
                logger.finest("not closing the client socket.");
            }
        }
    }

    public InetAddress getAddress() {
        return socket.getInetAddress();
    }

    public String getHostName() {
        if( logger.isLoggable(Level.FINE) )
            logger.fine("getting hostname: "
                    + socket.getLocalAddress().toString());
        return socket.getLocalAddress().toString();
    }

    public void run(){
        try {
            init();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error while getting input and output streams from socket: "
                            + e.getMessage(), e);
        }
        logger.fine("Run exiting.");
    }

    public void init() throws IOException {
        logger.info("socket isConnected "+socket.isConnected());
        logger.info("socket isClosed "+socket.isClosed());
        gatewayOutputStream = socket.getOutputStream();
        gatewayInputStream = socket.getInputStream();
        processor = getXMLProcessor();
        processor.setInputSource(new InputSource(gatewayInputStream));
    }

    /**
     *
     * @return an XML Processor implementation.
     */
    abstract GatewayXMLProcessor getXMLProcessor();

    /**
     *
     * @return whether this connection is in client mode or not.
     *         clientMode =true for receiving connections
     */
    abstract boolean getClientMode();

    public boolean enableSSL() {
        String clientKey = "nothing";
        logger.info("Converting socket for " + clientKey + " into SSL");

        try {
            SSLSocket sock = transformToSSLSocket(socket);
            if (sock == null) {
                logger.severe("Transformed socket is null!");
                return false;
            }
            sock.setUseClientMode(getClientMode());
            logger.finer("Starting ssl handshake");
            sock.startHandshake();
            logger.finer("Completed ssl handshake");
            // get the SSL versions from the new socket ...
            gatewayInputStream = sock.getInputStream();
            gatewayOutputStream = sock.getOutputStream();
            socket = sock;
            logger.info("SSL enabled for socket: "+socket+" re-initializing connection");
            processor.setInputSource(new InputSource(gatewayInputStream));
        } catch (IOException e) {
            logger.log(Level.SEVERE,
                    "Error getting input / output stream from client socket (SSL).", e);
            if (logger.isLoggable(Level.FINE)) {
                e.printStackTrace();
            }
            return false;
        }
        return true;
    }

    /**
     * close the streams and socket
     */
    void processCloseStream() {
        // handle the close stream for xop
        stopping=true;
        killSwitch.set(true);
        try{
            gatewayInputStream.close();
            gatewayOutputStream.close();

            socket.close();
        } catch (IOException e) {
            logger.severe("Exception closing streams and socket " + e.getMessage());
        }

    }

    /**
     * uses the socket.isConnected() method
     * @return true if the socket is connected, false otherwise
     */
    boolean isConnected(){
        return socket.isConnected();
    }
}
