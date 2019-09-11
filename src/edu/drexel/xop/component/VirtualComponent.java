/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component;

import org.xmpp.packet.Packet;

import edu.drexel.xop.core.PacketFilter;
import edu.drexel.xop.router.PacketRouter;

/**
 * This interface is intended to be used by anything wishing to look like an
 * XMPP server component to the client.
 * <p/>
 * When used in conjunction with the component manager
 * 
 * @author David Millar
 */
public interface VirtualComponent extends PacketFilter {
    public void init(PacketRouter pr, ComponentManager cm);

    public void stop();

    public void processPacket(Packet p);

    public void processCloseStream(String jidStr);
}