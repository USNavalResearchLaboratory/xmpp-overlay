/**
 * (c) 2010 Drexel University
 */
package edu.drexel.xop.client;

import java.io.StringReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.xmpp.packet.JID;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.Base64;
import edu.drexel.xop.util.XMLLightweightParser;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Handles client authentication, setups up encryption/compression/etc.
 * 
 * @author David Millar
 * @author Duc Nguyen
 */
public class ClientAuthenticator implements StanzaProcessor {

    private static final Logger logger = LogUtils.getLogger(ClientAuthenticator.class.getName());
    static public final String DOMAIN = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);
    private static final String STREAM_FEATURES_TLS = "<stream:features><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'><required/></starttls></stream:features>";
    static public final String STREAM_FEATURES_PLAIN = "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>";
    private static final String STREAM_CLOSE = "</stream:stream>";
    private static final String STARTTLS_PROCEED = "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";
    private static final String STARTTLS_FAIL = "<failure xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";
    private static final String TEMPORARY_FAIL = "<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><temporary-auth-failure/></failure>";
    private static final String SUCCESS = "<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"></success>";
    private static final String BIND_FEATURE = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>";
    private static final String SESSION_FEATURE = "<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>";
    private static final String SUB_FEATURE = "<sub xmlns='urn:xmpp:features:pre-approval'/>";
    private static final String STREAM_FEATURES = "<stream:features>" + BIND_FEATURE + SESSION_FEATURE + SUB_FEATURE
        + "</stream:features>";

    private AuthenticationProvider authenticationProvider;
    private XMLLightweightParser parser = new XMLLightweightParser("UTF-8");
    private XMLInputFactory inputFactory;

    private ClientConnection clientConnection;
    private ClientManager clientManager;

    private boolean authenticated = false;
    private boolean firstStream = true;

    // state machine for TLS+SASL client authentication
    // this reflects the steps in the 9.1.1, 9.1.2 of rfc 6120
    private static enum AUTH_STATE {
        INITIATE, // step 1
        ERROR_INITIATE, START_TLS, NEGOTIATE_TLS_SUCC, NEGOTIATE_TLS_FAIL, SASL_HANDLE_AUTH, // step 9
        RESOURCE_BINDING, // // step 13 bind resource
        HANDLE_PLAIN_TEXT_AUTH, AUTHENTICATED, AUTH_ERROR, VOLUNTARY_FEATURES
    }

    private AUTH_STATE currentState;
    private boolean userAuthenticated;

    /**
     * @param clientConnection
     */
    public ClientAuthenticator(ClientManager clientManager, ClientConnection clientConnection) {
        this.clientConnection = clientConnection;
        this.clientManager = clientManager;
        this.authenticationProvider = new AuthenticationProvider(clientManager);
        init();
    }

    /**
     *
     */
    private void init() {
        currentState = AUTH_STATE.INITIATE;
        logger.fine("setting the current TLS negotiation state to " + AUTH_STATE.INITIATE);
        inputFactory = XMLInputFactory.newInstance();
    }

    /**
     * All packets coming from a client will end up here before the client<br/>
     * is authenticated.
     * 
     * @param s xml chunk to be parsed
     * @param fromJid never used
     */
    @Override
    public void processStanza(String s, String fromJid) throws ParserException {
        logger.fine("parsing string: ----" + s + "----");
        parser.read(s.getBytes());
        if (parser.areThereMsgs()) {
            for (String msg : parser.getMsgs()) {
                logger.fine("msg: ----" + msg + "----");
                processMsg(msg);
            }
        }
    }

    /**
     * handle authentication
     * will also set the authenticated flag
     * 
     * @param retVal
     * @param userName
     * @param password
     * @param jid
     * @return the next authentication state
     */
    private AUTH_STATE processAuthentication(AUTH_STATE retVal, String userName, String password, String jid) {
        if (authenticationProvider == null) {
            logger.log(Level.INFO, "Error!  authenticationProvider is null!");
            throw new RuntimeException("null authenticationProvider!");
        }
        authenticated = authenticationProvider.authenticate(new JID(jid), password);

        // Send 'Success' or 'Failure' message
        if (authenticated) {
            logger.log(Level.FINE, "Client successfully authenticated: " + userName);
            logger.fine("BEFORE clientConnection.jid: " + clientConnection.getJid());
            clientConnection.setJid(new JID(jid));
            logger.fine("AFTER: clientConnection.jid: " + clientConnection.getJid());
            send(SUCCESS);
        } else {
            logger.log(Level.SEVERE, "User failed authentication: " + userName);
            send(TEMPORARY_FAIL);
            clientConnection.stop();
            retVal = AUTH_STATE.ERROR_INITIATE;
        }
        return retVal;
    }

    /**
     * this is the "state machine" that handles all the messages and transitions to other states.
     * 
     * @param msgStr
     */
    void processMsg(String msgStr) {
        logger.fine("Process Message " + msgStr);
        switch (currentState) {
        case INITIATE:
            logger.info("New Client connected... authenticating.");
            currentState = handleInitialState(msgStr);
            break;
        case START_TLS:
            logger.fine("handling start tls state");
            currentState = handleStartTLSState(msgStr);
            break;
        case SASL_HANDLE_AUTH:
            logger.fine("handling client auth");
            currentState = handleClientAuth(msgStr);
            break;
        case RESOURCE_BINDING:
            logger.fine("sending the response");
            currentState = handleResourceBinding();
            break;
        case AUTHENTICATED:
            logger.info("Client authenticated.");
            // urlass: when would we ever reach this point?
            break;
        // this is the old method of authenticating
        case HANDLE_PLAIN_TEXT_AUTH:
            logger.fine("handling plain text authentication");
            currentState = handlePlainTextAuth(msgStr);
            break;
        case AUTH_ERROR:
            handleErrorState();
            break;
        default: // set error state
            currentState = handleErrorState();
        }
    }

    /**
     * handles the case where XOP receives an open stream, the response is to send
     * 
     * @param msgStr
     * @return
     */
    private AUTH_STATE handleInitialState(String msgStr) {
        AUTH_STATE nextState = AUTH_STATE.START_TLS; // NEGOTIATE_TLS;
        // <stream:stream
        // from='juliet@im.example.com'
        // to='im.example.com'
        // version='1.0'
        // xml:lang='en'
        // xmlns='jabber:client'
        // xmlns:stream='http://etherx.jabber.org/streams'>
        try {
            XMLEventReader xmlEventReader = inputFactory.createXMLEventReader(new StringReader(msgStr));
            if (xmlEventReader.hasNext()) {
                XMLEvent evt = xmlEventReader.nextEvent();
                // iterate over all the xml nodes.
                while (evt != null) {
                    if (evt.isStartDocument()) {
                        logger.fine("start of document, stay in INITIATE state");
                        nextState = AUTH_STATE.INITIATE;
                    } else if (evt.isStartElement()) {
                        StartElement se = evt.asStartElement();
                        String startElemName = se.getName().getPrefix();
                        logger.fine("start element name: " + startElemName);
                        if (startElemName != null && startElemName.contains("stream")) {
                            Attribute fromAttr = se.getAttributeByName(new QName("from"));
                            if (fromAttr != null) {
                                logger.fine("fromAttr: " + fromAttr.getValue() + ", setting as JID");
                                JID clientJid = new JID(fromAttr.getValue());
                                clientConnection.setJid(clientJid);
                            }

                            logger.info("Client establishing new stream with authenticator, sending open stream");
                            send("<stream:stream from='"
                                + DOMAIN
                                + "' id=\"multicastX\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\" xml:lang='en'>");

                            // check the force plain text authentication
                            logger.fine("prop: " + XopProperties.USE_TLS_AUTH + " property"
                                + XopProperties.getInstance().getBooleanProperty(XopProperties.USE_TLS_AUTH));
                            if (XopProperties.getInstance().getBooleanProperty(XopProperties.USE_TLS_AUTH)) {
                                logger.fine("using tls authentication, sending " + STREAM_FEATURES_TLS);
                                send(STREAM_FEATURES_TLS);
                                nextState = AUTH_STATE.START_TLS;

                            } else {
                                logger.fine("using plain authentication, sending " + STREAM_FEATURES_PLAIN);
                                send(STREAM_FEATURES_PLAIN);
                                firstStream = false;
                                nextState = AUTH_STATE.HANDLE_PLAIN_TEXT_AUTH;
                            }

                            // return nextState; // we break here because if we try to read the next event we'll throw an exception because there is no close tag
                        } else if (startElemName != null && startElemName.contains("xml")) {
                            logger.fine("start of document, stay in INITIATE state");
                            nextState = AUTH_STATE.INITIATE;
                            // return AUTH_STATE.INITIATE; // we break here because if we try to read the next event we'll throw an exception because there is no close tag
                        }
                    }
                    if (xmlEventReader.hasNext()) {
                        evt = xmlEventReader.nextEvent();
                    }
                }
            }

            xmlEventReader.close();
            nextState = AUTH_STATE.ERROR_INITIATE;

        } catch (XMLStreamException e) {
            if (e.getMessage().contains("Premature end of file")
                || e.getMessage().contains("must start and end within the same entity")) {
                logger.info("XML Streamreader encountered end of stream before a close tag, This should be ok, do nothing.");
            } else {
                e.printStackTrace();
                nextState = AUTH_STATE.ERROR_INITIATE;
            }
        }

        return nextState;
    }

    /**
     * @param msgStr
     * @return
     */
    private AUTH_STATE handleStartTLSState(String msgStr) {
        AUTH_STATE nextState = AUTH_STATE.SASL_HANDLE_AUTH;

        try {
            XMLEventReader xmlEventReader = inputFactory.createXMLEventReader(new StringReader(msgStr));
            XMLEvent evt = xmlEventReader.nextEvent();
            while (evt != null) {
                if (evt.isStartElement()) {
                    StartElement se = evt.asStartElement();
                    QName nm = se.getName();
                    String startElemName = nm.getLocalPart();
                    logger.fine("start element name: " + startElemName);
                    if (startElemName != null && startElemName.contains("starttls")) {
                        String ns = nm.getNamespaceURI();
                        if ("urn:ietf:params:xml:ns:xmpp-tls".equals(ns)) {
                            logger.fine("sending starttls proceed");
                            send(STARTTLS_PROCEED);
                            this.clientConnection.enableSSL();
                            this.clientConnection.sslPostProcessing();

                            // send(STREAM_CLOSE);
                            xmlEventReader.close();

                            // negotiate tls (i.e. turn on ssl)

                            // This comment may be old and wrong:
                            // After this method returns, [something] will turn
                            // on SSL, closing the original, unencrypted stream,
                            // and opening the new encrypted stream.
                            // The sslPostProcessing() method in [something]
                            // will also send some streams to the client.

                            // proceed to next state
                            return nextState;
                        } else {
                            // else not received tls namespace
                            logger.warning("ns not xmpp-tls");
                        }
                    } else {
                        // else start element not starttls
                        logger.warning("not starttls");
                    }
                }
                if (xmlEventReader.hasNext()) {
                    evt = xmlEventReader.nextEvent();
                }
            }

            xmlEventReader.close();

            // Should never reach this point!

            // What are the fail conditions for starttls
            send(STARTTLS_FAIL);
            nextState = AUTH_STATE.NEGOTIATE_TLS_FAIL;

        } catch (XMLStreamException e) {
            if (e.getMessage().contains("Premature end of file")
                || e.getMessage().contains("must start and end within the same entity")) {
                logger.info("XML Streamreader encountered end of stream before a close tag, This should be ok, do nothing.");
            } else {
                e.printStackTrace();
                nextState = AUTH_STATE.NEGOTIATE_TLS_FAIL;
            }
        }

        return nextState;
    }

    /**
     * @param msgStr
     * @return
     */
    private AUTH_STATE handleClientAuth(String msgStr) {
        AUTH_STATE nextState = AUTH_STATE.SASL_HANDLE_AUTH; // step
        // Get Auth info (PLAIN)
        // <auth mechanism="PLAIN" xmlns="urn:ietf:params:xml:ns:xmpp-sasl">AGRhdmUAZGF2ZQ==</auth>

        try {
            XMLEventReader xmlEventReader = inputFactory.createXMLEventReader(new StringReader(msgStr));
            XMLEvent evt = xmlEventReader.nextEvent();
            while (evt != null) {
                if (evt.isStartElement()) {
                    StartElement se = evt.asStartElement();
                    QName nm = se.getName();
                    String startElemName = nm.getLocalPart();
                    logger.fine("start element name: " + startElemName);
                    Attribute attr = se.getAttributeByName(new QName("type"));
                    if (attr != null) {
                        logger.fine("if the thing has a type attr: " + attr.getValue());
                    }
                    if (startElemName != null && startElemName.contains("stream")) {
                        xmlEventReader.close();
                        return nextState; // stay in this state
                    } else if (startElemName != null && startElemName.contains("auth")) {

                        String ns = nm.getNamespaceURI();
                        if ("urn:ietf:params:xml:ns:xmpp-sasl".equals(ns)) {
                            logger.fine("handling sasl auth");

                            int start = msgStr.indexOf(">") + 1;
                            int end = msgStr.indexOf("<", start);
                            String base64info = msgStr.substring(start, end);
                            String[] creds = Base64.decode(base64info).split("\\00");
                            String userName = creds[1];
                            String password = creds[2];
                            logger.finest(userName + " " + password);
                            // Get JID
                            String jid = userName + "@" + DOMAIN;

                            // authenticate the user
                            nextState = processAuthentication(nextState, userName, password, jid);
                            logger.fine("jid: " + clientConnection.getJid());
                            xmlEventReader.close();

                            // proceed to next state
                            return AUTH_STATE.RESOURCE_BINDING;
                        } else {
                            // else not received sasl namespace
                            logger.warning("ns not xmpp-sasl");
                        }
                    } else if (startElemName != null && startElemName.contains("iq")
                        && se.getAttributeByName(new QName("type")).getValue().equalsIgnoreCase("get")) {
                        logger.fine("handling registration request");

                        Attribute fromAttr = se.getAttributeByName(new QName("from"));
                        String jid = fromAttr.getValue();

                        String registeredMsg = "<iq type='result' id='reg1'><query xmlns='jabber:iq:register'><registered/><username>"
                            + jid + "</username><password>a</password><email>does@notmatt.er</email></query></iq>";
                        send(registeredMsg);

                        return nextState;
                    } else {
                        // else start element not starttls
                        logger.warning("not stream or auth");
                    }
                }
                if (xmlEventReader.hasNext()) {
                    evt = xmlEventReader.nextEvent();
                }
            }
        } catch (XMLStreamException e) {
            if (e.getMessage().contains("Premature end of file")
                || e.getMessage().contains("must start and end within the same entity")) {
                logger.info("XML Streamreader encountered end of stream before a close tag, This should be ok, do nothing.");
            } else {
                e.printStackTrace();
                nextState = AUTH_STATE.NEGOTIATE_TLS_FAIL;
            }
        }

        return nextState;
    }

    /**
     * @return
     */
    private AUTH_STATE handleErrorState() {
        send(STREAM_CLOSE);
        return AUTH_STATE.INITIATE; // reset to initial state
    }

    public void send(String s) {
        clientConnection.writeRaw(s.getBytes());
    }

    /**
     * The above method breaks a bunch of strings into individual xml stanzas and feeds them here
     * 
     * @param s a complete xml stanza/message
     * @return
     */
    private AUTH_STATE handlePlainTextAuth(String s) {
        AUTH_STATE retVal = AUTH_STATE.HANDLE_PLAIN_TEXT_AUTH;
        logger.log(Level.FINE, ">>>>>>> thread id:" + Thread.currentThread().getId() + " name: "
            + Thread.currentThread().getName());
        logger.fine("string: {{{{" + s + "}}}}");
        // Authenticating
        if (s.contains("<auth")) {
            // Get Auth info (PLAIN)
            // <auth mechanism="PLAIN" xmlns="urn:ietf:params:xml:ns:xmpp-sasl">AGRhdmUAZGF2ZQ==</auth>
            int start = s.indexOf(">") + 1;
            int end = s.indexOf("<", start);
            String base64info = s.substring(start, end);
            String[] creds = Base64.decode(base64info).split("\\00");
            String userName = creds[1];
            String password = creds[2];

            logger.log(Level.INFO, "Authenticating user: (" + userName + "," + password + ")");

            // Get JID
            String jid = userName + "@" + DOMAIN;

            retVal = processAuthentication(retVal, userName, password, jid);

        } // Respond to second open stream from client
        else if (s.contains("<stream:stream") && !firstStream && authenticated) {
            logger.log(Level.FINE, "Client authenticated and this is the second stream");
            retVal = handleResourceBinding();
        } // Client is closing
        else if (s.contains("</stream")) {
            logger.log(Level.FINE, "Client disconnected while authenticating");
            clientConnection.stop();
        }

        return retVal;
    }

    /**
     * handle binding the client to a resource. just send the resource binding message. the iq manager should handle the subsequent messages
     * 
     * @return
     */
    private AUTH_STATE handleResourceBinding() {
        AUTH_STATE retVal;
        logger.log(Level.FINE, "handling resource binding");
        send("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' id='c2s_345' from='"
            + DOMAIN + "' version='1.0'>" + STREAM_FEATURES);

        clientManager.addLocalUser(clientConnection);
        userAuthenticated = true;
        retVal = AUTH_STATE.AUTHENTICATED;
        return retVal;
    }

    public boolean isAuthenticated() {
        return userAuthenticated;
    }
}