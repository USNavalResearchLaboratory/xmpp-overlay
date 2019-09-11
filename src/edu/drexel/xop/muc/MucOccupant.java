package edu.drexel.xop.muc;

import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import edu.drexel.xop.net.discovery.MembershipDiscoverableObject;

/**
 * Represents a room occupant
 * 
 * @author David Millar
 */
public class MucOccupant {

    private JID jid;
    private String resource;
    private MucRoomPresence roomPresence;
    private MembershipDiscoverableObject membershipDiscoverableObject;
    private boolean local;

    public MucOccupant(JID jid, boolean localClient) {
        this.jid = jid;
        this.local = localClient;
    }

    public void setMembershipDiscoverableObject(MembershipDiscoverableObject obj) {
        this.membershipDiscoverableObject = obj;
    }

    public MembershipDiscoverableObject getMembershipDiscoverableObject() {
        return this.membershipDiscoverableObject;
    }

    /**
     * @return the jid
     */
    public JID getJid() {
        return jid;
    }

    /**
     * @param jid
     * the jid to set
     */
    public void setJid(JID jid) {
        this.jid = jid;
    }

    /**
     * @return the resource
     */
    public String getResource() {
        return resource;
    }

    /**
     * @return true if the MUC occupant is on the same host as the xop instance,
     * false otherwise
     */
    public boolean isLocal() {
        return this.local;
    }

    /**
     * This is the presence that was served to the client from the MUC component
     * 
     * @return the roomPresence
     */
    public MucRoomPresence getPresence() {
        return roomPresence;
    }

    /**
     * 
     * @return a copy of the room presence for this MucOccupant
     */
    public Presence getOutgoingPresence() {
        return roomPresence.getPresence().createCopy();
    }

    /**
     * @param roomPresence
     * the roomPresence to set
     */
    public void setPresence(MucRoomPresence roomPresence) {
        this.roomPresence = roomPresence;
    }

    /**
     * @param resource
     * the resource to set
     */
    public void setResource(String resource) {
        this.resource = resource;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("USER: local(").append(local).append(") jid(").append(jid).append(") resource(").append(getResource()).append(")");
        sb.append("\nPresence:").append(roomPresence.getPresence().toString());
        return sb.toString();
    }
}
