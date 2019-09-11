package edu.drexel.transportengine.components.protocolmanager.protocols;

import edu.drexel.transportengine.components.protocolmanager.Protocol;
import edu.drexel.transportengine.components.protocolmanager.ProtocolManagerComponent;
import edu.drexel.transportengine.components.protocolmanager.protocols.norm.NORMSocket;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.logging.LogUtils;
import mil.navy.nrl.norm.NormData;
import mil.navy.nrl.norm.NormEvent;
import mil.navy.nrl.norm.NormInstance;
import mil.navy.nrl.norm.NormObject;
import mil.navy.nrl.norm.enums.NormEventType;
import mil.navy.nrl.norm.enums.NormObjectType;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.logging.Logger;

public class NORM extends Protocol {
    private final Logger logger = LogUtils.getLogger(this.getClass().getName());

    private static NormInstance normInstance;
    private LinkedList<NORMSocket> sockets;
    private int nextId = 0;

    public NORM(ProtocolManagerComponent protocolManager) {
        super(protocolManager);
        try {
            normInstance = new NormInstance();

            sockets = new LinkedList<>();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        listen();
                    } catch (IOException e) {
                        logger.severe("NORM listen loop failed!");
                    }
                }
            }).start();
        } catch (IOException e) {
            logger.severe("Could not create NORM instance.");
        }
    }

    @Override
    protected NORMSocket createSocket(InetAddress address) throws IOException {
        for (NORMSocket sock : sockets) {
            if (sock.getInetAddress().equals(address) && sock.getPort() == getPort()) {
                return sock;
            }
        }

        NORMSocket socket = new NORMSocket(normInstance,
                address,
                getPort(),
                getProtocolManager().getTransportEngine().getGUID(),
                nextId++);

        sockets.add(socket);

        return socket;
    }

    @Override
    public String getProtocolName() {
        return "norm";
    }

    @Override
    public boolean isReliable() {
        return true;
    }

    @Override
    public void send(Event event, InetAddress address) {
        try {
            NORMSocket sock = createSocket(address);

            byte[] data = event.pack();
            sock.getSession().dataEnqueue(ByteBuffer.wrap(data), 0, data.length);
        } catch (IOException e) {
            logger.severe("Could not send data with NORM.");
        }
    }

    private void listen() throws IOException {
        NormEvent event;
        while ((event = normInstance.getNextEvent()) != null) {
            NormEventType eventType = event.getType();
            NormObject normObject = event.getObject();
            switch (eventType) {
                case NORM_RX_OBJECT_COMPLETED:
                    if (normObject.getType() == NormObjectType.NORM_OBJECT_DATA) {
                        NormData data = (NormData) normObject;
                        handleIncoming(data.getData());
                    }
                    break;
            }
        }
    }
}
