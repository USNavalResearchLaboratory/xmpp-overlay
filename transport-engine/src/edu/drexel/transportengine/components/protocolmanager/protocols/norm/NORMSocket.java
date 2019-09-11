package edu.drexel.transportengine.components.protocolmanager.protocols.norm;


import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import mil.navy.nrl.norm.NormInstance;
import mil.navy.nrl.norm.NormSession;
import mil.navy.nrl.norm.enums.NormProbingMode;

import java.io.IOException;
import java.net.InetAddress;

public class NORMSocket {
    private NormSession session;
    private InetAddress address;
    private int port;

    private static int BUFFER_SPACE =
            Configuration.getInstance().getValueAsInt(TEProperties.PROTOCOL_NORM_BUFFER_SPACE);
    private static int SEGMENT_SIZE =
            Configuration.getInstance().getValueAsInt(TEProperties.PROTOCOL_NORM_SEGMENT_SIZE);
    private static int BLOCK_SIZE =
            Configuration.getInstance().getValueAsInt(TEProperties.PROTOCOL_NORM_BLOCK_SIZE);
    private static int NUM_PARITY =
            Configuration.getInstance().getValueAsInt(TEProperties.PROTOCOL_NORM_NUM_PARITY);
    private static int RECV_BUFFER_SPACE =
            Configuration.getInstance().getValueAsInt(TEProperties.PROTOCOL_NORM_RECV_BUFFER_SPACE);

    public NORMSocket(NormInstance normInstance, InetAddress address, int port, String guid,
                      int id) throws IOException {
        this.address = address;
        this.port = port;

        session = normInstance.createSession(address.getHostAddress(), port, Long.valueOf(guid, 36));
        session.setTTL((byte) 32);
        session.setGrttProbingMode(NormProbingMode.NORM_PROBE_NONE);
        session.setTxRobustFactor(-1);
        session.setDefaultRxRobustFactor(-1);
        session.startSender(id, BUFFER_SPACE, SEGMENT_SIZE,
                BLOCK_SIZE, NUM_PARITY);
        session.startReceiver(RECV_BUFFER_SPACE);
    }

    public InetAddress getInetAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public NormSession getSession() {
        return session;
    }
}
