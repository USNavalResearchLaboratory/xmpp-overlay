package edu.drexel.transportengine.core;

import com.almworks.sqlite4java.SQLiteException;
import edu.drexel.transportengine.components.Component;
import edu.drexel.transportengine.components.clientmanager.ClientManagerComponent;
import edu.drexel.transportengine.components.contentstore.ContentStoreComponent;
import edu.drexel.transportengine.components.ddm.PTPAgent;
import edu.drexel.transportengine.components.persistencemanager.PersistenceManagerComponent;
import edu.drexel.transportengine.components.protocolmanager.ProtocolManagerComponent;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The core of the Transport Engine.  This class instantiates and manages all components,
 * and also handles event processing.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class TransportEngine {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private String guid;
    private List<Component> components;
    private boolean running;
    private long startTime;
    private boolean isEmulated;

    /**
     * Instantiate the Transport Engine with a GUID.
     *
     * @param guid the globally unique ID for the Transport Engine instance.
     */
    public TransportEngine(String guid) {
        this.guid = guid;
        components = new LinkedList<>();

        isEmulated = Configuration.getInstance().getValueAsBool(TEProperties.TE_EMULATED);
        for (String comp : Configuration.getInstance().getValueAsArray(TEProperties.TE_COMPONENTS)) {
            switch (comp) {
                case "clientmgr":
                    components.add(new ClientManagerComponent(this,
                            Configuration.getInstance().getValueAsString(TEProperties.CLIENTMGR_LISTEN_IFACE),
                            Configuration.getInstance().getValueAsInt(TEProperties.CLIENTMGR_LISTEN_PORT)));
                    break;
                case "contentstore":
                    try {
                        components.add(new ContentStoreComponent(this));
                    } catch (SQLiteException ex) {
                        logger.severe("Could not create content store: " + ex.getMessage());
                    }
                    break;
                case "persistencemgr":
                    components.add(new PersistenceManagerComponent(this));
                    break;
                case "protocolmgr":
                    components.add(new ProtocolManagerComponent(this));
                    break;
                case "ptpagent":
                    components.add(new PTPAgent(this));
                    break;
            }
        }
    }

    /**
     * Determine if the Transport Engine is running in emulation.
     *
     * @return if the Transport Engine is running in emulation.
     */
    public boolean isEmulated() {
        return isEmulated;
    }

    /**
     * Adds a component to the Transport Engine and starts it if necessary.
     *
     * @param comp the component to add.
     */
    public void addComponent(Component comp) {
        components.add(comp);
        if (running) {
            comp.start();
        }
    }

    /**
     * Gets the GUID of the Transport Engine instance./c
     *
     * @return the GUID of the Transport Engine instance./c
     */
    public String getGUID() {
        return guid;
    }

    /**
     * Starts the Transport Engine components.
     */
    public void start() {
        running = true;
        logger.info("Starting TransportEngine with ID " + guid);
        logger.info("Starting components...");
        for (Component c : components) {
            logger.info("\tStarting " + c.getClass().getSimpleName());
            c.start();
        }
        logger.info("Components started.");
        startTime = System.currentTimeMillis();
    }

    /**
     * Triggers an event in the Transport Engine.
     *
     * @param event the event to trigger.
     */
    public void executeEvent(Event event) {
        synchronized (event) {
            if (event.getEventSrc() == null) {
                event.setEventSrc(getGUID());
            }
            //logger.fine("Executing event: " + event.getContentType()
            //        + ", " + "(src: " + event.getEventSrc() + ")" + ", Local?: "
            //        + (event.getEventSrc().equals(getGUID()) ? "Yes" : "No"));
            for (Component c : components) {
                c.handleEvent(event);
            }
        }
    }

    /**
     * Triggers an event in the Transport Engine and waits for a response.  Note that this will block indefinitely if
     * no components respond.
     * <p/>
     * A response is signaled by calling <code>event.setResponded()</code>.
     *
     * @param event the event to trigger and wait for.
     */
    public void executeEventAwaitResponse(final Event event) {
        executeEvent(event);
        synchronized (event) {
            if (!event.hasResponse()) {
                try {
                    event.wait();
                } catch (InterruptedException ex) {
                    logger.warning("Unable to wait.");
                }
            }
        }
    }

    /**
     * Gets the number of milliseconds the Transport Engine has been running.
     *
     * @return the number of milliseconds the Transport Engine has been running.
     */
    public long getTimeRunning() {
        return System.currentTimeMillis() - startTime;
    }

    public synchronized void shutdown() {
        for (Component c : components) {
            c.shutdown();
        }
    }
}
