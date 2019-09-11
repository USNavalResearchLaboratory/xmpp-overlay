package edu.drexel.xop.net.discovery;

import edu.drexel.xop.net.api.DiscoverableGroupListener;
import edu.drexel.xop.net.api.DiscoverableMembershipListener;
import edu.drexel.xop.net.api.DiscoverableUserListener;
import edu.drexel.xop.util.logger.LogUtils;
import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.ServiceInfoEndpoint;
import mil.navy.nrl.protosd.api.distobejcts.*;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;

import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The entry point to a service discovery system. This class contains
 * an interface to the underlying service discovery system instances and provides a discoverable
 * object interface for interacting with that system for XOP components. It inherits the
 * underlyign structure from ProtoSD's SDObject
 * 
 * XOP should only interface with SDObjects when dealing with the underlying discovery system.
 * 
 * @author Ian Taylor
 */
public class SDXOPObject extends SDObject {
    private static final Logger logger = LogUtils.getLogger(SDXOPObject.class.getName());

    Vector<DiscoverableGroupListener> groupListeners;
    Vector<DiscoverableMembershipListener> membershipListeners;
    Vector<DiscoverableUserListener> userListeners;

    private SDPropertyValues sdvalues;


    public SDXOPObject(SDInstance sdInstance) {
        super(sdInstance);

        this.sdvalues = sdInstance.getSDPropertyValues();

        groupListeners = new Vector<>();
        userListeners = new Vector<>();
        membershipListeners = new Vector<>();

        // add type listeners

        try {
            sdInstance.addServiceListener(this, UserDiscoverableObject.serviceType, sdvalues.getDomain());
            sdInstance.addServiceListener(this, GroupDiscoverableObject.serviceType, sdvalues.getDomain());
            sdInstance.addServiceListener(this, ServerDiscoverableObject.serviceType, sdvalues.getDomain());
            sdInstance.addServiceListener(this, MembershipDiscoverableObject.serviceType, sdvalues.getDomain());
        } catch (ServiceInfoException e) {
            e.printStackTrace();
        }
    }

    public SDPropertyValues getSDPropertyValues() {
        logger.finer("return sdvalues");
        return sdvalues;
    }

    @Override
    public DiscoverableLookUp getDiscoverableLookup() {
        return new XOPDiscoverableLookUp();
    }

    /**
     * Takes a service info in and notifies the discoverable object listeners.
     * 
     * @param serviceInfo
     */
    public void serviceRemoved(ServiceInfo serviceInfo) {
        super.serviceRemoved(serviceInfo);

        logger.finer("serviceInfo: " + serviceInfo);
        final DiscoverableObject discoverableObject = getDiscoverableLookup().getDiscoverableFor(this, serviceInfo);

        if (discoverableObject instanceof GroupDiscoverableObject) {
            final GroupDiscoverableObject group = (GroupDiscoverableObject) discoverableObject;
            for (final DiscoverableGroupListener listener : groupListeners) {
                executor.submit(new Runnable() {
                    public void run() {
                        listener.groupRemoved(group);
                    }
                });
            }
        } else if (discoverableObject instanceof UserDiscoverableObject) {
            final UserDiscoverableObject user = (UserDiscoverableObject) discoverableObject;
            for (final DiscoverableUserListener listener : userListeners) {
                executor.submit(new Runnable() {
                    public void run() {
                        listener.userRemoved(user);
                    }
                });
            }
        } else if (discoverableObject instanceof MembershipDiscoverableObject) {
            final MembershipDiscoverableObject membership = (MembershipDiscoverableObject) discoverableObject;
            for (final DiscoverableMembershipListener listener : membershipListeners) {
                executor.submit(new Runnable() {
                    public void run() {
                        listener.membershipRemoved(membership);
                    }
                });
            }
        }

    }

    public void serviceResolved(ServiceInfoEndpoint serviceInfoEndpoint) {
        super.serviceResolved(serviceInfoEndpoint);

        logger.log(Level.FINE, "serviceResolved: " + serviceInfoEndpoint);

        final DiscoverableObject discoverableObject = getDiscoverableLookup().getDiscoverableFor(this, serviceInfoEndpoint);

        if (discoverableObject instanceof GroupDiscoverableObject) {
            final GroupDiscoverableObject group = (GroupDiscoverableObject) discoverableObject;
            for (final DiscoverableGroupListener listener : groupListeners) {
                executor.submit(new Runnable() {
                    public void run() {
                        logger.finer("firing groupAdded for "+listener.getClass().getName());
                        listener.groupAdded(group);
                    }
                });
            }
        } else if (discoverableObject instanceof UserDiscoverableObject) {
            final UserDiscoverableObject user = (UserDiscoverableObject) discoverableObject;
            for (final DiscoverableUserListener listener : userListeners) {
                executor.submit(new Runnable() {
                    public void run() {
                        logger.finer("firing userAdded for "+listener.getClass().getName());
                        listener.userAdded(user);
                    }
                });
            }
        } else if (discoverableObject instanceof MembershipDiscoverableObject) {
            final MembershipDiscoverableObject membership = (MembershipDiscoverableObject) discoverableObject;
            for (final DiscoverableMembershipListener listener : membershipListeners) {
                executor.submit(new Runnable() {
                    public void run() {
                        logger.finer("firing membershipAdded for "+listener.getClass().getName());
                        listener.membershipAdded(membership);
                    }
                });
            }
        }
    }

    /**
     * Adds a discoverable group listener
     * 
     */
    public void addDiscoverableGroupListener(DiscoverableGroupListener listener) {
        if (!groupListeners.contains(listener))
            groupListeners.add(listener);
    }

    /**
     * Removes a discoverable group listener
     * 
     */
    public void removeDiscoverableGroupListener(DiscoverableGroupListener listener) {
        groupListeners.remove(listener);
    }

    /**
     * Adds a discoverable user listener
     * 
     */
    public void addDiscoverableUserListener(DiscoverableUserListener listener) {
        if (!userListeners.contains(listener))
            userListeners.add(listener);
    }

    /**
     * Removes a discoverable user listener
     * 
     */
    public void removeDiscoverableUserListener(DiscoverableUserListener listener) {
        userListeners.remove(listener);
    }

    /**
     * Adds a discoverable membership listener
     * 
     */
    public void addDiscoverableMembershipListener(DiscoverableMembershipListener listener) {
        try {
            sdInstance.addServiceListener(this, MembershipDiscoverableObject.serviceType, sdvalues.getDomain());
        } catch (ServiceInfoException e) {
            // can this ever be thrown ?? let's leave this here for now but we
            // know the service types so should be fine ...
            e.printStackTrace();
        }
        if (!membershipListeners.contains(listener))
            membershipListeners.add(listener);
    }

    /**
     * Removes a discoverable group listener
     * 
     */
    public void removeDiscoverableMembershipListener(DiscoverableMembershipListener listener) {
        membershipListeners.remove(listener);
    }

    /**
     * Gets all MUC group discoverable objects
     * 
     * @return
     */
    public DiscoverableObject[] getDiscoveredGroups() {
        Hashtable<String, List<ServiceInfoEndpoint>> services = sdInstance.getProtoSD().getServiceCache().getStructuredServiceList();

        List<ServiceInfoEndpoint> groups = services.get(GroupDiscoverableObject.serviceType);

        return getObjectsForServiceList(groups);
    }

    /**
     * Gets all MUC user discoverable objects
     * 
     * @return
     */
    public DiscoverableObject[] getDiscoveredUsers() {
        Hashtable<String, List<ServiceInfoEndpoint>> services = sdInstance.getProtoSD().getServiceCache().getStructuredServiceList();

        List<ServiceInfoEndpoint> users = services.get(UserDiscoverableObject.serviceType);

        return getObjectsForServiceList(users);

    }

    /**
     * Gets all MUC membership discoverable objects
     * 
     * @return
     */
    public DiscoverableObject[] getDiscoveredGroupMemberships() {
        Hashtable<String, List<ServiceInfoEndpoint>> services = sdInstance.getProtoSD().getServiceCache().getStructuredServiceList();

        List<ServiceInfoEndpoint> memberships = services.get(UserDiscoverableObject.serviceType);

        return getObjectsForServiceList(memberships);
    }

}