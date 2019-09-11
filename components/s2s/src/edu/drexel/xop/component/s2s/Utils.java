/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

import edu.drexel.xop.util.logger.LogUtils;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author charlesr
 */
public class Utils {
    
    private static final Logger logger = LogUtils.getLogger(Utils.class.getName());

    public static String SHA1(String text)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md;
        md = MessageDigest.getInstance("SHA-1");
        md.update(text.getBytes("iso-8859-1"), 0, text.length());
        byte[] sha1hash = md.digest();
        return convertToHex(sha1hash);
    }

    private static String convertToHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
        for (byte aData : data) {
            int halfbyte = (aData >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9)) {
                    buf.append((char) ('0' + halfbyte));
                } else {
                    buf.append((char) ('a' + (halfbyte - 10)));
                }
                halfbyte = aData & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }


    private static DirContext context;

    static {
        try {
            context = new InitialDirContext();
        } catch (NamingException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

    }

    public static DNSEntry resolveXMPPServerDomain(String domain, int defaultPort) {
        
        String host = domain;
        int port = defaultPort;
        try {
            javax.naming.directory.Attributes dnsLookup = context.getAttributes("_xmpp-server._tcp." + domain, new String[]{"SRV"});
            String srvRecord = (String) dnsLookup.get("SRV").get();
            String[] srvRecordEntries = srvRecord.split(" ");
            port = Integer.parseInt(srvRecordEntries[srvRecordEntries.length - 2]);
            host = srvRecordEntries[srvRecordEntries.length - 1];
        } catch (Exception e) {
            // Attempt lookup with older "jabber" name.
            try {
                javax.naming.directory.Attributes dnsLookup = context.getAttributes("_jabber._tcp." + domain, new String[]{"SRV"});
                String srvRecord = (String) dnsLookup.get("SRV").get();
                String[] srvRecordEntries = srvRecord.split(" ");
                port = Integer.parseInt(srvRecordEntries[srvRecordEntries.length - 2]);
                host = srvRecordEntries[srvRecordEntries.length - 1];
            } catch (Exception e2) {
                // Do nothing
                logger.log(Level.INFO,"Failed to find either xmpp or jabber dns records");
            }
        }
        // Host entries in DNS should end with a ".".
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return new DNSEntry(host, port);
    }

    public static class DNSEntry {

        public DNSEntry(String s, int p) {
            hostname = s;
            port = p;
        }
        public String hostname;
        public int port;
    }
}
