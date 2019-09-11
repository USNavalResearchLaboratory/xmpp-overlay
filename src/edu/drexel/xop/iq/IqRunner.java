/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError.Condition;
import org.xmpp.packet.PacketError.Type;

import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Wrapper to run IQ queries in their own thread
 * 
 * @author David Millar
 */
public class IqRunner implements Runnable {
    private static final Logger logger = LogUtils.getLogger(IqRunner.class.getName());

    private IQ query;
    private IqHandler handler;

    public IqRunner() {
    }

    public IqRunner(IQ query, IqHandler handler) {
        setQuery(query);
        setIqHandler(handler);
    }

    public void run() {
        try {
            Thread.currentThread().setName("IQRunner");
            int nThreads = Thread.activeCount();
            logger.log(Level.FINEST, "[" + query.getID()
                + "] Thread running for query. (" + nThreads
                + " active threads)");

            // Handle query, add the correct id to the packet just in case
            String id = query.getID();
            IQ response = handler.handleIq(query);
            if (response == null) {
                throw new NullPointerException("Null result returned from IQ Handler. Offending Class: "
                    + handler.getClass().getName());
            }
            response.setID(id);

            // Send response back to the IqManager
            ClientProxy.getInstance().processPacket(response);
            logger.log(Level.FINEST, "[" + query.getID()
                + "] Thread finishing.");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception thrown by Iq Runner: ", ex);
            ClientProxy.getInstance().processPacket(IqManager.getErrorForIq(query, Condition.internal_server_error, Type.cancel));
        }
    }

    public final void setQuery(IQ query) {
        this.query = query;
    }

    public final void setIqHandler(IqHandler handler) {
        this.handler = handler;
    }

}
