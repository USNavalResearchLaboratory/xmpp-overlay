package edu.drexel.transportengine.components.persistencemanager.algorithms;

import edu.drexel.transportengine.components.contentstore.QueryMessageEvent;
import edu.drexel.transportengine.components.persistencemanager.PersistenceAlgorithm;
import edu.drexel.transportengine.components.persistencemanager.util.MessageManifest;
import edu.drexel.transportengine.components.persistencemanager.util.MessageManifest.MessageEntry;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.TransportProperties;
import edu.drexel.transportengine.core.events.ApplicationMessage;
import edu.drexel.transportengine.core.events.ApplicationMessage.MessageUID;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Implementation of the Trickle persistence algorithm.  Occasionally broadcasts a manifest of
 * local objects.  Remote instances use this information to transmit objects that are missing
 * from the local instance.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 * @see <a href="http://dl.acm.org/citation.cfm?id=1251177">Trickle: a self-regulating algorithm for
 *      code propagation and maintenance in wireless sensor networks</a>
 */
public class TrickleAlgorithm extends PersistenceAlgorithm {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private Map<MessageUID, TrickleObjectParameters> parameters;
    private int k;
    private long tauL;
    private long tauH;
    private int advertInterval;
    private String destination;
    private int manifestLength;
    private TransportProperties properties;

    /**
     * Instatiates a Trickle algorithm.
     *
     * @param engine reference to the Transport Engine.
     */
    public TrickleAlgorithm(TransportEngine engine) {
        super(engine);

        parameters = new HashMap<>();
        k = Configuration.getInstance().getValueAsInt(TEProperties.ALGORITHM_TRICKLE_K);
        tauL = Configuration.getInstance().getValueAsLong(TEProperties.ALGORITHM_TRICKLE_TAUL);
        tauH = Configuration.getInstance().getValueAsLong(TEProperties.ALGORITHM_TRICKLE_TAUH);
        advertInterval = Configuration.getInstance().getValueAsInt(TEProperties.ALGORITHM_TRICKLE_ADVERT_MS);
        destination = Configuration.getInstance().getValueAsString(TEProperties.ALGORITHM_TRICKLE_DESTINATION);
        manifestLength = Configuration.getInstance().getValueAsInt(TEProperties.ALGORITHM_TRICKLE_MANIFEST_LENGTH);
        properties = new TransportProperties(
                Configuration.getInstance().getValueAsBool(TEProperties.ALGORITHM_TRICKLE_RELIABLE), 0, false);
    }

    /**
     * Broadcasts manifests every <code>advertInterval</code> milliseconds.
     */
    @Override
    public void whileActive() {
        MessageManifest manifest = new MessageManifest(getAlgorithmName());

        // Find the messages that are due for a broadcast
        for (Event<ApplicationMessage> event : getAllPersistentMessages()) {
            TrickleObjectParameters params = getParameters(event.getContents().getUID());

            if (params.getNextCheckTime() <= getTime()) {
                manifest.addEntries(new MessageEntry(event));
                params.c = 0;
                params.increaseTau();
            }
        }

        // Fragment the messages to broadcast, and send sub-manifests
        for (MessageManifest m : manifest.fragmentManifest(manifestLength)) {
            getTransportEngine().executeEvent(new Event<>(destination, properties, m));
        }

        try {
            Thread.sleep(advertInterval);
        } catch (InterruptedException ex) {
            logger.warning("Unable to sleep.");
        }
    }

    /**
     * Handles events from the Transport Engine.
     *
     * @param event the event to handle.
     */
    @Override
    public void handleEvent(Event event) {
        if (isActive() && !event.getEventSrc().equals(getTransportEngine().getGUID())) {
            if (event.getContents() instanceof MessageManifest) {
                handleManifest(event);
            }
        }
    }

    /**
     * Handles an incoming manifest from a remote instance.
     *
     * @param event the event to handle.
     */
    private void handleManifest(Event<MessageManifest> event) {
        if (event.getContents().getAlgorithmName().equals(getAlgorithmName())) {
            // Get the local messages
            List<MessageEntry> localMessages = new MessageManifest(getAlgorithmName(),
                    getAllPersistentMessages()).getEntries();
            List<MessageEntry> remoteMessages = event.getContents().getEntries();

            // For every remote message...
            for (MessageEntry remoteEntry : remoteMessages) {
                // If the remote instance has the message, and the local does not
                if (!localMessages.contains(remoteEntry) && remoteEntry.version > 0) {
                    // Create dummy object with version 0
                    ApplicationMessage msg = new ApplicationMessage(
                            remoteEntry.uid.getSrcEngine(),
                            remoteEntry.uid.getSrcClient(),
                            remoteEntry.uid.getId(),
                            0,
                            "<< NO DATA >>");
                    // TODO: This null and -1 are placeholders for unknown data which will still be processed by the
                    // content store.  Should probably be done in a cleaner way.
                    getTransportEngine().executeEvent(new Event<>(null,
                            new TransportProperties(false, -1, false), msg));
                    // Force a broadcast next iteration to get remote version
                    getParameters(msg.getUID()).zeroTimer();

                    // If the local instance contains the entry but is outdated
                } else if (localMessages.contains(remoteEntry)
                        && remoteEntry.version > localMessages.get(localMessages.indexOf(remoteEntry)).version) {
                    // Force a broadcast next iteration to get remote version
                    getParameters(remoteEntry.uid).zeroTimer();
                    // If the local instance contains the entry and the remote is
                    // outdated or missing...
                } else if (localMessages.contains(remoteEntry)
                        && remoteEntry.version < localMessages.get(localMessages.indexOf(remoteEntry)).version) {
                    // Remotely missing or out of date --- query the local content store and wait for population
                    Event<QueryMessageEvent> queryEvent = new Event<>(new QueryMessageEvent(remoteEntry.uid));
                    getTransportEngine().executeEventAwaitResponse(queryEvent);

                    if (queryEvent.getContents().getMessage() != null) {
                        // Re-execute the event
                        getTransportEngine().executeEvent(queryEvent.getContents().getMessage());
                        // Zero the timer
                        getParameters(remoteEntry.uid).resetTau();
                    }
                }
            }
        }
    }

    /**
     * Gets the name of the algorithm.
     *
     * @return name of the algorithm.
     */
    @Override
    public String getAlgorithmName() {
        return "trickle";
    }

    /**
     * Gets the current time.
     *
     * @return the current time.
     */
    private static long getTime() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    private TrickleObjectParameters getParameters(MessageUID uid) {
        if (!parameters.containsKey(uid)) {
            parameters.put(uid, new TrickleObjectParameters(k, tauL, tauH));
        }
        return parameters.get(uid);
    }

    /**
     * Class containing parameters for the Trickle algorithm.  Each persisted object maintains its own set of these
     * parameters.
     */
    public class TrickleObjectParameters {

        /**
         * Number of broadcasts heard.
         */
        public volatile int c;
        /**
         * Broadcast threshold.
         */
        public volatile int k;
        private volatile long tau;
        private volatile long tauL;
        private volatile long tauH;
        private volatile long nextCheckTime;
        private Random random;

        /**
         * Instantiates a new set of Trickle parameters.
         *
         * @param k    broadcast threshold.
         * @param tauL minimum tau value.
         * @param tauH maximum tau value.
         */
        public TrickleObjectParameters(int k, long tauL, long tauH) {
            random = new Random();
            this.c = 0;
            this.k = k;
            this.tauL = tauL;
            this.tauH = tauH;
            this.tau = tauL + ((tauH - tauL) / (long) 2.0);
            generateNextCheckTime();
        }

        /**
         * Determines the next time the object should be checked.
         */
        public final synchronized void generateNextCheckTime() {
            this.nextCheckTime = (int) (TrickleAlgorithm.getTime())
                    + (this.tau / (long) 2.0)
                    + (long) (random.nextFloat() * (this.tau / (long) 2.0));
        }

        /**
         * Gets the next time the object should be checked.
         *
         * @return time the object should be checked.
         */
        public long getNextCheckTime() {
            return nextCheckTime;
        }

        /**
         * Sets the timer to zero, forcing a metadata broadcast next iteration.
         */
        public void zeroTimer() {
            nextCheckTime = 0;
        }

        /**
         * Resets <code>tau</code> to <code>tauL</code>.
         */
        public synchronized void resetTau() {
            this.tau = this.tauL;
            generateNextCheckTime();
        }

        /**
         * Doubles the value of <code>tau</code> up to <code>tauH</code>.
         */
        public synchronized void increaseTau() {
            this.tau = Math.min(this.tau * (long) 2.0, this.tauH);
            generateNextCheckTime();
        }
    }
}
