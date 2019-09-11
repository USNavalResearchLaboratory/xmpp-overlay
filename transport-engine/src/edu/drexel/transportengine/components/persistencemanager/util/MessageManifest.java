package edu.drexel.transportengine.components.persistencemanager.util;

import edu.drexel.transportengine.core.events.ApplicationMessage;
import edu.drexel.transportengine.core.events.ApplicationMessage.MessageUID;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.core.events.EventContents;
import edu.drexel.transportengine.util.packing.Packer;
import edu.drexel.transportengine.util.packing.Unpacker;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Manifest of messages used for persistence algorithms.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class MessageManifest extends EventContents {
    private List<MessageEntry> messages;
    private String algoName;

    /**
     * Instantiates a new manifest associated with a specified algorith,
     *
     * @param algoName algorithm name to associate.
     */
    public MessageManifest(String algoName, List<Event<ApplicationMessage>> entries) {
        this(algoName);
        if (entries.size() > 0) {
            // TODO: Should use a function for this, but casting toArray throws an Exception.
            //addEntriesByEvents((Event<ApplicationMessage>[]) entries.toArray());
            for (Event<ApplicationMessage> e : entries) {
                addEntries(new MessageEntry(e));
            }
        }
    }

    /**
     * Instantiates a new manifest associated with a specified algorith,
     *
     * @param algoName algorithm name to associate.
     */
    public MessageManifest(String algoName) {
        this.messages = new LinkedList<>();
        this.algoName = algoName;
    }

    /**
     * Instantiates a new manifest from a packed byte array.
     *
     * @param packed byte array to unpack.
     */
    public MessageManifest(byte[] packed) {
        this.messages = new LinkedList<>();
        Unpacker up = new Unpacker(packed);
        short len = up.readShort();
        this.algoName = up.readString();
        for (int i = 0; i < len; i++) {
            String engine = up.readString();
            String client = up.readString();
            String id = up.readString();
            int version = up.readInt();
            addEntries(new MessageEntry(new MessageUID(engine, client, id), version));
        }
    }

    /**
     * Gets the entries in the manifest.
     *
     * @return entries in the manifest.
     */
    public List<MessageEntry> getEntries() {
        return messages;
    }

    /**
     * Adds entries to the manifest.
     *
     * @param add entry to add.
     */
    public final void addEntriesByEvents(Event<ApplicationMessage>... add) {
        for (Event<ApplicationMessage> e : add) {
            addEntries(new MessageEntry(e));
        }
    }

    /**
     * Adds entries to the manifest.
     *
     * @param add entry to add.
     */
    public final void addEntries(MessageEntry... add) {
        Collections.addAll(messages, add);
    }

    /**
     * Gets the name of the event.
     *
     * @return name of the event.
     */
    @Override
    public String getName() {
        return "manifest";
    }

    /**
     * Gets the algorithm name associated with the manifest.
     *
     * @return algorithm name associated with the manifest.
     */
    public String getAlgorithmName() {
        return algoName;
    }

    public List<MessageManifest> fragmentManifest(int maxSize) {
        List<MessageManifest> fragments = new LinkedList<>();
        for (int i = 0; i < messages.size(); i += maxSize) {
            MessageManifest m = new MessageManifest(getAlgorithmName());
            // TODO: Should use a function for this, but casting toArray throws an Exception.
            //m.addEntries((MessageEntry[]) messages.subList(i, Math.min(messages.size(), i + maxSize)).toArray());
            for (MessageEntry e : messages.subList(i, Math.min(messages.size(), i + maxSize))) {
                m.addEntries(e);
            }
            fragments.add(m);
        }
        return fragments;
    }

    /**
     * Packs the manifest into a byte array to send over a socket.
     *
     * @return byte array of the manifest.
     * @throws IOException if the manifest cannot be packed.
     */
    @Override
    public byte[] pack() throws IOException {
        Packer p = new Packer();
        p.writeShort((short) messages.size());
        p.writeString(algoName);
        for (MessageEntry entry : messages) {
            p.writeString(entry.uid.getSrcEngine());
            p.writeString(entry.uid.getSrcClient());
            p.writeString(entry.uid.getId());
            p.writeInt(entry.version);
        }
        return p.getBytes();
    }

    /**
     * A single entry in a manifset containing a UID and version.
     */
    public static class MessageEntry {

        /**
         * UID of the message.
         */
        public MessageUID uid;
        /**
         * Version of the message.
         */
        public int version;

        /**
         * Instantiates an entry.
         *
         * @param uid     message id.
         * @param version message version.
         */
        public MessageEntry(MessageUID uid, int version) {
            this.uid = uid;
            this.version = version;
        }

        /**
         * Instantiates an entry.
         *
         * @param event event to use for instantiation.
         */
        public MessageEntry(Event<ApplicationMessage> event) {
            this.uid = event.getContents().getUID();
            this.version = event.getContents().getVersion();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MessageEntry that = (MessageEntry) o;

            if (version != that.version) return false;
            if (uid != null ? !uid.equals(that.uid) : that.uid != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = uid != null ? uid.hashCode() : 0;
            result = 31 * result + version;
            return result;
        }
    }
}
