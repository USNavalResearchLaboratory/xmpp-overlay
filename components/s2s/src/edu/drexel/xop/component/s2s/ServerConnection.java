/**
 * (c) 2010 Drexel University
 */
package edu.drexel.xop.component.s2s;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.dom4j.Element;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.XMLLightweightParser;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Establishes a connection with an XMPP server.
 */
public class ServerConnection extends Thread {

    private Socket socket;
    private final PacketRoutingDevice s2sComponent;
    private boolean init = true;
    private boolean run = true;
    private static final Logger logger = LogUtils.getLogger(ServerConnection.class.getName());
    private ServerSetup setup = null;
    private XMLLightweightParser parser = new XMLLightweightParser("UTF-8");
    private XMPPStream stream;
    private final String localDomain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);

    ServerConnection(Socket s, PacketRoutingDevice route) {
        socket = s;
        s2sComponent = route;
    }

    ServerConnection(PacketRoutingDevice route) {
        s2sComponent = route;
    }

    public void init_incoming() {
        setup = new IncomingSetup(this);
        this.setName("Incoming S2S");
    }

    /**
     * 
     * @param name
     * @return
     * @throws FailedSetupException
     */
    public boolean piggyback(String name) throws FailedSetupException {
        if (stream.writable()) {
            boolean test = setup.handle(stream, name);
            logger.log(Level.INFO, "Piggyback was " + (test ? "" : "NOT ") + " SUCCESSFUL");
            return test;
        }
        return false;
    }

    public void init_outgoing(String server) throws IOException {
        if (socket == null) {
            // Utils.DNSEntry entry = Utils.resolveXMPPServerDomain(server, 5269);
            Utils.DNSEntry entry = new Utils.DNSEntry(XopProperties.getInstance().getProperty(XopProperties.S2S_SERVER), XopProperties.getInstance().getIntProperty(XopProperties.S2S_PORT));
            logger.log(Level.INFO, "connection is null, resolved host: " + entry.hostname + ", port:" + entry.port
                + ". creating new socket.");
            socket = new Socket(entry.hostname, entry.port);
        }
        this.setName(server);
        if (XopProperties.getInstance().getProperty(XopProperties.MULTIPLE_GATEWAYS).equals("true")) {
            logger.log(Level.INFO, "S2S initialization: Using multiple gateways.");
            String gatewaySubdomain = XopProperties.getInstance().getProperty(XopProperties.GATEWAY_SUBDOMAIN);
            setup = new OutgoingSetup(gatewaySubdomain + "." + this.localDomain, server, this.getParser());
        } else {
            logger.log(Level.INFO, "S2S initialization: NOT using multiple gateways.");
            setup = new OutgoingSetup(this.localDomain, server, this.getParser());
        }
        try {
            stream = setup.handle(socket.getInputStream(), socket.getOutputStream());
            logger.log(Level.INFO, setup.toString() + " Dialback was " + (stream.valid() ? "" : "NOT ") + "SUCCESSFUL");
        } catch (FailedSetupException ex) {
            logger.log(Level.SEVERE, "FailedSetupException while initializing outgoing connection", ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "IOException while initializing outgoing connection", ex);
        }
        init = false;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[2048];
        Packet p;

        while (run) {
            if (init) {
                try {
                    stream = setup.handle(socket.getInputStream(), socket.getOutputStream());
                    logger.log(Level.INFO, setup.toString() + "Dialback was {" + (stream.valid() ? "" : "NOT ")
                        + "} SUCCESSFUL.");
                } catch (FailedSetupException | IOException ex) {
                    logger.log(Level.SEVERE, "Exception while setting up a " + setup.toString()
                        + " Dialback connection", ex);
                }
                init = false;
                if (stream != null && !stream.valid()) {
                    logger.warning("XMPP Stream is not null and NOT valid, exiting this thread!");
                    break;
                }
            }
            try {
                if (stream != null && !stream.writable()) {
                    // Fill the buffer
                    if (this.getName().startsWith("Thread")) {
                        this.setName("ServerConnectionThread");
                    }
                    int bytesRead = socket.getInputStream().read(buffer);
                    if (bytesRead == -1) {
                        logger.log(Level.SEVERE, "Unable to read from Socket!");
                        return;
                    }
                    parser.read(buffer, 0, bytesRead);

                    // Pass any stanzas we have off to the listener
                    if (parser.areThereMsgs()) {
                        String[] msgs = parser.getMsgs();
                        for (String s : msgs) {
                            logger.log(Level.FINE, "s: " + s);
                            if (s.equals("</stream:stream>")) {
                                continue;
                            }
                            try {
                                Element e = edu.drexel.xop.util.Utils.elementFromString(s);
                                logger.log(Level.FINE, "message as element: " + e.asXML());
                                if (s.startsWith("<db:")) {
                                    logger.log(Level.SEVERE, "Recieved a db, someone is attempting to multiplex me");
                                    String to, from;
                                    to = e.attributeValue("to");
                                    from = e.attributeValue("from");
                                    // id = e.attributeValue("id");
                                    String tmp = "<db:result " + " from='" + to + "'" + " to='" + from + "'"
                                        + " type='valid' />";
                                    stream.setWritable(true);
                                    stream.write(edu.drexel.xop.util.Utils.elementFromString(tmp));
                                    stream.setWritable(false);
                                } else {

                                    p = edu.drexel.xop.util.Utils.packetFromElement(e);
                                    logger.log(Level.FINE, "message as packet:" + p.toString());
                                    s2sComponent.routePacket(p);
                                }
                            } catch (Exception ex) {
                                logger.log(Level.SEVERE, "Catching exception trying to read from stream.", ex);
                            }
                        }
                    }
                } else {
                    break;
                }
            } catch (SocketException se) {
                logger.log(Level.SEVERE, "SocketException caught", se);
                return;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "IOException caught", ex);
                return;
            } catch (Exception ex) {
                logger.log(Level.SEVERE, setup.getClass().getName(), ex);
                return;
            }
        }
    }

    public void sendPacket(Packet packet) throws IOException {
        if (packet instanceof Message) {
            try { // incase tinder throws an exception as it is known to do
                packet.deleteExtension("x", "http://jabber.org/protocol/muc#user");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        stream.write(packet);
    }

    public void close() {
        try {
            logger.log(Level.FINE, "CLOSING ServerConnection");
            socket.close();
            run = false;
        } catch (IOException ex) {
            logger.severe(ex.getLocalizedMessage());
        }
    }

    XMLLightweightParser getParser() {
        return parser;
    }
}