package edu.drexel.xop.client;

import java.util.Vector;
import java.util.logging.Logger;

import org.xmpp.packet.Packet;

import edu.drexel.xop.core.PacketListener;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Manages multiple PacketListeners
 * 
 * @author di
 */
public class PacketManager {
    private static final Logger logger = LogUtils.getLogger(PacketManager.class.getName());

    private Vector<PacketListener> packetListeners = new Vector<PacketListener>();

    public void addPacketListener(PacketListener listener) {
        logger.fine("adding PacketListener: " + listener.toString());
        packetListeners.add(listener);
    }

    /**
     * Calls processPacket on all added PacketListeners
     * 
     * @param p
     */
    public void processPacket(Packet p) {
        if (p == null) {
            logger.warning("received a null packet!");
        } else {
            logger.fine("processing packet: " + p.toString());
            for (PacketListener pl : packetListeners) {
                pl.processPacket(p);
            }
        }
    }
}