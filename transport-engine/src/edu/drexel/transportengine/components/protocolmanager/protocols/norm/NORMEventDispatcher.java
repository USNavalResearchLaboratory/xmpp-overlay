package edu.drexel.transportengine.components.protocolmanager.protocols.norm;

import mil.navy.nrl.norm.*;
import mil.navy.nrl.norm.enums.NormEventType;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Collection;
import java.util.HashMap;

/**
 * The class reads in incoming data from multiple NORM streams and converts them
 * to DatagramPackets for dispatching to an application.
 * <p/>
 * Created by Ian Taylor Date: Mar 26, 2010 Time: 12:45:06 PM
 */
public class NORMEventDispatcher extends Thread {

    NormInstance instance;
    NormEvent event;
    NORMMessageListener messageListener;
    HashMap<NormSession, MessageFIFO> sessionFifos = new HashMap<>();
    HashMap<NormSession, MessageFIFO> sessionOutputSyncs = new HashMap<>();

    public NORMEventDispatcher(NormInstance instance) {
        this.instance = instance;
    }

    public NORMMessageListener getMessageListener() {
        return messageListener;
    }

    public void setMessageListener(NORMMessageListener messageListener) {
        this.messageListener = messageListener;
    }

    public void addOutputStreamWriteSync(NormSession session, MessageFIFO outputStreamWriteSync) {
        sessionOutputSyncs.put(session, outputStreamWriteSync);
    }

    /**
     * Adds a session to the dispatcher. Each session has a fifo for queuing
     * input data packets received through this event dispatcher.
     *
     * @param session the norm session handle.
     * @param fifo    the message FIFO queue.
     */
    public void addSession(NormSession session, MessageFIFO fifo) {
        sessionFifos.put(session, fifo);
    }

    public void run() {
        try {
            runDispatcher();
        } catch (Throwable throwable) {
            throwable.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void runDispatcher() throws Throwable {
        while ((event = instance.getNextEvent()) != null) {
            NormEventType eventType = event.getType();
            NormObject normObject = event.getObject();
            NormSession session = event.getSession();

            switch (eventType) {
                case NORM_RX_OBJECT_NEW: // Stream Created for new Sender ....
                    if (normObject instanceof NormStream) {
                        NormStream normStreamobj;
                        normStreamobj = (NormStream) normObject;
                        //  DON'T NOTIFY FOR STREAM CREATED....
                    }
                    break;
                case NORM_RX_OBJECT_UPDATED: // Stream updated = data to read ....
                    if (normObject instanceof NormStream) {
                        NormStream normStreamobj;
                        normStreamobj = (NormStream) normObject;
                        notifyOrQueueMessage(normStreamobj, session);
                    }
                    break;
                case NORM_TX_QUEUE_EMPTY: // Output Stream is empty, ready to write ....
                    if (normObject instanceof NormStream) {
                        MessageFIFO outputStreamWriteSync = sessionOutputSyncs.get(session);
                        NormStream normStreamobj = (NormStream) normObject;
                        if ((outputStreamWriteSync != null) && (outputStreamWriteSync.getSize() == 0)) {
                            new SyncControl(outputStreamWriteSync).start();   // Do not block the Norm intersection !!!
                        }
                    }
                    break;
                case NORM_TX_QUEUE_VACANCY: // Called when buffer has some room.
                    if (normObject instanceof NormStream) {
                        MessageFIFO outputStreamWriteSync = sessionOutputSyncs.get(session);
                        NormStream normStreamobj = (NormStream) normObject;
                        if ((outputStreamWriteSync != null) && (outputStreamWriteSync.getSize() == 0)) {
                            new SyncControl(outputStreamWriteSync).start();   // Do not block the Norm intersection !!!
                        }
                    }
                    break;
                case NORM_RX_OBJECT_COMPLETED:
                    if (normObject instanceof NormStream) {
                        NormStream normStreamobj;
                        normStreamobj = (NormStream) normObject;
                        //  DON'T NOTIFY FOR STREAM Closed....
                    }
                    break;
            }
        }
    }

    /**
     * Class that provides a non-blocking mechanism to tell the datagram send()
     * method that the stream is ok to send data to.
     */
    class SyncControl extends Thread {

        MessageFIFO syncObj;

        public SyncControl(MessageFIFO syncObj) {
            this.syncObj = syncObj;
        }

        public void run() {
            boolean interrupted = true;
            while (interrupted) {
                try {
                    syncObj.addObject(syncObj); // we can add whatever object we want here.  It is not used
                    // for anything except to sync.
                    interrupted = false;
                } catch (InterruptedException e) {  // if its interrupted then we should retry
                    Thread.yield();
                    interrupted = true;
                }
            } // interrupted
        }
    }

    /**
     * Either Queues the message (blocking) or notifies the message listener
     * <p/>
     * TODO: Read from the stream until read() returns fewer bytes than we ask
     * for. Deliver all bytes to the fifo. Invoke NormStream.seekMsgStart() if
     * read() returns -1.
     *
     * @param stream the stream handle.
     */
    public void notifyOrQueueMessage(NormStream stream, NormSession session) throws IOException {
        int numRead;

        MessageFIFO fifo = sessionFifos.get(session);

        // TALMAGE: Why not throw a more specific Exception?
        // This is an IOException at least.
        // Ian T. Yes, could do.  This really should never really happen if norm is
        // behaving correctly. We should probably exit also, if it does.
        if (fifo == null) { // We lose the packet after this.
            throw new IOException("Fifo not found for session " + session
                    + "! No such session error - was the socket deleted?");
        }

        boolean moreData = true;

        // TODO - read method returns -1 when the stream nis out of sync
        // ths is related to profiles. We either  need to mark boundaries
        // if we understand the message structure well enough
        // otherwise we simply read again.

        while (moreData) {
            byte[] buf = new byte[65536];
            numRead = stream.read(buf, 0, buf.length);

            if (numRead == -1) {
                // This is dumb TCP mode
                //
                // continue; // Could do this instead of throwing IOException
                throw new IOException("Norm Stream out of whack!");
            }

            DatagramPacket packet = new DatagramPacket(buf, 0, numRead);
            packet.setSocketAddress(stream.getSender().getAddress());

            if (messageListener != null) {
                messageListener.messageReceived(packet);
            } else {
                boolean interrupted = true;
                while (interrupted) {
                    try {
                        fifo.addObject(packet);
                        interrupted = false;
                    } catch (InterruptedException e) {  // if its interrupted then we should retry
                        Thread.yield();
                        interrupted = true;
                    }
                } // interrupted
            }  // else

            moreData = (numRead == buf.length);
        }
    }

    /**
     * Cleans up this dispatcher object for garbage collection
     */
    public void close() {

        Collection syncs = sessionOutputSyncs.values();

        for (Object fifo : syncs) {
            ((MessageFIFO) fifo).reset();
        }

        sessionOutputSyncs.clear();

        Collection sessions = sessionFifos.values();

        for (Object fifo : syncs) {
            ((MessageFIFO) fifo).reset();
        }

        sessionFifos.clear();
    }

    /**
     * Cleans up this dispatcher object for a given session
     */
    public void close(NormSession session) {

        MessageFIFO osync = sessionOutputSyncs.get(session);
        if (osync != null) {
            osync.reset();
        }

        MessageFIFO fifo = sessionFifos.get(session);
        if (fifo != null) {
            fifo.reset();
        }
    }
}
