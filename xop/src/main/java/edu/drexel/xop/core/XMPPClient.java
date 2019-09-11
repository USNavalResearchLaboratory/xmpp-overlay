package edu.drexel.xop.core;

import edu.drexel.xop.util.CONSTANTS;
import org.dom4j.Element;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * XMPP client object that contains a XOPConnection, client JID, displayed name,
 * Created by duc on 2/27/17.
 */

public class XMPPClient {
    private JID fullJID;
    private JID bareJID;
    private String displayName;
    private String resource;
    private boolean resourceBound;
    private String status;
    private Presence.Show show;
    private NodeStatus nodeStatus;
    private boolean online;
    private Date lastUpdated;
    private String lastId;

    public XMPPClient(JID fullJID,
                      String displayName, String status, Presence.Show show, NodeStatus nodeStatus) {
        this.fullJID = fullJID;
        this.bareJID = new JID(fullJID.toBareJID());
        this.displayName = displayName;
        this.status = status;
        this.show = show;
        this.nodeStatus = nodeStatus;
        this.resourceBound = false;

    }

    public XMPPClient(JID fullJID,
                      String displayName, String resource,
                      String status, Presence.Show show, NodeStatus nodeStatus) {
        this(fullJID, displayName, status, show, nodeStatus);
        this.resource = resource;
        resourceBound = true;
    }


    public JID getFullJID() {
        return fullJID;
    }

    public JID getBareJID() {
        return bareJID;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Presence.Show getShow() {
        return show;
    }

    public void setShow(Presence.Show show) {
        this.show = show;
    }

    public NodeStatus getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(NodeStatus nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    public String getResource() {
        return this.resource;
    }

    public void bindResource(String resource) {
        this.resource = resource;
        resourceBound = true;
    }

    public boolean isResourceBound() {
        return resourceBound;
    }

    public void setResourceBound(boolean resourceBound) {
        this.resourceBound = resourceBound;
    }

    public void setLastPresenceId(String lastId) {
        this.lastId = lastId;
    }

    public String toString(){
        return displayName;
    }

    public void update(Presence presence) {
        setShow(presence.getShow());
        setStatus(presence.getStatus());
        setLastPresenceId(presence.getID());
        nodeStatus = NodeStatus.offline;
        if (presence.isAvailable()) {
            nodeStatus = NodeStatus.online;
        }
    }

    /**
     * Ternary status represents XO Proxy connectivity for each client.
     * - offline: XMPP Client is 'offline' or 'unavailable'. The last presence sent is offline
     * - online: XMPP Client is 'online' and 'available' for chat. The last presence sent was 'available'
     * - disconnected: XMPP is disconnected from the network but the last presence sent was 'available'.
     * Messages will be delivered immediately after being marked as online.
     */
    public enum NodeStatus {
        offline,
        online,
        disconnected
    }

    public Presence generateCurrentPresence() {
        Presence presence = new Presence();
        presence.setFrom(fullJID);
        presence.setStatus(status);
        presence.setShow(show);
        if (lastId != null)
            presence.setID(lastId);
        if (lastUpdated != null) {
            Element element = presence.addChildElement("delay", CONSTANTS.MISC.DELAY_NAMESPACE);
            DateFormat df = new SimpleDateFormat(CONSTANTS.MISC.XMPP_DATETIME_FORMAT, Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            element.addAttribute("stamp", df.format(lastUpdated));
        }
        return presence;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}


