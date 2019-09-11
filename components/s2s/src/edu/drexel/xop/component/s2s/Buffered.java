/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.Packet;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tradams
 */
public class Buffered {
    private static final Logger logger = LogUtils.getLogger(Buffered.class.getName());

    private static final int maxThreads = 20;
    private static final int queueSize = 50;

    private BlockingQueue<Packet> packets = new LinkedBlockingQueue<>();
    private ThreadPoolExecutor threadPool;
    private boolean shutdown = false;
    private Map<String, PacketsProcessor> packetsProcessors = new HashMap<>();
    private StreamManager manager;

    public Buffered(StreamManager manager) {
        super();
        this.manager = manager;
        init();

    }

    private void init() {
        // Create a pool of threads that will process queued packets.
        threadPool =
                new ThreadPoolExecutor(Math.round(maxThreads / 4), maxThreads, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(queueSize),
                new ThreadPoolExecutor.CallerRunsPolicy());

        Thread thread = new Thread(new Runnable() {

            public void run() {
                while (!shutdown) {
                    try {
                        if (threadPool.getActiveCount() < threadPool.getMaximumPoolSize()) {
                            // Wait until a packet is available
                            final Packet packet = packets.take();

                            boolean newProcessor = false;
                            PacketsProcessor packetsProcessor;
                            String domain = packet.getTo().getDomain();
                            packetsProcessor = packetsProcessors.get(domain);

                            if (packetsProcessor == null) {

                                packetsProcessor =
                                        new PacketsProcessor(Buffered.this, domain, manager);
                                packetsProcessors.put(domain, packetsProcessor);
                                newProcessor = true;

                            }
                            packetsProcessor.addPacket(packet);

                            if (newProcessor) {
                                // Process the packet in another thread
                                threadPool.execute(packetsProcessor);
                            }
                        } else {
                            // No threads are available so take a nap :)
                            Thread.sleep(200);
                        }
                    } catch (InterruptedException e) {
                        // Do nothing
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                }
            }
        }, "Queued Packets Processor");
        thread.setDaemon(true);
        thread.start();
    }

    public void addPacket(Packet pack) {
        packets.add(pack);
    }

    private void processorDone(PacketsProcessor packetsProcessor) {
        if (packetsProcessor.isDone()) {
            packetsProcessors.remove(packetsProcessor.getDomain());
        } else {
            threadPool.execute(packetsProcessor);
        }
    }

    private static class PacketsProcessor implements Runnable {

        private Buffered promise;
        private String domain;
        private Queue<Packet> packetQueue = new ConcurrentLinkedQueue<>();
        private long failureTimestamp = -1;
        private StreamManager manager;

        public PacketsProcessor(Buffered promise, String domain, StreamManager manager) {
            this.promise = promise;
            this.manager = manager;
            this.domain = domain;

        }

        public void run() {
            Thread.currentThread().setName("S2S Packets Processor");
            while (!isDone()) {
                Packet packet = packetQueue.peek();
                if (packet != null) {
                    // Check if s2s already failed
                    if (failureTimestamp > 0) {
                        // Check if enough time has passed to attempt a new s2s
                        if (System.currentTimeMillis() - failureTimestamp < 5000) {
                            // ignore this for now
                            // TODO: figure out what is supposed to happen here - millar
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ex) {
                                logger.log(Level.SEVERE, "interupted", ex);
                                continue;
                            }
                        }
                        // Reset timestamp of last failure since we are ready to try again doing a s2s
                        failureTimestamp = -1;

                    }
                    try {
                        sendPacket(packet);
                        packetQueue.poll();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "DIALBACK: Error sending packet to remote server: " + packet, e);
                        // Mark the time when s2s failed
                        failureTimestamp = System.currentTimeMillis();
                    }
                }
            }
            promise.processorDone(this);
        }

        private void sendPacket(Packet packet) throws Exception {
            // Create a connection to the remote server from the domain where the packet has been sent
            boolean created = true;
            ServerConnection connection = null;
            try {
                connection = manager.getConnectionForDomain(packet.getTo().getDomain());

            } catch (FailedSetupException ex) {
                logger.log(Level.SEVERE, null, ex);
                created = false;
            }
            if (created) {
                if (connection != null) {
                    synchronized (connection) {
                        try {
                            connection.sendPacket(packet);
                        } catch (Exception ioe) {
                            logger.log(Level.SEVERE, "IOException sending packet: " + packet.toString());
                            manager.reapConnection(connection);
                            connection.close();

                            throw ioe;
                        }
                    }
                }

            } else {
                throw new Exception("Failed to create connection to remote server");
            }
        }

        public void addPacket(Packet packet) {
            packetQueue.add(packet);
        }

        public String getDomain() {
            return domain;
        }

        public boolean isDone() {
            return packetQueue.isEmpty();
        }
    }
}
