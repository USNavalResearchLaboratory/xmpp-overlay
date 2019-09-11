/**
 * (c) 2010 Drexel University
 */
package edu.drexel.xop.component.xog;

import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import edu.drexel.xop.util.ConcurrentLimitedLinkedQueue;
import edu.drexel.xop.util.logger.LogUtils;

import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * @author urlass
 *
 * This class is like ForwardEverythingGateway, but it only forwards packets that have not come back to it from the ForwardEverythingGateway.  
 * The intention is that there may be multiple gateways, and we want to minimize the number of duplicate forwards
 * by limiting the forwarded packets to only the packets that this object has not yet seen other gateways forward.
 *
 */
class ForwardUnseenGateway extends ForwardEverythingGateway {
	private static final Logger logger = LogUtils.getLogger(ForwardUnseenGateway.class.getName());
	private ConcurrentLimitedLinkedQueue<String> q;
	private static final int Q_SIZE=1000; // number of messages to store for duplicate detection
	
	public ForwardUnseenGateway() {
        super();
        q = new ConcurrentLimitedLinkedQueue<>(Q_SIZE);
    }
	
    /**
     * 
     * This function serves two purposes:
     *      1.  Saving the unique ID of every message that it sees from the server.
     *      2.  Determining if the packet from the MANET is one that we have already seen from the server.
     * 
     * TODO:  	This assumes that XO and the server both use the same prefix for MUCs (e.g., "conference").  This is likely to be 
     * 			true, but this should be re-written to work even if it is not.
     * 
     * ASSUMPTIONS:  	The message will have the same ID on the MANET as when it comes back from the server.  Smack and pidgeon both 
     * 					seem to use the unique ids in the ID field, and OpenFire seems to always return any message it receives with 
     * 					the same ID.  If you use another client or server, this may not be true. 
     * 
     * @param p the packet
     * @return Always returns true in this base class.
     */
    public boolean shouldForward(Packet p){
    	
    	//Save the unique ID of every message coming in from the server
    	 if (p instanceof Message) {
             if (isMessageFromServer(p)) { 
                 String id = p.getID();
                 if(id!=null){
                	 q.add(id);
                     logger.log(Level.FINE, "Added packet from enterprise server to buffer, ID: " + id);
                 }else{
                	 logger.severe("Received a Message packet without an ID!  It's fine if it's a status message from the server, "
                			 + "otherwise this could break the gateway!  Packet:  " + p);
                 }
                 return true;
             }
         } else if (p instanceof Presence) {
             if (isPresenceFromServer(p)) { 
                 String id = p.getID();
                 if(id!=null){
                	 q.add(id);
                	 logger.log(Level.FINE, "Added packet from enterprise server to buffer, ID: " + id);
                 }else{
                	 logger.severe("Received a Presence packet without an ID!  It's fine if it's a status message from the server, "
                			 + "otherwise this could break the gateway!  Packet:  " + p);                	 
                 }
                 return true;
             }
         }
    	 
    	 //if the message is from the MANET, determine if we've already seen it from the server
         if (p instanceof Message) {
             if (isMessageFromManet(p)) {
            	 String id = p.getID();
            	 if(id!=null && q.contains(id)){
            		 logger.log(Level.FINE, "MUC packet arrived that we've already seen from server: " + p);
            		 return false;
            	 }
                 
             }
         } else if (p instanceof Presence) {
             if (isPresenceFromManet(p)) {
            	 String id = p.getID();
            	 if(id!=null && q.contains(id)){
            		 logger.fine("Presence packet arrived that we've already seen from server: " + p);
            		 return false;
            	 }
             }
         }
    	 
    	return true;
    }
}