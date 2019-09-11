package edu.drexel.transportengine.util.addressing;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

/**
 * @author urlass
 *         <p/>
 *         This class holds a contiguous range of multicast addresses.
 *         <p/>
 *         TODO: add code to check that start is smaller than end. the other code
 *         assumes that this is the case.
 */
public final class MulticastRange {

    Logger logger = Logger.getLogger(this.getClass().getName());
    private InetAddress range_start;
    private InetAddress range_end;

    /**
     * @param range A multicast range in the form 224.0.0.1-224.0.0.200 (ie: a
     *              dash between the beginning and end of the range)
     * @throws UnknownHostException There was a problem converting this String
     *                              into an InetAddress
     * @throws NotAMulticastAddress The beginning or end of the range is not a
     *                              multicast address.
     */
    MulticastRange(String range) throws UnknownHostException, NotAMulticastAddress {
        setStart(range.substring(0, (range.indexOf("-"))));
        setEnd(range.substring((range.indexOf("-") + 1)));
    }

    public void setStart(InetAddress range_start) throws NotAMulticastAddress {
        if (range_start.isMulticastAddress()) {
            this.range_start = range_start;
        } else {
            throw new NotAMulticastAddress();
        }
    }

    public void setStart(String range_start) throws UnknownHostException, NotAMulticastAddress {
        if (InetAddress.getByName(range_start).isMulticastAddress()) {
            this.range_start = InetAddress.getByName(range_start);
        } else {
            throw new NotAMulticastAddress();
        }
    }

    public void setEnd(InetAddress range_end) throws NotAMulticastAddress {
        if (range_start.isMulticastAddress()) {
            this.range_end = range_end;
        } else {
            throw new NotAMulticastAddress();
        }
    }

    public void setEnd(String range_end) throws UnknownHostException, NotAMulticastAddress {
        if (InetAddress.getByName(range_end).isMulticastAddress()) {
            this.range_end = InetAddress.getByName(range_end);
        } else {
            throw new NotAMulticastAddress();
        }
    }

    /**
     * @return The number of adresses in the range represented by this object.
     */
    public int getSpaceSize() {
        String start = this.range_start.toString().substring(1);
        String end = this.range_end.toString().substring(1);

        //convert these numbers into a decimal representation, and then take the difference
        String[] nums = start.split("\\.");
        long start_value = Long.parseLong(nums[0]) * 255 * 255 * 255 + Long.parseLong(nums[1]) * 255 * 255 + Long.parseLong(nums[2]) * 255 + Long.parseLong(nums[3]);
        nums = end.split("\\.");
        long end_value = Long.parseLong(nums[0]) * 255 * 255 * 255 + Long.parseLong(nums[1]) * 255 * 255 + Long.parseLong(nums[2]) * 255 + Long.parseLong(nums[3]);

        long size = end_value - start_value + 1;
        if (size > Integer.MAX_VALUE) {
            Logger.getLogger(this.getClass().getName()).severe("This address space (" + start + "--" + end + ") is way too large!");
        }
        return (int) (end_value - start_value + 1);
    }

    /**
     * Returns the address_index^{th} element of this range
     */
    public InetAddress getAddressByIndex(long address_index) {

        //make sure the index is valid
        if (address_index >= this.getSpaceSize()) {
            logger.severe("The address index provided is too large!  " + address_index + ">=" + this.getSpaceSize());
        }

        //convert the start of the range into a single decimal number
        String[] nums = this.range_start.toString().substring(1).split("\\.");
        long start_address = Long.parseLong(nums[0]) * 255 * 255 * 255 + Long.parseLong(nums[1]) * 255 * 255 + Long.parseLong(nums[2]) * 255 + Long.parseLong(nums[3]);

        //add the index to it
        start_address += address_index;

        //convert the decimal number back into an ip address
        int octet1 = (int) (start_address / (255 * 255 * 255));
        start_address -= octet1 * 255 * 255 * 255;

        int octet2 = (int) (start_address) / (255 * 255);
        start_address -= octet2 * 255 * 255;

        int octet3 = (int) (start_address) / 255;
        start_address -= octet3 * 255;

        int octet4 = (int) (start_address);


        String address = octet1 + "." + octet2 + "." + octet3 + "." + octet4;

        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            logger.severe("Unable to turn " + address + " into an IP address!");
        }
        return null;
    }

    @Override
    public String toString() {
        return this.range_start.toString().substring(1) + "--" + this.range_end.toString().substring(1);
    }

    public class NotAMulticastAddress extends Exception {
    }
}
