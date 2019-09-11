package edu.drexel.transportengine.components.ddm;

import edu.drexel.transportengine.components.Component;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.TransportProperties;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * A class representing a peer-to-peer agent.  This agent exchanges data with neighbors about
 * the state of the network to make a distributed decision.
 *
 * @author <a href="http://www.cs.drexel.edu/~urlass" target="_blank">Rob
 *         Lass</a>
 * @author <a href="mailto:ar374@drexel.edu">Aaron Rosenfeld</a>
 *         Modified for use in the TransportEngine.
 */
public class PTPAgent extends Component {
    protected final Logger logger = LogUtils.getLogger(this.getClass().getName());

    // These values are straight out of the paper, except lambda which was 80
    private double denseMu = 0.1825;
    private double denseSigmaSq = 0.019;
    private double sparseMu = 0.38;
    private double sparseSigmaSq = 0.0105;
    private double lambda = 100;
    private long freshPeriod;
    private int refreshMs;
    private double elDense;
    private double elSparse;
    private Map<String, Observation> observations;
    private List<Event<DDMMessage>> buffer;
    private Set<String> neighbors;
    private String lastPublishedState;
    private String state;

    /**
     * Instantiates a new agent.
     *
     * @param engine reference to the Transport Engine.
     */
    public PTPAgent(TransportEngine engine) {
        super(engine);
        state = new Random().nextBoolean() ? "dense" : "sparse";

        int numNodes = Configuration.getInstance().getValueAsInt(TEProperties.DDM_NUM_NODES);
        int denseThreshold = Configuration.getInstance().getValueAsInt(TEProperties.DDM_DENSE_THRESHOLD);
        freshPeriod = Configuration.getInstance().getValueAsInt(TEProperties.DDM_FRESH_PERIOD_MS);
        refreshMs = Configuration.getInstance().getValueAsInt(TEProperties.DDM_REFRESH_MS);
        lambda = Configuration.getInstance().getValueAsInt(TEProperties.DDM_LAMBDA);
        denseMu *= numNodes * denseThreshold;
        denseSigmaSq *= numNodes;
        sparseMu *= numNodes * denseThreshold;
        sparseSigmaSq *= numNodes;
        lastPublishedState = null;

        elDense = 0;
        elSparse = 0;
        observations = Collections.synchronizedMap(new HashMap<String, Observation>());
        buffer = Collections.synchronizedList(new ArrayList<Event<DDMMessage>>());
        neighbors = Collections.synchronizedSet(new HashSet<String>());
    }

    /**
     * Updates the state and publishes a <code>DDMMessage</code> every <code>refreshMs</code> milliseconds.
     */
    @Override
    public void run() {
        while (true) {
            elDense = -Math.log(gaussianProbability(neighbors.size(), denseMu,
                    denseSigmaSq));
            elSparse = -Math.log(gaussianProbability(neighbors.size(), sparseMu,
                    sparseSigmaSq));

            updateState();

            DDMMessage msg = new DDMMessage(getTransportEngine().getGUID(), "density", state);
            getTransportEngine().executeEvent(new Event<>("ddm-channel", new
                    TransportProperties(false, 0, false), msg));

            try {
                Thread.sleep(refreshMs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Aggregates state information to determine if the network is dense or sparse.
     */
    private void updateState() {
        //synchronized (buffer) {
        for (Event<DDMMessage> message : buffer) {
            DDMMessage contents = message.getContents();
            observations.put(contents.getId(), new Observation(contents.getValue(), System.currentTimeMillis()));
        }
        //}

        int numDense = 0;
        int numSparse = 0;

        Iterator<Map.Entry<String, Observation>> it = observations.entrySet().iterator();
        while (it.hasNext()) {
            // Remove old observations
            Map.Entry<String, Observation> ai = it.next();
            if (System.currentTimeMillis() - ai.getValue().getTime() > freshPeriod) {
                it.remove();
            } else {
                // Count new observations
                if (ai.getValue().getObservation().equals("dense")) {
                    numDense++;
                } else {
                    numSparse++;
                }
            }
        }

        double esDense = 0;
        double esSparse = 0;
        if (!observations.isEmpty()) {
            esDense = lambda * (1.0 * numDense / observations.size());
            esSparse = lambda * (1.0 * numSparse / observations.size());
        }

        if (elDense + esDense > elSparse + esSparse) {
            state = "dense";
        } else {
            state = "sparse";
        }

        if (lastPublishedState == null || !lastPublishedState.equals(state)) {
            logger.fine("P2PStatus "
                    + getTransportEngine().getTimeRunning()
                    + " " + getTransportEngine().getGUID()
                    + " " + (elDense + esDense)
                    + " " + (elSparse + esSparse)
                    + " " + neighbors.size()
                    + " " + state.toUpperCase());
            lastPublishedState = state;

            if (getTransportEngine().isEmulated()) {
                String path = "/usr/lib/core/icons/normal/router_" + (state.equals("dense") ? "green" : "red") + ".gif";
                try {
                    Runtime.getRuntime().exec("coresendmsg.py -a 172.16.0.254 node icon=" + path + " number=" + getTransportEngine().getGUID().substring(1));
                } catch (IOException ex) {
                    logger.warning("Could not set node color.");
                }
            }
        }

        buffer.clear();
        neighbors.clear();
    }

    /**
     * Listens for DDMMessages from remote instances, and adds them to <code>buffer</code>.
     *
     * @param event the event to process.
     */
    @Override
    public void handleEvent(Event event) {
        if (!event.getEventSrc().equals(getTransportEngine().getGUID()) &&
                event.getContents() instanceof DDMMessage) {
            Event<DDMMessage> msg = (Event<DDMMessage>) event;
            neighbors.add(msg.getContents().getId());
            buffer.add(event);
        }
    }

    /**
     * Figure out the probability given the parameters for a Gaussian
     * distribution and the value of $x$.
     */
    private double gaussianProbability(double x, double mu, double sigmaSq) {
        double p = 1 / Math.sqrt(sigmaSq * 2 * Math.PI);
        p *= Math.pow(Math.E, -1 * ((x - mu) * (x - mu)) / (2 * sigmaSq));
        return p;
    }

    /**
     * Class representing an observation.
     */
    private class Observation {
        private String value;
        private long time;

        /**
         * Instantiate a new observation.
         *
         * @param value the value of the observation.
         * @param time  the time the observation was taken.
         */
        public Observation(String value, long time) {
            this.value = value;
            this.time = time;
        }

        /**
         * Gets the observation value.
         *
         * @return observation value.
         */
        public String getObservation() {
            return value;
        }

        /**
         * Gets the time of the observation.
         *
         * @return time of the observation.
         */
        public long getTime() {
            return time;
        }
    }
}
