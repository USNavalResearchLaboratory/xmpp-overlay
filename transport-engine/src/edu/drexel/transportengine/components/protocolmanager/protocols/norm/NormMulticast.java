package edu.drexel.transportengine.components.protocolmanager.protocols.norm;

import mil.navy.nrl.norm.NormInstance;
import mil.navy.nrl.norm.NormSession;
import mil.navy.nrl.norm.NormStream;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Norm binding for the Gump multicast interface
 * <p/>
 * <p/>
 * Created by Ian Taylor Date: March 27th 20101
 */
public class NormMulticast {

    public static long startSimulationTime;
    // Only one norm instance per VM.
    private static NormInstance norminst;
    private static NORMEventDispatcher dispatcher;
    private NormSession session; // for this socket
    private String networkInterface = null;
    private InetAddress inetAddress = null;
    private byte ttl = -1;
    private NORMOutputStream outputStream;
    private int sessionID = 1;
    private boolean isBound = false;
    private boolean isOpen = false;
    private MessageFIFO outputStreamWriteSync;
    private MessageFIFO fifo = new MessageFIFO(10); // allow some room but not much otherwise
    // the application can get left too far behind
    // the application should be keeping up anyway....
    private boolean receiverStarted = false;
    private boolean senderStarted = false;
    private boolean reuseAddress = false; // default server
    private static Hashtable normSocketInstances = new Hashtable();
    private final int defaultPort; // default port if none is specified
    private static int socketInst = 0;
    public InetAddress multicastInterface = null;

    /**
     * Have to exit if we fail to instantiate a Norm instance as the user chose
     * it and we can't really continue without it.
     */
    static {
        try {
            System.err.println("NormMulticast: Instantiating NORM");
            startSimulationTime = System.currentTimeMillis();
            norminst = new NormInstance();
            System.err.println("NormMulticast: NORM Instantiated!");
            dispatcher = new NORMEventDispatcher(norminst);
            dispatcher.start();
            System.err.println("NormMulticast: Instantiating NORM");

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            System.err.println("Cannot instantiate Norm - see above error for more information");
            System.err.println("Try checking that your LD_LIBRARY_PATH is set to the locaiton of the \n"
                    + "NORM shared library, must exit now.");
            System.exit(1);
        }
    }

    public NormMulticast(int port) {
        ++socketInst;
        isOpen = true;
        defaultPort = port;
    }

    /**
     * Out of the Java API - gets the norm session associated with this datagram
     * socket.
     *
     * @return
     */
    public NormSession getSession() {
        return session;
    }

    /**
     * Gets the output stream associated with this Norm socket - extension to
     * Java API as DatagramSockets obviously do not have streams ...
     *
     * @return
     */
    public NORMOutputStream getOutputStream() {
        return outputStream;
    }

    /**
     * Starts a norm session - parameters ???? public native void
     * startSender(int sessionId, long bufferSpace, // int segmentSize, int
     * blockSize, int numParity) throws IOException; // public native void
     * startReceiver(long bufferSpace) throws IOException;
     *
     * @param mcastAddr
     * @param port
     * @throws IOException
     */
    public void startNORMSession(InetAddress mcastAddr, int port) throws IOException {
        if (session != null) {
            throw new IOException("Socket is already a member of a group - only 1 group is allowed");
        }

        outputStreamWriteSync = new MessageFIFO(1);

        String mcastAddrStr = mcastAddr.getHostAddress();
        System.err.println(System.currentTimeMillis() + "hostStr: " + mcastAddrStr);
        long nodeId = (long) (Math.random() * 10000); //NormNode.NORM_NODE_ANY;// 
        System.err.println(System.currentTimeMillis() + "nodeId: " + nodeId);

        System.err.println(System.currentTimeMillis() + " Norm starting, hostName: " + mcastAddr + ", " + " port: " + port);
        session = norminst.createSession(mcastAddrStr, port, nodeId);
        System.err.println(System.currentTimeMillis() + " NORM Session created for: " + mcastAddr + ", " + " port: " + port);

        if (multicastInterface != null) {
            System.out.println("Setting NORM multicast interface to " + multicastInterface.getHostAddress());
            session.setMulticastInterface(multicastInterface.getHostAddress());
        }
        System.err.println(System.currentTimeMillis() + " adding output stream write sync" + mcastAddr + ", " + " port: " + port);

        dispatcher.addOutputStreamWriteSync(session, outputStreamWriteSync); // one per session

        System.err.println("session: " + session);

        // Ian T - changed this to be equivalent to the NormStreamReceiver code.

        boolean useUnicastNACKs = !mcastAddr.isMulticastAddress();
        session.setDefaultUnicastNack(useUnicastNACKs);

        dispatcher.addSession(session, fifo);

        // TODO: Configurable
        session.setLoopback(false);
//        session.setRxPortReuse(true, true);

        ++sessionID;

        isBound = true;
        System.err.println("Done creating session.");
    }

    /**
     * Gets the output stream for a given session
     *
     * @param streamobj
     * @return
     */
    public static NormMulticast getSocketForNormStream(NormStream streamobj) {
        return (NormMulticast) normSocketInstances.get(streamobj);
    }

    public void joinGroup(InetAddress mcastaddr) throws IOException {
        startNORMSession(mcastaddr, defaultPort);
    }

    public void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf) throws IOException {
        startNORMSession(InetAddress.getByName(((InetSocketAddress) mcastaddr).getHostName()),
                ((InetSocketAddress) mcastaddr).getPort());

        session.setMulticastInterface(netIf.getName());
    }

    public void leaveGroup(InetAddress mcastaddr) throws IOException {
        System.err.println("Leaving group " + mcastaddr
                + " at sim time T+" + ((System.currentTimeMillis() - startSimulationTime) / 1000.0));
        cleanUpSession(); // leaving a group basically destroys the session because each session is
        // bound to a group and if you leave there is no session.
    }

    public void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf) throws IOException {
        System.err.println("Leaving group " + mcastaddr + " " + netIf
                + " at sim time T+" + ((System.currentTimeMillis() - startSimulationTime) / 1000.0));
        cleanUpSession();
    }

    public void send(DatagramPacket p, byte ttl) throws IOException {
        session.setTTL(ttl);
        send(p);
    }

    // TODO: Configurable
    private static final int BUFFER_SPACE = 1024 * 1024;
    private static final int RECV_BUFFER_SPACE = 1024 * 1024;
    private static final int SEGMENT_SIZE = 1400;
    private static final int BLOCK_SIZE = 64;
    private static final int NUM_PARITY = 16;
    private static final boolean AUTO_FLUSH = true;
    private static final int REPAIR_WINDOW_SIZE = 1024 * 1024;
    //

    public void send(DatagramPacket p) throws IOException {
        if (!senderStarted) {
            session.startSender(sessionID, BUFFER_SPACE, SEGMENT_SIZE,
                    BLOCK_SIZE, NUM_PARITY); // start sender anyway
            outputStream = new NORMOutputStream(session, NORMOutputStream.TcpMode.DEFAULT,
                    AUTO_FLUSH, REPAIR_WINDOW_SIZE);
            normSocketInstances.put(outputStream.getNormOutputStream(), this);
            senderStarted = true;
        }

        boolean writtenok;
        int offset = p.getOffset();
        int length = p.getLength() - offset;
        byte[] message = p.getData();

        do {
            outputStreamWriteSync.reset(); // start fresh before we write so we know we are in sync here
            try {
                outputStream.write(message, offset, length);
                writtenok = true;
            } catch (NORMBufferFullException e) {
                writtenok = false;
                int bytesWritten = e.getBytesWritten();
                length -= bytesWritten;
                offset += bytesWritten;

                long nowInsimulation = System.currentTimeMillis() - startSimulationTime;

                System.out.println("NormDebug at " + nowInsimulation + " BufferFull - Wrote: " + bytesWritten + " New length: "
                        + length + " New offset: " + offset);
                System.out.println("Waiting for sync for output stream!");

                long then = System.currentTimeMillis();
                System.out.println("Waiting for space to write.");
                boolean interrupted = true;
                while (interrupted) {
                    try {
                        outputStreamWriteSync.removeObject();
                        interrupted = false;
                    } catch (InterruptedException err) {  // if its interrupted then we should retry
                        Thread.yield();
                        interrupted = true;
                    }
                } // interrupted
                System.out.println("Space available after " + (System.currentTimeMillis() - then) + "ms");
            }
        } while (!writtenok);

        //  outputStream.getNormOutputStream().markEom();
    }

    /**
     * // TALMAGE: Could this be faster and use less memory if we made it //
     * zero-copy? // Ian T. Well, the input data is now 64K, so we should make a
     * buffer containing the actual // size really before passing it to the
     * proxy developer. So, we have to copy at some stage.
     *
     * @param p
     * @throws IOException @todo Need to check on datagram packet when the input
     *                     is greater than the space allocated
     */
    public void receive(DatagramPacket p) throws IOException {
        try {
            if (!receiverStarted) {
                // Page 5: Norm Manual:
                // "The repair boundary can also be set for individual remote senders using the
                // NormNodeSetRepairBoundary() function.NORM_OBJECT_FILE objects. This function
                // must be called before any file objects may be received and thus should be
                // called before any calls to NormStartReceiver() are made.
                // Ian T. How do we do this???  In Java, there is no such method...

                session.startReceiver(RECV_BUFFER_SPACE);
            }
            receiverStarted = true;
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        DatagramPacket packetArriving = null;

        boolean interrupted = true;
        while (interrupted) {
            try {
                packetArriving = (DatagramPacket) fifo.removeObject();
                interrupted = false;
            } catch (InterruptedException e) {  // if its interrupted then we should retry
                Thread.yield();
                interrupted = true;
            }
        } // interrupted

        int spaceAvailable = p.getLength() - p.getOffset();

        // Ian T.  I throw an error here for now.  I need to check what error Java throws
        // here to be consistent.

        if (packetArriving.getLength() > spaceAvailable) {
            throw new IOException("NormMulticast.receive(): Space available ("
                    + spaceAvailable + " insufficient for "
                    + packetArriving.getLength() + " bytes.");
        }

        int toCopy = packetArriving.getLength();
        p.setLength(toCopy);
        System.arraycopy(packetArriving.getData(), 0, p.getData(), p.getOffset(), toCopy);
        p.setAddress(packetArriving.getAddress());// who it came from
    }

    public void setInterface(InetAddress inf) throws SocketException {
        try {
            // networkInterface = inf.getHostName();
            inetAddress = inf;
            networkInterface = NetworkInterface.getByInetAddress(inf).getName();
            if (session != null) {
                session.setMulticastInterface(networkInterface);
            }
        } catch (IOException e) {
            throw new SocketException(e.getMessage());
        }
    }

    /**
     * Disable/Enable local loopback of multicast datagrams The option is used
     * by the platform's networking code as a hint for setting whether multicast
     * data will be looped back to the local socket.
     * <p/>
     * <p>Because this option is a hint, applications that want to verify what
     * loopback mode is set to should call
     * {@link #getLoopbackMode()}
     *
     * @param disable <code>true</code> to disable the LoopbackMode
     * @throws SocketException if an error occurs while setting the value
     * @see #getLoopbackMode
     * @since 1.4
     */
    public void setLoopbackMode(boolean disable) throws SocketException {
        // opposite for norm's call true means disable so !disable should equal true for norm call
        try {
            session.setLoopback(!disable);
        } catch (IOException e) {
            throw new SocketException(e.getMessage());
        }
    }

    public void setNetworkInterface(NetworkInterface netIf) throws SocketException {
        try {
            networkInterface = netIf.getName();
            if (session != null) {
                session.setMulticastInterface(networkInterface);
            }
        } catch (IOException e) {
            throw new SocketException(e.getMessage());
        }
    }

    public InetAddress getInterface() throws SocketException {
        if (networkInterface != null) {
            Enumeration en = NetworkInterface.getByName(networkInterface).getInetAddresses();
            return en.hasMoreElements() ? (InetAddress) en.nextElement() : null;
        } else {
            return null;
        }
    }

    public NetworkInterface getNetworkInterface() throws SocketException {
        if (networkInterface != null) {
            return NetworkInterface.getByName(networkInterface);
        } else if (inetAddress != null) {
            return NetworkInterface.getByInetAddress(inetAddress);
        } else {
            return null;
        }
    }

    public boolean getLoopbackMode() {
        return false;
    }

    public void setTimeToLive(int ttl) throws IOException {
        this.ttl = (byte) ttl;
        session.setTTL((byte) ttl);
    }

    public int getTimeToLive() {
        return ttl;
    }

    /**
     * This is not needed I think. We are not implementing datagram socket here,
     * the multicast address is the remote address which we want to bind to so
     * this is where we do the binding i.e. when we join a group ?????
     *
     * @param lport
     * @param laddr
     */
    public void bind(int lport, InetAddress laddr) {
        multicastInterface = laddr;
    }

    private void cleanUpSession() {
        if (outputStream != null) {
            try {
                // outputStream.flush(); // should we flush? not sure - it causes data to be sent and can mess
                // up the getnextevent
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            outputStream = null;
        }

        if (session != null) {
            session.stopSender();
            session.stopReceiver();
            session.destroySession();
            session = null;
        }

        dispatcher.close(session);

        receiverStarted = false;
        senderStarted = false;
    }

    public void close() {
        cleanUpSession();
        isOpen = false;
        isBound = false;
    }
}
