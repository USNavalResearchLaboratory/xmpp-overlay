package edu.drexel.transportengine.core.events;

import edu.drexel.transportengine.core.TransportProperties;
import edu.drexel.transportengine.util.packing.Packer;
import edu.drexel.transportengine.util.packing.Unpacker;

import java.io.IOException;

/**
 * This class represents any event that can be handled by the Transport Engine.  It directly specifies addressing
 * information, the transport properties to use, and contains a reference to a <code>EventContents</code> object.
 * This object stores event-specific information.
 * <p/>
 * Events without a destination remain local to the Transport Engine instance.  If the destination and transport
 * properties are set, the protocol manager will attempt to find an appropriate transport layer to send the message.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public final class Event<C extends EventContents> {

    protected boolean responded;
    protected String eventSrc;
    protected String dest;
    protected TransportProperties properties;
    protected String contentType;
    protected C contents;

    /**
     * Instantiates an event for the Transport Engine.
     *
     * @param dest       the destination of the event.  If this is null, the event will remain local.
     * @param properties transport properties of the event.
     * @param contents   event-specific contents.
     */
    public Event(String dest, TransportProperties properties, C contents) {
        this.eventSrc = null;
        this.responded = false;
        this.dest = dest;
        this.properties = properties;
        this.contentType = contents.getName();
        this.contents = contents;
    }

    /**
     * Instantiates an event for the Transport Engine.  This constructor should be used for local events.
     *
     * @param contents event-specific contents.
     */
    public Event(C contents) {
        this(null, null, contents);
    }

    /**
     * Instantiates an event for the Transport Engine from a packed byte array.
     *
     * @param packed event packed as a byte array.
     */
    public Event(byte[] packed) {
        Unpacker up = new Unpacker(packed);
        this.responded = false;
        this.eventSrc = up.readString();
        this.dest = up.readString();
        this.properties = TransportProperties.fromByte(up.readByte(), up.readInt());
        this.contents = (C) EventCreator.createContents(up.readString(), up.getRemainder());
        this.contentType = this.contents.getName();
    }

    /**
     * Gets the content type of the event.
     *
     * @return the content type of the event.
     */
    public String getContentType() {
        return this.contentType;
    }

    /**
     * Sets that this event has been responded to.  This is used to wake up
     * <code>TransportEngine.executeEventAwaitResponse</code> when called on this event.
     */
    public synchronized void setResponded() {
        responded = true;
        notifyAll();
    }

    /**
     * Determines if the event has a response.
     *
     * @return if the event has a response.
     */
    public synchronized boolean hasResponse() {
        return responded;
    }

    /**
     * Gets the transport properties of the event.
     *
     * @return the transport properties of the event.
     */
    public TransportProperties getTransportProperties() {
        return properties;
    }

    /**
     * Gets the destination of the event.
     *
     * @return the destination of the event.
     */
    public String getDest() {
        return dest;
    }

    /**
     * Sets the source of the event.  This should always be set to valid GUID for an active Transport Engine.
     *
     * @param eventSrc the source GUID of the event.
     */
    public void setEventSrc(String eventSrc) {
        this.eventSrc = eventSrc;
    }

    /**
     * Gets the event source.
     *
     * @return the event source.
     */
    public String getEventSrc() {
        return eventSrc;
    }

    /**
     * Gets the event-specific contents.
     *
     * @return event-specific contents.
     */
    public C getContents() {
        return contents;
    }

    /**
     * Packs the event into a byte array for sending over a socket.
     *
     * @return event encoded as a byte array.
     * @throws IOException if the event can not be packed.
     */
    public final byte[] pack() throws IOException {
        Packer p = new Packer();
        p.writeString(eventSrc);
        p.writeString(dest);
        p.writeByte(properties.toByte());
        p.writeInt(properties.persistentUntil);

        byte[] packed = contents.pack();
        if (packed != null) {
            p.writeString(contents.getName());
            p.writeBytes(contents.pack());
        }

        return p.getBytes();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + (this.eventSrc != null ? this.eventSrc.hashCode() : 0);
        hash = 83 * hash + (this.dest != null ? this.dest.hashCode() : 0);
        hash = 83 * hash + (this.contents != null ? this.contents.hashCode() : 0);
        return hash;
    }
}
