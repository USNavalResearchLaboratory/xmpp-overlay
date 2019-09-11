package edu.drexel.xop.gateway;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Static Utility functions for Gateway (S2S) operation.
 * Created by duc on 10/19/16.
 */
class GatewayUtil {
    private static final Logger logger = LogUtils.getLogger(GatewayUtil.class.getName());


    static String generateS2SOpenStream(String from, String to, String sessionId){
        String STREAM_OPEN_SERVER_WITH_DIALBACK =
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' "
                        + "xmlns='" + CONSTANTS.GATEWAY.SERVER_NAMESPACE + "' "
                        + "xmlns:db='" + CONSTANTS.GATEWAY.SERVER_DIALBACK_NAMESPACE + "' "
                        + "from='" + from + "' to='" + to +"' ";
        if( sessionId != null )
            STREAM_OPEN_SERVER_WITH_DIALBACK += "id='"+ sessionId  + "' ";
        STREAM_OPEN_SERVER_WITH_DIALBACK += "xml:lang='en' version='1.0'>";
        return STREAM_OPEN_SERVER_WITH_DIALBACK;
    }

    static String generateDialbackRequest(String from, String to, String dialbackKey) {
        return "<db:result from='" + from + "' to='" + to + "'>" + dialbackKey + "</db:result>";
    }

    static String generateVerifyRequest(String from, String to, String dialbackKey, String sessionId){
        return "<db:verify from='" + from + "' to='" + to + "' id='"+sessionId+"'>" + dialbackKey + "</db:verify>";

    }

    static String generateDialbackKey(String from, String to, String session) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest((from + " " + to + " " + session).getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException e) {
            logger.severe("No such algorithm: " + e.getMessage());
        } catch (UnsupportedEncodingException e) {
            logger.severe("Unsupported encoding: " + e.getMessage());
        }

        return "";
    }

    static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for(byte b : bytes) {
            result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

}
