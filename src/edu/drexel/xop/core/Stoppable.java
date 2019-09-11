package edu.drexel.xop.core;

/**
 * User: urlass
 * Date: 1/13/13
 * Time: 4:36 PM
 * description: Used by ClientProxy to shutdown threads as cleanly as possible.
 * Example classes implementing this are: ClientListenerThread, IQReaper, and 
 * WebServer.
 */
public interface Stoppable {
    public void stopMe();
}
