package edu.drexel.transportengine.components.protocolmanager.protocols.norm;

import mil.navy.nrl.norm.NormSession;
import mil.navy.nrl.norm.NormStream;
import mil.navy.nrl.norm.enums.NormFlushMode;

import java.io.IOException;
import java.io.OutputStream;

/**
 * The class creates a Java style output stream for norm for a session with some
 * optimisations set.
 * <p/>
 * <p/>
 * Created by Ian Taylor Date: Mar 26, 2010 Time: 12:45:22 PM
 */
public class NORMOutputStream extends OutputStream {

    private NormStream normOutputStream;
    private byte temp[] = new byte[1];
    private NormSession normSession;

    public enum TcpMode {

        TCP_MODE, DEFAULT
    }

    public NORMOutputStream(NormSession normSession, TcpMode tcpMode,
                            boolean autoFlush, int repairWindowSize)
            throws IOException {
        this.normSession = normSession;

        if (tcpMode == TcpMode.TCP_MODE) {
            normSession.setCongestionControl(true, true);
        }

        normOutputStream = normSession.streamOpen(repairWindowSize);

        if (autoFlush) {
            normOutputStream.setAutoFlush(NormFlushMode.NORM_FLUSH_PASSIVE);
        }
        // "active" or "passive" auto flush achieves the same the thing with respect
        // to marking application message boundaries ... the distinction is that "active" flash
        // actually sends NORM_CMD(FLUSH) messages while "passive" flush simply sends any
        // buffered data immediately, but does not send a NORM_CMD(FLUSH)        
    }

    public void write(int b) throws IOException {
        temp[0] = (byte) b;
        this.write(temp, 0, 1);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        this.write(bytes, 0, bytes.length);
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        int written = normOutputStream.write(data, offset, length);
        if (written != length) {
            throw new NORMBufferFullException(written);
        }
    }

    @Override
    public void flush() throws IOException {
        normOutputStream.flush();
    }

    @Override
    public void close() throws IOException {
        normOutputStream.close();
    }

    public NormStream getNormOutputStream() {
        return normOutputStream;
    }

    public NormSession getNormSession() {
        return normSession;
    }
}
