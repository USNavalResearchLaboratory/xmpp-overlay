/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.muc;

/**
 * Names of properties and defaults for MUC
 * 
 * @author di
 */
public class MucProperties {
    public static final String MUC_PORT = "muc.port";
    public static final String MUC_SERVICE_NAME = "muc.servicename";
    public static final int MUC_PORT_DEFAULT = 5150;
    public static final String MUC_SERVICE_NAME_DEFAULT = "_xopmuc._udp";
    public static final String MUC_USER_NAMESPACE = "http://jabber.org/protocol/muc#user";
    public static final String MUC_NAMESPACE = "http://jabber.org/protocol/muc";
    // Status Codes
    public static final int SELF_REFERENTIAL_CODE = 110;
    public static final int ENTERING_ROOM_CODE = 100;
    public static final int ENTERING_NEW_ROOM_CODE = 201;
}
