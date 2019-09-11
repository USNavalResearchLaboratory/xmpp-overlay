/**
 * (c) 2011 Drexel University
 *
 * Controller for XOP.  Use this to stop it.  If you want to add other
 * controls to XOP, you could also do that here.
 *
 *
 * @author Rob Lass <urlass@cs.drexel.edu>
 */
package edu.drexel.xop.controller;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Controller extends Thread {

    AtomicBoolean command_is_ready = new AtomicBoolean();
    Command command = null;
    //there has to be a better way to handle this logging situation
    protected Logger logger = null;

    public enum Command {
        EXIT,
        COMPONENTS,
        CLIENTS,
        ROOMS,
        ROSTERS,
        ROUTES
    }

    public Controller(Logger logger) {
        this.logger = logger;
        command_is_ready.set(false);
    }

    /**
     * @return the last command received.
     */
    public Command getCommand() {
        return command;
    }

    /**
     * sends the Controller command to exit
     *
     * @param s
     */
    public synchronized void sendRawCommand(String s) {
        if (s == null) {
            //do nothing
        } else if (s.equalsIgnoreCase("exit")) {
            sendCommand(Controller.Command.EXIT);
        } else if (s.equalsIgnoreCase("rooms")) {
            sendCommand(Controller.Command.ROOMS);
        } else if (s.equalsIgnoreCase("components")) {
            sendCommand(Controller.Command.COMPONENTS);
        } else if (s.equalsIgnoreCase("clients")) {
            sendCommand(Controller.Command.CLIENTS);
        } else if (s.equalsIgnoreCase("rosters")) {
            sendCommand(Controller.Command.ROSTERS);
        } else if (s.equalsIgnoreCase("routes")) {
            sendCommand(Controller.Command.ROUTES);
        } else {
            logger.log(Level.WARNING, "Received an unknown command: " + s);
            String rval = "Available commands: [";
            String delim = "";
            for (Command cmd : Controller.Command.values()) {
                rval += delim + cmd;
                delim = ", ";
            }
            rval += "]\n";
            System.out.println(rval);
        }
    }

    /**
     * Send a command to XOP.  Need to modify this if more than one command is
     * ever sent.
     */
    protected synchronized void sendCommand(Command c) {
        command = c;
        command_is_ready.set(true);
        notifyAll();
    }

    /**
     * This function blocks until a command is received.
     */
    public synchronized void await() throws InterruptedException {
        while (!command_is_ready.get()) {
            wait();
        }
        command_is_ready.set(false);
    }
}
