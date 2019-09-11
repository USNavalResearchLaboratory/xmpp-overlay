package edu.drexel.transportengine.core;

/**
 * This class defines properties of a message or client connection.  A message can be reliable, persistent,
 * and/or ordered.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class TransportProperties {

    public boolean reliable;
    public int persistentUntil;
    public boolean ordered;

    /**
     * Instantiates a new set of properties.
     *
     * @param reliable        if the properties indicate reliable.
     * @param persistentUntil until what time the properties indicate persistence (0 for not persistent).
     * @param ordered         if the properties indicate ordering.
     */
    public TransportProperties(boolean reliable, int persistentUntil, boolean ordered) {
        this.reliable = reliable;
        this.persistentUntil = persistentUntil;
        this.ordered = ordered;
    }

    /**
     * Duplicates the transport properties with a deep copy.
     *
     * @return copy of the transport properties.
     */
    @Override
    public TransportProperties clone() {
        return new TransportProperties(reliable, persistentUntil, ordered);
    }

    /**
     * Gets the transport properties as a byte.  This is used to calculate port numbers.
     *
     * @return transport properties as a byte.
     */
    public byte toByte() {
        byte r = (byte) (reliable ? 1 : 0);
        byte o = (byte) (ordered ? 1 : 0);
        return (byte) ((r << 1) & o);
    }

    /**
     * Converts byte encoded transport properties into a <code>TransportProperties</code> instance.
     *
     * @param fields       the byte encoding.
     * @param persistUntil until what time the properties indicate persistence (0 for not persistent).
     * @return the <code>TransportProperties</code> instance.
     */
    public static TransportProperties fromByte(byte fields, int persistUntil) {
        return new TransportProperties(
                ((fields >> 1) & 1) == 1,
                persistUntil,
                (fields & 1) == 1);
    }

    @Override
    public String toString() {
        return "Reliable: " + (reliable ? "true" : "false") + "\n"
                + "Ordered: " + (ordered ? "true" : "false") + "\n"
                + "Persist Until: " + persistentUntil + "\n";
    }
}
