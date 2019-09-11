package edu.drexel.xop.core.roster;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.LocalXMPPClient;
import edu.drexel.xop.core.XMPPClient;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.*;
import java.util.logging.Logger;

/**
 * One RosterList per logged in user
 *
 * XO does not support roster versioning as defined in RFC 6121 Sec 2.6
 *
 * Created by duc on 6/3/16.
 *
 */
public class RosterList {
    private static Logger logger = LogUtils.getLogger(RosterListManager.class.getName());

    private HashMap<JID, RosterItem> rosterItems;
    private Set<JID> deletedRosterItems;
    // dnn 2016-11-07: subscriptions are managed in the groups
    // private HashSet<JID> subscriptions = new HashSet<>(); // dnn 2016
    private JID clientJID;

    String getID() {
        return id;
    }

    void setID(String id) {
        this.id = id;
    }

    private String id;

    private boolean initialPresence = false;

    /**
     * constructs a new roster list for the given full jid of the client.
     * Supports constructing a list of known subscriptions (i.e. discovered remote entities)
     *
     * @param clientJID the full jid of the client
     */
    RosterList(IQ rosterGetIq, JID clientJID, ClientManager clientManager) {
        this.clientJID = clientJID;
        this.id = rosterGetIq.getID();
        this.rosterItems = new HashMap<>();
        this.deletedRosterItems = new HashSet<>();
        logger.fine("Populating rosterList with discovered clients");

        for (XMPPClient client : clientManager.getXMPPClients()) {
            Set<String> group = new HashSet<String>(){{add("Discovered");}};
            if( client instanceof LocalXMPPClient ){
                group = new HashSet<String>(){{add("Local");}};
            }
            if( !client.getFullJID().equals(clientJID) ) {
                String displayName = !"".equals(client.getDisplayName()) ?
                        client.getDisplayName():client.getFullJID().toString();
                addRosterItem(client.getBareJID(),displayName , group, "both");
            }
        }

        logger.fine("initial number of rosterItems:"+rosterItems.size());
        initialPresence = true;
    }

    public boolean isInitialPresence(){
        return initialPresence;
    }

    public void setInitialPresence(boolean val){
        this.initialPresence = val;
    }

    /**
     * returns the rosterlist IQ result when requested.  By default, and per the Serverless XMPP paradigm
     *
     * @return an IQ containing the roster result
     */
    public IQ getRosterListIQ(){
        IQ rosterListIq = new IQ(IQ.Type.result);
        rosterListIq.setTo(clientJID);
        rosterListIq.setID(id);
        Element queryElement = rosterListIq.setChildElement("query", "jabber:iq:roster");
        for( RosterItem rosterItem : rosterItems.values() ){
            queryElement.add(rosterItem.getItemElement());

        }

        logger.fine("RosterList IQ: " + rosterListIq);
        return rosterListIq;
    }

    /**
     * Adds this fullJID as a rosterItem. Will replace previous roster item if this key exists already.
     * @param clientJID the jid of the client being added
     * @param name the string name of the client
     * @param groups add the client to this group
     * @param subscriptionType "from", "to", "none", or "both" defined by https://xmpp.org/rfcs/rfc6121.html#roster-syntax-items-subscription
     * @return a roster push IQ with no to field set to be sent to the client's interested parties. Return null if trying to add an already unsubscribed client
     */
    public IQ addRosterItem(JID clientJID, String name, Set<String> groups, String subscriptionType){
        if( deletedRosterItems.contains(clientJID)) {
            // The local client is unsubscribed, return error per RFC 6121 Sec 2.3.3
            return createForbiddenErrorIQ(null);
        }
        RosterItem rosterItem = new RosterItem(clientJID,name,groups, subscriptionType);
        rosterItems.put(clientJID, rosterItem);

        IQ rosterPushIQ = new IQ(IQ.Type.result);
        rosterPushIQ.setTo(this.clientJID);
        Element queryElem = rosterPushIQ.setChildElement("query", "jabber:iq:roster");
        queryElem.add(rosterItem.getItemElement());

        return rosterPushIQ;
    }

    /**
     * convenience method that calls registerClient(fullJID, name, "Local", "both")
     * @param clientJID the client JID to add to the "Local" group
     * @param name the display name
     */
    public IQ registerLocalClient(JID clientJID, String name){
        Set<String> groups = new HashSet<>();
        groups.add("Local");
        return addRosterItem(clientJID, name, groups, "both");
    }

    /**
     * convenience method that calls registerClient(fullJID, name, "Discovered", "both")
     * @param clientJID the client JID to add to the "Local" group
     * @param name the display name
     */
    public IQ registerDiscoveredClient(JID clientJID, String name){
        Set<String> groups = new HashSet<>();
        groups.add("Discovered");
        return addRosterItem(clientJID, name, groups, "both");
    }

    /**
     * Remove subscription to a JID for a specific user
     * @param removedJID the client JID
     * @return a rosterIQ result
     */
    public List<Packet> removeItem(JID removedJID) {
        List<Packet> returnPackets = new LinkedList<>();
        RosterItem item = rosterItems.remove(removedJID);
        deletedRosterItems.add(removedJID);

        IQ removeResultIQ = new IQ(IQ.Type.result);
        removeResultIQ.setTo(this.clientJID);
        returnPackets.add(removeResultIQ);
//        Element queryElem = rosterPushIQ.setChildElement("query", "jabber:iq:roster");
//        queryElem.add(item.getItemElement());

        Presence unsubscribePresence = new Presence(Presence.Type.unsubscribe);
        unsubscribePresence.setFrom(clientJID);
        unsubscribePresence.setTo(removedJID);

        Presence unsubscribedPresence = unsubscribePresence.createCopy();
        unsubscribedPresence.setType(Presence.Type.unsubscribed);

        returnPackets.add(unsubscribePresence);
        returnPackets.add(unsubscribedPresence);

        return returnPackets;
    }

    public void clearAllClients(){
        rosterItems.clear();
        deletedRosterItems.clear();
    }

//    /**
//     *
//     * @param clientJID jid to test
//     * @return true if this client was removed, false otherwise
//     */
//    public boolean wasRemoved(JID clientJID){
//        return deletedRosterItems.contains(clientJID);
//    }

    /**
     *
     * @return set of all members on all lists
     */
    public Set<JID> getAllRosterMemberJIDs(){
        return rosterItems.keySet();
    }

    public JID getClientJID(){
        return clientJID;
    }

    boolean isInRosterList(JID jid){
        return rosterItems.containsKey(jid);
    }

    boolean isClientRemoved(JID jid){
        return deletedRosterItems.contains(jid);
    }

    /**
     * Creates a roster push IQ and updates the rosterList with the fullJID under the groupName group
     * Roster push IQ are generated by the server when a client updates their roster list.
     * This pushIQ goes to the relevante parties.
     *
     * Due to serverless nature, subscription is marked as "both" when a user adds a user to their roster.
     *
     * @param rosterList the rosterList to update
     * @param clientJID the jid of the client
     * @param name name associated with the jid
     * @param groups the groups this client belongs to on this list
     * @param subscriptionType "none", "both", "to", "from"
     * @return a roster push IQ
     */
    public static IQ getRosterPushIQ(RosterList rosterList, JID toJID, JID clientJID, String name, Set<String> groups, String subscriptionType){
        IQ rosterPushIQ;
        rosterPushIQ = rosterList.addRosterItem(clientJID, name, groups, subscriptionType);
        rosterPushIQ.setTo(toJID);
        return rosterPushIQ;
    }

    /**
     * RosterItem private class.
     */
    private class RosterItem {
        String displayName;
        Set<String> groups;
        String subscriptionType;
        JID clientJID;

        RosterItem(JID clientJID, String displayName, Set<String> groups, String subscriptionType){
            this.clientJID = clientJID;
            this.displayName = displayName;
            this.groups = groups;
            this.subscriptionType = subscriptionType;
        }

        Element getItemElement(){
            Element itemElem = new DefaultElement("item");
            itemElem.addAttribute("jid", clientJID+"" );
            itemElem.addAttribute("name", displayName );
            itemElem.addAttribute("subscription", subscriptionType);
            if( groups != null ) {
                for (String group : groups) {
                    Element groupElem = itemElem.addElement("group");
                    groupElem.addText(group);
                }
            }
            return itemElem;
        }
    }

    /**
     * utility method for creating a "Forbidden" Error IQ per RFC 6121 Chap 2.3.3
     * @param toJID the tofield for this IQ. if passed a null, the tofield is not set
     * @return the forbidden error IQ
     */
    public static IQ createForbiddenErrorIQ(JID toJID){
        IQ forbiddenErrorIQ = new IQ(IQ.Type.error);
        if( toJID != null){
            forbiddenErrorIQ.setTo(toJID);
        }
        Element errorElem = new DefaultElement("error");
        errorElem.addAttribute("type","auth");
        errorElem.addElement("forbidden", "urn:ietf:params:xml:ns:xmpp-stanzas");
        forbiddenErrorIQ.setChildElement(errorElem);
        return forbiddenErrorIQ;

    }
}
