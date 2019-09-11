package edu.drexel.transportengine.util.packing;

import java.nio.ByteBuffer;

/**
 * Helper class for unpacking a byte array.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class Unpacker {

    private ByteBuffer bb;

    /**
     * Instantiates an unpacker with a given buffer.
     *
     * @param buf buffer to unpack.
     */
    public Unpacker(byte[] buf) {
        bb = ByteBuffer.wrap(buf);
    }

    /**
     * Reads a byte from the buffer.
     *
     * @return the byte read.
     */
    public byte readByte() {
        return bb.get();
    }

    /**
     * Reads bytes from the buffer.
     *
     * @return the bytes read.
     */
    public byte[] readBytes(int len) {
        byte[] buf = new byte[len];
        bb.get(buf, 0, len);
        return buf;
    }

    /**
     * Reads a string from the buffer.
     *
     * @return the string read.
     */
    public String readString() {
        return new String(readBytes(readInt()));
    }

    /**
     * Reads a short from the buffer.
     *
     * @return the short read.
     */
    public short readShort() {
        return bb.getShort();
    }

    /**
     * Reads an int from the buffer.
     *
     * @return the int read.
     */
    public int readInt() {
        return bb.getInt();
    }

    /**
     * Sets the position in the byte buffer.
     *
     * @param i the position.
     */
    public void position(int i) {
        bb.position(i);
    }

    /**
     * Gets the remaining buffer bytes.
     *
     * @return the remaining buffer.
     */
    public byte[] getRemainder() {
        return readBytes(bb.capacity() - bb.position());
    }

    /**
     * Determines if there are remaining bytes.
     *
     * @return if there are remaining bytes.
     */
    public boolean hasRemaining() {
        return bb.hasRemaining();
    }
}
