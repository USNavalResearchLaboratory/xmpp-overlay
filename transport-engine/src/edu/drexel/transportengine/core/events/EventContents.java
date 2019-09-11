package edu.drexel.transportengine.core.events;

import java.io.IOException;

/**
 * This class encapsulates event-specific information.  Every <code>Event</code> instance contains a subclass of this
 * class.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public abstract class EventContents {

    /**
     * Gets the name of the event contents (e.g. "message").
     *
     * @return the name of the event contents.
     */
    public abstract String getName();

    /**
     * Packs the event contents to be sent over a socket.
     *
     * @return the packed event contents.
     * @throws IOException if the contents can not be packed.
     */
    public byte[] pack() throws IOException {
        return null;
    }
}
