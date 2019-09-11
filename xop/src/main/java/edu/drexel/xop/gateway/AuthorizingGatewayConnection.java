package edu.drexel.xop.gateway;

import edu.drexel.xop.Run;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import org.xml.sax.InputSource;

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
 * A connection that will send a
 */
class AuthorizingGatewayConnection extends GatewayConnection { //implements XOPConnection {//
    private static final Logger logger = LogUtils.getLogger(AuthorizingGatewayConnection.class.getName());

    /// From GatewayConnection
    private static SSLSocketFactory socketFactory = null;
    private Socket socket = null;
    private boolean clientMode = true;
    private GatewayXMLProcessor processor = null;
    private InputStream clientInputStream = null;
    private OutputStream clientOutputStream = null;
    private boolean stopping;
    /// END From GatewayConnection

    private AuthorizingGatewayXMLProcessor authorizingGatewayXMLProcessor;

    AuthorizingGatewayConnection(String remoteGatewayDomainName, int port,
                                 String xopGatewayDomainName,
                                 String streamId, String hashKey,
                                 ReceivingGatewayConnection receivingGatewayConnection) throws IOException {
        super();
        InetAddress address = InetAddress.getByName(remoteGatewayDomainName);
        logger.info("Creating new Socket on "+address+" and port "+port);
        this.socket = new Socket(address, port);
        this.clientMode = true;
        logger.info("Connected to external server: " + socket.getInetAddress() + ":" + socket.getPort());
        authorizingGatewayXMLProcessor = new AuthorizingGatewayXMLProcessor(this,
                xopGatewayDomainName, remoteGatewayDomainName, streamId, hashKey, receivingGatewayConnection);
    }

    public void writeRaw(byte[] bytes) {

        if( logger.isLoggable(Level.FINER) ) {
            logger.finer("writing raw bytes to "
                    + ((socket != null) ? socket.getInetAddress().getHostAddress() : "null address")
                    + ": ===" + new String(bytes) + "===");
        }

        try {
            clientOutputStream.write(bytes, 0, bytes.length);
        } catch (IOException e) {
            logger.severe("Error writing raw bytes to client!");
            e.printStackTrace();
        } finally {
            if (stopping) {
                try {
                    logger.info("client socket is closing!");
                    clientOutputStream.close();
                    clientInputStream.close();
                    socket.close();
                } catch (IOException e) {
                    logger.severe("Unable to close socket!");
                }
            } else {
                logger.finest("not closing the client socket.");
            }
        }
    }

    public boolean isLocal() {
        return true;
    }

    public InetAddress getAddress() {
        return socket.getInetAddress();
    }

    public String getHostName() {
        if( logger.isLoggable(Level.FINE) ) logger.fine("hostname for address: " + socket.getLocalAddress().toString());
        return socket.getLocalAddress().toString();
    }

    public void run(){
        try {
            clientInputStream = socket.getInputStream();
            clientOutputStream = socket.getOutputStream();
            processor = getXMLProcessor();
            processor.setInputSource(new InputSource(clientInputStream));
        } catch (IOException e) {
            logger.severe("Error while getting input and output streams from socket: " + e.getMessage());
        }
        logger.fine("Run exiting.");
    }

    public void processCloseStream() {
        // handle the close stream for xop
        stopping=true;
        try{
            clientInputStream.close();
            clientOutputStream.close();

            socket.close();
        } catch (IOException e) {
            logger.severe("Exception closing streams and socket " + e.getMessage());
        }

    }

    public void stop() {
        stopping = true;
        writeRaw(CONSTANTS.AUTH.STREAM_CLOSE.getBytes());
    }

    @Override
    public String toString() {
        return "AuthorizingGatewayConnection";
    }

    GatewayXMLProcessor getXMLProcessor() {
        return authorizingGatewayXMLProcessor;
    }

    boolean getClientMode() {
        return clientMode;
    }

    public boolean enableSSL() {
        String clientKey = "nothing";
        logger.info("Converting socket for " + clientKey + " into SSL");

        try {
            SSLSocket sock = transformToSSLSocket(socket);
            sock.setUseClientMode(getClientMode());
            logger.finer("Starting ssl handshake");
            sock.startHandshake();
            logger.finer("Completed ssl handshake");
            // get the SSL versions from the new socket ...
            clientInputStream = sock.getInputStream();
            clientOutputStream = sock.getOutputStream();
            socket = sock;
            logger.fine("SSL enabled");
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



    /**
     * Call this externally to set the keystore used for the SSLSocketFactory
     * @param storeLocation The input stream at for the keystore location
     * @param trustLocation the input strum of the trust store
     * @param storePass the password for the keystore
     * @param trustPass the passwor dfor the trust store
     */
    private static void setKeyStoreAndTrustStore(InputStream storeLocation, InputStream trustLocation, String storePass, String trustPass) {
        try {
            logger.fine("Setting up keystore...");
            SSLContext context = SSLContext.getInstance("TLS");
            KeyManagerFactory keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = loadKeyStore(storeLocation, storePass);
            keyManager.init(keyStore, XOP.GATEWAY.STOREPASSWORD.toCharArray());

            TrustManager[] trustManagers;
            if( XOP.GATEWAY.ACCEPT_SELF_SIGNED_CERTS ){
                logger.info("Accepting Self-signed certificates");
                trustManagers = new TrustManager[]{
                        new X509TrustManager(){
                            public java.security.cert.X509Certificate[] getAcceptedIssuers(){
                                return null;
                                //return new X509Certificate[0];
                            }
                            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType){}
                            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType){}
                        }
                };
            } else {
                logger.info("using trust store located at "+trustLocation);
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                KeyStore trustStore = loadKeyStore(trustLocation, trustPass);

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
     * Do not use this, it is just a utility method for setting the keystore
     * @param location
     * @return
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
        } catch (KeyStoreException e) {
            logger.severe("KeyStoreException: Unable to load keystore: " +e.getMessage());
        } catch (CertificateException e) {
            logger.severe("CertificateException: Unable to load keystore: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            logger.severe("NoSuchAlgorithmException: Unable to load keystore: " + e.getMessage());
        } catch (IOException e) {
            logger.severe("IOException: Unable to load keystore: " + e.getMessage());
        } finally {
            try {
                location.close();
            } catch (IOException e) {
                logger.warning("Could not close file stream: " + e.getMessage());
            }
        }
        return store;
    }


    /**
     * Transforms a conventional socket into a SSL socket.
     *
     * @param socket the existing socket to be converted to SSL
     * @return an SSLSocket
     * @throws Exception if unable to create one
     */
    private static SSLSocket transformToSSLSocket(Socket socket) throws Exception {
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
            sock.setEnabledProtocols(sock.getSupportedProtocols());
            sock.setEnabledCipherSuites(sock.getSupportedCipherSuites());
            logger.fine("SSL enabled");
            return sock;
        } else {
            String msg = "Couldn't open SSL Socket, the SSLServerSocketFactory was null";
            logger.severe(msg);
            Run.shutdown();
            throw new Exception(msg);
        }
    }
}
