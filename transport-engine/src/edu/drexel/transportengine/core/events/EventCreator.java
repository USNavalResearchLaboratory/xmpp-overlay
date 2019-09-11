package edu.drexel.transportengine.core.events;

import edu.drexel.transportengine.components.ddm.DDMMessage;
import edu.drexel.transportengine.components.persistencemanager.util.MessageManifest;
import edu.drexel.transportengine.components.persistencemanager.util.MessageRequests;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.util.logging.Logger;

/**
 * Helper class for events.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class EventCreator {

    private static final Logger logger = LogUtils.getLogger(EventCreator.class.getName());

    /**
     * Creates an <code>EventContents</code> instance based on a packed byte array.
     *
     * @param type   the type of the event.
     * @param packed the packed contents of the event.
     * @return the event created.
     */
    public static EventContents createContents(String type, byte[] packed) {
        switch (type) {
            case "message":
                return new ApplicationMessage(packed);
            case "manifest":
                return new MessageManifest(packed);
            case "requests":
                return new MessageRequests(packed);
            case "ddm-message":
                return new DDMMessage(packed);
        }

        return null;
    }
}
