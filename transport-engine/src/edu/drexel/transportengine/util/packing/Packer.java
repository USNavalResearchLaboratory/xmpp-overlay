package edu.drexel.transportengine.util.packing;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Helper class for packing byte array without knowing its size a priori.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class Packer {

    private DataOutputStream os;
    private ByteArrayOutputStream baos;

    /**
     * Create a new packable byte buffer.
     */
    public Packer() {
        baos = new ByteArrayOutputStream();
        os = new DataOutputStream(baos);
    }

    /**
     * Write a byte to the buffer.
     *
     * @param v byte to write.
     * @throws IOException if the value could not be written.
     */
    public void writeByte(byte v) throws IOException {
        os.writeByte(v);
    }

    /**
     * Writes bytes to the buffer.
     *
     * @param v bytes to write.
     * @throws IOException if the value could not be written.
     */
    public void writeBytes(byte[] v) throws IOException {
        os.write(v);
    }

    /**
     * Writes a string to the buffer.  The length of the string is preserved for unpacking,
     * so no sentinel (such as \\0) need be used.
     *
     * @param s string to write.
     * @throws IOException if the value could not be written.
     */
    public void writeString(String s) throws IOException {
        os.writeInt(s.length());
        writeBytes(s.getBytes());
    }

    /**
     * Writes a short to the buffer.
     *
     * @param v short to write.
     * @throws IOException if the value could not be written.
     */
    public void writeShort(short v) throws IOException {
        os.writeShort(v);
    }

    /**
     * Writes an int to the buffer.
     *
     * @param v int to write.
     * @throws IOException if the value could not be written.
     */
    public void writeInt(int v) throws IOException {
        os.writeInt(v);
    }

    /**
     * Gets the underlying byte array.
     *
     * @return the underlying byte array.
     */
    public byte[] getBytes() {
        return baos.toByteArray();
    }
}
