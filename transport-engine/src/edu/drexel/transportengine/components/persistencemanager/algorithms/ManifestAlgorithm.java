package edu.drexel.transportengine.components.persistencemanager.algorithms;

import edu.drexel.transportengine.components.contentstore.QueryMessageEvent;
import edu.drexel.transportengine.components.persistencemanager.PersistenceAlgorithm;
import edu.drexel.transportengine.components.persistencemanager.util.MessageManifest;
import edu.drexel.transportengine.components.persistencemanager.util.MessageManifest.MessageEntry;
import edu.drexel.transportengine.components.persistencemanager.util.MessageRequests;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.TransportProperties;
import edu.drexel.transportengine.core.events.ApplicationMessage;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.util.logging.Logger;

/**
 * A simple manifest algorithm for persistence.  Every epoch, a fragmented list of all locally
 * persisted objects is broadcast to the network (via the event bus).  If an instance hears of
 * an object it does not have, it sends a <code>MessageRequests</code> event.  If it hears that
 * a remote instance does not have an object currently available, it is broadcast.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class ManifestAlgorithm extends PersistenceAlgorithm {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private int manifestLength;
    private int sleepTime;
    private String destination;
    private TransportProperties properties;

    /**
     * Instantiates a manifest algorithm.
     *
     * @param engine reference to the Transport Engine.
     */
    public ManifestAlgorithm(TransportEngine engine) {
        super(engine);
        this.manifestLength = Configuration.getInstance().getValueAsInt(TEProperties.ALGORITHM_MANIFEST_MANIFEST_LENGTH);
        this.sleepTime = Configuration.getInstance().getValueAsInt(TEProperties.ALGORITHM_MANIFEST_SLEEP_MS);
        this.destination = Configuration.getInstance().getValueAsString(TEProperties.ALGORITHM_MANIFEST_DESTINATION);
        this.properties = new TransportProperties(
                Configuration.getInstance().getValueAsBool(TEProperties.ALGORITHM_MANIFEST_RELIABLE), 0, false);
    }

    /**
     * Runs the broadcast loop.  Every <code>sleepTime</code>, a set of <code>MessageManifest</code>
     * events are triggered.
     */
    @Override
    public void whileActive() {
        MessageManifest manifest = new MessageManifest(getAlgorithmName(), getAllPersistentMessages());

        for (MessageManifest m : manifest.fragmentManifest(manifestLength)) {
            getTransportEngine().executeEvent(new Event<>(destination, properties, m));
        }

        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException ex) {
            logger.warning("Unable to sleep.");
        }
    }

    /**
     * Gets the name of the algorithm.
     *
     * @return name of the algorithm.
     */
    @Override
    public String getAlgorithmName() {
        return "manifest";
    }

    /**
     * Handles manifest and request events from remote instances.
     *
     * @param event the event to handle.
     */
    @Override
    public void handleEvent(Event event) {
        if (isActive() && !event.getEventSrc().equals(getTransportEngine().getGUID())) {
            if (event.getContents() instanceof MessageRequests) {
                handleRequests(event);
            } else if (event.getContents() instanceof MessageManifest) {
                handleManifest(event);
            }
        }
    }

    /**
     * Handles <code>MessageManifest</code> events originating from a remote instance. For each entry,
     * if the associated message is newer locally, the entire message is broadcast. If not,
     * it is added to a list of requests and broadcast.
     *
     * @param event the <code>MessageManifest</code> event to handle.
     */
    private void handleManifest(Event<MessageManifest> event) {
        if (event.getContents().getAlgorithmName().equals(getAlgorithmName())) {
            MessageRequests reqs = new MessageRequests("manifest");

            for (MessageEntry entry : event.getContents().getEntries()) {
                Event<QueryMessageEvent> queryEvent = new Event<>(new QueryMessageEvent(entry.uid));
                getTransportEngine().executeEventAwaitResponse(queryEvent);

                Event<ApplicationMessage> resp = queryEvent.getContents().getMessage();
                if (resp != null && resp.getContents().getVersion() > entry.version) {
                    // Remote copy outdated...send it
                    getTransportEngine().executeEvent(resp);
                } else if (resp == null || resp.getContents().getVersion() < entry.version) {
                    // Local copy missing or outdated...request it
                    if (resp != null) {
                        reqs.addEntries(new MessageEntry(resp));
                    } else {
                        reqs.addEntries(new MessageEntry(entry.uid, 0));

                        // Add a dummy entry locally
                        ApplicationMessage msg = new ApplicationMessage(
                                entry.uid.getSrcEngine(),
                                entry.uid.getSrcClient(),
                                entry.uid.getId(),
                                0,
                                "<< NO DATA >>");
                        // TODO: This null and -1 are placeholders for unknown data which will still be processed by the
                        // content store.  Should probably be done in a cleaner way.
                        getTransportEngine().executeEvent(new Event<>(null,
                                new TransportProperties(false, -1, false), msg));
                    }
                }
            }

            if (reqs.getEntries().size() > 0) {
                getTransportEngine().executeEvent(new Event<>(destination, properties, reqs));
            }
        }
    }

    /**
     * Handles requests from remote instances.  If there is a local copy of the requested message that is newer than
     * that in the request, it is broadcast.
     *
     * @param event the <code>MessageRequest</code> event to handle.
     */
    private void handleRequests(Event<MessageRequests> event) {
        if (event.getContents().getAlgorithmName().equals(getAlgorithmName())) {
            for (MessageEntry entry : event.getContents().getEntries()) {
                Event<QueryMessageEvent> queryEvent = new Event<>(new QueryMessageEvent(entry.uid));
                getTransportEngine().executeEventAwaitResponse(queryEvent);

                if (queryEvent.getContents().getMessage() != null
                        && queryEvent.getContents().getMessage().getContents().getVersion() > entry.version) {
                    Event<ApplicationMessage> resp = queryEvent.getContents().getMessage();
                    if (resp != null) {
                        getTransportEngine().executeEvent(resp);
                    }
                }
            }
        }
    }
}
