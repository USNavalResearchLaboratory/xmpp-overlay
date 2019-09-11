/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.xmpp.packet.IQ;
import org.xmpp.packet.Roster;

import edu.drexel.xop.client.ClientConnection;
import edu.drexel.xop.client.ClientManager;
import edu.drexel.xop.client.LocalClientListener;

/**
 * Builds a roster for locally connected clients
 * 
 * @author di
 */
public class LocalRosterRetriever implements RosterRetriever, LocalClientListener {
    private static final String GROUP_NAME = "Local Users";
    private static Collection<String> GROUPS = new ArrayList<>(1);
    private Set<ClientConnection> knownLocalClients;

    public LocalRosterRetriever() {
        GROUPS.add(GROUP_NAME);
        knownLocalClients = new HashSet<>();
    }

    public void initialize(ClientManager clientManager) {
        clientManager.addLocalClientListener(this);
    }

    public Roster getRoster(IQ iq) {
        Roster roster = new Roster(IQ.Type.result, iq.getID());
        for (ClientConnection cc : knownLocalClients) {
            roster.addItem(cc.getJid(), null, null, Roster.Subscription.both, GROUPS);
        }
        return roster;
    }

    public String toString() {
        String rval = GROUP_NAME + ":\n";
        for (ClientConnection user : knownLocalClients) {
            rval += " > " + user.getJid() + "\n";
        }
        return rval;
    }

    @Override
    public void localClientAdded(ClientConnection clientConnection) {
        knownLocalClients.add(clientConnection);
    }

    @Override
    public void localClientRemoved(ClientConnection clientConnection) {
        // Do nothing, the roster list should always have all known clients
    }
}