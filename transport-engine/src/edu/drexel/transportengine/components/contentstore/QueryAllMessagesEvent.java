package edu.drexel.transportengine.components.contentstore;

import edu.drexel.transportengine.core.events.ApplicationMessage;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.core.events.EventContents;

import java.util.List;

public class QueryAllMessagesEvent extends EventContents {

    private List<Event<ApplicationMessage>> messages;

    /**
     * Gets the name of this event type.
     *
     * @return name of this event type.
     */
    @Override
    public String getName() {
        return "query-all-messages";
    }

    /**
     * All application events.  This is filled in by the <code>ContentStoreComponent</code> after the query.
     *
     * @return all application events.
     */
    public List<Event<ApplicationMessage>> getMessages() {
        return messages;
    }

    /**
     * Sets the messages.  The <code>ContentStoreComponent</code> calls this when it receives this event.
     *
     * @param messages all persistent events in the Transport Engine.
     */
    public void setMessages(List<Event<ApplicationMessage>> messages) {
        this.messages = messages;
    }
}
