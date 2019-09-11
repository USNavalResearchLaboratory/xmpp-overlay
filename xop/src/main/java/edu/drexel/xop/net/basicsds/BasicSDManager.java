package edu.drexel.xop.net.basicsds;

import edu.drexel.xop.net.SDListener;
import edu.drexel.xop.net.SDManager;
import edu.drexel.xop.room.Room;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.net.InetAddress;

/**
 * SDS system based on BasicTransport
 * Created by duc on 10/15/17.
 */

public class BasicSDManager implements SDManager {
    public BasicSDManager(String bindInterface, InetAddress sdsGroup, int sdsPort) {

    }

    @Override
    public void start() {

    }

    @Override
    public void close() {

    }

    @Override
    public void addGateway(InetAddress address, JID domain) {

    }

    @Override
    public void removeGateway(JID domain) {

    }

    @Override
    public void advertiseClient(Presence presence) {

    }

    @Override
    public void removeClient(Presence presence) {

    }

    @Override
    public void updateClientStatus(Presence presence) {

    }

    @Override
    public void advertiseMucOccupant(Presence presence) {
        // JID clientJID, JID mucOccupantJID
    }

    @Override
    public void removeMucOccupant(Presence presence) {
        // JID mucOccupantJID
    }

    @Override
    public void updateMucOccupantStatus(Presence presence) {

    }

    @Override
    public void addSDListener(SDListener listener) {

    }

    @Override
    public void advertiseMucRoom(Room room) {

    }

    @Override
    public void removeMucRoom(Room room) {

    }
}
