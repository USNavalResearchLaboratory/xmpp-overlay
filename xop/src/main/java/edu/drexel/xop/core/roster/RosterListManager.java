package edu.drexel.xop.core.roster;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.ProxyUtils;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Handles the roster lists for all logged in users. This should be used in conjunction with the
 * IqManager when roster requests come in.
 * Created by duc on 6/3/16.
 */
public class RosterListManager {
    private static Logger logger = LogUtils.getLogger(RosterListManager.class.getName());

    private HashMap<JID, RosterList> rosterLists = new HashMap<>();
    // private static RosterListManager instance;
    private ClientManager clientManager;

    /**
     * private constructor for the singleton
     */
    public RosterListManager(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    // /**
    //  *
    //  * @return singleton RosterListManager
    //  */
    // public static RosterListManager instance(){
    //     if( instance == null ){
    //         instance = new RosterListManager();
    //     }
    //     return instance;
    // }


    RosterList createRosterList(IQ rosterGetIq, JID clientJID){
        logger.finer("creating new roster list for "+clientJID);
        RosterList newRoster = new RosterList(rosterGetIq, clientJID, clientManager);
        rosterLists.put(clientJID, newRoster);
        return newRoster;
    }

    /**
     * Called by IqManager for roster list queries on initial login
     * @param rosterGet the roster IQ message
     * @return a RosterList Object
     */
    public RosterList getRosterList(IQ rosterGet) {
        logger.finer("retrieving roster list");
        if( !rosterLists.containsKey(rosterGet.getFrom())){
            return createRosterList(rosterGet, new JID(rosterGet.getFrom().toBareJID()));
        }
        RosterList rl = getRosterList(rosterGet.getFrom());
        if( !rosterGet.getID().equals(rl.getID())){
            // Hack if a user is logging out/logging back in, update the ID field and force
            // the sending of presences of available users to this client
            rl.setID(rosterGet.getID());
            rl.setInitialPresence(true);
        }
        return rl;
    }

    /**
     * Called by PresenceManager for when an inbound subscription message is received from the client
     * @param fromJID the JID of the requesting client
     * @return the RosterList object
     */
    public RosterList getRosterList(JID fromJID){
        return rosterLists.get(new JID(fromJID.toBareJID()));
    }

    /**
     * removes the roster list from the rosterlist manager
     * @param clientJID the jid of the rosterlist to remove
     * @return the RosterList (one last time)
     */
    public RosterList removeRosterList(JID clientJID){
        return rosterLists.remove(clientJID);
    }

    public void addDiscoveredClientToRosterLists(JID discoveredClientJID){
        for( RosterList rosterList : rosterLists.values() ){
            if( !rosterList.isInRosterList(discoveredClientJID)
                    && !rosterList.isClientRemoved(discoveredClientJID) ){
                IQ rosterPushIQ = rosterList.registerDiscoveredClient(discoveredClientJID,
                        discoveredClientJID.toBareJID());
                logger.fine("sending rosterPushIQ to: "+rosterPushIQ.getTo());
                ProxyUtils.sendPacketToLocalClient(rosterPushIQ, clientManager);
            }
        }
    }

}
