package edu.drexel.xop.stream;

/*
 * (c) 2013 Drexel University
 */

import edu.drexel.xop.client.XOPConnection;

import java.net.InetAddress;
import java.net.Socket;

/**
 * Handles socket connections from streams
 * //TODO: this should be a socks5 proxy, but it isn't implemented yet
 *
 * @author Rob Taglang
 */
public class StreamClientHandler implements XOPConnection {

    private Socket clientSocket;

    StreamClientHandler(Socket sock) {
        clientSocket = sock;
    }
    @Override
    public void writeRaw(byte[] bytes) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void processCloseStream() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public InetAddress getAddress() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getHostName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void run() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

}
