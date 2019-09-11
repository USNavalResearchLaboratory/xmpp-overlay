/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq;

import org.xmpp.packet.IQ;
import org.xmpp.packet.Roster;

import edu.drexel.xop.client.ClientManager;

/**
 * Interface for components that gather roster info
 * 
 * @author David Millar
 */
public interface RosterRetriever {
    public Roster getRoster(IQ iq);

    public void initialize(ClientManager cm);
}
