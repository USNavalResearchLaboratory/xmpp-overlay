package edu.drexel.xop.net.discovery;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Hashtable;

import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobejcts.DOServiceTypes;
import mil.navy.nrl.protosd.api.distobejcts.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobejcts.MulticastAddressPool;
import mil.navy.nrl.protosd.api.distobejcts.SDObject;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import edu.drexel.xop.properties.XopProperties;

/**
 * A group discoverable object has the following parameters:
 * 
 * 1. roomname – the roomname of the group – should be the same as the service roomname ?
 * 2. msg - Natural-language text describing the group’s prupose in life
 * 3. maddr – multicast address used for communications with this group
 * 4. mport – port used for traffic for this group.
 * 5. roomtype – The properties of this room as specified by a number of roomtype constants, as defined in the MUC specification (See room types below)
 * 6. owner (or manage this with presence messages?)
 * 
 */
public class GroupDiscoverableObject extends DiscoverableObject {

    // groupName/address mapping
    static Hashtable<String, InetSocketAddress> addressMapping;
    static final String serviceType = "_presence._udp";

    static {
        DOServiceTypes.register(serviceType, GroupDiscoverableObject.class);
    }

    static {
        addressMapping = new Hashtable<>();
    }

    // TXT field contains
    private String roomName;
    private static String roomNameKey = "roomname";
    private String message;
    private static String messageKey = "msg";
    private String multicastAddress;
    private static String multicastAddressKey = "maddr";
    private int port;
    private static String portKey = "mport";
    private String roomType;
    private static String roomTypeKey = "roomtype";
    private String owner;
    private static String ownerKey = "Owner";

    /**
     * Allows package local creation of this discoverable object
     * 
     * @param info
     * @param sdObject
     */
    GroupDiscoverableObject(ServiceInfo info, SDObject sdObject) {
        super(info, sdObject);
    }

    /**
     * Creates a GroupDiscoverable Object with the specified parameters
     * 
     * @param sdobject
     * @param roomName
     * @param msg
     * @param roomType
     * @param owner
     * @throws java.io.IOException
     * @throws mil.navy.nrl.protosd.api.exception.InitializationException
     * @throws mil.navy.nrl.protosd.api.exception.ServiceInfoException
     */
    public GroupDiscoverableObject(SDObject sdobject, String roomName, String msg, RoomType roomType, String owner) throws IOException, InitializationException, ServiceInfoException {
        super(sdobject, roomName, serviceType, sdobject.getSDPropertyValues().getDomain(), sdobject.getSDPropertyValues().getMulticastPort(), getTxtFieldParameters(sdobject, roomName, msg, roomType, owner));
        this.roomName = roomName;
    }

    /**
     * Creates a GroupDiscoverable Object for a room which has a given message
     * 
     * @param sdobject
     * @param roomName
     * @param msg
     * @throws java.io.IOException
     * @throws mil.navy.nrl.protosd.api.exception.InitializationException
     * @throws mil.navy.nrl.protosd.api.exception.ServiceInfoException
     */
    public GroupDiscoverableObject(SDObject sdobject, String roomName, String msg) throws IOException, InitializationException, ServiceInfoException {
        super(sdobject, roomName, serviceType, sdobject.getSDPropertyValues().getDomain(), sdobject.getSDPropertyValues().getMulticastPort(), getTxtFieldParameters(sdobject, roomName, msg, new RoomType(), "none"));
        this.roomName = roomName;
    }

    /**
     * Creates a GroupDiscoverable Object for a room with a given name
     * 
     * @param sdobject
     * @param roomName
     * @throws java.io.IOException
     * @throws mil.navy.nrl.protosd.api.exception.InitializationException
     * @throws mil.navy.nrl.protosd.api.exception.ServiceInfoException
     */
    public GroupDiscoverableObject(SDObject sdobject, String roomName) throws IOException, InitializationException, ServiceInfoException {
        super(sdobject, roomName, serviceType, sdobject.getSDPropertyValues().getDomain(), sdobject.getSDPropertyValues().getMulticastPort(), getTxtFieldParameters(sdobject, roomName, "A XOP Room", new RoomType(), "none"));
        this.roomName = roomName;
    }

    /**
     * Takes the room parameters and generates a service info TXT field that represents this entity
     * 
     * @param roomName
     * @param msg
     * @param roomType
     * @param owner
     * @return
     * @throws java.io.IOException
     */
    private static Hashtable<String, String> getTxtFieldParameters(SDObject sdObject, String roomName, String msg, RoomType roomType, String owner) throws IOException {
        Hashtable<String, String> keyValues = new Hashtable<>();
        keyValues.put(roomNameKey, roomName);
        keyValues.put(messageKey, msg);
        InetSocketAddress address = addressMapping.get(roomName);
        if (address == null) { // not found
            XopProperties xopProps = XopProperties.getInstance();
            address = MulticastAddressPool.getMulticastAddress(sdObject, roomName, xopProps.getProperty(XopProperties.MULTICAST_ADDRESS_SPACE));
            addressMapping.put(roomName, address);
        }
        String addrStr = address.getAddress().getHostAddress();
        keyValues.put(multicastAddressKey, addrStr);
        keyValues.put(portKey, String.valueOf(address.getPort()));
        keyValues.put(roomTypeKey, roomType.toString());
        keyValues.put(ownerKey, owner);
        return keyValues;
    }

    /**
     * This is called by the constructor to initialise subclasses internal variables from the
     * TXT field set of key value pairs.
     * 
     * @param keyValues
     */
    protected void setFromTxtField(Hashtable<String, String> keyValues) {
        roomName = keyValues.get(roomNameKey);
        message = keyValues.get(messageKey);
        multicastAddress = keyValues.get(multicastAddressKey);
        String sport = keyValues.get(portKey);
        if (sport != null) {
            port = Integer.parseInt(sport);
        }
        roomType = keyValues.get(roomTypeKey);
        owner = keyValues.get(ownerKey);
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("mDNS Advert: " + serviceInfo.getServiceName() + "," + serviceInfo.getQualifiedServiceType());
        if (serviceInfo instanceof ServiceInfoEndpoint) {
            str.append("," + ((ServiceInfoEndpoint) serviceInfo).getServiceEndpoint() + "/"
                + ((ServiceInfoEndpoint) serviceInfo).getPort());
        }
        str.append("\nTXT Parameters\n");
        str.append("Room Name = " + roomName + "\n");
        str.append("Room Message = " + message + "\n");
        str.append("Room Multicast Address = " + multicastAddress + "\n");
        str.append("Room Port = " + port + "\n");
        str.append("Room Type = " + roomType + "\n");
        str.append("Room Owner = " + owner + "\n");
        return str.toString();
    }

    public String getRoomName() {
        return roomName;
    }

    public String getMessage() {
        return message;
    }

    public String getMulticastAddress() {
        return multicastAddress;
    }

    public int getPort() {
        return port;
    }

    public String getRoomType() {
        return roomType;
    }

    public String getOwner() {
        return owner;
    }
}