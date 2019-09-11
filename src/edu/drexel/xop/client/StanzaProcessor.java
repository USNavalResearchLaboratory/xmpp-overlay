/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.client;

/**
 * processes stanzas from clients.
 *
 * @author David Millar
 */
public interface StanzaProcessor {

    /**
     * processes a stanza from an XMPP client
     *
     * @param stanza
     * @param fromJID
     * @throws ParserException
     */
    public void processStanza(String stanza, String fromJID) throws ParserException;
}
