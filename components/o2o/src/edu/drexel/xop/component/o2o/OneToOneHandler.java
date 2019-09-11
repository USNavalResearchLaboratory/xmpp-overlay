package edu.drexel.xop.component.o2o;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.logging.Logger;

import org.dom4j.DocumentException;
import org.xmpp.packet.Packet;

import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;

public class OneToOneHandler extends Thread {

    Socket socket;
    private static final Logger logger = LogUtils.getLogger(OneToOneHandler.class.getName());

    OneToOneHandler(Socket s) {
        this.socket = s;
    }

    @Override
    public void run() {
        boolean done = false;
        while (!done) {
            BufferedReader in = null;
            try {
                in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Packet p = Utils.packetFromString(in.readLine());
                logger.info("Received one-to-one packet from network: " + p.toString());

                // TODO: first check the to field to make sure we have a connected o2o chat with this jid. otherwise, send an error packet.
                // pass the packet to the packet router to (hopefully) route to a connected user
                ClientProxy.getInstance().processPacket(p);

            } catch (DocumentException | IOException e) {
                e.printStackTrace();
            }

        }
    }

}
