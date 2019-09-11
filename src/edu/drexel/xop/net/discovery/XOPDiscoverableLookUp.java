package edu.drexel.xop.net.discovery;

import mil.navy.nrl.protosd.api.ServiceInfo;
import mil.navy.nrl.protosd.api.distobejcts.DiscoverableLookUp;
import mil.navy.nrl.protosd.api.distobejcts.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobejcts.SDObject;

/**
 * Simple Lookup for getting the right classes for the given service types;
 */
public class XOPDiscoverableLookUp extends DiscoverableLookUp {


    /**
     * Returns the discoverable XOP object for the given service type
     *
     * @param sdObject service discovery system
     * @param info service info object
     * @return
     */
    public DiscoverableObject getDiscoverableFor(SDObject sdObject, ServiceInfo info) {
        if (info.getServiceType().equals(GroupDiscoverableObject.serviceType)) {
            return new GroupDiscoverableObject(info,sdObject);
        }  else if (info.getServiceType().equals(UserDiscoverableObject.serviceType)) {
            return new UserDiscoverableObject(info,sdObject);
        }  else if (info.getServiceType().equals(MembershipDiscoverableObject.serviceType)) {
            return new MembershipDiscoverableObject(info,sdObject);
        }  else if (info.getServiceType().equals(ServerDiscoverableObject.serviceType)) {
            return new ServerDiscoverableObject(info,sdObject);
        } else {
            return null; // not a discoverable object, so must be some other advert we are not interested in
        }
    }
}
