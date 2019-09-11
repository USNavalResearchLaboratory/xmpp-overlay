package edu.drexel.transportengine.components.protocolmanager.protocols.norm;

/**
 * Simple Message FIFO Queue
 * <p/>
 * <p/>
 * Created by Ian Taylor Date: Mar 17, 2009 Time: 9:46:38 AM
 */
public class MessageFIFO extends Thread {

    private Object[] queue;
    private int capacity;
    private int size;
    private int head;
    private int tail;

    public MessageFIFO(int cap) {
        capacity = (cap > 0) ? cap : 1; // at least 1
        queue = new Object[capacity];
        head = 0;
        tail = 0;
        size = 0;
    }

    public synchronized int getSize() {
        return size;
    }

    public synchronized boolean isFull() {
        return (size == capacity);
    }

    public synchronized void addObject(Object obj) throws InterruptedException {
        // logger.fine("FIFO: Adding object");
        while (isFull()) {
            //System.out.println("FIFO full " + capacity);
            wait();
        }

        queue[head] = obj;
        head = (head + 1) % capacity;
        size++;

        notifyAll(); // let any waiting threads know about change

    }

    public synchronized Object removeObject() throws InterruptedException {
        //      logger.fine("FIFO: Removing object");
        while (size == 0) {
            // System.out.println("FIFO empty " + capacity);
            wait();
        }

        Object obj = queue[tail];
        queue[tail] = null; // don't block GC by keeping unnecessary reference
        tail = (tail + 1) % capacity;
        size--;

        notifyAll(); // let any waiting threads know about change

        return obj;
    }

    public synchronized void reset() {
        try {
            while (size > 0) {
                removeObject();
                notifyAll();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public synchronized void printState() {
        StringBuffer sb = new StringBuffer();

        sb.append("TAPSObjectFIFO:\n");
        sb.append("       capacity=" + capacity + "\n");

        sb.append("           size=" + size);
        if (isFull()) {
            sb.append(" - FULL");
        } else if (size == 0) {
            sb.append(" - EMPTY");
        }
        sb.append("\n");

        sb.append("           head=" + head + "\n");
        sb.append("           tail=" + tail + "\n");

        for (int i = 0; i < queue.length; i++) {
            sb.append("       queue[" + i + "]=" + queue[i] + "\n");
        }

        System.out.print(sb);
    }

    public void run() {

        int c = 0;

        while (c < 10) {
            Object obj = null;
            try {
                Thread.sleep(2000);
                obj = removeObject();
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            ++c;
        }
    }
}