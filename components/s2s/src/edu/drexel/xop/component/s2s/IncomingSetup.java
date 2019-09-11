/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

import edu.drexel.xop.util.XMLLightweightParser;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.Element;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author charlesr
 */
class IncomingSetup implements ServerSetup {

    private final ServerConnection server;
    private State state;
    static final Logger logger = LogUtils.getLogger(IncomingSetup.class.getName());
    boolean auth = true;
    public IncomingSetup(ServerConnection aThis) {
        server = aThis;
    }

    @Override
    public XMPPStream handle(InputStream inputStream, OutputStream outputStream) throws FailedSetupException {
        XMPPStream stream = new XMPPStream(inputStream, outputStream);
        try {
            String streamID = UUID.randomUUID().toString();
            byte[] buffer = new byte[2048];
            int bytesRead;
            state = State.WAIT_FOR_STREAM;
            XMLLightweightParser parser = server.getParser();
            while (state != State.DONE) {
                //log.logger(Level.INFO, "Current state: {0}", state);
                switch (state) {
                    case WAIT_FOR_STREAM:
                        bytesRead = inputStream.read(buffer);
                        if (bytesRead == -1) {
                            throw new FailedSetupException("No bytes found reading inputStream in WAIT_FOR_STREAM state");
                        }
                        parser.read(buffer, 0, bytesRead);

                        // Pass any stanzas we have off to the listener
                        if (parser.areThereMsgs()) {
                            String[] msgs = parser.getMsgs();
                            for (String s : msgs) {
                  //              logger.logger(Level.INFO, "s:{0}", s);
                                if (s.startsWith("<stream:stream")) {
                                    String tmp = "<stream:stream"
                                            + " xmlns:stream='http://etherx.jabber.org/streams'"
                                            + " xmlns='jabber:server'"
                                            + " xmlns:db='jabber:server:dialback' "
                                             + " id='" + streamID + "' "
                                           + "version='1.0'>";
                                    //ostream.write(tmp.getBytes());
                                    Element e = edu.drexel.xop.util.Utils.elementFromString(s);

                                    //stream.open(streamID, true);
                                    if (e.attribute("version") != null && e.attributeValue("version").equals("1.0")) {
                                        auth = false;
                                        tmp += "<stream:features>"
                                                + " <dialback xmlns='urn:xmpp:features:dialback'>"
                                                + " <required/>"
                                                + "  <errors/>"
                                                + "</dialback>"
                                                + "</stream:features>";
                                        //stream.write(Utils.elementFromString(tmp));
                                    }

                         //           logger.logger(Level.INFO, "auth:{0} {1}", new Object[]{auth, tmp});
                                    outputStream.write(tmp.getBytes());



                                    state = State.WAIT_FOR_AUTH;
                                    break;
                                }
                            }
                        }
                        break;
                    case WAIT_FOR_AUTH:
                        bytesRead = inputStream.read(buffer);
                        if (bytesRead == -1) {
                            throw new FailedSetupException("no bytes read from inputStream in WAIT_FOR_AUTH state.");
                        }
                        parser.read(buffer, 0, bytesRead);
                       // logger.logger(Level.INFO, "Waiting for some auth");
                        // Pass any stanzas we have off to the listener
                        if (parser.areThereMsgs()) {
                            String[] msgs = parser.getMsgs();
                            for (String s : msgs) {
                               // logger.logger(Level.INFO, "s:{0}", s);
                                if (s.startsWith("<db:verify")) {
                         //           logger.logger(Level.INFO, "auth:{0}"+" "+"Hey I am in the first if statement: ", auth);
                                    Element e = edu.drexel.xop.util.Utils.elementFromString(s);
                                    String to, from, id;
                                    to = e.attributeValue("to");
                                    from = e.attributeValue("from");
                                    id = e.attributeValue("id");
                                    String tmp = "<db:verify "
                                            + " from='" + to + "'"
                                            + " to='" + from + "'"
                                            + " id='" + id + "'"
                                            + " type='valid' />";
                             //       logger.logger(Level.INFO, "auth:{0}"+" "+"response: " + "{1}", new Object[]{auth, tmp});
                                    stream.write(edu.drexel.xop.util.Utils.elementFromString(tmp));
                                    state = State.CLOSE;
                                    break;
                                } else if (s.startsWith("<db:result")) {
                           //         logger.logger(Level.INFO, "auth:{0}"+" "+"Hey I am in the second if statement: ", auth);
                                    Element e = edu.drexel.xop.util.Utils.elementFromString(s);
                                    String to, from;
                                    to = e.attributeValue("to");
                                    from = e.attributeValue("from");
                                    //id = e.attributeValue("id");
                                    String tmp = "<db:result "
                                            + " from='" + to + "'"
                                            + " to='" + from + "'"
                                            + " type='valid' />";

                               //     logger.logger(Level.INFO, "auth:{0}"+" "+"response: " + "{1}", new Object[]{auth, tmp});
                                    stream.write(edu.drexel.xop.util.Utils.elementFromString(tmp));
                                    stream.setWritable(false);
                                    stream.setValid(true);
                                    state = State.DONE;
                                    break;
                                }
                            }
                            break;
                        }
                        //TODO (urlass):  make sure this break is supposed to be here...
                        break;
                    case CLOSE:
                       // String tmp = "</stream:stream>";
                        try {
                            //log.logger(Level.SEVERE,"auth:{0}"+" "+"Closing a Stream", auth);
                            stream.close();
                            //outputStream.write(tmp.getBytes());
                        } catch (SocketException ex) {
                            //ignore
                        }
                        server.close();
                        state = State.DONE;
                }

            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception caught on IncomingSetup.handle()", ex);
            throw new FailedSetupException(ex.getMessage());
        }
        return stream;
    }

    public boolean canSend() {
        return false;
    }

    public void setState(State state) {
        this.state = state;
    }

    public State getState() {
        return state;
    }

    public boolean handle(XMPPStream stream, String domain) throws FailedSetupException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setLocal(String local) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    @Override
    public String toString(){
        return "Incoming: "+auth;
    }
}
