/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.router;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmpp.packet.Packet;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Queues up packets to be fed to the PacketRouter.<br/>
 * Uses a @ThreadPoolExecutor to deliver packets in threads.
 *
 * @author David Millar
 */
class ThreadedPacketDeliveryQueue {

    private static final Logger logger = LogUtils.getLogger(ThreadedPacketDeliveryQueue.class.getName());

    private ThreadPoolExecutor executor = null;
    private PacketRouter pktRouter = null;

    public ThreadedPacketDeliveryQueue(PacketRouter pktRouter) {
        this.pktRouter = pktRouter;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();

        XopProperties props = XopProperties.getInstance();
        int CORE_POOL_SIZE = props.getIntProperty(XopProperties.PACKET_HANDLER_CORE_POOL_SIZE);
        int MAX_POOL_SIZE = props.getIntProperty(XopProperties.PACKET_HANDLER_MAX_POOL_SIZE);
        int KEEP_ALIVE_TIME = props.getIntProperty(XopProperties.PACKET_HANDLER_KEEP_ALIVE_TIME);

        // If MAX_POOL_SIZE is set to 0, make it Integer.MAX_VALUE, essentially unbounded
        if (MAX_POOL_SIZE <= 0) {
            MAX_POOL_SIZE = Integer.MAX_VALUE;
        }
        RejectedExecutionHandler rejectedExecutionHandler = new PacketHandlerRejectedExecutionHandler();

        executor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS, workQueue, rejectedExecutionHandler);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler);

        logger.log(Level.CONFIG, "Thread Pool -- Core Pool: " + CORE_POOL_SIZE + " Max  Pool: " + MAX_POOL_SIZE + " Keepalive: " + KEEP_ALIVE_TIME);

    }

    public void enqueuePacket(Packet packet) {
        executor.submit(new PacketDelivererTask(packet));
    }

    public void stop() {
        logger.fine("Shutting down ThreadPool");
        executor.shutdown();
    }

    public List<Runnable> stopNow() {
        return executor.shutdownNow();
    }

    // Simple Runnable to deliver a packet
    private class PacketDelivererTask implements Runnable {
        Packet packet = null;

        public PacketDelivererTask(Packet packet) {
            this.packet = packet;
        }

        public void run() {
            Thread.currentThread().setName("ThreadedPacketDeliveryQueue Worker");
            logger.fine("routing packet!");
            pktRouter.route(packet);
            logger.fine("done routing packet!");
        }

        public Packet getPacket() {
            return packet;
        }

        @Override
        public String toString() {
            return "PacketDelivererTask Packet:\n " + packet.toString();
        }
    }

    /**
     * Logs rejected packet delivery tasks and the state of the IncomingClientPacketQueue
     */
    private static class PacketHandlerRejectedExecutionHandler implements RejectedExecutionHandler {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            PacketDelivererTask task = (PacketDelivererTask) r;
            logger.log(Level.WARNING, "Unable to deliver packet: \n{0}\nExecutor: \n{1}", new Object[]{task.getPacket().toString(), executor.toString()});
        }
    }
}
