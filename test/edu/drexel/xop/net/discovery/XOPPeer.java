package edu.drexel.xop.net.discovery;

import mil.navy.nrl.protosd.api.DiscoverableObjectListener;
import mil.navy.nrl.protosd.ProtoSD;
import mil.navy.nrl.protosd.api.*;
import mil.navy.nrl.protosd.api.distobejcts.DiscoverableObject;
import mil.navy.nrl.protosd.api.distobejcts.SDObject;
import mil.navy.nrl.protosd.api.distobejcts.SDObjectFactory;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import mil.navy.nrl.protosd.events.Advertiser;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

/**
 * XOP Peer for sending and receiving adverts.
 */
public class XOPPeer extends Advertiser implements DiscoverableObjectListener {
    SDObject sdObject;
    static int count=0;
    DiscoveryInstance discoveryInstance;
    ServiceRegistration serviceRegistration;
    ProtoSD protoSD;

    ArrayList<ServiceInfoEndpoint> serviceInfoEndpoints = new ArrayList<ServiceInfoEndpoint>();

    public XOPPeer(String args[]) {
        setUnregisterEvents(false);
        super.initCommands(args);
        super.startEvents();

    }


    public void listen() {
        sdObject.addDiscoverableObjectListener(this);
    }

    public void discoverableObjectAdded(DiscoverableObject discoverableObject) {
         System.out.println("Client at " +  discoverableObject.getServiceInfo().getNetworkInterface()  + " discovered advert " + discoverableObject.toString());
     }

     public void discoverableObjectRemoved(DiscoverableObject discoverableObject) {
         System.out.println("Client at " +  discoverableObject.getServiceInfo().getNetworkInterface()  + " removed advert " + discoverableObject.toString());
     }

    @Override
    public ServiceInfoEndpoint getNextServiceAdvert() {
        System.out.println("XOP Peer getting service advert !!!!");

        System.out.println("Getting service advert - service count is " + serviceInfoEndpoints.size());
        if (serviceInfoEndpoints.size()==0)  {
            try {
                generateEventList();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ServiceInfoEndpoint info = serviceInfoEndpoints.get(count);

        if (count > serviceInfoEndpoints.size())
            count=0;
        else
            ++count;

        return info;
    }

    @Override
    public void createDiscoverySystem() {
        try {
            protoSD = new ProtoSD(parser, true, false);

            System.out.println("Created OK");

            sdObject = SDObjectFactory.create(protoSD, new XOPPropertyValues(), SDXOPObject.class);

            discoveryInstance = sdObject.getSDInstance().getDiscoveryInstance();
            serviceRegistration = sdObject.getSDInstance().getServiceRegistration();

            System.out.println("ProtoSD is = " + getProtoSD());
        } catch (InitializationException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InstantiationException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IllegalAccessException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InvocationTargetException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (NoSuchMethodException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }

    @Override
    public ProtoSD getProtoSD() {
        return sdObject.getSDInstance().getProtoSD();
    }

    @Override
    public DiscoveryInstance getDiscoveryInstance() {
        return discoveryInstance;
    }

    @Override
    public ServiceRegistration getServiceRegistration()  {
        return serviceRegistration;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private void generateEventList() throws IOException, InitializationException, ServiceInfoException {

        System.out.println("Adding services to event list now !!!!");

        System.out.println("Adding Groups");
        for (int i=0; i<5; ++i) {
            GroupDiscoverableObject myMUCRoom = new GroupDiscoverableObject(sdObject,"XOPRoom-" + i);
            System.out.println("Adding service: " + myMUCRoom.toString());
            serviceInfoEndpoints.add((ServiceInfoEndpoint)myMUCRoom.getServiceInfo());
        }

        System.out.println("Adding Members");
        for (int i=0; i<5; ++i) {
            MembershipDiscoverableObject myMembership =
                    new MembershipDiscoverableObject(sdObject,"member-"+i,"jid-" + i,"nick"+i, "XOPRoom", OccupantStatus.AVAILABLE);
            System.out.println("Adding service: " + myMembership.toString());
            serviceInfoEndpoints.add((ServiceInfoEndpoint)myMembership.getServiceInfo());
        }

        UserDiscoverableObject user;

        System.out.println("Adding users");
        for (int i=0; i<5; ++i) {
            user =   new UserDiscoverableObject(sdObject, "bob" + i, "my-jid-" + i, "??", "cool", "1", "Ian", "ian.j.taylor@gmail.com", "Taylor", "In Jacksonville");
            System.out.println("Adding service: " + user.toString());
            serviceInfoEndpoints.add((ServiceInfoEndpoint) user.getServiceInfo());
        }

        System.out.println("Done adding " + serviceInfoEndpoints.size() + " !!!!");
    }

    public static void main(String [] args) throws Exception {
        new XOPPeer(args).listen();
    }

}
