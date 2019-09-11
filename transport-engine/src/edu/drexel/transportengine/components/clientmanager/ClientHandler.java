package edu.drexel.transportengine.components.clientmanager;

import edu.drexel.transportengine.components.contentstore.QueryMessageEvent;
import edu.drexel.transportengine.components.protocolmanager.CreateSocketEvent;
import edu.drexel.transportengine.core.TransportProperties;
import edu.drexel.transportengine.core.events.ApplicationMessage;
import edu.drexel.transportengine.core.events.ApplicationMessage.MessageUID;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;
import org.json.simple.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Handles a single client connection.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class ClientHandler extends Thread {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private ClientManagerComponent clientManager;
    private Socket socket;
    private TransportProperties currentProperties;
    private Set<String> subscriptions;
    private boolean isRunning;
    private DataOutputStream output;
    private int clientId;
    private BoundedConcurrentLinkedQueue<VersionedMessageUID> received;

    /**
     * Creates a handler for a client.
     *
     * @param clientManager reference to a <code>ClientManager</code>
     * @param clientId      unique ID for the client.
     * @param socket        established socket to the client.
     */
    public ClientHandler(ClientManagerComponent clientManager, int clientId, Socket socket) {
        this.clientManager = clientManager;
        this.socket = socket;
        this.clientId = clientId;
        subscriptions = new HashSet<>();
        received = new BoundedConcurrentLinkedQueue<>(
                Configuration.getInstance().getValueAsInt(TEProperties.CLIENTMGR_BUFFER_SIZE));
    }

    /**
     * Determine if the client is subscribed to an address.
     *
     * @param address the address to check.
     * @return if the client is subscribed to <code>address</code>.
     */
    private boolean isSubscribed(String address) {
        return subscriptions.contains(address);
    }

    /**
     * Get the client's ID
     *
     * @return the client's ID.
     */
    private int getClientId() {
        return clientId;
    }

    /**
     * Determine if the client is running.
     *
     * @return if the client is running.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Listens for JSON messages from the client and handles each sequentially.
     */
    @Override
    public void run() {
        isRunning = true;
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            while (isRunning && !isInterrupted()) {
                byte[] data = new byte[in.readInt()];
                in.readFully(data);
                try {
                    handleClientCommand(new String(data));
                } catch (APIParseException ex) {
                    sendJSONToClient("error", "Unable to handle API command.");
                    logger.warning("Unable to handle API command: " + ex.getLocalizedMessage());
                }
            }
            socket.close();
        } catch (IOException ex) {
            logger.severe("Error receiving from client.");
            stopClient();
        }
    }

    /**
     * Sends a raw JSON string to the client.
     *
     * @param type the type of message (e.g. "error", "message")
     * @param json the JSON to send.
     */
    private void sendJSONToClient(String type, JSONObject json) {
        JSONObject push = new JSONObject();
        push.put("type", type);
        push.put("info", json);
        try {
            byte[] data = push.toJSONString().getBytes();
            output.writeInt(data.length);
            output.write(data);
        } catch (IOException ex) {
            logger.warning("Error sending to client.  Removing client...");
            stopClient();
        }
    }

    /**
     * Sends a raw JSON string to the client.
     *
     * @param type the type of message (e.g. "error", "message")
     * @param msg  the JSON to send.
     */
    private void sendJSONToClient(String type, String msg) {
        JSONObject json = new JSONObject();
        json.put("message", msg);
        sendJSONToClient(type, json);
    }

    /**
     * Converts an <code>ApplicationMessage</code> event into a JSON object.
     *
     * @param event the <code>ApplicationMessage</code> object.
     * @return the converted JSON object.
     */
    private JSONObject messageToJSON(Event<ApplicationMessage> event) {
        JSONObject json = new JSONObject();
        ApplicationMessage msg = event.getContents();
        json.put("src-engine", msg.getUID().getSrcEngine());
        json.put("src-client", msg.getUID().getSrcClient());
        json.put("id", msg.getUID().getId());
        json.put("dest", event.getDest());
        json.put("version", msg.getVersion());
        json.put("payload", msg.getPayload());

        return json;
    }

    /**
     * Handles commands coming from the client.
     *
     * @param json the JSON sent from the client.
     * @throws APIParseException if the message does not adhere to the Transport Engine's API standard.
     */
    private void handleClientCommand(String json) throws APIParseException {
        JSONReader jer = new JSONReader(json);
        if (!jer.hasValue("command")) {
            throw new APIParseException("No command type specified.");
        }

        String command = jer.asString("command");
        JSONReader args = new JSONReader(jer.asJSON("args"));
        switch (command) {
            case "send":
                handleSend(args);
                break;
            case "set-properties":
                handleSetProperties(args);
                break;
            case "end-session":
                handleEndSession(args);
                break;
            case "subscription":
                handleSubscription(args);
                break;
            case "shutdown":
                handleShutdown(args);
                break;
            default:
                throw new APIParseException("Unknown command '" + command + "'.");
        }
    }

    /**
     * Handles events coming from the Transport Engine.
     *
     * @param event the event.
     */
    public void handleEngineEvent(Event event) {
        if (event.getContents() instanceof ApplicationMessage) {
            if (((ApplicationMessage) event.getContents()).getVersion() > 0 && isSubscribed(event.getDest())) {
                handleApplicationMessage((Event<ApplicationMessage>) event);
            }
        }
    }

    /**
     * Handles an incoming ApplicationMessage event.
     *
     * @param event the event.
     */
    private void handleApplicationMessage(Event<ApplicationMessage> event) {
        ApplicationMessage msg = event.getContents();
        logger.fine("Forwarding msg " + msg.getUID().getId() + " to client " + clientId);
        if (received.add(new VersionedMessageUID(msg.getUID(), msg.getVersion()))) {
            sendJSONToClient(msg.getName(), messageToJSON(event));
        }
    }

    /**
     * Stops the client connection.
     */
    private void stopClient() {
        isRunning = false;
        interrupt();
        logger.fine("Stopping client " + getClientId());
    }

    /**
     * Handles injecting an <code>ApplicationMessage</code> into the Transport Engine. If the message ID has been
     * seen before and is being persisted , the version is bumped.
     * <p/>
     * TODO: Sending non-persistent versioned messages will not work.
     *
     * @param jp the JSON containing the message.
     */
    private void handleSend(JSONReader jp) {
        if (currentProperties == null) {
            sendJSONToClient("error", "Transport properties not yet set.");
        } else if (!isSubscribed(jp.asString("destination"))) {
            sendJSONToClient("error", "Must subscribe to destination before sending.");
        } else {
            logger.fine("Got new message from client " + clientId);
            ApplicationMessage msg = new ApplicationMessage(
                    clientManager.getTransportEngine().getGUID(),
                    String.valueOf(clientId),
                    jp.asString("id"),
                    1,
                    jp.asString("payload"));

            if (currentProperties.persistentUntil > 0) {
                // TODO: This is dependent on a content store and shouldn't be.
                // Local event to query most recent version
                Event<QueryMessageEvent> queryEvent = new Event<>(
                        new QueryMessageEvent(new MessageUID(
                                clientManager.getTransportEngine().getGUID(),
                                String.valueOf(clientId),
                                jp.asString("id"))));

                // Execute the event and wait for the most recent version
                clientManager.getTransportEngine().executeEventAwaitResponse(queryEvent);

                if (queryEvent.getContents().getMessage() != null) {
                    // The same (srcEngine, srcClient, id) has been found.  Bump the version and
                    // populate the new payload.
                    Event<ApplicationMessage> found = queryEvent.getContents().getMessage();
                    msg = new ApplicationMessage(
                            found.getContents().getUID().getSrcEngine(),
                            found.getContents().getUID().getSrcClient(),
                            found.getContents().getUID().getId(),
                            found.getContents().getVersion() + 1,
                            jp.asString("payload"));
                }
            }

            clientManager.getTransportEngine().executeEvent(new Event<>(jp.asString("destination"),
                    currentProperties, msg));
        }
    }

    /**
     * Sets the transport properties for the client connection.
     *
     * @param jp JSON containing properties.
     */
    private void handleSetProperties(JSONReader jp) {
        currentProperties = new TransportProperties(
                jp.asBoolean("reliable"),
                jp.asInt("persistent"),
                jp.asBoolean("ordered"));
    }

    /**
     * Ends the client session gracefully.
     *
     * @param jp JSON containing end session information.
     */
    public void handleEndSession(JSONReader jp) {
        stopClient();
        sendJSONToClient("end-session", "");
    }

    private void handleShutdown(JSONReader jp) {
        clientManager.getTransportEngine().shutdown();
    }

    /**
     * Subscribes or unsubscribes the client fom a given destination.
     *
     * @param jp JSON containing subscription information.
     */
    private void handleSubscription(JSONReader jp) {
        if (jp.asBoolean("subscribe")) {
            subscriptions.add(jp.asString("destination"));
            clientManager.getTransportEngine().executeEvent(new Event<>(new CreateSocketEvent(jp.asString
                    ("destination"))));
        } else {
            // TODO: This should reap unused socket if no clients are subscribed.
            subscriptions.remove(jp.asString("destination"));
        }
    }

    /**
     * Exception thrown when there is an API exception.  Generally this is when there is malformed JSON
     * or an invalid API call.
     */
    public static class APIParseException extends Exception {

        private APIParseException(String exp) {
            super(exp);
        }
    }

    private static class VersionedMessageUID extends MessageUID {
        private int version;

        public VersionedMessageUID(String srcEngine, String srcClient, String id, int version) {
            super(srcEngine, srcClient, id);
            this.version = version;
        }

        public VersionedMessageUID(MessageUID uid, int version) {
            this(uid.getSrcEngine(), uid.getSrcClient(), uid.getId(), version);
        }

        public int getVersion() {
            return version;
        }
    }
}
