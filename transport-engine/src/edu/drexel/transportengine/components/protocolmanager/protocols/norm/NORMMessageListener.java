package edu.drexel.transportengine.components.protocolmanager.protocols.norm;

import java.net.DatagramPacket;

/**
 * The ... class ...
 * <p/>
 * Created by Ian Taylor Date: Mar 27, 2010 Time: 11:45:27 AM
 */
public interface NORMMessageListener {

    /**
     * @param message the received message.
     */
    public void messageReceived(DatagramPacket message);
}
