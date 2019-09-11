package edu.drexel.xop.component.s2s;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.logging.Logger;

import edu.drexel.xop.util.XMLLightweightParser;

public abstract class ConnectionHandler extends Thread {
    private static final Logger logger = Logger.getLogger(IncomingConnectionHandler.class.getName());
    LinkedList<String> msg_buffer = new LinkedList<>();

    protected static void sendMessage(String msg, Socket s) {
        try {
            OutputStream outStream = s.getOutputStream();
            outStream.write(msg.getBytes());
        } catch (IOException e1) {
            logger.severe("Unable to send message to remote server:" + s.getInetAddress().toString());
            e1.printStackTrace();
        }
    }

    /**
     * Wait for a message to come in, and then return it.
     * 
     * @param parser The parser to parse messages with.
     * @param inputStream The input stream that the message should be coming in over.
     * 
     */
    protected void waitForMessage(XMLLightweightParser parser, InputStream inputStream) {
        // wait for a message to be ready
        logger.fine("Waiting for a message.");
        byte[] buffer = new byte[2048];
        int bytesRead = 0;
        try {
            bytesRead = inputStream.read(buffer);
        } catch (IOException e) {
            logger.warning("Error reading incoming socket!");
            e.printStackTrace();
        }
        try {
            parser.read(buffer, 0, bytesRead);
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        while (!parser.areThereMsgs() && msg_buffer.isEmpty()) {
            try {
                sleep(500);
            } catch (InterruptedException e) {
                logger.severe("Error while sleeping, waiting for messages!");
                e.printStackTrace();
            }
        }
        if (parser.areThereMsgs()) {
            for (String msg : parser.getMsgs()) {
                msg_buffer.add(msg);
            }
        }

        logger.fine("Received Message: " + msg_buffer.peekFirst());
    }

    /**
     * Retrieves the dialback key from the
     * 
     * @param msg the dialback key message from which to extract the dialback key
     * @return dialback key
     */
    protected static String getIdFromDbMessage(String msg) {
        // according to the standard, this should work
        // return getField(msg, "id");

        // however, openfire actually does this:
        int start_pos = msg.indexOf(">") + 1;
        int end_pos = msg.indexOf("<", start_pos);
        if (start_pos == -1 || end_pos == -1) {
            logger.severe("Unable to find ID in DB message from server: " + msg);
            return null;
        }
        return msg.substring(start_pos, end_pos);
    }

    /**
     * This should be called if we receive a message we were not expecting.
     * 
     * @param msg The unexpected message.
     */
    protected void handleBadMessage(String msg, Socket sock) {
        logger.warning(msg);
        try {
            sock.close();
        } catch (IOException e) {
            logger.severe("Unable to close socket after receiving invalid input!");
            e.printStackTrace();
        }
    }

    /**
     * Extracts the value of a field from an XML tag. Kind of kludgey.
     * 
     * @param msg the message to extract the field from
     * @param fieldName the name of the field from which to extract the value
     * @return the value of the field, or null if it does not exist
     */
    public static String getField(String msg, String fieldName) {
        int equals_loc = msg.indexOf(fieldName + "=");
        if (equals_loc == -1) {
            logger.severe("Unable to find field in message!");
            return null;
        }
        int begin_loc = msg.indexOf("'", equals_loc);
        boolean use_double = false;
        if (begin_loc == -1 || begin_loc > msg.indexOf("\"", equals_loc)) {
            begin_loc = msg.indexOf("\"", equals_loc);
            use_double = true;
        }
        int end_loc = -1;
        if (use_double) {
            end_loc = msg.indexOf("\"", begin_loc + 1);
        } else {
            end_loc = msg.indexOf("'", begin_loc + 1);
        }
        if (end_loc >= 0 && begin_loc >= 0) {
            String value = msg.substring(begin_loc + 1, end_loc);
            return value;
        }
        return null;
    }
    
    /**
     * empty method to delegate to subclasses to implement. 
     * Will close active connections
     */
    public abstract void closeConnections();
    
}
