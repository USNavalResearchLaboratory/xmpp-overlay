package edu.drexel.transportengine.components.persistencemanager.util;

/**
 * List of messages to request.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class MessageRequests extends MessageManifest {

    /**
     * Instantiates a new request list associated with a specified algorith,
     *
     * @param algoName algorithm name to associate.
     */
    public MessageRequests(String algoName) {
        super(algoName);
    }

    /**
     * Instantiates a new request list from a packed byte array.
     *
     * @param packed byte array to unpack.
     */
    public MessageRequests(byte[] packed) {
        super(packed);
    }

    /**
     * Gets the name of the event.
     *
     * @return name of the event.
     */
    @Override
    public String getName() {
        return "requests";
    }
}
