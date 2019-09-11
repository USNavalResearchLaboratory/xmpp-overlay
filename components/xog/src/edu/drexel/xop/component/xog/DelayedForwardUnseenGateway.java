/*
 * Copyright (C) Drexel University 2012
 */
package edu.drexel.xop.component.xog;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * 
 *
 * @author duc
 *
 * Description: for every msg and presence from the manet, if a packet with the same 
 * id has not been seen within a random value between 0-XX ms, forward that packet.
 */
class DelayedForwardUnseenGateway extends ForwardEverythingGateway {
    private static final Logger logger = LogUtils.getLogger(DelayedForwardUnseenGateway.class.getName());
    
    private int forwardingDelay;
    private Random randomGenerator;
    
    private Map<String,Packet> unseenMap; //msgs that have been seen only once. the message will be removed if it is seen a second time.
    
    public DelayedForwardUnseenGateway(){
        super();
        unseenMap = Collections.synchronizedMap( new HashMap<String,Packet>() );
        forwardingDelay = XopProperties.getInstance().getIntProperty(XopProperties.GATEWAY_FORWARDING_DELAY, 0);
        randomGenerator = new Random();
        logger.info("Initializing DelayedForwardUnseenGateway with delay uniformly random between 0 and "+forwardingDelay);
    }
    
    public boolean accept(Packet p){
        if (!shouldForward(p)) {
            return false;
        }
        
        String id = p.getID();
        if( ((p instanceof Message) && isMessageFromManet(p)) 
            || ((p instanceof Presence) && isPresenceFromManet(p)) )
        {
            if( id != null ){
                if( unseenMap.containsKey(id) ){
                    logger.fine("removing packet "+id+" from unseenMap");
                    unseenMap.remove(id);
                } else{
                    logger.fine("Adding packet to unseenMap and starting delay thread for packet with id: "+id);
                    unseenMap.put(id, p);
                    (new DelayedPacketSenderThread(id,p)).start();
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    
    /**
     *
     * @author duc
     *
     * Description: delays the packet for a random time period.
     */
    private class DelayedPacketSenderThread extends Thread{
        private String id;
        private Packet delayedPacket;
        
        DelayedPacketSenderThread(String id, Packet p){
            setName(id);
            this.id = id;
            this.delayedPacket = p;
        }
        
        public void run(){
            int delay = randomGenerator.nextInt(forwardingDelay);
            logger.fine("starting delay "+delay+"for packet with id: "+id);
            
            
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
            }
            if( unseenMap.containsKey(id) ){
                logger.fine(id+" not already seen, sending");
                processPacket(delayedPacket);
            } else {
                logger.fine(id+" already seen, do nothing");
            }
            unseenMap.remove(id);
            logger.fine("end delay thread id: "+id);
        }
    }
}
