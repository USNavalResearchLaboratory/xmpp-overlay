/*
 * Copyright (C) Drexel University 2012
 */
package edu.drexel.xop.presence;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import mil.navy.nrl.protosd.api.exception.ServiceLifeCycleException;

import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import edu.drexel.xop.client.ClientManager;
import edu.drexel.xop.client.RemoteClient;
import edu.drexel.xop.component.ComponentBase;
import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.api.DiscoverableUserListener;
import edu.drexel.xop.net.discovery.SDXOPObject;
import edu.drexel.xop.net.discovery.UserDiscoverableObject;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * @author duc
 * <p/>
 * Description: Will listen on for UserDiscoverableObjects from the SD system and generate available or unavailable presence messages
 * @date Mar 20, 2012
 */
public class SDPresenceManager extends ComponentBase implements DiscoverableUserListener {
    private static final Logger logger = LogUtils.getLogger(SDPresenceManager.class.getName());

    // bareJID is the key
    private Map<String, UserDiscoverableObject> userDiscoverableObjects = Collections.synchronizedMap(new HashMap<String, UserDiscoverableObject>());
    private SDXOPObject sdObject;
    private ClientManager clientManager;

    public SDPresenceManager(ClientManager clientManager) {
        sdObject = XopNet.getSDObject();
        sdObject.addDiscoverableUserListener(this);
        this.clientManager = clientManager;
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.api.DiscoverableUserListener#userAdded(edu.drexel.xop.net.discovery.UserDiscoverableObject)
     */
    @Override
    public void userAdded(UserDiscoverableObject user) {
        // From the SD system, generate an available presence message and send it to the clientProxy.processPacket() method

        String jidStr = user.getJid();
        logger.fine("User added: " + jidStr);
        if (!clientManager.isLocalClient(jidStr)) {
            // construct a new initial presence message
            Presence initialPresence = new Presence();
            initialPresence.setFrom(new JID(jidStr));
            String msg = (user.getMsg() == null || user.getMsg().equals("")) ? user.getStatus() : user.getMsg();
            initialPresence.setStatus((msg == null) ? "avail" : msg);

            clientManager.addRemoteClient(new RemoteClient(user));
            userDiscoverableObjects.put(jidStr, user);

            logger.fine("Sending initialPresence to PacketManager");
            ClientProxy.getInstance().processPacket(initialPresence);
        } else {
            logger.fine("Discovered user is a local client: " + jidStr);
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.net.api.DiscoverableUserListener#userRemoved(edu.drexel.xop.net.discovery.UserDiscoverableObject)
     */
    @Override
    public void userRemoved(UserDiscoverableObject user) {
        logger.fine("removing user object: " + user);

        if (userDiscoverableObjects.containsKey(user.getJid())) {
            logger.fine("Removing UDO and sending unavailable presence messages.");
            sendUnavailablePresences(user.getJid());
            userDiscoverableObjects.remove(user.getJid());
        } else {
            logger.fine("User already removed from map of userDiscoverableObjects.");
        }

        // From the SD system, generate the unavailable presence message and send it to the clientProxy.processPacket() method
        if (!clientManager.isLocalClient(user.getJid())) { // a remote client
            logger.fine("REMOVEME: remote client!");
            clientManager.removeRemoteClient(user.getJid());
        }
    }

    private void sendUnavailablePresences(String fromJID) {
        logger.fine(fromJID + " closed the stream. Sending unavailable presence.");
        Presence unavailPresence = new Presence();
        unavailPresence.setType(Presence.Type.unavailable);
        unavailPresence.setFrom(fromJID);
        ClientProxy.getInstance().processPacket(unavailPresence);
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.component.VirtualComponent#processPacket(org.xmpp.packet.Packet)
     */
    @Override
    public void processPacket(Packet p) {
        Presence presencePkt = (Presence) p;

        // coming from the local system (check that the from field is a local client)
        String fromBareJid = presencePkt.getFrom().toBareJID();
        // only advertise initial presence messages. i.e. no TO field and no type field (per XMPP 6121)
        if (presencePkt.isAvailable() && (presencePkt.getTo() == null) && (presencePkt.getType() == null)) {
            // Send service discovery only once
            try {
                logger.fine("an Available Presence was received from " + fromBareJid
                    + ". Retrieving the user discoverable object.");

                UserDiscoverableObject user = userDiscoverableObjects.get(fromBareJid);
                if (user == null) {
                    logger.log(Level.FINE, "userDiscoverableObject does not exist, creating... ");
                    String resource = presencePkt.getFrom().getResource();
                    String node = presencePkt.getFrom().getNode();
                    String nick = (resource == null) ? node : resource;
                    String status = (presencePkt.getStatus() == null) ? "avail" : presencePkt.getStatus();
                    String ver = (presencePkt.getID() == null) ? "1" : presencePkt.getID(); // TODO: 2012-03-27 need to generate version based on XEP-0115
                    // TODO: 2012-03-27 need to define hashing function based on XEP-0115
                    user = new UserDiscoverableObject(sdObject, nick, fromBareJid, node, status, ver, "firstName", "email@example.com", "lastName", "msg");
                    userDiscoverableObjects.put(fromBareJid, user);
                    logger.fine("Added new user to map of SD objects. user:" + user);
                    sdObject.advertise(user);
                    logger.fine("Advertised a new UserDiscoverableObject for bareJid:" + fromBareJid);
                } else {
                    logger.log(Level.FINE, "user," + user.getNick()
                        + " already exists in map of userDiscoverableObjects. NOT advertising.");
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "IOException found while advertising: ", e);
            } catch (InitializationException e) {
                logger.log(Level.WARNING, "InitializationException found while advertising: ", e);
            } catch (ServiceInfoException e) {
                logger.log(Level.WARNING, "ServiceInfoException found while advertising: ", e);
            } catch (ServiceLifeCycleException e) {
                logger.log(Level.WARNING, "ServiceLifeCycleException found while advertising: ", e);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Exception caught trying to create and advertise a UserDiscoverableObject", e);
                e.printStackTrace();
            }
        } else if ((presencePkt.getType() == Presence.Type.unavailable) && (presencePkt.getTo() == null)) {
            logger.fine(fromBareJid + " sent an Unavailable Presence message, canceling the advert.");
            removeFromSDSystem(fromBareJid);
        } else {
            logger.fine("NOT processing this presence packet: " + presencePkt.toXML());
        }
    }

    /**
     * remove the SD advert associated with the given jidStr.
     * 
     * @param jidStr
     */
    private void removeFromSDSystem(String jidStr) {
        // Remove the SD advert from the system
        try {
            UserDiscoverableObject user = userDiscoverableObjects.remove(jidStr);
            if (user != null) {
                sdObject.remove(user);
            } else {
                logger.warning("UserDiscoverableObject for " + jidStr + " not available, unable to cancel advert");
            }
        } catch (ServiceLifeCycleException e) {
            logger.log(Level.WARNING, "ServiceLifeCycleException found while advertising: ", e);
        } catch (ServiceInfoException e) {
            logger.log(Level.WARNING, "ServiceInfoException found while advertising: ", e);
        }
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.core.PacketFilter#accept(org.xmpp.packet.Packet)
     */
    @Override
    public boolean accept(Packet packet) {
        if (packet instanceof Presence) {
            Presence presencePkt = (Presence) packet;
            // Accept available|unavailable presence messages from local clients to create|remove the UDO
            if (clientManager.isLocalClient(presencePkt.getFrom().toBareJID())
                && (presencePkt.isAvailable() || (presencePkt.getType() == (Presence.Type.unavailable)))
                && (presencePkt.getTo() == null)) {
                return true;
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * @see edu.drexel.xop.component.ComponentBase#processCloseStream(java.lang.String)
     */
    @Override
    public void processCloseStream(String fromJID) {
        logger.fine("Attempting to processCloseStream for " + fromJID);
        removeFromSDSystem(fromJID);
    }
}