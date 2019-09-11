/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq;

import org.xmpp.packet.IQ;

/**
 * @author David Millar
 */
public abstract class IqHandler {
    public abstract IQ handleIq(IQ iq);
}