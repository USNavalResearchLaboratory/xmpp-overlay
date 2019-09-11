/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq;

import edu.drexel.xop.component.ComponentBase;
import edu.drexel.xop.component.ComponentManager;
import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.core.PacketFilter;
import edu.drexel.xop.iq.disco.ProxyDiscoIqHandler;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.router.PacketRouter;
import edu.drexel.xop.util.logger.LogUtils;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages IQ Queries directed at this server
 * 
 * @author David Millar
 */
public class IqManager extends ComponentBase {
    private static final Logger logger = LogUtils.getLogger(IqManager.class.getName());

    private List<IqHandler> filterRunners = new LinkedList<>();

    // Thread pool executor and associated parameters
    private ThreadPoolExecutor threadPoolExecutor;
    private LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

    private PacketRouter packetRouter;
    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 10;
    public static final int INACTIVE_THREAD_KEEP_ALIVE_MS = 10000;

    public IqManager(PacketRouter packetRouter, ComponentManager componentManager, RosterManager rosterManager) {
        // Setup thread pool executor with the parameters above
        logger.fine("Initializing ThreadPoolExecutor.");
        threadPoolExecutor = new ThreadPoolExecutor(MIN_THREADS, MAX_THREADS, INACTIVE_THREAD_KEEP_ALIVE_MS, TimeUnit.MILLISECONDS, workQueue);

        // Add all the filters
        this.addFilterHandler(rosterManager);
        this.addFilterHandler(new BindIqHandler());
        this.addFilterHandler(new SessionIqHandler());
        this.addFilterHandler(new ProxyDiscoIqHandler(componentManager));
        this.addFilterHandler(new PingIqHandler());
        this.addFilterHandler(new VCardIqHandler());

        this.packetRouter = packetRouter;
        this.packetRouter.addRoute(this);
    }

    /**
     * Add a FilterHandlerEntry. If the incoming IQ matches the filter, then the
     * Handler is invoked with the IQ, and a response is sent to the
     * responseHandler
     * 
     * @param filterHandler FilterHandler entry
     */
    public synchronized void addFilterHandler(IqHandler filterHandler) {
        filterRunners.add(filterHandler);
    }

    public synchronized void setFilterHandlers(List<IqHandler> entries) {
        for (IqHandler fhe : entries) {
            addFilterHandler(fhe);
        }
    }

    /**
     * Start a thread to resolve this iq
     * 
     * @param fr FilterRunnerEntry matching the request
     * @param iq iq stanza to handle
     */
    private void spinOffThread(IqHandler fr, IQ iq) {
        // Setup Runner
        IqRunner runner = new IqRunner(iq, fr);

        // Spin off thread
        threadPoolExecutor.submit(runner);
        logger.fine("IqRunner spun off. " + threadPoolExecutor.getCompletedTaskCount() + " tasks completed. "
            + threadPoolExecutor.getActiveCount() + " Active tasks.");
    }

    /**
     * Respond to unhandled IQ's with an error
     * 
     * @param iq the iq that was not handled
     */
    public static IQ getErrorForIq(IQ iq, PacketError.Condition condition, PacketError.Type type) {
        IQ response = IQ.createResultIQ(iq);
        response.setType(IQ.Type.error);

        PacketError error = new PacketError(condition, type);
        error.setText("Unfortunately, we do not support this feature at this time.");
        response.setError(error);

        logger.fine("Generating an error packet for: " + iq.toString());
        return response;
    }

    public static IQ getGenericErrorForIq(IQ iq) {
        return getErrorForIq(iq, PacketError.Condition.feature_not_implemented, PacketError.Type.cancel);
    }

    @Override
    public void processPacket(Packet p) {
        IQ iq = (IQ) p;

        logger.finest("Number of 'filterRunners': " + filterRunners.size());
        // Pass the iq to the first handler that accepts it
        for (IqHandler fr : filterRunners) {
            logger.finest("Trying " + fr.getClass().getSimpleName());
            if (((PacketFilter) fr).accept(iq)) {
                logger.fine("Accepted by': " + fr.getClass().getSimpleName());
                spinOffThread(fr, iq);
                return;
            }
            logger.finest("Rejected by': " + fr.getClass().getSimpleName());
        }
        logger.finest("Rejected by all 'filterRunners'");

        // If we get here, the IQ was not handled
        // if these aren't addressed, the client may
        // queue up a lot of requests and have memory issues
        IQ error = getGenericErrorForIq(iq);
        ClientProxy.getInstance().processPacket(error);
    }

    @Override
    public void processCloseStream(String fromJID) {
        // TODO Seriously what do we do here
    }

    @Override
    public boolean accept(Packet p) {
        if (p instanceof IQ) {
            if (p.getFrom() != null
                && p.getFrom().toString().equals(XopProperties.getInstance().getProperty(XopProperties.DOMAIN))) {
                logger.fine("Ignoring IQ message from the server");
                return false;
            }
            IQ iq = (IQ) p;
            // Pass the iq to the first handler that accepts it
            for (IqHandler fr : filterRunners) {
                logger.finest("Trying " + fr.getClass().getSimpleName());
                if (((PacketFilter) fr).accept(iq)) {
                    logger.fine("Accepted by: " + fr.getClass().getSimpleName());
                    return true;
                }
                logger.finest("Rejected by: " + fr.getClass().getSimpleName());
            }
            logger.finest("Rejected by all 'filterRunners'");
        }
        return false;
    }
}
