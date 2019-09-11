/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.interceptor;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import edu.drexel.xop.util.ConcurrentLimitedLinkedQueue;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Rejects packets with the same hash value
 * TODO: Is this necessary anymore? SMF won't pass up forwarded (i.e. duplicate) packets to XOP and the Transport Engine won't deliver duplicate XMPP messages to XOP either (with some exceptions that shouldn't happen). 
 * @author David Millar
 */
public class DuplicatePacketInterceptor implements PacketInterceptor {
    private static final Logger logger = LogUtils.getLogger(DuplicatePacketInterceptor.class.getName());

    private ConcurrentLimitedLinkedQueue<String> msg_q;
    private HashMap<String, ConcurrentLimitedLinkedQueue<String>> prs_q;
    private final static int DEFAULT_DUPLICATE_PACKET_INTERCEPTOR_QUEUE_SIZE = 512;

    public DuplicatePacketInterceptor() {
        this(DEFAULT_DUPLICATE_PACKET_INTERCEPTOR_QUEUE_SIZE);
    }

    public DuplicatePacketInterceptor(int size) {
        msg_q = new ConcurrentLimitedLinkedQueue<>(size);
        prs_q = new HashMap<>();
    }

    private String getHash(Packet p) {
        try {
            return new String(Utils.MD5(p.toXML()), Charset.forName("iso-8859-1"));
        } catch (NoSuchAlgorithmException e) {
            logger.severe("Unable to use MD5 algorithm!");
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            logger.severe("Unable to use iso-8859-1 encoding!");
            e.printStackTrace();
        }
        logger.severe("Unable to generate md5 string! Returning random number as a hash.");
        return String.valueOf(Math.random());
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.drexel.xop.component.PacketInterceptor#interceptPacket(org.xmpp.packet.Packet)
     */
    @Override
    public boolean interceptPacket(Packet p) {
        logger.finer("Checking for duplicates");
        if (p instanceof Message) {
            String hash = getHash(p);
            logger.finer("Hash: " + hash);
            if (msg_q.contains(hash)) {
                logger.finer("Dropping duplicate message packet");
                return false;
            } else {
                logger.finer("Adding packet to duplicate message packet queue");
                msg_q.add(hash);
                return true;
            }
        } else if (p instanceof Presence && p.getFrom() != null) {
            String from = p.getFrom().toString();
            String hash = getHash(p);
            if (prs_q.containsKey(from)) {
                // TODO: Figure out if we use this, and solve this bug if we do
                if (hash.equals((prs_q.get(from)).contains(hash))) {
                    logger.finer("Dropping duplicate presence packet");
                    return false;
                } else {
                    logger.finer("Adding packet to duplicate presence packet queue");
                    (prs_q.get(from)).add(hash);
                    return true;
                }
            } else {
                logger.finer("Got a presence from a new address");
                logger.finer("Adding packet to duplicate presence packet queue");
                ConcurrentLimitedLinkedQueue<String> tmp = new ConcurrentLimitedLinkedQueue<>(1);
                tmp.add(hash);
                prs_q.put(from, tmp);
                return true;
            }
        } else {
            logger.finer("Not a message packet");
            return true;
        }
    }
}