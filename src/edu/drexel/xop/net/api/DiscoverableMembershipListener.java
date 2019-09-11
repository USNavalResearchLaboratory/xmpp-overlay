package edu.drexel.xop.net.api;

import edu.drexel.xop.net.discovery.MembershipDiscoverableObject;

/**
 * The Discoverable Membership listener is implemented by objects wishing to retrieve notifications
 * of new discoverable membership events.
 *
 * @author Ian Taylor
 */
public interface DiscoverableMembershipListener {

    /**
     * Notifies when a new discoverable membership has been discovered
     *
     * @param membershipDiscoverableObject the user discoverable membership object that has been discovered.
     */
    public void membershipAdded(MembershipDiscoverableObject membershipDiscoverableObject);

    /**
     * Notifies when a membership object has been removed
     *
     * @param membershipDiscoverableObject the user discoverable membership  object, which has been removed
     */
    public void membershipRemoved(MembershipDiscoverableObject membershipDiscoverableObject);

}
