/**
 	* (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.dom4j.Element;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.XMLLightweightParser;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * 
 * @author charlesr
 */
class OutgoingSetup implements ServerSetup {

    // private ServerConnection server;
    private State state = State.SEND_STREAM;
    static final Logger logger = LogUtils.getLogger(OutgoingSetup.class.getName());
    private String rdn = "";
    private String ldn = "";
    private XMLLightweightParser parser;
    public static final String CLOSE_STREAM = "</stream:stream>";

    public OutgoingSetup(String ldn, String rdn, XMLLightweightParser parse) {
        // server = aThis;
        this.ldn = ldn;
        this.rdn = rdn;
        parser = parse;
    }

    public XMPPStream handle(InputStream inputStream, OutputStream outputStream) throws FailedSetupException {
        try {
            state = State.SEND_STREAM;
            XMPPStream xmppStream = new XMPPStream(inputStream, outputStream);
            // ldn = server.getHandler().getLocalDomain();
            String streamID;
            String key = "bbbb";
            byte[] buffer = new byte[2048];
            int bytesRead;
            String tmp;
            // XMLLightweightParser parser = server.getParser();
            while (state != State.DONE) {
                // logger.logger(Level.FINER, "Current state: {0}", state);
                switch (state) {
                case SEND_STREAM:
                    xmppStream.open();
                    state = State.WAIT_FOR_STREAM;

                    break;
                case WAIT_FOR_STREAM:
                    bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) {
                        throw new FailedSetupException("no bytes read in WAIT_FOR_STREAM state!");
                    } else {
                        logger.log(Level.ALL, Arrays.toString(buffer));
                    }
                    parser.read(buffer, 0, bytesRead);

                    // Pass any stanzas we have off to the listener
                    if (parser.areThereMsgs()) {
                        String[] msgs = parser.getMsgs();
                        for (String s : msgs) {
                            logger.fine("WAIT_FOR_STREAM: INCOMING msg: " + s);
                            if (s.startsWith("<stream:stream")) {
                                Element e = edu.drexel.xop.util.Utils.elementFromString(s);
                                streamID = e.attributeValue("id");
                                xmppStream.setStreamID(streamID);
                                key = Utils.SHA1(ldn + rdn + streamID);
                                state = State.REQUEST_AUTH;
                                break;
                            }
                        }
                    } else {
                        logger.fine("WAIT_FOR_STREAM no messages");
                    }
                    break;
                case REQUEST_AUTH:
                    tmp = "<db:result" + " from='" + ldn + "'" + " to='" + rdn + "'>" + key + "</db:result>";
                    logger.fine("REQUEST_AUTH: OUTGOING " + tmp + " to server");
                    xmppStream.write(edu.drexel.xop.util.Utils.elementFromString(tmp));

                    state = State.WAIT_FOR_AUTH;
                    break;
                case WAIT_FOR_AUTH:

                    bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) {
                        throw new FailedSetupException("no bytes read in WAIT_FOR_AUTH state!");
                    }
                    parser.read(buffer, 0, bytesRead);

                    // Pass any stanzas we have off to the listener
                    if (parser.areThereMsgs()) {
                        String[] msgs = parser.getMsgs();
                        logger.log(Level.FINE, "WAIT_FOR_AUTH: num msgs: " + msgs.length);
                        for (String s : msgs) {
                            logger.log(Level.FINE, "WAIT_FOR_AUTH: INCOMING msg" + s);
                            // <stream:features>
                            // <mechanisms xmlns="urn:ietf:params:xml:ns:xmpp-sasl"></mechanisms>
                            // <dialback xmlns="urn:xmpp:features:dialback"/>
                            // </stream:features>
                            Element e = edu.drexel.xop.util.Utils.elementFromString(s);
                            logger.fine("e.getName(): " + e.getName());
                            if ("stream:features".equals(e.getName())) {
                                Element mechElem = e.element("mechanisms");
                                Element dialback = e.element("dialback");
                                logger.fine("mechelem uri: " + mechElem.getNamespaceURI());
                                logger.fine("dialback uri: " + dialback.getNamespaceURI());
                                if ("urn:xmpp:features:dialback".equals(dialback.getNamespaceURI())) {
                                    tmp = "<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='EXTERNAL'>=</auth>";
                                    logger.fine("WAIT_FOR_AUTH: OUTGOING " + tmp);
                                    xmppStream.write(edu.drexel.xop.util.Utils.elementFromString(tmp));
                                    state = State.WAIT_FOR_SUCCESS;
                                }
                            }
                        }
                    } else {
                        logger.fine("WAIT_FOR_AUTH no messages");
                    }
                    break;
                case WAIT_FOR_SUCCESS:

                    bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) {
                        throw new FailedSetupException("no bytes read in WAIT_FOR_AUTH state!");
                    }
                    parser.read(buffer, 0, bytesRead);

                    // Pass any stanzas we have off to the listener
                    if (parser.areThereMsgs()) {
                        String[] msgs = parser.getMsgs();
                        logger.log(Level.FINE, "WAIT_FOR_AUTH: num msgs: " + msgs.length);
                        for (String s : msgs) {
                            logger.log(Level.FINE, "WAIT_FOR_SUCCESS: INCOMING msg" + s);
                            // <success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>
                            Element e = edu.drexel.xop.util.Utils.elementFromString(s);
                            if ("success".equals(e.getName())
                                && "urn:ietf:params:xml:ns:xmpp-sasl".equals(e.getNamespaceURI())) {
                                logger.fine("Server returns success.");

                                xmppStream.close();
                                logger.fine("Open new stream");
                            } else if (CLOSE_STREAM.equals(e.asXML().trim())) {
                                logger.fine("WAIT_FOR_SUCCESS state: CLOSE_STREAM encountered!");
                                String hostname = XopProperties.getInstance().getProperty(XopProperties.S2S_SERVER);
                                int port = XopProperties.getInstance().getIntProperty(XopProperties.S2S_PORT);
                                xmppStream.createNewOutputStream(hostname, port);
                                xmppStream.setValid(true); // setting to true?
                                return xmppStream;
                                // stream.write("");
                            } else if ("db:result".equals(e.getName())
                                && "valid".equals(e.attribute("type").getValue())) {
                                logger.fine("WAIT_FOR_SUCCESS state: receiving server on " + rdn
                                    + " validated this connection, continue sending traffic");
                                xmppStream.setValid(true);
                                return xmppStream;
                            } else {
                                logger.severe("WAIT_FOR_SUCCESS state: this message didn't follow the dialback protocol!");
                            }
                        }
                    } else {
                        logger.fine("WAIT_FOR_AUTH no messages");
                    }
                    break;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "unable to set up connection. connection state: " + state.toString(), ex);
            throw new FailedSetupException(ex.getMessage());
        }
        return null;
    }

    public boolean canSend() {
        return true;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean handle(XMPPStream stream, String remoteDomain) throws FailedSetupException {
        if (stream.valid()) {
            try {
                byte[] buffer = new byte[2048];
                int bytesRead;
                String key = Utils.SHA1(ldn + remoteDomain + stream.getStreamID());
                state = State.REQUEST_AUTH;
                while (state != State.DONE) {
                    logger.log(Level.FINER, "Current state: {0}", state);
                    switch (state) {
                    case REQUEST_AUTH:
                        String tmp = "<db:result" + " from='" + ldn + "'" + " to='" + rdn + "'>" + key
                            + "  </db:result>";
                        stream.write(edu.drexel.xop.util.Utils.elementFromString(tmp));

                        state = State.WAIT_FOR_AUTH;
                        break;
                    case WAIT_FOR_AUTH:

                        bytesRead = stream.getInputStream().read(buffer);
                        if (bytesRead == -1) {
                            throw new FailedSetupException("WAIT_FOR_AUTH state unable to read buffer");
                        }
                        parser.read(buffer, 0, bytesRead);
                        logger.log(Level.FINEST, "Just chilling : {0}", state);
                        // Pass any stanzas we have off to the listener
                        if (parser.areThereMsgs()) {
                            String[] msgs = parser.getMsgs();
                            for (String s : msgs) {
                                Element e = edu.drexel.xop.util.Utils.elementFromString(s);
                                if (e.attributeValue("type") != null && e.attributeValue("type").equals("valid")) {
                                    return true;
                                }
                            }
                        }
                        break;
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception handling a new XMPPStream", ex);
                throw new FailedSetupException(ex.getMessage());
            }
        }
        return false;
    }

    public void setLocal(String local) {
        ldn = local;
    }

    public State getState() {
        return state;
    }

    @Override
    public String toString() {
        return "Outgoing";
    }
}
