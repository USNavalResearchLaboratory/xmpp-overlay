package edu.drexel.transportengine.util;

import edu.drexel.transportengine.util.logging.LogUtils;
import edu.drexel.transportengine.util.packing.Packer;
import edu.drexel.transportengine.util.packing.Unpacker;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class CORELinkMonitor extends Thread {

    protected final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private Socket sock;
    private Map<String, Set<String>> links;

    public CORELinkMonitor(String hostname, int port) throws IOException {
        links = new HashMap<>();
        String id = "0";
        sock = new Socket(hostname, port);
        Packer p = new Packer();
        p.writeByte((byte) 9); // message type
        p.writeByte((byte) 1); // flags
        p.writeShort((short) (2 + id.length())); // Length

        p.writeByte((byte) 1); // TLV type = session number
        p.writeByte((byte) id.length()); // TLV length = session number length
        p.writeBytes(id.getBytes()); // session number
        sock.getOutputStream().write(p.getBytes());
        logger.fine("connected");
    }

    public CORELinkMonitor() throws IOException {
        this("172.16.0.254", 4038);
    }

    public Set<String> getLinks(String node) {
        if (links.containsKey(node)) {
            return links.get(node);
        }
        logger.fine("No such node " + node);
        return new HashSet<>();
    }

    public void setColor(String node, String color) {
        try {
            Packer p = new Packer();
            int padding = 32 - ((2 + color.length()) % 32);
            p.writeByte((byte) 1); // message type
            p.writeByte((byte) 0); // message flags
            p.writeShort((byte) (10 + padding + color.getBytes().length)); // message length

            p.writeByte((byte) 0x01); // tlvtype=node id
            p.writeByte((byte) 6); // tlvlen=6 (4 + padding)
            p.writeInt(Integer.parseInt(node.substring(1))); // tlvdat = 2 (node id) 
            p.writeShort((short) 0); // padding

            p.writeByte((byte) 0x42); // tlv type=icon
            p.writeByte((byte) (color.length() + padding)); // tlv length=strlen
            p.writeBytes(color.getBytes()); // tlv data=path
            p.writeBytes(new byte[padding]);

            sock.getOutputStream().write(p.getBytes());
        } catch (IOException ex) {
            logger.warning("Unable to set node color.");
        }
    }

    @Override
    public void run() {
        Unpacker up;
        InputStream in = null;
        try {
            in = sock.getInputStream();
        } catch (IOException e) {
            logger.warning("Could not get input stream from CORE.");
        }
        while (true) {
            try {
                byte[] buf = new byte[4];
                in.read(buf);
                up = new Unpacker(buf);
                byte type = up.readByte();
                byte flags = up.readByte();
                short len = up.readShort();

                buf = new byte[len];
                in.read(buf);
                up = new Unpacker(buf);

                if (type == 2) {
                    String nid1 = "";
                    String nid2 = "";
                    while (up.hasRemaining()) {
                        byte tlvType = up.readByte();
                        byte tlvLength = up.readByte();
                        byte[] data = up.readBytes(tlvLength + 2);
                        if (tlvType == 1) {
                            nid1 = "n" + Integer.toString((data[4] << 8) | (data[5] & 0xff));
                        } else if (tlvType == 2) {
                            nid2 = "n" + Integer.toString((data[4] << 8) | (data[5] & 0xff));
                        }
                    }
                    if (!links.containsKey(nid1)) {
                        links.put(nid1, new HashSet<String>());
                    }
                    if (!links.containsKey(nid2)) {
                        links.put(nid2, new HashSet<String>());
                    }

                    if (flags == 1) {
                        links.get(nid1).add(nid2);
                        links.get(nid2).add(nid1);
                    } else {
                        links.get(nid1).remove(nid2);
                        links.get(nid2).remove(nid1);
                    }
                    logger.fine(nid1 + " " + nid2 + " " + (flags == 1 ? "up" : "down"));
                }
            } catch (IOException ex) {
                logger.warning("Unable to access CORE API.");
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        CORELinkMonitor clm = new CORELinkMonitor("localhost", 4038);
        clm.start();
    }
}