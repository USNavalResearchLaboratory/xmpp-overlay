/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component;

import java.util.logging.Logger;

import edu.drexel.xop.router.PacketRouter;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Handles boiler plate stuff for components.
 * implements VirtualComponent which in turn implements PacketFilter
 * 
 * @author David Millar
 */
public abstract class ComponentBase implements VirtualComponent {
    private static final Logger logger = LogUtils.getLogger(ComponentBase.class.getName());
    protected PacketRouter packetRouter;
    protected ComponentManager componentManager;

    public final void init(PacketRouter pr, ComponentManager cm) {
        packetRouter = pr;
        componentManager = cm;
        initialize();
    }

    /**
     * Override this instead of the init above to initialize your component
     * if it needs to.
     */
    public void initialize() {
        logger.fine("initializing CompontentBase");
    }

    /**
     * Override this if you want to have a component cleanup after itself.
     */
    @Override
    public void stop() {
        logger.fine("ComponentBase stop method called.");
    }

    /**
     * Override this method if you want a component to remove a client based
     * on the provided jidStr
     * 
     * @param jidStr the bare JID of the client to remove
     */
    @Override
    public void processCloseStream(String jidStr) {
        logger.fine("ComponentBase processCloseStream() called for jid: " + jidStr);
    }
}
