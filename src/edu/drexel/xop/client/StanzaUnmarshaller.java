/*
 * (c) 2010 Drexel University
 */
package edu.drexel.xop.client;

import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XMLLightweightParser;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.DocumentException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This takes xmpp stanzas in string form, creates Packet objects, and <br/>
 * pumps them to a packet listener specified in the constructor.
 *
 * @author David Millar
 */
public class StanzaUnmarshaller implements StanzaProcessor {

    private static final Logger logger = LogUtils.getLogger(StanzaUnmarshaller.class.getName());
    private XMLLightweightParser parser = new XMLLightweightParser("UTF-8");
    private ClientProxy proxy;
    private HashMap<JID, Integer> sourceNextId = new HashMap<>();
    private HashMap<JID, Integer> randomIdPrefix = new HashMap<>();
    Random random = new Random();

    public StanzaUnmarshaller() {
        this.proxy = ClientProxy.getInstance();
    }

    /*
     * (non-Javadoc)
     * 
     * @see edu.drexel.xop.client.StanzaProcessor#processStanza(java.lang.String, java.lang.String)
     */
    @Override
    public void processStanza(String string, String fromJID) throws ParserException {
        try {
            // Authenticated, so start parsing packets
            logger.log(Level.FINE, "Feeding packet parser this: " + string);

            parser.read(string.getBytes());
            if (parser.areThereMsgs()) {
                String[] messages = parser.getMsgs();
                for (String message : messages) {
                    if (!Utils.isCloseStream(message)) {
                        Packet p = Utils.packetFromString(message);
                        p.setFrom(fromJID);
                        logger.fine("Processing packet: " + p);
                        // add an ID, if there isn't one already from the client
                        if(p.getID()==null){
                            modifyId(p);
                        }
                        proxy.processPacket(p);
                    } else {
                        logger.log(Level.FINE, "found close stream: " + message);

                        ClientProxy cp = ClientProxy.getInstance();
                        cp.handleCloseStream(fromJID);

                        // is there anything left in the </stream...> tag?
                        // TODO: we should fix XMLLightweightParser so that we
                        // don't get to this point...
                        int nextTagStartLocation = message.indexOf("<", message.indexOf(">"));
                        if (nextTagStartLocation != -1) {
                            processStanza(message.substring(nextTagStartLocation), fromJID);
                        }
                    }
                }
            } else {
                logger.log(Level.FINE, "No Messages!");
            }
        } catch (DocumentException ex) {
            logger.log(Level.SEVERE, "Error parsing packet from string " + string, ex);
            throw new ParserException("Error parsing packet from string " + string);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Issue parsing data stream at data: " + string, e);
            e.printStackTrace();
        }
    }

    private void modifyId(Packet p) {
        // if it's not a Message, don't modify it
        if (!(p instanceof Message)) {
            return;
        }
        JID from = p.getFrom();
        if (!sourceNextId.containsKey(from)) {
            sourceNextId.put(from, 0);
        }
        if(!randomIdPrefix.containsKey(from)){
            //generate a random number between 1000 and 9999 (we want exactly 4 digits)
            int rint = random.nextInt() % 100;
            rint += 1000*((random.nextInt() % 9)+1);
            randomIdPrefix.put(from, rint);

        }
        p.setID(randomIdPrefix.get(from).toString() + sourceNextId.get(from).toString());
        sourceNextId.put(from, sourceNextId.get(from) + 1);
    }
}
