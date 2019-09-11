package edu.drexel.transportengine.components.protocolmanager.protocols.norm;

import java.io.IOException;

/**
 * The ... class ...
 * <p/>
 * Created by Ian Taylor Date: Apr 28, 2010 Time: 4:28:21 PM
 */
public class NORMBufferFullException extends IOException {

    int bytesWritten;

    public NORMBufferFullException(int bytesWritten) {
        this.bytesWritten = bytesWritten;
    }

    public int getBytesWritten() {
        return bytesWritten;
    }
}
