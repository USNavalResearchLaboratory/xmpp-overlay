package edu.drexel.xop.net.discovery;

import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobejcts.DOServiceTypes;
import mil.navy.nrl.protosd.api.distobejcts.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobejcts.SDObject;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;

import java.io.IOException;
import java.util.Hashtable;

/**
 *
 * 1.	hash - The hashing algorithm used to generated the 'ver' attribute in Entity Capabilities (XEP-0115) and therefore the ver parameter in Link-Local Messaging.
 2.	jid - The Jabber ID of the user; can contain a space-separated list of more than one JID.
 3.	nick - A friendly or informal name for the user.
 4.	node - A unique identifier for the application; the value of this parameter MUST be the same as that provided via normal XMPP presence (if applicable) in the 'node' attribute specified in Entity Capabilities (XEP-0115).
 5.	status -  The presence availability of the user. Allowable values are "avail", "away", and "dnd", which map to mere XMPP presence (the user is available) and the XMPP <show/> values of "away" and "dnd", respectively; if the status parameter is not included, the status SHOULD be assumed to be "avail".
 6.	ver - A hashed string that defines the XMPP service discovery (XEP-0030) identity of the application and the XMPP service discovery features supported by the application; the value of this parameter MUST be the same as that provided via normal XMPP presence (if applicable) in the 'ver' attribute specified in Entity Capabilities (XEP-0115).

 1.	1st  - The given or first name of the user.	optional
 2.	email - The email address of the user; can contain a space-separated list of more than one email address.
 3.	last - The family or last name of the user
 4.	msg - Natural-language text describing the user's state. This is equivalent to the XMPP <status/>; element.

 */
public class UserDiscoverableObject extends DiscoverableObject {
    static final String serviceType = "_presence._tcp";

    static {
        DOServiceTypes.register(serviceType, UserDiscoverableObject.class);
    }

    // TXT field contains

    private String hash="SHA1";
    private static String hashKey="hash";
    private String nick;
    private static String nickKey="nick";
    private String jid;
    private static String jidKey="jid";
    private String node;
    private static String nodeKey = "node";
    private String status;
    private static String statusKey="status";
    private String ver;
    private static String verKey="ver";
    private static int port;
    private static String portKey="p2pj";
    private String firstname;
    private static String firstnameKey="1st";
    private String email;
    private static String emailKey="email";
    private String lastname;
    private static String lastnameKey="last";
    private String msg;
    private static String msgKey="msg";

    /**
       * Allows package local creation of this discoverable object
       *
       * @param info
       * @param sdObject
       */
      UserDiscoverableObject(ServiceInfo info, SDObject sdObject) {
          super(info, sdObject);
          port = sdObject.getSDPropertyValues().getMulticastPort();
      }

    /**
     * Creates a User object
     *
     * @param sdobject
     * @param nick
     * @param jid
     * @param node
     * @param status
     * @param ver
     * @throws java.io.IOException
     * @throws mil.navy.nrl.protosd.api.exception.InitializationException
     * @throws mil.navy.nrl.protosd.api.exception.ServiceInfoException
     */
    public UserDiscoverableObject(SDObject sdobject, String nick, String jid, String node, String status, String ver) throws IOException, InitializationException, ServiceInfoException {
        super(sdobject, nick + "@" + "universe", serviceType, sdobject.getSDPropertyValues().getDomain(),  sdobject.getSDPropertyValues().getMulticastPort(), getTxtFieldParameters(nick,jid,node,status,ver));
        port = sdobject.getSDPropertyValues().getMulticastPort();
    }


    /**
     * Wow ... lots of crap ... this is a full user object.
     *
     * @param sdobject
     * @param nick
     * @param jid
     * @param node
     * @param status
     * @param ver
     * @param first
     * @param email
     * @param last
     * @param msg
     * @throws java.io.IOException
     * @throws mil.navy.nrl.protosd.api.exception.InitializationException
     * @throws mil.navy.nrl.protosd.api.exception.ServiceInfoException
     */
    public UserDiscoverableObject(SDObject sdobject, String nick, String jid, String node,
                                  String status, String ver, String first, String email, String last, String msg)
            throws IOException, InitializationException, ServiceInfoException {
//        super(sdobject, nick + "@" + getServiceEndpoint(sdobject), serviceType, getDomain(),  port, getTxtFieldParameters(nick,jid,node,status,ver,first,email,last,msg));
        super(sdobject, nick + "@" + "universe", serviceType, sdobject.getSDPropertyValues().getDomain(), sdobject.getSDPropertyValues().getMulticastPort(), getTxtFieldParameters(nick,jid,node,status,ver,first,email,last,msg));
        port = sdobject.getSDPropertyValues().getMulticastPort();
    }

    private static Hashtable<String,String> getTxtFieldParameters(String nick, String jid, String node,
                                                                  String status, String ver) throws IOException  {
        Hashtable <String,String>keyValues = new Hashtable<>();
        keyValues.put(nickKey, nick);
        keyValues.put(jidKey, jid);
        keyValues.put(nodeKey, node);
        keyValues.put(statusKey, status );
        keyValues.put(verKey, ver);
        keyValues.put(portKey, String.valueOf(port));

        return keyValues;
    }

    private static Hashtable<String,String> getTxtFieldParameters(String nick, String jid, String node,
                                                                  String status, String ver, String first, String email, String last, String msg) throws IOException  {
        Hashtable <String,String>keyValues = getTxtFieldParameters(nick, jid,node,status,ver);
        keyValues.put(firstnameKey, first );
        keyValues.put(emailKey, email);
        keyValues.put(lastnameKey, last);
        keyValues.put(msgKey, msg);

        return keyValues;
    }

    @Override
    protected void setFromTxtField(Hashtable<String, String> keyValues) {
        nick = keyValues.get(nickKey);
        jid = keyValues.get(jidKey);
        node = keyValues.get(nodeKey);
        status= keyValues.get(statusKey);
        ver = keyValues.get(verKey);
        String sport = keyValues.get(portKey);
        if(sport != null){
        	port = Integer.parseInt(sport);
        }//otherwise, we'll just use the default port from our properties file
        firstname = keyValues.get(firstnameKey);
        email = keyValues.get(emailKey);
        lastname = keyValues.get(lastnameKey);
        msg = keyValues.get(msgKey);
    }
    
    /**
     * 
     * @return the ServiceAddress of this DiscoverableObject
     */
    public String getServiceAddress(){
        if (serviceInfo instanceof ServiceInfoEndpoint) {
            return ((ServiceInfoEndpoint)serviceInfo).getServiceEndpoint();// + "/" + ((ServiceInfoEndpoint) serviceInfo).getMucPort());
        }
        return null;
    }
    
    public int getServicePort(){
    	return ((ServiceInfoEndpoint) serviceInfo).getPort();
    }

    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("mDNS Advert: " + serviceInfo.getServiceName() + "," + serviceInfo.getQualifiedServiceType());

        if (serviceInfo instanceof ServiceInfoEndpoint) {

            str.append("," + ((ServiceInfoEndpoint)serviceInfo).getServiceEndpoint() + "/" + ((ServiceInfoEndpoint) serviceInfo).getPort());
        }

        str.append("\nTXT Parameters\n");
        str.append("User Nick Name = " + nick + "\n");
        str.append("JID = " + jid + "\n");
        str.append("Node = " + node + "\n");
        str.append("Status = " + status + "\n");
        str.append("Ver = " + ver+ "\n");
        str.append("P2PJ = " + port + "\n");
        str.append("First Name = " + firstname + "\n");
        str.append("Email = " + email + "\n");
        str.append("Last Name = " + lastname + "\n");
        str.append("Message  = " + msg + "\n");

        return str.toString();
    }

    public String getHash() {
        return hash;
    }

    public String getNick() {
        return nick;
    }

    public String getJid() {
        return jid;
    }

    public String getNode() {
        return node;
    }

    public String getStatus() {
        return status;
    }

    public String getVer() {
        return ver;
    }

    public static int getPort() {
        return port;
    }

    public String getFirstname() {
        return firstname;
    }

    public String getEmail() {
        return email;
    }

    public String getLastname() {
        return lastname;
    }

    public String getMsg() {
        return msg;
    }

}
