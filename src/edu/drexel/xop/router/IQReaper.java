/*
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.router;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.DelayQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;

import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.core.Stoppable;
import edu.drexel.xop.iq.IqManager;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * According to the XMPP spec, all iq packets must be responded to. this queue holds onto
 * packets that have not been responded to and will dequeue packets with a generic error
 * after a specified xop.router.iq.timeout
 * <p/>
 * http://xmpp.org/rfcs/rfc6120.html#stanzas-semantics-iq<br/>
 * http://xmpp.org/rfcs/rfc6121.html#iq<br/>
 * 
 * @author tradams
 */
public class IQReaper extends Thread implements Stoppable {
    private final static Logger logger = LogUtils.getLogger(IQReaper.class.getName());
    private DelayQueue<DelayedIQ> timeoutQueue = new DelayQueue<>();
    private Map<String, DelayedIQ> timeoutMap = new HashMap<>();
    protected boolean running = true;

    public IQReaper() {
        ClientProxy.getInstance().addThreadToStop(this);
    }

    public void run() {
        while (running) {
            try {
                DelayedIQ delayed = timeoutQueue.take();
                IQ iq = delayed.getPacket();
                logger.log(Level.INFO, "Packet timeout: " + iq);
                // construct the error packet here
                IQ eiq = IqManager.getGenericErrorForIq(iq);// error iq
                ClientProxy.getInstance().processPacket(eiq);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Delays IQ messages as necessary.
     * 
     * @param iq
     */
    public void handleDelayedIQ(IQ iq) {
        logger.log(Level.FINER, "Handling a delayed IQ message");
        Type iqType = iq.getType();
        String iqID = iq.getID();
        if (iqType.equals(IQ.Type.set) || iqType.equals(IQ.Type.get)) {
            logger.log(Level.FINER, "Putting a delayed IQ message in the timeoutQueue (" + iqID + ")");
            DelayedIQ d = new DelayedIQ(iq);
            timeoutQueue.put(d);
            timeoutMap.put(iqID, d);
        } else {
            logger.log(Level.FINER, "Removing a delayed IQ message from the timeoutQueue (" + iqID + ")");
            DelayedIQ d = timeoutMap.remove(iqID);
            if (d != null) {
                timeoutQueue.remove(d);
                logger.log(Level.FINER, "Removed a delayed IQ message from the timeoutQueue (" + iqID + ")");
            } else {
                logger.log(Level.FINER, "Could not find corresponding delayed IQ message! (" + iqID + ")");
            }
        }
    }

    public void stopMe() {
        this.interrupt();
    }
}