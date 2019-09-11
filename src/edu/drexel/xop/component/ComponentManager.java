/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.router.PacketRouter;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Manages Virtual Components
 * (things that look like a standard XMPP server Component to clients ie: MUC Component)
 * 
 * @author David Millar
 */
public class ComponentManager {

    private PacketRouter packetRouter;
    // Maps subdomain to components
    private Map<String, VirtualComponent> components = new ConcurrentHashMap<String, VirtualComponent>();
    Collection<VirtualComponent> uninitializedComponents = null;

    private static final Logger logger = LogUtils.getLogger(ComponentManager.class.getName());
    private final String domain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);

    ComponentAutoLoader autoLoader = null;

    public ComponentManager(PacketRouter pr) {
        this.packetRouter = pr;
        autoLoader = new ComponentAutoLoader(this);
        addComponents(uninitializedComponents);
        uninitializedComponents = null;
    }

    public void addComponent(VirtualComponent component) {
        // Initialize component
        logger.log(Level.INFO, "Initializing component: " + component.getClass().getName());
        component.init(this.packetRouter, this);

        // Add routes
        packetRouter.addRoute(component);
        components.put(component.getClass().getName(), component);
        logger.log(Level.INFO, "added, initialized, and routes created for component:, "
            + component.getClass().getName());
    }

    /**
     * Iterates through all components, removes them
     */
    public synchronized void shutDown() {
        for (String subdomain : components.keySet()) {
            VirtualComponent component = components.remove(subdomain);
            packetRouter.removeRoute(component);
            if (component != null) {
                logger.log(Level.INFO, "Stopping component: (" + component.getClass().getName() + ") ");
                component.stop();
            }
        }
    }

    public void addComponents(Collection<VirtualComponent> vc) {
        if (vc != null) {
            for (VirtualComponent component : vc) {
                try {
                    addComponent(component);
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Unable to initialize component: (" + component.getClass().getName()
                        + ")");
                }
            }
        }
    }

    /**
     * @return the components
     */
    public Collection<VirtualComponent> getComponents() {
        return Collections.unmodifiableCollection(components.values());
    }

    public VirtualComponent getComponent(String className) {
        return components.get(className);
    }

    /**
     * @param vc the components to add
     */
    public void setComponents(Collection<VirtualComponent> vc) {
        uninitializedComponents = vc;
    }

    public String toString() {
        return "This proxy (\"" + domain + "\") has the following components: " + components.keySet().toString() + "\n";
    }

    public void removeRoute(VirtualComponent vc) {
        packetRouter.removeRoute(vc);
    }
}