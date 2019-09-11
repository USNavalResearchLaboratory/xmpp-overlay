package edu.drexel.xop.net.discovery;

import mil.navy.nrl.protosd.ProtoSD;
import mil.navy.nrl.protosd.api.*;
import mil.navy.nrl.protosd.api.distobejcts.SDObject;
import mil.navy.nrl.protosd.api.distobejcts.SDObjectFactory;
import mil.navy.nrl.protosd.api.exception.InitializationException;
import mil.navy.nrl.protosd.api.exception.ServiceInfoException;
import mil.navy.nrl.protosd.events.Advertiser;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Advert Generation for XOP.
 */
public class XOPGeneration extends Advertiser {
    private static final Logger logger = Logger.getLogger(XOPGeneration.class.getName());

    SDObject sdObject;
    static int count=0;
    DiscoveryInstance discoveryInstance;
    ServiceRegistration serviceRegistration;

    ArrayList<ServiceInfoEndpoint> serviceInfoEndpoints = new ArrayList<ServiceInfoEndpoint>();

    public XOPGeneration(String args[]) {
        setUnregisterEvents(true);
        super.initCommands(args);
        super.startEvents();
    }

    @Override
    public ServiceInfoEndpoint getNextServiceAdvert() {
        logger.fine("Getting service advert - service count is " + serviceInfoEndpoints.size());
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
            ProtoSD protoSD = new ProtoSD(parser, true, false);

            sdObject = SDObjectFactory.create(protoSD, new XOPPropertyValues(), SDXOPObject.class);

            discoveryInstance = protoSD.getDiscoveryInstance();
            serviceRegistration = protoSD.getServiceRegistration();
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
        return sdObject.getSDInstance().getProtoSD();  //To change body of implemented methods use File | Settings | File Templates.
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

    //    logger.fine("Adding services to event list now !!!!");

  //      logger.fine("Adding Members");
        for (int i=0; i<5; ++i) {
            MembershipDiscoverableObject myMembership =
                    new MembershipDiscoverableObject(sdObject,"member-"+i,"jid-" + i, "nick"+i, "XOPRoom", OccupantStatus.AVAILABLE);
    //        logger.fine("Adding service: " + myMembership.toString());
            serviceInfoEndpoints.add((ServiceInfoEndpoint)myMembership.getServiceInfo());
        }

    //  logger.fine("Adding Groups");
        for (int i=0; i<5; ++i) {
            GroupDiscoverableObject myMUCRoom = new GroupDiscoverableObject(sdObject,"XOPRoom-" + i);
        //    logger.fine("Adding service: " + myMUCRoom.toString());
            serviceInfoEndpoints.add((ServiceInfoEndpoint)myMUCRoom.getServiceInfo());
        }

        UserDiscoverableObject user;

      //  logger.fine("Adding users");
        for (int i=0; i<5; ++i) {
            user =   new UserDiscoverableObject(sdObject, "bob" + i, "my-jid-" + i, "??", "cool", "1", "Ian", "ian.j.taylor@gmail.com", "Taylor", "In Jacksonville");
        //    logger.fine("Adding service: " + user.toString());
            serviceInfoEndpoints.add((ServiceInfoEndpoint) user.getServiceInfo());
        }

      //  logger.fine("Done adding !!!!");
    }

    public static void main(String [] args) throws Exception {
        new XOPGeneration(args);
    }

}
