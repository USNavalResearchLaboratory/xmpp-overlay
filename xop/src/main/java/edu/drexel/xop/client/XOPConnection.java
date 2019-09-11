package edu.drexel.xop.client;

import java.net.InetAddress;

/**
 * Interface for XopConnections<br/>
 * Handles client connections at the byte-stream level
 * manages authentication and ssl connections
 *
 */
public interface XOPConnection extends Runnable {
    /**
     * This is for sending anything that isn't a stanza, like a close of stream<br/>
     * or start of stream.
     *
     * @param bytes the bytes to send
     */
    void writeRaw(byte[] bytes);

    // /**
    //  * Returns whether or not the user is local to this xop instance.<br/>
    //  * an example of non-local users could be muc participants or XEP-0174 users<br/>
    //  *
    //  * @return true if the user is connected to this instance of XOP, false otherwise
    //  */
    // boolean isLocal();


    // void stop();
    /**
     *
     */
    void processCloseStream();

    //    boolean enableSSL();

    InetAddress getAddress();

    String getHostName();

    // /**
    //  * @return true if the entity is authenticated, false otherwise
    //  */
    // boolean isAuthenticated();
}