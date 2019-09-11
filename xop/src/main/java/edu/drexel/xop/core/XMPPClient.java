package edu.drexel.xop.core;

import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.Element;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * XMPP client object that contains client JID, displayed name, status, etc
 * Created by duc on 2/27/17.
 */

public class XMPPClient implements Serializable {
    private static Logger logger = LogUtils.getLogger(XMPPClient.class.getName());
    private JID fullJID;
    private JID bareJID;
    private String displayName;
    private String resource; // TODO: 2019-05-20 Support multiple resources (i.e. entity from 2+ diff resources)
    private boolean resourceBound;
    private String status;
    private Presence.Show show;
    private NodeStatus nodeStatus;
    private boolean online;
    private Date lastUpdated;
    private String lastId;

    // TODO 2019-05-20 Do something with the capabilities XEP-0115
    private class Pair {
        private String hash;
        private String ver;
        private Pair(String hash, String ver) {
            this.hash = hash;
            this.ver = ver;
        }
    }
    private Map<String,Pair> caps;

    public XMPPClient(Presence presence) {
        this(presence.getFrom(), presence.getFrom().toString(), presence.getFrom().getResource(), presence.getStatus(),
        presence.getShow(), NodeStatus.online);

        Element c = presence.getChildElement("c", "http://jabber.org/protocol/caps");
        if( c != null ){
            if( c.attribute("node") != null
                    && c.attribute("ver") != null
                    && c.attribute("hash") != null
            ) {
                caps.put(c.attribute("node").getValue(),
                        new Pair(c.attribute("hash").getValue(), c.attribute("ver").getValue()));
            } else {
                logger.finer("Unable to add capabilities per XEP-0115 c element is null");
            }
        }
    }


    public XMPPClient(JID fullJID,
                      String displayName, String status, Presence.Show show, NodeStatus nodeStatus) {
        this.fullJID = fullJID;
        this.bareJID = new JID(fullJID.toBareJID());
        this.displayName = displayName;
        this.status = status;
        this.show = show;
        this.nodeStatus = nodeStatus;
        this.resourceBound = false;
        this.caps = new HashMap<>();

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

    public void addCapability(String node, String verificationStr, String hash) {
        caps.put(node, new Pair(hash, verificationStr));
    }

    public String getCapabilityVerification(String node) {
        return caps.get(node).ver;
    }

    public Set<String> getCapabilityNodes() {
        return caps.keySet();
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

    /**
     *
     * @return a new Presence based on the status, show, id, last updated (if necessary). NO to: field is set
     */
    public Presence generateCurrentPresence() {
        Presence presence = new Presence();
        if (online) {
            presence.setFrom(fullJID);
            presence.setStatus(status);
            presence.setShow(show);
        } else {
            presence.setType(Presence.Type.unavailable);
        }
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


