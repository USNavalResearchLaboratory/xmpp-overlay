package edu.drexel.transportengine.components.persistencemanager;

import edu.drexel.transportengine.components.Component;
import edu.drexel.transportengine.components.persistencemanager.algorithms.ManifestAlgorithm;
import edu.drexel.transportengine.components.persistencemanager.algorithms.TrickleAlgorithm;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manager for persistence algorithms.  This class is in charge of selecting an algorithm to persist any message
 * marked as persistent.  Additionally it starts and shuts down algorithms as necessary.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class PersistenceManagerComponent extends Component {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private Map<String, PersistenceAlgorithm> algorithms;

    /**
     * Instantiates a new persistence manager.
     *
     * @param engine reference to the Transport Engine.
     */
    public PersistenceManagerComponent(TransportEngine engine) {
        super(engine);
        algorithms = new HashMap<>();

        // TODO: Do this at runtime, and allow order to be specified.
        addAlgorithm(new ManifestAlgorithm(getTransportEngine()));
        addAlgorithm(new TrickleAlgorithm(getTransportEngine()));
        // TODO: Get rid of this
        algorithms.get("manifest").setActive(true);
    }

    /**
     * Handles events coming from the Transport Engine.  This method selects an algorithm for persistence and forwards
     * Transport Engine events to each algorithm.
     *
     * @param event event to handle.
     */
    @Override
    public void handleEvent(Event event) {
        // TODO: This eventually needs to be abstracted.
        /*
        if (event.getContents() instanceof DDMMessage) {
            if (((DDMMessage) event.getContents()).getType().equals("density")) {
                if (((DDMMessage) event.getContents()).getValue().equals("dense")) {
                    logger.fine("Using TRICKLE");
                    algorithms.get("manifest").setActive(false);
                    algorithms.get("trickle").setActive(true);
                } else {
                    logger.fine("Using MANIFEST");
                    algorithms.get("trickle").setActive(false);
                    algorithms.get("manifest").setActive(true);
                }
            }
        }
        */
    }

    /**
     * Adds an algorithm to the manager.
     *
     * @param algo algorithm to add.
     */
    private void addAlgorithm(PersistenceAlgorithm algo) {
        algorithms.put(algo.getAlgorithmName(), algo);
        getTransportEngine().addComponent(algo);
    }
}
