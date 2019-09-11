package edu.drexel.xop.gateway;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Extracted Gateway Ping Thread
 * Created by duc on 8/10/16.
 */

class GatewayPing extends Thread {
    private static Logger logger = LogUtils.getLogger(GatewayPing.class.getName());
    private GatewayConnection gatewayConnection;
    private String to;
    private String from;
    private Set<String> pingIds;
    private Set<String> pongIds;

    GatewayPing(GatewayConnection gatewayConnection, String to, String from) {
        this.gatewayConnection = gatewayConnection;
        this.to = to;
        this.from = from;
        pingIds = Collections.synchronizedSet(new HashSet<String>());
        pongIds = Collections.synchronizedSet(new HashSet<String>());
    }

    public void run() {
        // try {
        //     Thread.sleep(XOP.GATEWAY.PING);
        // } catch (InterruptedException e) {
        //     logger.severe("Unable to make thread sleep: " + e.getMessage());
        // }

        while (!gatewayConnection.killSwitch.get()) {
            String id = Utils.generateID(9);
            String pingMessage = Utils.getPingMessageString(to, from, id);
            if(logger.isLoggable(Level.FINE))
                logger.fine("Send ping over gateway connection, " + gatewayConnection
                        + ", id:" + id);
            pingIds.add(id);
            try {
                gatewayConnection.writeRaw(pingMessage.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(XOP.GATEWAY.PING);
            } catch (InterruptedException e) {
                logger.severe("Unable to make thread sleep: " + e.getMessage());
            }
            if( pongIds.contains(id) ){
                // not received a ping response
                pingIds.remove(id);
                pongIds.remove(id);
                logger.fine("Received pong with id: "+id);
                logger.fine("pongIds size: "+pongIds.size());
            } else {
                logger.fine("Not received pong with id: "+id);
                int maxPongSet = 4;
                if( pongIds.size() >= maxPongSet){
                    logger.fine("pongIds.size() greater than "+maxPongSet+ "stopping connection");
                    gatewayConnection.stop();

                }
            }

        }
        logger.info("Gateway ping Ending. from: " + from + " to: " + to);
    }

    /**
     *
     * @param id the id of the iq result
     */
    void handlePing(String id){
        if(logger.isLoggable(Level.FINE))
            logger.fine("adding "+id+" to pongIds of size "+pongIds.size());
        pongIds.add(id);
    }

}
