package edu.drexel.transportengine.components.protocolmanager;

import edu.drexel.transportengine.core.events.EventContents;

public class CreateSocketEvent extends EventContents {
    private String dest;

    public CreateSocketEvent(String dest) {
        this.dest = dest;
    }

    @Override
    public String getName() {
        return "create-socket-event";
    }

    public String getDest() {
        return dest;
    }
}
