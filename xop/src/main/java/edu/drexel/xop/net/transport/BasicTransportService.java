package edu.drexel.xop.net.transport;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.room.Room;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.Packet;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * Basic Transport service using standard multicast
 * Created by duc on 9/16/16.
 */
public class BasicTransportService extends AbstractBasicTransportService {
    private static Logger logger = LogUtils.getLogger(BasicTransportService.class.getName());
    private Room room;

    public BasicTransportService(String ifname, InetAddress group, int port, Room room,
                                 ClientManager clientManager) throws IOException {
        super(ifname, group, port, clientManager);
        this.room = room;
    }

    /**
     * This is called by AbstractBasicTransportService UDP socket and will handle messages for it
     */
    @Override
    public void processIncomingPacket(Packet packet) {
        //logger.info("basic transport sending message to room");
        room.handleIncomingMessage(packet);
        // IncomingPacketProcessor.processPacketForRoom(room.getRoomJid(), packet);
    }
}
