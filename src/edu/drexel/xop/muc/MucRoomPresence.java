package edu.drexel.xop.muc;

import java.util.logging.Logger;

import org.dom4j.Element;
import org.xmpp.packet.Presence;

import edu.drexel.xop.util.logger.LogUtils;

/**
 * Presence message container for retrieving join, exit, presence messages of a
 * member of a MUC room.
 * 
 * @author tradams
 */
public class MucRoomPresence {
    private static final Logger logger = LogUtils.getLogger(MucRoomPresence.class.getName());
    private final Presence presence;

    public MucRoomPresence(Presence presence) {
        logger.finest("Creating new MucRoomPresence");
        this.presence = presence.createCopy();
        getMucAttributes(this.presence);
        this.presence.setFrom(presence.getTo());
        this.presence.setTo(presence.getFrom());
    }

    private void addStatusCode(Presence presence, int statusCode) {
        logger.finest("Adding status code " + Integer.toString(statusCode) + " to presence");
        Element e = getMucAttributes(presence);
        e.addElement("status").addAttribute("code", Integer.toString(statusCode));
    }

    public Presence getJoinPresence(boolean first) {
        logger.finest("Getting the joining presence");
        Presence p = presence.createCopy();
        addStatusCode(p, MucProperties.ENTERING_ROOM_CODE);
        if (first) {
            addStatusCode(p, MucProperties.ENTERING_NEW_ROOM_CODE);
        }
        // it's always self referential
        addStatusCode(p, MucProperties.SELF_REFERENTIAL_CODE);
        return p;
    }

    public Presence getExitPresence(boolean self) {
        logger.finest("Getting the 'other' exit presence");
        Presence p = presence.createCopy();
        Element e = getMucAttributes(p);
        p.setType(Presence.Type.unavailable);
        e.element("item").attribute("role").setValue("none");
        if (self) {
            addStatusCode(p, MucProperties.SELF_REFERENTIAL_CODE);
        }
        return p;
    }

    public Presence getPresence() {
        logger.finest("Getting the presence");
        return presence;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("MucRoomPresence: ").append(presence);
        return sb.toString();
    }

    private Element getMucAttributes(Presence presence) {
        logger.finest("Getting MUC attributes (or setting)");
        Element e = presence.getChildElement("x", MucProperties.MUC_USER_NAMESPACE);
        if (e == null) {
            e = presence.addChildElement("x", MucProperties.MUC_USER_NAMESPACE);
            e.addElement("item").addAttribute("affiliation", "member").addAttribute("role", "participant").addAttribute("jid", presence.getTo().toString());
        }
        return e;
    }
}