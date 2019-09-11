package edu.drexel.transportengine.components;

import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.events.Event;

/**
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public abstract class Component extends Thread {

    private TransportEngine engine;

    public Component(TransportEngine engine) {
        this.engine = engine;
    }

    public TransportEngine getTransportEngine() {
        return engine;
    }

    public abstract void handleEvent(Event event);

    public void shutdown() {
    }
}
