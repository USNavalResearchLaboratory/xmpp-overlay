package edu.drexel.transportengine.components.contentstore;

import edu.drexel.transportengine.core.events.ApplicationMessage;
import edu.drexel.transportengine.core.events.ApplicationMessage.MessageUID;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.core.events.EventContents;

/**
 * An event used to query the <code>ContentStoreComponent</code>.  This is primarily used to determine versioning
 * information for incoming messages.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class QueryMessageEvent extends EventContents {

    private MessageUID uid;
    private Event<ApplicationMessage> message;

    /**
     * Instantiates a new <code>QueryMessageEvent</code>.
     *
     * @param uid the UID to query.
     */
    public QueryMessageEvent(MessageUID uid) {
        this.uid = uid;
    }

    /**
     * Gets the name of this event type.
     *
     * @return name of this event type.
     */
    @Override
    public String getName() {
        return "query-message";
    }

    /**
     * The message UID to query.
     *
     * @return the UID to query.
     */
    public MessageUID getUID() {
        return uid;
    }

    /**
     * The event associated with the UID.  This is filled in by the <code>ContentStoreComponent</code>
     * after the query.
     *
     * @return the associated event.
     */
    public Event<ApplicationMessage> getMessage() {
        return message;
    }

    /**
     * Sets the message associated with the UID.  The <code>ContentStoreComponent</code> calls this
     * when it receives this event.
     *
     * @param message the event associated with the UID.
     */
    public void setMessage(Event<ApplicationMessage> message) {
        this.message = message;
    }
}
