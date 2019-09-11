package edu.drexel.xop.net.discovery;

import java.io.IOException;
import java.util.Hashtable;
import java.util.logging.Logger;

import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobejcts.DOServiceTypes;
import mil.navy.nrl.protosd.api.distobejcts.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobejcts.SDObject;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * A user presence within a muc room
 * ServiceName = user@machine/group
 *
 * 1.	nickname – nickname of the user
2.	jid – jid of the user
3.	affiliation – owner, admin, member, outcast – the user that is the owner of this room.  A room can only have one owner so if two users create two groups with the same name independently, then these groups must be resolved and combined into one, with one owner being selected.
4.	role – role of this user in the room (defined on a per session basis) – can be moderator, non, participant, visitor.

 */
public class MembershipDiscoverableObject extends DiscoverableObject {
    static final String serviceType = "_roompresence._udp";
    private static final Logger logger = LogUtils.getLogger(MembershipDiscoverableObject.class.getName());

    static {
        DOServiceTypes.register(serviceType, MembershipDiscoverableObject.class);
    }

    // TXT field contains

    private String userJid;
    private static String userJidKey="userJid";
    private String mucOccupantJid;
    private static String mucOccupantJidKey="mucOccJid";
    private String roomName;
    private static String roomNameKey ="roomname";
    private Affiliation affiliation;
    private static String affiliationKey ="affiliation";
    private static String nickKey="nick";
    private String nick;
    Role role;
    private static String roleKey ="role";
    private static String type  = "type"; // AVAILABLE OR UNAVAILABLE
    OccupantStatus status;

    /**
       * Allows package local creation of this discoverable object
       *
       * @param info
       * @param sdObject
       */
     MembershipDiscoverableObject(ServiceInfo info, SDObject sdObject) {
          super(info, sdObject);
      }


    /**
     * Create a membership object
     *
     * @param sdobject
     * @param userJid
     * @param mucOccupantJid
     * @param roomName
     * @param affiliation
     * @param role
     * @throws java.io.IOException
     * @throws mil.navy.nrl.protosd.api.exception.InitializationException
     * @throws mil.navy.nrl.protosd.api.exception.ServiceInfoException
     */
    public MembershipDiscoverableObject(SDObject sdobject, String userJid, String mucOccupantJid, String nick,
                                        String roomName, Affiliation affiliation, Role role,
                                        OccupantStatus occupantStatus)
            throws IOException, InitializationException, ServiceInfoException {
//        super(sdobject, nick + "@" + getServiceEndpoint(sdobject) + "/" + roomName,
        super(sdobject, userJid,
                serviceType, sdobject.getSDPropertyValues().getDomain(), 
                sdobject.getSDPropertyValues().getMulticastPort(), 
                getTxtFieldParameters(userJid, mucOccupantJid, nick, roomName, affiliation, role, occupantStatus));
        this.roomName = roomName;
    }

    /**
     * Shortcut that assumes the MUC occupant is a MEMBER and has a role of PARTICIPANT
     *
     * @param sdobject
     * @param userJid
     * @param mucOccupantJid
     * @param roomName
     * @throws java.io.IOException
     * @throws mil.navy.nrl.protosd.api.exception.InitializationException
     * @throws mil.navy.nrl.protosd.api.exception.ServiceInfoException
     */
    public MembershipDiscoverableObject(SDObject sdobject, String userJid, String mucOccupantJid, String nick,
                                        String roomName, OccupantStatus occupantStatus)
            throws IOException, InitializationException, ServiceInfoException {
        this(sdobject, userJid,mucOccupantJid, nick, roomName, Affiliation.MEMBER, Role.PARTICPANT, occupantStatus);
    }



    /**
     * 
     * @param userJid
     * @param mucOccupantJid
     * @param roomName
     * @param affiliation
     * @param role
     * @param occupantStatus
     * @return a Hashtable representing the TXT field 
     * @throws IOException
     */
    private static Hashtable<String,String> getTxtFieldParameters(String userJid, 
    		String mucOccupantJid, String nick, String roomName, Affiliation affiliation, Role role, 
    		OccupantStatus occupantStatus) throws IOException  {
        Hashtable <String,String>keyValues = new Hashtable<>();
        keyValues.put(userJidKey, userJid );
        keyValues.put(mucOccupantJidKey, mucOccupantJid);
        keyValues.put(nickKey, nick);
        keyValues.put(roomNameKey, roomName);
        keyValues.put(affiliationKey, affiliation.toString());
        keyValues.put(roleKey, role.toString());
        keyValues.put(type, occupantStatus.toString());
        return keyValues;
    }

    /**
     * This is called by the constructor to initialise subclasses internal variables from the
     * TXT field set of key value pairs.
     *
     * @param keyValues
     */
    protected void setFromTxtField(Hashtable <String,String>keyValues) {
        userJid = keyValues.get(userJidKey);
        mucOccupantJid = keyValues.get(mucOccupantJidKey);
        roomName = keyValues.get(roomNameKey);
        nick = keyValues.get(nickKey);
        //this is somehow null!
        if( keyValues.get(affiliationKey) != null ){
            affiliation = Affiliation.valueOf(keyValues.get(affiliationKey));
        } else {
            logger.warning("Affiliation key was null!");
            for( String key : keyValues.keySet()){
                logger.warning(key +":"+keyValues.get(key));
            }
            affiliation = Affiliation.MEMBER;
        }
        role = Role.valueOf(keyValues.get(roleKey));
        status = OccupantStatus.valueOf(keyValues.get(type));
    }
    
    public void updateStatus(OccupantStatus status){
    	this.status = status;
    }

    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("mDNS Advert: " + serviceInfo.getServiceName()).append( ",").append( serviceInfo.getQualifiedServiceType());

        if (serviceInfo instanceof ServiceInfoEndpoint) {

            str.append("," + ((ServiceInfoEndpoint)serviceInfo).getServiceEndpoint() + "/" + ((ServiceInfoEndpoint) serviceInfo).getPort());
        }

        str.append("\nTXT Parameters\n");
        str.append("userJid = " + userJid + "\n");
        str.append("mucOccupantJid = " + mucOccupantJid + "\n");
        str.append("nick = ").append(nick).append("\n");
        str.append("Room Name = " + roomName + "\n");
        str.append("Affiliation = " + affiliation + "\n");
        str.append("role  = " + role + "\n");
        str.append("type = " + status + "\n");

        return str.toString();
    }


    public String getRoomName() {
        return roomName;
    }

    public String getUserJid() {
        return userJid;
    }

    public String getMUCOccupantJid() {
        return mucOccupantJid;
    }

    public Affiliation getAffiliation() {
        return affiliation;
    }

    public Role getRole() {
        return role;
    }
    
    public OccupantStatus getStatus(){
    	return status;
    }
}
