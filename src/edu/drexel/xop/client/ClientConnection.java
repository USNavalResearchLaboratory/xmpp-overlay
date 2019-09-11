/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.client;

import org.xmpp.packet.JID;

import edu.drexel.xop.component.VirtualComponent;

/**
 * Interface for ClientConnections<br/>
 * Handles client connections at the byte-stream level
 * Also manages authentication
 *
 * @author David Millar
 */
public interface ClientConnection extends VirtualComponent {
    /**
     * Returns the client's JID
     *
     * @return the jid
     */
    public JID getJid();

    /**
     * Call this after jid and proxy are set
     */
    public void init();

    /**
     * Sets a client's JID
     *
     * @param jid the jid to set
     */
    public void setJid(JID jid);

    /**
     * This is for sending anything that isn't a stanza, like a close of stream<br/>
     * or start of stream.
     *
     * @param bytes the bytes to send
     */
    public void writeRaw(byte[] bytes);

    /**
     * This method is called when a client sends data. No guarantees of a complete packet here.
     *
     * @param bytes the incoming bytes from the client
     */
    public void handleClientInput(byte[] bytes);

    /**
     * Close and cleanup this connection.
     */
    public void stop();

    /**
     * Returns whether or not the user is local to this xop instance.<br/>
     * an example of non-local users could be muc participants or XEP-0174 users<br/>
     *
     * @return true if the user is connected to this instance of XOP, false otherwise
     */
    public boolean isLocal();

    /**
     * Turn on SSL
     */
    public void enableSSL();

    /**
     * sslPostProcessing
     */
    public void sslPostProcessing();

    /**
     * @param fromJID
     */
    public void processCloseStream(String fromJID);

}