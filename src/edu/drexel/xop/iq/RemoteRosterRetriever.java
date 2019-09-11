/*
 * Copyright (C) Drexel University 2012
 */
package edu.drexel.xop.iq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.xmpp.packet.IQ;
import org.xmpp.packet.Roster;

import edu.drexel.xop.client.ClientManager;
import edu.drexel.xop.client.RemoteClient;
import edu.drexel.xop.client.RemoteClientListener;

/**
 * Builds a roster list for XMPP clients discovered from the network.
 * 
 * @author di
 */
public class RemoteRosterRetriever implements RosterRetriever, RemoteClientListener {
    private static final String GROUP_NAME = "Remote Users";
    private static Collection<String> GROUPS = new ArrayList<>(1);
    private Set<RemoteClient> knownRemoteClients;

    public RemoteRosterRetriever() {
        GROUPS.add(GROUP_NAME);
        knownRemoteClients = new HashSet<>();
    }

    public void initialize(ClientManager clientManager) {
        clientManager.addRemoteClientListener(this);
    }

    @Override
    public Roster getRoster(IQ iq) {
        Roster roster = new Roster(IQ.Type.result, iq.getID());
        for (RemoteClient remoteClient : knownRemoteClients) {
            roster.addItem(remoteClient.getJid(), null, null, Roster.Subscription.both, GROUPS);
        }
        return roster;
    }

    @Override
    public String toString() {
        String rval = GROUP_NAME + ":\n";
        for (RemoteClient user : knownRemoteClients) {
            rval += " > " + user.getJid() + "\n";
        }
        return rval;
    }

    @Override
    public void remoteClientAdded(RemoteClient remoteClient) {
        knownRemoteClients.add(remoteClient);
    }

    @Override
    public void remoteClientRemoved(RemoteClient remoteClient) {
        // Do nothing, the roster list should always have all known clients
    }
}