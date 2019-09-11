package edu.drexel.xop.client.local;

import edu.drexel.xop.client.AuthenticationProvider;
import edu.drexel.xop.client.SAXTerminatorException;
import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.core.LocalXMPPClient;
import edu.drexel.xop.packet.LocalPacketProcessor;
import edu.drexel.xop.util.Base64;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.tree.DefaultElement;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import org.xmpp.packet.*;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moved XML Handler outside of the ClientXMLProcessor
 *
 * @deprecated Moving toward kotlin version
 */
class UnauthenticatedClientHandler extends DefaultHandler {
    private static Logger logger = LogUtils.getLogger(UnauthenticatedClientHandler.class.getName());
    private String currentElement = "";
    private String text = "";
    private Element element = null;
    private boolean stanza = false;
    private boolean authenticated = false;
    private boolean ssl = false;
    private JID jid = null;
    private AuthenticationProvider authenticationProvider;
    private LocalClientConnection xopConnection;
    private final String DOMAIN = XOP.DOMAIN; //XopProperties.getProperty("xop.domain");
    private ClientManager clientManager;
    private LocalPacketProcessor localPacketProcessor;

    UnauthenticatedClientHandler(LocalClientConnection clientConnection,
                                 ClientManager clientManager, LocalPacketProcessor localPacketProcessor) {
        this.xopConnection = clientConnection;
        this.clientManager = clientManager;
        this.localPacketProcessor = localPacketProcessor;
        authenticationProvider = new AuthenticationProvider(clientManager); // TODO 2018-10-15 REFACTOR, this should not be a singleton.
    }

    private void send(String s) {
        xopConnection.writeRaw(s.getBytes());
    }

    public void startElement(String uri, String localName, String qName, Attributes attr) { // throws SAXTerminatorException {
        currentElement = localName;
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("uri: " + uri + " localname: " + localName + " qName: " + qName + " attr: " + attr);
        }

        if (!stanza) {
            if (!ssl && !authenticated) {
                //negotiate tls
                if (localName.equals("stream")) {
                    String from = attr.getValue("from");
                    if (from != null) {
                        if (logger.isLoggable(Level.FINE))
                            logger.fine("fromAttr: " + from + ", setting as JID");
                        jid = new JID(from);
                    }
                    logger.info("Client establishing new stream with authenticator, sending open stream");
                    send("<stream:stream from='"
                            + DOMAIN
                            + "' id=\"multicastX\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\" xml:lang='en'>");
                    if (XOP.TLS.AUTH) {//XopProperties.getBooleanProperty("xop.tls.auth")) {
                        if (logger.isLoggable(Level.FINE))
                            logger.fine("Using tls authentication, sending " + CONSTANTS.AUTH.STREAM_FEATURES_TLS);
                        send(CONSTANTS.AUTH.STREAM_FEATURES_TLS);
                    } else {
                        if (logger.isLoggable(Level.FINE))
                            logger.fine("Using plain authentication, sending " + CONSTANTS.AUTH.STREAM_FEATURES_PLAIN);
                        send(CONSTANTS.AUTH.STREAM_FEATURES_PLAIN);
                    }
                } else if (localName.equals("starttls")) {
                    //start ssl
                    if (logger.isLoggable(Level.FINE)) logger.fine("Sending starttls proceed");
                    send(CONSTANTS.AUTH.STARTTLS_PROCEED);
                    ssl = true;
                    xopConnection.enableSSL();
                }
            } else {
                //ssl post processing
                if (localName.equals("stream") && !authenticated) {
                    if (logger.isLoggable(Level.FINE)) logger.fine("Handling ssl post processing");
                    String SASL_START_STREAM = "<stream:stream from='" + DOMAIN
                            + "' id='multicastX' to='" + ((jid != null) ? jid : "")
                            + "' version='1.0' xml:lang='en' xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams'>";
                    send(SASL_START_STREAM);
                    send(CONSTANTS.AUTH.STREAM_FEATURES_PLAIN);
                } else if (localName.equals("stream")) {
                    //handle resource binding
                    if (logger.isLoggable(Level.FINE))
                        logger.fine("User " + jid + " authenticated, handling resource binding");
                    send("<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' id='c2s_345' from='"
                            + DOMAIN + "' version='1.0'>" + CONSTANTS.AUTH.STREAM_FEATURES);

                    LocalXMPPClient localXMPPClient = new LocalXMPPClient(jid, jid.toString(), "", null, xopConnection);
                    clientManager.addLocalXMPPClient(localXMPPClient);
                    stanza = true;
                }
            }
        } else { // client authenticated and bound. Ready to receive XMPP stanzas
            //create a new element if there isn't a currently opened one
            if (element == null) {
                element = new DefaultElement(new QName(qName, new Namespace(null, uri)));
            } else {
                //if there is an existing element, add this one to it
                Element newElement = new DefaultElement(new QName(qName, new Namespace(null, uri)));
                element.add(newElement);
                element = newElement;
            }

            //Add all the attributes to the element
            for (int i = 0; i < attr.getLength(); i++) {
                element.addAttribute(attr.getQName(i), attr.getValue(i));
            }
        }
        text = "";
    }

    /**
     * Adds characters (between element tags) to the current element being constructed by this
     * XML processor
     *
     * @param ch
     *         the characters
     * @param start
     *         start index
     * @param length
     *         end index
     */
    public void characters(char[] ch, int start, int length) {
        //push any content between tags to the text variable
        text = text + new String(ch, start, length);
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest(text);
        }
        //if stanza parsing is enabled, set the content of the current element
        if (stanza && element != null) {
            element.setText(text);
        }
    }

    /**
     * Makes calls to outside processes once the end of an element is reached. If the client
     * has not been authenticated, then authentication is carried out. Once authenticated,
     * this method will construct XMPP MessageByteBuffer, IQ, or Presence messages and process as an
     * Incoming Packet
     *
     * @param uri
     *         the uri of the element
     * @param localName
     *         localname e.g. iq, message, stream
     * @param qName
     *         the qualified name
     *
     * @throws SAXTerminatorException
     *         if the process is interrupted
     */
    public void endElement(String uri, String localName, String qName) throws SAXTerminatorException {
        if (logger.isLoggable(Level.FINEST))
            logger.finest("uri: " + uri + " localName: " + localName + " qName: " + qName);
        if (!stanza) {
            //handle authentication
            handleAuthentication(localName);
        } else {
            //handle end of stream
            if (localName.equals("stream")) {
                if (logger.isLoggable(Level.FINE)) logger.fine("Found close stream tag.");
                throw new SAXTerminatorException("client connection closing");
            }

            if (element != null) {
                handleCompleteElement(localName);
            }
        }
        if (logger.isLoggable(Level.FINEST))
            logger.finest("leaving end element, authenticated?" + authenticated);
    }

    private void handleCompleteElement(String localName) {
        //create packets from complete xml elements
        if (element.getParent() != null) {
            element = element.getParent();
            return;
        }


        Packet p = null;
        switch (localName) {
            case "iq":
                p = new IQ(element);
                break;
            case "presence":
                if (XOP.ENABLE.DELAY) {
                    Utils.addDelay(element);
                    logger.finest("Added delay element to Presence");
                }
                p = new Presence(element);
                break;
            case "message":
                if (XOP.ENABLE.DELAY) {
                    Utils.addDelay(element);
                    logger.finest("Added delay element to MessageByteBuffer");
                }
                p = new Message(element);
                break;
            default:
                logger.warning("Error parsing packet: " + localName + " : unknown packet type.");
                break;
        }

        if (p != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.finer("Setting FROM attribute on Packet to the JID of the client");

            }
            // NOTE: this is a hack to ensure that packets from a connected client are tagged with their source.
            p.setFrom(jid);
            if (logger.isLoggable(Level.FINER))
                logger.finer("Processing packet from this client. Packet: " + p);
            localPacketProcessor.processPacket(p.getFrom(), p);
            // XOProxy.getInstance().processIncomingPacket(p, true);
        }
        element = null;
    }

    private void handleAuthentication(String localName) {
        if (localName.equals("auth")) {
            String[] creds = Base64.decode(text).split("\\00");
            if (logger.isLoggable(Level.FINER))
                logger.finer("creds is: " + creds[0] + ", " + creds[1]);
            String username = creds[1];
            // a password is not currently required for xabber accounts
            String password = "";
            if (creds.length > 2) {
                password = creds[2];
            }
            if (logger.isLoggable(Level.FINEST))
                logger.finest("Authenticating username: " + username + " password: " + password);
            String id = username + "@" + DOMAIN;

            if (authenticationProvider == null) {
                logger.log(Level.INFO, "Error!  authenticationProvider is null!");
                throw new RuntimeException("null authenticationProvider!");
            }
            authenticated = authenticationProvider.authenticate(new JID(id), password);

            if (authenticated) {
                logger.info("Client successfully authenticated: " + username);
                jid = new JID(id);
                send(CONSTANTS.AUTH.SUCCESS);
            } else {
                logger.severe("User failed authentication: " + username);
                send(CONSTANTS.AUTH.TEMPORARY_FAIL);
            }
        }
    }
}

