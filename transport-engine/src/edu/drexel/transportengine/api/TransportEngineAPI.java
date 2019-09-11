package edu.drexel.transportengine.api;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides JSON API for interacting with the Transport Engine.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
@SuppressWarnings("unchecked") // This is due to JSONsimple
public class TransportEngineAPI extends Thread {

    private Socket socket;
    private MessageCallback callback;
    private boolean running;

    /**
     * Instantiates an API object.
     *
     * @param address the address of the Transport Engine.
     * @param port    the port of the Transport Engine.
     * @throws IOException if the address cannot be reached.
     */
    private TransportEngineAPI(InetAddress address, int port) throws IOException {
        socket = new Socket(address, port);
    }

    /**
     * Instantiates an API object.
     *
     * @param address the address of the Transport Engine.
     * @param port    the port of the Transport Engine.
     * @throws IOException if the address cannot be reached.
     */
    public TransportEngineAPI(String address, int port) throws IOException {
        this(InetAddress.getByName(address), port);
    }

    /**
     * Listens for JSON messages coming from the Transport Engine and invokes
     * <code>callback.processMessage</code> on each.
     */
    @Override
    public void run() {
        try {

            DataInputStream in = new DataInputStream(socket.getInputStream());
            running = true;
            while (running) {
                byte[] data = new byte[in.readInt()];
                in.readFully(data);
                JSONObject json = (JSONObject) JSONValue.parse(new String(data));

                if (callback != null) {
                    callback.processMessage(json);
                }

                if (json.get("type").equals("end-session") && running) {
                    running = false;
                    socket.close();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(TransportEngineAPI.class.getName()).log(Level.WARNING,
                    "The connection to the Transport Engine has closed.");
        }
    }

    /**
     * Registers a callback method for API messages sent from the Transport Engine.
     *
     * @param callback method to call.
     */
    public void registerMessageCallback(MessageCallback callback) {
        this.callback = callback;
    }

    /**
     * Changes the properties of the API connection.  All messages sent after this call will take on the properties
     * specified.
     *
     * @param reliable   if messages should be sent reliably.
     * @param persistent until what time messages should be persisted.  If this is zero, messages will not be persisted.
     * @param ordered    if messages should be ordered.
     * @throws IOException if the API could not be reached.
     */
    public void executeChangeProperties(boolean reliable, int persistent, boolean ordered) throws IOException {
        JSONObject json = new JSONObject();
        json.put("reliable", reliable ? "true" : "false");
        json.put("persistent", persistent);
        json.put("ordered", ordered ? "true" : "false");
        send("set-properties", json);
    }

    /**
     * Ends the current API session.
     *
     * @throws IOException if the API could not be reached.
     */
    public void executeEndSession() throws IOException {
        running = false;
        send("end-session", new JSONObject());
        //this.socket.shutdownInput();
        //this.socket.shutdownOutput();
    }

    /**
     * Sends a message to a specified destination.  The <code>id</code> must be unique to this session.  If it is not,
     * the Transport Engine will assume the message with that ID has been modified, and will increment the local version
     * number.
     * <p/>
     * Note to send a message, the API must have previously subscribed to the specified destination.
     *
     * @param id          unique ID of the message.
     * @param destination destination of the message.
     * @param payload     contents of the message.
     * @throws IOException if the API could not be reached.
     */
    public void executeSend(String id, String destination, String payload) throws IOException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("destination", destination);
        json.put("payload", payload);
        send("send", json);
    }

    /**
     * Subscribes or unsubscribes to a destination address.
     *
     * @param destination the address to subscribe to or unsubscribe from.
     * @param subscribe   if the subscription should be enabled or disabled.
     * @throws IOException if the API could not be reached.
     */
    public void executeSubscription(String destination, boolean subscribe) throws IOException {
        JSONObject json = new JSONObject();
        json.put("destination", destination);
        json.put("subscribe", subscribe ? "true" : "false");
        send("subscription", json);
    }

    /**
     * Shuts down the Transport Engine.  Use only when absolutely necessary,
     * as other connected clients will also be disconnected.
     *
     * @throws IOException if the API could not be reached.
     */
    public void executeShutdown() throws IOException {
        send("shutdown", new JSONObject());
    }

    /**
     * Sends a JSON message to the Transport Engine
     *
     * @param command the type of command (e.g. "send", "subscription").
     * @param json    the JSON-encoded payload.
     * @throws IOException if the API could not be reached.
     */
    private void send(String command, JSONObject json) throws IOException {
        JSONObject wrapped = new JSONObject();
        wrapped.put("command", command);
        wrapped.put("args", json);

        byte[] data = wrapped.toJSONString().getBytes();
        ByteBuffer bb = ByteBuffer.allocate(data.length + 4);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        bb.putInt(data.length);
        bb.put(data);
        out.write(bb.array());
    }

    /**
     * Message callback interface.  <code>processMessage</code> is called when a Transport Engine JSON message
     * is sent.
     */
    public interface MessageCallback {

        public void processMessage(JSONObject message);
    }
}
