package edu.drexel.transportengine.components.ddm;

import edu.drexel.transportengine.core.events.EventContents;
import edu.drexel.transportengine.util.packing.Packer;
import edu.drexel.transportengine.util.packing.Unpacker;

import java.io.IOException;

/**
 * A class encapsulating a DDM message.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class DDMMessage extends EventContents {

    private String id;
    private String type;
    private String value;

    /**
     * Instantiates a DDM message with given parameters.
     *
     * @param id    the ID of the Transport Engine.
     * @param type  the type of DDM message.
     * @param value the value of the DDM message.
     */
    public DDMMessage(String id, String type, String value) {
        this.id = id;
        this.type = type;
        this.value = value;
    }

    /**
     * Instantiates a DDM message from a packed byte array.
     *
     * @param packed byte array encoding a <code>DDMMessage</code>.
     */
    public DDMMessage(byte[] packed) {
        Unpacker up = new Unpacker(packed);
        this.id = up.readString();
        this.type = up.readString();
        this.value = up.readString();
    }

    /**
     * Gets the name of this event type.
     *
     * @return name of this event type.
     */
    @Override
    public String getName() {
        return "ddm-message";
    }

    /**
     * Gets the name of the DDM message.
     *
     * @return the name of the DDM message.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the type of the DDM message.
     *
     * @return the type of the DDM message.
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the value of the DDM message.
     *
     * @return the value of the DDM message.
     */
    public String getValue() {
        return value;
    }

    /**
     * Packs the DDM message into a byte array for sending over a socket.
     *
     * @return packed DDM message.
     * @throws IOException if the message cannot be packed.
     */
    @Override
    public byte[] pack() throws IOException {
        Packer p = new Packer();
        p.writeString(id);
        p.writeString(type);
        p.writeString(value);

        return p.getBytes();
    }
}
