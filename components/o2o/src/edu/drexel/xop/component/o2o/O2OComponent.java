package edu.drexel.xop.component.o2o;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import edu.drexel.xop.component.ComponentBase;
import edu.drexel.xop.interceptor.PacketInterceptor;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.api.DiscoverableUserListener;
import edu.drexel.xop.net.discovery.UserDiscoverableObject;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * 
 * @author urlass
 * 
 * Intercepts packets that are one-to-one. If they are MUC PMs, it re-writes the to and from fields as described in XEP-0045.
 * 
 * This class is not currently working.
 * 
 */
public class O2OComponent extends ComponentBase implements PacketInterceptor, DiscoverableUserListener {

    private static final Logger logger = LogUtils.getLogger(O2OComponent.class.getName());
    protected HashMap<String, InetAddress> jidToIpAddress = new HashMap<>();
    protected HashMap<String, Integer> jidToPort = new HashMap<>();
    protected HashMap<String, OutputStreamWriter> jidToOstream = new HashMap<>();
    private String domain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);

    @Override
    public void initialize() {
        super.initialize();

        // register with the relevant components
        super.packetRouter.addPacketInterceptor(this);
        XopNet.getSDObject().addDiscoverableUserListener(this);

        // kick off a listener for remote incoming one-to-one connections
        IncomingOneToOneListener iotol = new IncomingOneToOneListener();
        iotol.start();
    }

    public boolean interceptPacket(Packet p) {
        boolean retVal = true;
        if (p instanceof Message) {
            Message msg = (Message) p;
            if (msg.getType() == Message.Type.chat) { // this is a one-to-one message
                retVal = false; // don't want other handlers / the packetrouter to process this

                // TODO: add checking code to make sure there is a valid "to" and "from" field in the packet

                // if we have a mapping for this jid, let's process it
                if (jidToIpAddress.containsKey(msg.getTo().toString())) {
                    logger.fine("Got a message for " + msg.getTo());
                    // if it's local, send it to the user directly
                    try { // this looks weird, but isLinkLocalAddress() was returning false, so this is my hack to make it work
                        if (jidToIpAddress.get(msg.getTo().toString()).equals(InetAddress.getByName("127.0.0.1"))) {
                            logger.fine("Link local address, sending message to user.");
                            return true; // packet router will give this to the user by default
                        }
                    } catch (UnknownHostException e) {
                        logger.severe("Error converting 127.0.0.1 into an InetAddress!");
                        return true; // in the unlikely event we get here, we'll let the packet router deal with this packet
                    }

                    // if it's remote, send the message out over the network to all of the logged in instances of this bare JID

                    // Create the socket connection if it doesn't exist
                    if (jidToOstream.containsKey(p.getTo().toString())) {
                        InetAddress remote_addr = jidToIpAddress.get(p.getTo().toString());
                        int remote_port = jidToPort.get(p.getTo().toString());

                        Socket connection = null;
                        // open a socket
                        try {
                            connection = new Socket(remote_addr, remote_port);
                        } catch (IOException e) {
                            e.printStackTrace();
                            logger.severe("Could not connect to remote host for one-to-one chat!");
                        }
                        BufferedOutputStream bos = null;
                        try {
                            bos = new BufferedOutputStream(connection.getOutputStream());
                        } catch (IOException e) {
                            e.printStackTrace();
                            logger.severe("Couldn't open BufferedOutputStream to " + p.getTo());
                        }
                        OutputStreamWriter osw = new OutputStreamWriter(bos);
                        jidToOstream.put(p.getTo().toString(), osw);
                    }

                    // send the message to remote user
                    OutputStreamWriter osw = jidToOstream.get(p.getTo().toString());
                    try {
                        osw.write(p.toString());
                    } catch (IOException e) {
                        logger.severe("Couldn't write message to " + p.getTo() + " using OutputStreamWriter!");
                        e.printStackTrace();
                    }
                } else {
                    logger.fine("Not processing because " + msg.getTo()
                        + " is not in my list of known users.  My knows users are:");
                    for (String jid : jidToIpAddress.keySet()) {
                        logger.fine("     " + jid);
                    }
                }
            }
        }
        return retVal;
    }

    @Override
    public void processPacket(Packet p) {
        // all the real work should have been done in interceptPacket()
        if (p instanceof Message && ((Message) p).getType() == Message.Type.chat) {
            logger.warning("This function shouldn't be called.");
        }
    }

    public String toString() {
        return "OneToOneChatInterceptor";
    }

    @Override
    public void userAdded(UserDiscoverableObject discoverableObject) {
        if (discoverableObject instanceof UserDiscoverableObject) {
            try {
                jidToIpAddress.put((discoverableObject).getJid(), InetAddress.getByName((discoverableObject).getServiceAddress()));
                logger.fine("Added address mapping of " + (discoverableObject).getJid() + " to "
                    + (discoverableObject).getServiceAddress());
            } catch (UnknownHostException e) {
                logger.severe("Unable to understand advertised service address: "
                    + (discoverableObject).getServiceAddress());
            }
            jidToPort.put((discoverableObject).getJid(), (discoverableObject).getServicePort());
            logger.fine("Added port mapping of " + (discoverableObject).getJid() + " to "
                + (discoverableObject).getServicePort());
        }
    }

    /**
     * Removes all info about this user.
     * 
     * @param discoverableObject The UDO object for the user being removed.
     */
    @Override
    public void userRemoved(UserDiscoverableObject discoverableObject) {
        jidToIpAddress.remove(discoverableObject.getJid());
    }

    @Override
    public boolean accept(Packet p) {
        try {
            if (p.getTo() != null) {
                if (domain.equals((p.getTo().getDomain()))) {
                    logger.log(Level.FINE, "Accepted a packet");
                    return true;
                } else {
                    logger.log(Level.FINE, "Rejected packet (this.domain=" + domain + ", p.getTo().getDomain()="
                        + p.getTo().getDomain());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}