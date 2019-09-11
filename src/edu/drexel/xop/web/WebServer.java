package edu.drexel.xop.web;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.core.PacketListener;
import edu.drexel.xop.core.Stoppable;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

public class WebServer extends Thread implements PacketListener, Stoppable {
    private static final Logger logger = LogUtils.getLogger(WebServer.class.getName());

    private final ConcurrentLinkedQueue<Message> messages;
    private final ConcurrentLinkedQueue<Connection> connections;
    private final ConcurrentHashMap<String, String> resources;
    private boolean stopListening = false;

    ServerSocket listen_socket;
    String httpRootDir;

    public WebServer() {
        this(XopProperties.getInstance().getProperty(XopProperties.WEB_PORT), XopProperties.getInstance().getProperty(XopProperties.WEB_PATH));
    }

    public WebServer(String port, String httpRoot) {
        messages = new ConcurrentLinkedQueue<>();
        connections = new ConcurrentLinkedQueue<>();
        resources = new ConcurrentHashMap<>();
        try {
            int servPort = Integer.parseInt(port);
            httpRootDir = httpRoot;
            listen_socket = new ServerSocket(servPort);
        } catch (IOException e) {
            System.err.println(e);
        }
        this.setName("Webserver");
        this.start();
        ClientProxy.getInstance().addThreadToStop(this);
    }

    public void run() {
        logger.info("WebServer started on port " + XopProperties.getInstance().getProperty(XopProperties.WEB_PORT));
        try {
            while (!stopListening) {
                listen_socket.setSoTimeout(1000);
                try {
                    cleanConnections();
                    Socket client_socket = listen_socket.accept();
                    logger.finer("Connection request received");
                    Connection c = new Connection(client_socket, httpRootDir);
                    connections.add(c);
                    Thread connectionThread = new Thread(c);
                    connectionThread.start();
                    checkForMessages();
                } catch (SocketTimeoutException ste) {
                    // do nothing
                }
            }
        } catch (IOException e) {
            System.err.println(e);
        } catch (Exception e) {
            System.err.println("WebServer Exiting.");
        }
    }

    private void cleanConnections() {
        for (Connection c : connections) {
            if (!c.isRunning()) {
                logger.finer("Removing dead connection");
                connections.remove(c);
            }
        }
    }

    private void checkForMessages() {
        cleanConnections();
        if (!connections.isEmpty() && !messages.isEmpty()) {
            logger.finer("Connection queue has " + connections.size() + "connections");
            for (Connection c : connections) {
                for (Object o : messages.toArray()) {
                    Message m = (Message) o;
                    logger.fine("Sending message to connection: " + m.getBody());
                    c.consume(m);
                }
                logger.finer("Stopping connection");
                c.stop();
            }
            logger.finer("Clearing message queue");
            messages.clear();
        } else {
            logger.finer("Connection queue was empty");
        }
    }

    @Override
    public void processPacket(Packet packet) {
        Packet p = packet.createCopy();
        if (p instanceof Message) {
            // TODO: This is kind of a hack... should check the domain/subdomain too
            Message m = (Message) p;
            if (m.getTo() != null && m.getTo().getResource() == null && m.getFrom().getResource() == null
                && m.getBody() != null) {
                logger.fine("accepted: " + m);
                String resource = resources.get(m.getFrom().toBareJID());
                logger.fine("Resource: " + resource);
                if (resource == null) {
                    resource = "undefined";
                }
                logger.fine("Resource: " + resource);
                m.setFrom(new JID(m.getTo().getNode(), m.getTo().getDomain(), resource));
                logger.fine("adding: " + m);
                messages.add(m); // TODO: This needs to be separated into MUC rooms
                checkForMessages();
            } else {
                logger.fine("rejected: " + p);
            }
        } else if (p instanceof Presence) {
            if (p.getTo() != null && p.getTo().getResource() != null) { // TODO: This is kind of a hack...
                logger.fine("got: " + p);
                resources.put(p.getFrom().toBareJID(), p.getTo().getResource());
            }
        }
    }

    public void stopMe() {
        this.stopListening = true;
    }

}