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

/**
 * Advert client (consumer) test unit test in core for XOP.
 */
public class XOPClient extends Advertiser implements DiscoverableObjectListener {
    SDObject sdObject;
    DiscoveryInstance discoveryInstance;
    ServiceRegistration serviceRegistration;
    ServiceDiscovery serviceDiscovery;

    public XOPClient(String args[]) {
        setUnregisterEvents(false);
        super.initCommands(args);
    }


    public void listen() {
        sdObject.addDiscoverableObjectListener(this);
    }

    @Override
    public ServiceInfoEndpoint getNextServiceAdvert() {

        // no adverts, we are a client

        return null;
    }

    public void discoverableObjectAdded(DiscoverableObject discoverableObject) {
        System.out.println("Client at " +  discoverableObject.getServiceInfo().getNetworkInterface()  + " discovered advert " + discoverableObject.toString());
    }

    public void discoverableObjectRemoved(DiscoverableObject discoverableObject) {
        System.out.println("Client at " +  discoverableObject.getServiceInfo().getNetworkInterface()  + " removed advert " + discoverableObject.getServiceInfo().getServiceName());
    }

    @Override
    public void createDiscoverySystem() {
        try {
            ProtoSD protoSD = new ProtoSD(parser, true, false);

            //   System.out.println("Created OK");

            sdObject = SDObjectFactory.create(protoSD, new XOPPropertyValues(), SDXOPObject.class);

            discoveryInstance = protoSD.getDiscoveryInstance();
            serviceRegistration = protoSD.getServiceRegistration();
            serviceDiscovery = protoSD.getServiceDiscovery();
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

    public SDObject getSdObject() {
        return sdObject;
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

    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    private void generateEventList() throws IOException, InitializationException, ServiceInfoException {
    }

    public static void main(String [] args) throws Exception {
        XOPClient client = new XOPClient(args);
        client.listen();
    }

}
