package mil.navy.nrl.util;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

import javax.xml.bind.DatatypeConverter;

import edu.drexel.xop.util.XOP;

/**
 * Utility class for GCS
 * Created by duc on 3/16/16.
 */
public class GcsUtils {

    /**
     *
     * @param jid
     * @return a long generated from a jid value
     */
    public static Integer calculateGroupId(String jid) {
        Integer grpId = jid.hashCode() & Integer.MAX_VALUE;
        return grpId == XOP.TRANSPORT.GC.DISCOVERY.GROUP ? 908 : grpId; // Note: 908 is an arbitrary selection
    }

    public static long calculateMemberId(String jid){
        byte[] jidBytes = jid.getBytes();
        String hexStr = DatatypeConverter.printHexBinary(jidBytes);
        long retVal = Long.parseLong(hexStr, 16);
        return retVal;
    }

    public static String decodeJid(long id){
        try {
            String hexStr = Long.toHexString(id);
            byte[] decodedHex = DatatypeConverter.parseHexBinary(hexStr);
            String decodedString = new String(decodedHex, "UTF-8");
            System.out.printf("decoded : %s\n", decodedString);

            return decodedString;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args){
        System.out.println("utility for generating hex names for GCS");
        long id = calculateMemberId("reliableroom@conference.openfire");
        System.out.println("calculateMemberId: "+id);
        System.out.println("decodeJid: "+decodeJid(id));
    }
}
