/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.util.logger.LogUtils;

/**
 * 
 * @author tradams
 */
public class StreamManager {

    private static final Logger logger = LogUtils.getLogger(StreamManager.class.getName());
    private Map<String, ServerConnection> connections = new HashMap<>();
    private PacketRoutingDevice router;

    public StreamManager(PacketRoutingDevice route) {
        router = route;
    }

    public void reapConnection(ServerConnection connection) {
        synchronized (connection) {
            Object[] ary = connections.keySet().toArray();
            // TODO: fix this to a param list
            for (int i = ary.length - 1; i >= 0; i--) {
                if (connections.get(ary[i]).equals(connection)) {
                    connections.remove(ary[i]);
                }
            }
        }
    }

    public ServerConnection getConnectionForDomain(String domain) throws FailedSetupException {
        ServerConnection conn = connections.get(domain);
        if (conn == null) {
            for (String dom : connections.keySet()) { // try to piggyback
                if (domain.endsWith(dom)) {
                    conn = connections.get(dom);
                    boolean valid = conn.piggyback(domain);
                    if (valid) {
                        connections.put(domain, conn);
                        break;
                    } else {
                        conn = null;
                    }
                }
            }
            if (conn == null) { // all piggybacking failed so create a new one
                conn = new ServerConnection(router);
                try {
                    conn.init_outgoing(domain);
                    connections.put(domain, conn);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                    throw new FailedSetupException(ex.getMessage());
                }
                conn.start();
            }
        }
        return conn;
    }
}
