package edu.drexel.transportengine.components.ddm;

import edu.drexel.transportengine.components.Component;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Reads stats from <code>/proc</code> about network traffic.
 *
 * @author Rob Lass <urlass@drexel.edu>
 */
public class StatsManager extends Component {
    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private String iface;
    private boolean readyToReport = false;
    private long lastRxPackets = 0;
    private long lastRxBytes = 0;
    private long rxPacketsPerUnit = 0;
    private long rxBytesPerUnit = 0;
    private int SLEEP_MS;
    private long BYTES_PER_UNIT_THRESHOLD;

    /**
     * Instantiates a new statistics manager.
     *
     * @param iface the interface to monitor.
     */
    public StatsManager(TransportEngine engine, String iface) {
        super(engine);

        this.BYTES_PER_UNIT_THRESHOLD = Configuration.getInstance().getValueAsLong(TEProperties
                .DDM_BYTES_PER_UNIT_THRESHOLD);
        this.SLEEP_MS = Configuration.getInstance().getValueAsInt(TEProperties
                .DDM_SECONDS_PER_UNIT) * 1000;
        this.iface = iface;
    }

    /**
     * Gets the line in <code>/proc/net/dev</code> for the specified interface.
     *
     * @param iface interface to query.
     * @return line for <code>interface</code>
     */
    private String getInterfaceLine(String iface) {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/net/dev"));
            String line;
            while ((line = br.readLine().trim()) != null) {
                if (line.startsWith(iface)) {
                    return line;
                }
            }
            br.close();
        } catch (IOException e) {
            logger.warning("Exception getting interface line.");
        }
        logger.warning("Could not get interface line.");
        return null;
    }

    /**
     * Gets the number of received packets per time unit.
     * <p/>
     * This function will block until this thread has initialized itself (takes a few cycles).
     *
     * @return the number of packets per time unit.
     */
    public synchronized long getRxPacketsPerUnit() {
        while (!readyToReport) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        return this.rxPacketsPerUnit;
    }

    /**
     * Gets the number of received bytes per time unit.
     * This function will block until this thread has initialized itself (takes a few cycles).
     *
     * @return the number of bytes per time unit.
     */
    public synchronized long getRxBytesPerUnit() {
        while (!readyToReport) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        return this.rxBytesPerUnit;
    }

    /**
     * Periodically polls the <code>/proc/net/dev</code> file for network statistics.
     */
    @Override
    public void run() {
        while (true) {
            String line = getInterfaceLine(this.iface);
            if (line != null) {
                long thisRxPackets = Long.parseLong(line.split(" +")[2]);
                long thisRxBytes = Long.parseLong(line.split(" +")[1]);

                //calculate change since last time
                this.rxPacketsPerUnit = thisRxPackets - this.lastRxPackets;
                this.rxBytesPerUnit = thisRxBytes - this.lastRxBytes;

                this.lastRxPackets = thisRxPackets;
                this.lastRxBytes = thisRxBytes;
                readyToReport = true;
                synchronized (this) {
                    notifyAll();
                }
            }

            try {
                sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            getTransportEngine().executeEvent(
                    new Event<>(
                            new DDMMessage(getTransportEngine().getGUID(), "bandwidth",
                                    rxBytesPerUnit > BYTES_PER_UNIT_THRESHOLD ? "high" : "low")));
        }
    }

    @Override
    public void handleEvent(Event event) {
    }
}