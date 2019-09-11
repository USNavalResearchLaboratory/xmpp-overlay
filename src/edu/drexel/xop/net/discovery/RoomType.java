package edu.drexel.xop.net.discovery;

/**
 * A class to represent the room type of a MUC room. Room types are a collection of
 * variables that can be defined for a room that exhibit different features.  Room
 * types can be:
 *
 * 1.	public or hidden  - exposure
 * 2.	persistent or temporary - availability
 * 3.	password-protected or unsecured - authentication
 * 4.	members-only or open - authorization
 * 5.	moderated or unmoderated - moderation
 * 6.	non-anonymous or semi-anonymous - privacy
 *
 * Which are defined as:
 *
 * a.	Fully-Anonymous Room where full JIDs or bare JIDs of occupants cannot be discovered by anyone, including room admins and room owners - NOT RECOMMENDED.
 * b.	Non-Anonymous Room A room in which an occupant’s full JID is exposed to all other occupants, although the occupant may choose any desired room nickname.
 * c.	Semi-Anonymous Room - A room in which an occupant’s full JID can be discovered by room admins only
 * d.	Open Room - A room that anyone may enter without being on the member list; antonym: Members-Only Room.
 * e.	Hidden Room – a room that cannot be found by any user through normal means such as searching and service discovery.
 * f.	Public Room A room that can be found by any user through normal means such as searching and service discovery.
 * g.	Members-Only Room A room that a user cannot enter without being on the member list; antonym: Open Room.
 * h.	Moderated Room - A room in which only those with ”voice” may send messages to all occupants.
 * i.	Unmoderated Room - A room in which any occupant is allowed to send messages to all occupants.
 * j.	Password-Protected Room – secured through password
 * k.	Unsecured Room - A room that anyone is allowed to enter without first providing the correct password
 * l.	Temporary Room - A room that is destroyed if the last occupant exits.
 * m.	PersistentRoom  A room that is not destroyed if the last occupant exits
 */
public class RoomType {
    public enum Exposure {HIDDEN,PUBLIC}
    public enum Availability {TEMPORARY,PERSISTENT}
    public enum Authentication {PASSWORD_PROTECTED, UNSECURED}
    public enum Authorization {OPEN,MEMBERS_ONLY}
    public enum Privacy {FULLY_ANONYMOUS, NON_ANONYMOUS, SEMI_ANONYMOUS}
    public enum Moderation {MODERATED,UN_MODERATED}

    // Variables with default value

    private Exposure exposure = Exposure.PUBLIC;
    private Availability availability = Availability.PERSISTENT;
    private Authentication authentication = Authentication.UNSECURED;
    private Authorization authorization = Authorization.OPEN;
    private Privacy privacy = Privacy.FULLY_ANONYMOUS;
    private Moderation moderation = Moderation.UN_MODERATED;

    /**
     * Creates a room with the default values of: <ol>
     * <li>Exposure.PUBLIC </li>
     * <li>Availability.PERSISTENT </li>
     * <li>Authentication.UNSECURED </li>
     * <li>Authorization.OPEN </li>
     * <li>Privacy.FULLY_ANONYMOUS </li>
     * <li>Moderation.UN_MODERATED </li>
     * </ol>
     */
    public RoomType() {
    }

    /**
     * Creates a RoomType from the provided values
     *
     * @param exposure is either public or hidden
     * @param availability is either persistent or temporary
     * @param authentication is either password-protected or unsecured
     * @param authorization is either members-only or open
     * @param privacy is either moderated or unmoderated
     * @param moderation is either non-anonymous, fully-anonymous or semi-anonymous
     */
    public RoomType(Exposure exposure, Availability availability, Authentication authentication, Authorization authorization, Privacy privacy, Moderation moderation) {
        this.exposure = exposure;
        this.availability = availability;
        this.authentication = authentication;
        this.authorization = authorization;
        this.privacy = privacy;
        this.moderation = moderation;
    }

    /**
     * Initialises the object from the txtValue of the key value pair provided in the mDNS advert.
     * @param txtValue
     */
    public RoomType(String txtValue) {
        String[] vals = txtValue.split(",");

        this.exposure = Exposure.valueOf(vals[0].trim());
        this.availability = Availability.valueOf(vals[1].trim());
        this.authentication = Authentication.valueOf(vals[2].trim());
        this.authorization = Authorization.valueOf(vals[3].trim());
        this.privacy = Privacy.valueOf(vals[4].trim());
        this.moderation = Moderation.valueOf(vals[5].trim());
    }

    public Exposure getExposure() {
        return exposure;
    }

    public void setExposure(Exposure exposure) {
        this.exposure = exposure;
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(Authorization authorization) {
        this.authorization = authorization;
    }

    public Privacy getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Privacy privacy) {
        this.privacy = privacy;
    }

    public Moderation getModeration() {
        return moderation;
    }

    public void setModeration(Moderation moderation) {
        this.moderation = moderation;
    }

    /**
     * Converts this object into a string, which forms the value part of the
     * key/value pair stored in the TXT field
     *
     * @return
     */
    public String toString() {
       return this.exposure.toString()  + "," +
         this.availability.toString()  + "," +
         this.authentication.toString()  + "," +
         this.authorization.toString()  + "," +
         this.privacy.toString()  + "," +
         this.moderation.toString();
    }
}
