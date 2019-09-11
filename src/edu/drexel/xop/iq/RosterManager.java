package edu.drexel.xop.iq;

import edu.drexel.xop.client.*;
import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.core.PacketFilter;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Roster;
import org.xmpp.packet.Roster.Ask;
import org.xmpp.packet.Roster.Subscription;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages the rosters, handles a roster request.
 * 
 * @author di
 */
public class RosterManager extends IqHandler implements LocalClientListener, RemoteClientListener, PacketFilter {
    private static final Logger logger = LogUtils.getLogger(RosterManager.class.getName());
    private Set<RosterRetriever> rosterRetrievers;
    private ClientManager clientManager;

    public RosterManager(ClientManager clientManager) {
        this.clientManager = clientManager;
        this.rosterRetrievers = new HashSet<>();
        this.addRosterRetriever(new LocalRosterRetriever());
        this.addRosterRetriever(new RemoteRosterRetriever());
        this.clientManager.addLocalClientListener(this);
        this.clientManager.addRemoteClientListener(this);
    }

    public Roster getRoster(IQ iq) {
        logger.fine("Getting the rosters from the rosterRetrievers:" + iq);
        Roster roster = new Roster(IQ.Type.result, iq.getID());
        for (RosterRetriever rr : rosterRetrievers) {
            logger.finest("Trying rosterRetriever " + rr.getClass().getSimpleName());
            roster = combineRosters(rr.getRoster(iq), roster);
        }
        roster.setTo(iq.getFrom());
        return roster;
    }

    /**
     * @param rosterRetriever the rosterlist to add
     */
    public void addRosterRetriever(RosterRetriever rosterRetriever) {
        rosterRetriever.initialize(this.clientManager);
        rosterRetrievers.add(rosterRetriever);
    }

    /**
     * Horrible hack because Roster.getItems() seems to be broken in Tinder.
     * Suppress warnings because there isn't a better way to do it.
     * 
     * @param r1
     * @param r2
     * @return both rosters combined
     */
    @SuppressWarnings("unchecked")
    private static Roster combineRosters(Roster r1, Roster r2) {
        Element element = r1.getElement();
        // Collection<Item> items = new ArrayList<Item>();
        Element query = element.element(new QName("query", Namespace.get("jabber:iq:roster")));
        if (query != null) {
            for (Iterator<Element> i = query.elementIterator("item"); i.hasNext();) {
                Element item = i.next();
                String jid = item.attributeValue("jid");
                String name = item.attributeValue("name");
                String ask = item.attributeValue("ask");
                String subscription = item.attributeValue("subscription");
                Collection<String> groups = new ArrayList<>();
                for (Iterator<Element> j = item.elementIterator("group"); j.hasNext();) {
                    Element group = j.next();
                    groups.add(group.getText().trim());
                }
                Ask askStatus = ask == null ? null : Ask.valueOf(ask);
                Subscription subStatus = subscription == null ? null : Subscription.valueOf(subscription);
                r2.addItem(new JID(jid, true), name, askStatus, subStatus, groups);
            }
        }
        // Add the approved attribute
        List<Element> items = r2.getChildElement().elements();
        for (Element e : items) {
            e.addAttribute("approved", "true");
        }
        return r2;
    }

    public String toString() {
        String rval = "";
        for (RosterRetriever rr : rosterRetrievers) {
            rval += rr.toString();
        }
        return rval;
    }

    /**
     * Pushes the roster out to locally connected clients
     */
    private void push() {
        Collection<ClientConnection> clients = clientManager.getLocalClients();
        for (ClientConnection client : clients) {
            logger.fine("Pushing new roster to: " + client.getJid().toBareJID());
            // Fake an IQ message, because this is how we generate rosters right now...
            IQ iq = new IQ(IQ.Type.get);
            iq.setFrom(client.getJid());
            Roster roster = getRoster(iq);
            ClientProxy.getInstance().processPacket(roster);
        }
    }

    public boolean accept(Packet packet) {
        if (!(packet instanceof IQ)) {
            return false;
        }

        IQ iq = (IQ) packet;
        if (iq.getType() != IQ.Type.get) {
            return false;
        }

        Element e = iq.getChildElement();
        return e.getName().equals("query") && e.getNamespaceURI().equals("jabber:iq:roster");

    }

    @Override
    public void localClientAdded(ClientConnection clientConnection) {
        logger.fine("New local client added, pushing roster to local clients");
        push();
    }

    @Override
    public void localClientRemoved(ClientConnection clientConnection) {
        // TODO Auto-generated method stub
    }

    @Override
    public void remoteClientAdded(RemoteClient remoteClient) {
        logger.fine("New remote client added, pushing roster to local clients");
        push();
    }

    @Override
    public void remoteClientRemoved(RemoteClient remoteClient) {
        // TODO Auto-generated method stub
    }

    @Override
    public IQ handleIq(IQ iq) {
        return this.getRoster(iq);
    }
}