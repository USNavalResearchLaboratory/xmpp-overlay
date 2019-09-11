package edu.drexel.xop.component.xog;

import java.net.InetAddress;

import org.xmpp.packet.Packet;

import edu.drexel.xop.net.XopNet;

/**
 * This class periodically elects a leader to do all of the gatewaying. It uses the MaxBestID
 * protocol described in Lynch's "Distributed Algorithms" book, with the least significant byte
 * of its IP Address as a GUID.
 * 
 * Since we have no way of knowing which gateways are in our MANET cluster at any given point in
 * time, it will recompute the leader every X seconds (configurable), by sending a message,
 * determining the MaxBestID, and then clearing the cache of read messages.
 * 
 * @author urlass
 * 
 */
public class ElectedLeaderGateway extends ForwardEverythingGateway {

    protected boolean amGateway = true;

    public ElectedLeaderGateway() {
        // spin off a thread to join the chatroom and get updates
        InetAddress i_addr = XopNet.getInstance().getBindAddress();
        String addr = i_addr.toString();
        addr = addr.substring(addr.indexOf(".")); // without first byte
        addr = addr.substring(addr.indexOf(".")); // without the second byte
        addr = addr.substring(addr.indexOf(".") + 1); // without the third byte and without period
        LeaderElectionCommunicator lec = new LeaderElectionCommunicator(Integer.parseInt(addr), this);
        lec.start();
    }

    public boolean shouldForward(Packet p) {
        return amGateway;
    }

    public void setAmGateway(boolean amGateway) {
        this.amGateway = amGateway;
        if (amGateway) {
            logger.fine("I am the gateway.");
        } else {
            logger.fine("I am NOT the gateway.");
        }
    }
}
