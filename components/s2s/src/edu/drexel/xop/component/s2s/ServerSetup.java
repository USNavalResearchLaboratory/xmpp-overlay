/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author charlesr
 */
public interface ServerSetup {
    boolean handle(XMPPStream stream, String domain) throws FailedSetupException;
    XMPPStream handle(InputStream inputStream, OutputStream outputStream) throws FailedSetupException;
    boolean canSend();
    void setState(State state);
    void setLocal(String  local);
    State getState();

}
