/*
 * (c) 2011 Drexel University
 */

package edu.drexel.xop.controller;

import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * This class is a utility that allows you to pass commands to a running
 * instance of XOP.  To use, just run its main function with the command as
 * an argument.  Optionally, you may also pass in an IP address and a port
 * number.  The default is the local machine.
 *
 * @author Rob Lass <urlass@cs.drexel.edu>
 */
public class NetworkController {

    protected int port = NetworkControlListener.DEFAULT_PORT;
    protected InetAddress addr = null;
    protected Socket socket = null;

    /**
     * Create a socket.
     */
    protected void establishConnection() {
        try {
            this.socket = new Socket(addr, port);
        } catch (IOException e) {
            die(e, "Unable to establish connection with " + addr + ":" + port);
        }
    }

    /**
     * Send the command over the socket.
     */
    protected void sendCommand(String cmd) {
        if (this.socket == null) {
            System.err.println("Error: Tried to send over a closed socket!");
            System.exit(-1);
        }
        try {
            PrintWriter out =
                    new PrintWriter(this.socket.getOutputStream(), true);
            out.print(cmd);
            out.close();
        } catch (IOException e) {
            die(e, "IOException trying to send command to host!");
        }
    }

    /**
     * Print an error message and stack trace, then exit.
     */
    protected static void die(Exception e, String msg) {
        System.err.println(msg);
        e.printStackTrace();
        System.exit(-1);
    }

    public NetworkController() {
        try {
            this.addr = InetAddress.getLocalHost();
        } catch (IOException e) {
            die(e, "IOException trying to get localhost!");
        }
    }

    public NetworkController(InetAddress addr) {
        this.addr = addr;
    }

    public NetworkController(InetAddress addr, int port) {
        this.addr = addr;
        this.port = port;
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage:  NetworkController "
                    + " COMMAND [IP ADDR] [PORT]");
            System.exit(0);
        }

        //parse cmd line args and construct object
        NetworkController nc = null;
        try {
            if (args.length == 1) {
                nc = new NetworkController();
            } else if (args.length == 2) {
                nc = new NetworkController(InetAddress.getByName(args[1]));

            } else if (args.length == 3) {
                nc = new NetworkController(InetAddress.getByName(args[1]),
                        Integer.parseInt(args[2]));
            }
        } catch (UnknownHostException e) {
            die(e, "Couldn't parse hostname from command line.");
        }
        if (nc != null) {
            nc.establishConnection();
            nc.sendCommand(args[0]);
        } else {
            System.err.println("Usage:  NetworkController "
                    + " COMMAND [IP ADDR] [PORT]");
            System.exit(0);
        }
    }
}
