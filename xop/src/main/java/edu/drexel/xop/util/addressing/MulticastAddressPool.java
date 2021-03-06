package edu.drexel.xop.util.addressing;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.logging.Logger;

import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;


/**
 * This class provides a means of hashing an arbitrary destination string into a
 * multicast IP address.
 * 
 * @author Rob Lass <urlass@drexel.edu>
 */
public class MulticastAddressPool {
    private static Logger logger = LogUtils.getLogger(MulticastAddressPool.class.getName());

    /**
     * Hashes <code>address</code> into a multicast IP.
     * 
     * @param address
     *            the address to hash.
     * @param mcastAddrSpace
     *            the multicast address space.
     * @return the multicast address.
     */
    public static InetAddress getMulticastAddress(String address, String mcastAddrSpace)
            throws UnknownHostException {
        // calculate the IP address using the spirit of the technique described
        // in our Milcom 2010 paper
        // 0. parse the ranges of multicast addresses, and store them in
        // mcastRanges. then figure out total size of the address space.
        HashSet<MulticastRange> mcastRanges = new HashSet<>();
        int pos = 0;
        while (mcastAddrSpace.indexOf(",", pos) > 0) {
            try {
                mcastRanges.add(new MulticastRange(mcastAddrSpace.substring(pos, mcastAddrSpace.indexOf(",", pos))));
            } catch (UnknownHostException ex) {
                logger.warning(
                        "Unknown host using multicast range: "
                                + mcastAddrSpace.substring(pos, mcastAddrSpace.indexOf(",", pos)));
            } catch (MulticastRange.NotAMulticastAddress ex) {
                logger.warning(
                        "Not a valid multicast range: "
                                + mcastAddrSpace.substring(pos, mcastAddrSpace.indexOf(",", pos)));
            }
            pos = mcastAddrSpace.indexOf(",", pos);
        }
        // don't forget the last address!
        try {
            mcastRanges.add(new MulticastRange(mcastAddrSpace.substring(pos)));
        } catch (UnknownHostException ex) {
            logger.warning(
                    "Unknown host using multicast range: "
                            + mcastAddrSpace.substring(pos, mcastAddrSpace.indexOf(",", pos)));
        } catch (MulticastRange.NotAMulticastAddress ex) {
            if (mcastAddrSpace.indexOf(",", pos) > 0) {
                logger.warning(
                        "Not a valid multicast range: "
                                + mcastAddrSpace.substring(pos, mcastAddrSpace.indexOf(",", pos)));
            } else {
                logger.warning("Not a valid multicast range: " + mcastAddrSpace);
            }
        }

        int addressSpaceSize = 0;
        for (MulticastRange mr : mcastRanges) {
            addressSpaceSize += mr.getSpaceSize();
        }

        logger.finer("There are " + addressSpaceSize + " addresses in the ranges provided.");

        byte[] current = null;

        // calculate the IP address using the spirit of the technique described
        // in our Milcom 2010 paper
        // 1. get the MD5 hash of the room name
        try {
            current = hashText(address);
        } catch (UnsupportedEncodingException e) {
            logger.warning("Unable to generate chat room IP address!");
            e.printStackTrace();
        } catch (NoSuchAlgorithmException uee) {
            logger.warning("Unable to generate chat room IP Address!");
            uee.printStackTrace();
        }

        if (current == null) {
            logger.warning("Error creating IP address from chatroom name!");
            return null;
        }
        // 2. take the first 8 bytes of info from the digest, convert it to a
        // positive digit, mod by the address space, and find that address
        long bigNumber = Math.abs(current[0]) * 255 * 255 * 255 + Math.abs(current[1]) * 255 * 255
                + Math.abs(current[2]) * 255 + Math.abs(current[3]);
        long addressIndex = bigNumber % addressSpaceSize;

        boolean done = false;
        try {
            MulticastRange chosenRange = new MulticastRange("224.0.0.1-224.0.0.200");
            for (MulticastRange mr : mcastRanges) {
                if (!done && mr.getSpaceSize() < addressIndex) {
                    addressIndex -= mr.getSpaceSize();
                } else {
                    done = true;
                    chosenRange = mr;
                }
            }

            InetAddress retVal = chosenRange.getAddressByIndex(addressIndex);
            logger.fine("Generated multicast group: " + retVal.getHostAddress());
            return retVal;
        } catch (UnknownHostException | MulticastRange.NotAMulticastAddress e) {
            logger.severe("Unknown multicast range or Not a Multicast Address!");
            logger.severe("Returning default multicast group: " + XOP.TRANSPORT.ADDRESS);
            return InetAddress.getByName(XOP.TRANSPORT.ADDRESS);
        }
    }

    /**
     * Hashes a string with MD5.
     * 
     * @param text
     *            string to hash.
     * @return hashed string.
     * @throws NoSuchAlgorithmException
     *             if MD5 is not accessible.
     * @throws UnsupportedEncodingException
     *             if the encoding for output is not supported.
     */
    private static byte[] hashText(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(text.getBytes("iso-8859-1"), 0, text.length());
        return md.digest();
    }

    public static void main(String[] args) {
        String address = args[0];
        String group2 = args[1];

        try {
            System.out.println(address + ": " + getMulticastAddress(address, group2 + "-" + group2));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
