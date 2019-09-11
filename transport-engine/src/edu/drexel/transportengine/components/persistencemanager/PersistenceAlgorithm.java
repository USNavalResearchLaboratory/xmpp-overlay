package edu.drexel.transportengine.components.persistencemanager;

import edu.drexel.transportengine.components.Component;
import edu.drexel.transportengine.components.contentstore.QueryAllMessagesEvent;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.events.ApplicationMessage;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.util.List;
import java.util.logging.Logger;

/**
 * This class represents a persistence algorithm, and maintains a list of <code>MessageUID</code>s which it is
 * maintaining.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public abstract class PersistenceAlgorithm extends Component {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private boolean active;

    /**
     * Instantiates an algorithm class.
     *
     * @param engine reference to the Transport Engine.
     */
    public PersistenceAlgorithm(TransportEngine engine) {
        super(engine);
        this.active = false;
    }

    /**
     * Gets the name of the algorithm.
     *
     * @return name of the algorithm
     */
    public abstract String getAlgorithmName();

    /**
     * Sets if the algorithm is active.
     *
     * @param active if the algorithm should run.
     */
    public final void setActive(boolean active) {
        this.active = active;
        logger.fine("Set " + getAlgorithmName() + " to " + (active ? "ACTIVE" : "INACTIVE"));
        synchronized (this) {
            notify();
        }
    }

    /**
     * Determine if the algorithm is active.
     *
     * @return if the algorithm is active.
     */
    public final boolean isActive() {
        return active;
    }

    public final void run() {
        while (true) {
            waitForActive();
            whileActive();
        }
    }

    /**
     * The method which is constantly run while the algorithm is active.
     */
    public abstract void whileActive();

    public final List<Event<ApplicationMessage>> getAllPersistentMessages() {
        Event<QueryAllMessagesEvent> q = new Event<>(new QueryAllMessagesEvent());
        getTransportEngine().executeEventAwaitResponse(q);
        return q.getContents().getMessages();
    }


    private void waitForActive() {
        while (!isActive()) {
            try {
                synchronized (this) {
                    wait();
                }
            } catch (InterruptedException e) {
            }
        }
    }
}
