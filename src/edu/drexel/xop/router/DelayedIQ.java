/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.router;

import edu.drexel.xop.properties.XopProperties;
import org.xmpp.packet.IQ;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * @author tradams
 */
public class DelayedIQ implements Delayed {
    protected IQ iq;
    //delay is in milliseconds
    protected long delay = 2000;
    protected long endDelay;

    public DelayedIQ(IQ i) {
        iq = i;
        delay = XopProperties.getInstance().getIntProperty(XopProperties.XOP_IQ_TIMEOUT, (int) delay);
        endDelay = delay + System.currentTimeMillis();
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.Delayed#getDelay(java.util.concurrent.TimeUnit)
     */
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(endDelay - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(Delayed o) {
        return Long.valueOf(o.getDelay(TimeUnit.MILLISECONDS)).compareTo(this.getDelay(TimeUnit.MILLISECONDS));
    }

    public IQ getPacket() {
        return iq;
    }
}
