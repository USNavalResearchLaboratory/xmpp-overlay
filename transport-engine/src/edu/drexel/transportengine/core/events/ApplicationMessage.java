package edu.drexel.transportengine.core.events;

import edu.drexel.transportengine.util.packing.Packer;
import edu.drexel.transportengine.util.packing.Unpacker;

import java.io.IOException;
import java.util.Objects;

/**
 * This class represents a message sent from a client to the Transport Engine.  This is uniquely defined by a
 * <code>MessageUID</code>.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class ApplicationMessage extends EventContents {

    private MessageUID uid;
    private int version;
    private String payload;

    /**
     * Instantiates a new application message.
     *
     * @param srcEngine the source Transport Engine GUID.
     * @param srcClient the source client ID.
     * @param id        the message ID.
     * @param version   the message version.
     * @param payload   the message payload.
     */
    public ApplicationMessage(String srcEngine, String srcClient, String id, int version, String payload) {
        this.uid = new MessageUID(srcEngine, srcClient, id);
        this.version = version;
        this.payload = payload;
    }

    /**
     * Instantiates a new application message from a packed byte array.
     *
     * @param packed event packed as a byte array.
     */
    public ApplicationMessage(byte[] packed) {
        Unpacker up = new Unpacker(packed);
        this.uid = new MessageUID(up.readString(), up.readString(), up.readString());
        this.version = up.readInt();
        this.payload = up.readString();
    }

    /**
     * Gets the GUID of the source.
     *
     * @return GUID of the source.
     */
    public MessageUID getUID() {
        return uid;
    }

    /**
     * Gets the version of the message.
     *
     * @return the version of the message.
     */
    public int getVersion() {
        return version;
    }

    /**
     * Gets the payload of the message.
     *
     * @return the payload of the message.
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Gets the name of the message contents.
     *
     * @return the name of the message contents.
     */
    @Override
    public String getName() {
        return "message";
    }

    /**
     * Packs the event into a byte array for sending over a socket.
     *
     * @return event encoded as a byte array.
     * @throws IOException if the event can not be packed.
     */
    @Override
    public byte[] pack() throws IOException {
        Packer p = new Packer();
        p.writeString(uid.getSrcEngine());
        p.writeString(uid.getSrcClient());
        p.writeString(uid.getId());
        p.writeInt(version);
        p.writeString(payload);

        return p.getBytes();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(uid.getSrcEngine());
        hash = 29 * hash + Objects.hashCode(uid.getSrcClient());
        hash = 29 * hash + Objects.hashCode(uid.getId());
        return hash;
    }

    /**
     * This class contains UID information which globally identifies a message.
     */
    public static class MessageUID {

        private String srcEngine;
        private String srcClient;
        private String id;

        /**
         * Instantiates a new <code>MessageUID</code>
         *
         * @param srcEngine the GUID of source Transport Engine.
         * @param srcClient the UID of source client.
         * @param id        the ID of the message.
         */
        public MessageUID(String srcEngine, String srcClient, String id) {
            this.srcEngine = srcEngine;
            this.srcClient = srcClient;
            this.id = id;
        }

        /**
         * Gets the UID of the source Transport Engine.
         *
         * @return the UID of the source Transport Engine.
         */
        public String getSrcEngine() {
            return srcEngine;
        }

        /**
         * Gets the ID of the source client.
         *
         * @return the ID of the source client.
         */
        public String getSrcClient() {
            return srcClient;
        }

        /**
         * Gets the message ID.
         *
         * @return the message ID.
         */
        public String getId() {
            return id;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + Objects.hashCode(this.srcEngine);
            hash = 97 * hash + Objects.hashCode(this.srcClient);
            hash = 97 * hash + Objects.hashCode(this.id);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MessageUID) {
                MessageUID m = (MessageUID) obj;
                return m.getSrcEngine().equals(getSrcEngine())
                        && m.getSrcClient().equals(getSrcClient())
                        && m.getId().equals(getId());
            }
            return false;
        }
    }
}
