package edu.drexel.xop.gateway;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.tree.DefaultElement;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketExtension;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.client.SAXTerminatorException;
import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.packet.LocalPacketProcessor;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * state machine for dialback and as a receiving S2S connection
 * Created by duc on 5/19/16.
 */
class ReceivingGatewayXMLHandler extends DefaultHandler {
    private final static Logger logger = LogUtils.getLogger(ReceivingGatewayXMLHandler.class.getName());
    //    private static boolean asAuthoritativeServer = true;
    private ReceivingGatewayConnection receivingGatewayConnection;
    private boolean multiplexedAuth;

    private enum S2S_DIALBACK_STATE {
        STEP_1,
        RCV_STREAM_OPEN,
        SND_STREAM_OPEN_FEATURES,
        RCV_DIALBACK_VERIFY,
        AWAIT_CLOSE_STREAM,
        RCV_DIALBACK_RESULT_KEY,
        STEP_3_RCV_FEATURES,
        RCV_STARTTLS,
        AUTHENTICATED,
        ERROR,
    }

    // private boolean delayEnabled = false;

    private String hashKey = null; //a received hashkey

    // needs to be static because multiple threads are accessing this
    private static boolean tlsEnabled = false;

    private Map<String, String> sessionIds
            = Collections.synchronizedMap(new HashMap<>());
    private String sessionId;
    // protected String subdomainRemoteHost = null;
    private String remoteHost = null;
    private String xopGatewayDomainName = null;

    private Element step2_stream;
    private Element dialbackVerifyElement;
    private Element dialbackResultElement;
    private Element starttlsElement;


    private Element currentElement = null;

    private S2S_DIALBACK_STATE currentState = S2S_DIALBACK_STATE.RCV_STREAM_OPEN;

    private LocalPacketProcessor localPacketProcessor;

    /**
     * constructor
     * @param xopConnection the connection to mess around with.
     */
    ReceivingGatewayXMLHandler(ReceivingGatewayConnection xopConnection, LocalPacketProcessor localPacketProcessor) {
        this.receivingGatewayConnection = xopConnection;
        this.localPacketProcessor = localPacketProcessor;
        // this.sessionIds = Collections.synchronizedMap(new HashMap<String, String>());
    }

    @Override
    public void startElement(String uri, String localName,
                             String qName, Attributes attr){
        if (logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Received: [[--");
            sb.append("localName: ").append(localName);
            sb.append(", ");
            sb.append("qName:").append(qName);
            sb.append(", ");
            sb.append("uri: ").append(uri);
            sb.append(", ");
            sb.append("attributes: [");
            for (int i = 0; i < attr.getLength(); i++) {
                sb.append("(").append(attr.getQName(i)).append(',').append(attr.getValue(i)).append("), ");
            }
            sb.append("]");
            sb.append("--]]");
            logger.finest(sb.toString());
            logger.finest("Current State: "+currentState);
        }

        try{
            switch(currentState){
                case RCV_STREAM_OPEN:
                    handleRcvStreamOpenStartElement( uri, localName, qName, attr);
                    break;
                case RCV_DIALBACK_VERIFY:
                    handleDialbackVerifyStartElement(uri, localName, qName, attr);
                case STEP_3_RCV_FEATURES:
                    // handleFeaturesStartElement( uri, localName, qName, attr);
                    break;
                case RCV_DIALBACK_RESULT_KEY: // step 1 of xep-0220
                    handleRcvDialbackResultStartElement(uri, localName, qName, attr);
                    break;
                case RCV_STARTTLS: // RFC6121 TLS initiation
                    handleRcvStarttlsStartElement( uri, localName, qName, attr );
                    break;
                case ERROR:
                    logger.severe("ERROR state!");
                    break;
                case AUTHENTICATED:
                    handleAuthenticatedStartElement(uri, localName, qName, attr);
                    break;
                default:
                    logger.info("unhandled start currentElement for current state "+currentState);
            }
        } catch(IOException ioe){
            logger.log(Level.SEVERE, "IOException caught writing bytes", ioe);
            throw new Error(ioe);
        }
    }


    @Override
    public void characters(char[] ch, int start, int length) {
        String chars = new String(ch, start, length);
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Recieved: [[chars: " + chars+"]]");
            logger.log(Level.FINEST, "currentState: "+currentState+" currentElement: "+currentElement);
        }

        switch(currentState){
            case RCV_DIALBACK_VERIFY:
            case RCV_DIALBACK_RESULT_KEY:
                hashKey = chars;
                break;
            case AUTHENTICATED:
                if (currentElement != null) {
                    currentElement.setText(chars);
                }
                break;
            default:
                logger.severe("unhandled state: "+currentState+", chars: [["+chars+"]]");
        }
    }

    @Override
    public void endElement(String uri, String localName,
                           String qName) throws SAXTerminatorException {
        if (logger.isLoggable(Level.FINEST)) {
            String sb = "Received: [[--" + "localName: "+localName+", "+"qName: "+qName
                    +", "+"uri: "+uri+"--]";
            logger.log(Level.FINEST, sb);
            logger.log(Level.FINEST, "currentState: "+currentState);//+", sessionId: "+sessionId+", remoteHost: "+remoteHost);
        }

        try {
            switch (currentState) {
                case RCV_DIALBACK_VERIFY:
                    handleDialbackVerifyEndElement(uri, localName);//, qName);
                    break;
                case AWAIT_CLOSE_STREAM:
                    handleCloseStream(localName);
                    break;
                case RCV_DIALBACK_RESULT_KEY:
                    handleRcvDialbackResultEndElement();
                    break;
                case RCV_STARTTLS:
                    handleRcvStarttlsEndElement();//uri, localName, qName);
                    break;
                case AUTHENTICATED:
                    handleAuthenticatedEndElement(uri, localName, qName);
                    break;
                default:
                    logger.severe("unknown state: " + currentState + ". current currentElement: " + currentElement.asXML());
            }
        } catch(IOException ioe){
            logger.log(Level.SEVERE, "IOException caught writing bytes", ioe);
            throw new Error(ioe);
        }
        logger.finest("currentState: "+currentState+". endElement complete");
    }


    private void handleRcvStreamOpenStartElement(String uri, String localName,
                                                 String qName, Attributes attr) throws IOException{

        if (!"stream".equals(localName) ){ //||
                //!CONSTANTS.GATEWAY.SERVER_DIALBACK_NAMESPACE.equals(uri)) {
            logger.severe("unknown element! "+localName+" or uri: "+uri);
            currentState = S2S_DIALBACK_STATE.ERROR;
            return;
        }
        step2_stream = createElement(step2_stream, uri, qName, attr); //localName,
        logger.fine("Receiving server received an open stream: "+step2_stream.asXML());

        extractToFromSessionId(attr);

        // confirm open stream
        String sessionId = sessionIds.get(xopGatewayDomainName +"=="+remoteHost);
        String openStreamAndFeatures = GatewayUtil.generateS2SOpenStream(xopGatewayDomainName, remoteHost, sessionId  );//CONSTANTS.GATEWAY.STREAM_OPEN_SERVER_WITH_DIALBACK;
        if (ServerDialbackSession.nextIncomingStreamIsForAuthoritativeServer ) {
        //if (!receivingGatewayConnection.isDomainVerified(remoteHost)) {
            openStreamAndFeatures += CONSTANTS.GATEWAY.STREAM_FEATURES_DIALBACK;
            ServerDialbackSession.nextIncomingStreamIsForAuthoritativeServer = false; // Set this back to be ready as a Receiving Server
            currentState = S2S_DIALBACK_STATE.RCV_DIALBACK_VERIFY;
            logger.fine("Sending open stream and dialback stream features: [["+openStreamAndFeatures+"]]");
        } else {
            if( XOP.GATEWAY.TLSAUTH && !tlsEnabled ) {
                openStreamAndFeatures += CONSTANTS.GATEWAY.STREAM_FEATURES_DIALBACK_TLS;
                currentState = S2S_DIALBACK_STATE.RCV_STARTTLS;
            } else {
                openStreamAndFeatures += CONSTANTS.GATEWAY.STREAM_FEATURES_DIALBACK;
                currentState = S2S_DIALBACK_STATE.RCV_DIALBACK_RESULT_KEY;
                tlsEnabled = false; // 2018-04-03 resetting to false if we need to reconnect and establish TLS
            }
            logger.fine("Sending open stream and stream features: [["+openStreamAndFeatures+"]]");
        }

        ServerDialbackSession.receivingGatewayConnections.put(xopGatewayDomainName +"=="+remoteHost, receivingGatewayConnection);
        receivingGatewayConnection.writeRaw(openStreamAndFeatures.getBytes());
    }

    private void extractToFromSessionId(Attributes attr) {
        if (attr.getValue("from") != null) {
            String fromAttr = attr.getValue("from");
            JID fromJID = new JID(fromAttr);
            remoteHost = fromJID.getDomain();
        } else {
            remoteHost = XOP.GATEWAY.SERVER;
            logger.fine("fromJID not found, remoteHost set to: "+remoteHost);
        }
        if (attr.getValue("to") != null){
            String toAttr = attr.getValue("to");
            JID toJID = new JID(toAttr);
            xopGatewayDomainName = toJID.getDomain();
        } else {
            xopGatewayDomainName = XOP.GATEWAY.DOMAIN;
            logger.fine("toJID not found, xopGatewayDomainName set to: "+xopGatewayDomainName);
        }
        if (attr.getValue("id") != null) {
            String sessionId = attr.getValue("id");
            logger.fine("SESSION ID FOR RECEIVING CONNECTION: "+sessionId);
            sessionIds.put(xopGatewayDomainName +"=="+remoteHost, sessionId );
            this.sessionId = sessionId;
        } else {
            logger.fine("No SESSION ID found in Attr");
            logger.fine("sessionId: "+this.sessionId);
            logger.fine("RETRIEVING FROM SET OF SESSION IDS: "+sessionIds);
            String sessionId = sessionIds.get(xopGatewayDomainName +"=="+remoteHost);
            if( sessionId == null ){
                logger.fine("not found for: "+xopGatewayDomainName +"=="+remoteHost+" trying: "+remoteHost+"=="+xopGatewayDomainName);
                sessionId = sessionIds.get(remoteHost+"=="+xopGatewayDomainName );
            }
            if( sessionId == null ){
                if( this.sessionId != null) {
                    sessionId = this.sessionId;
                } else {
                    sessionId = Utils.generateID(10);
                }
                logger.finer("no sessionId found, generated: " + sessionId);
                sessionIds.put(xopGatewayDomainName + "==" + remoteHost, sessionId);
            }
        }
    }

    private void handleDialbackVerifyStartElement(String uri, String localName, String qName, Attributes attributes){
        if( !"verify".equals(localName)
                || !CONSTANTS.GATEWAY.SERVER_DIALBACK_NAMESPACE.equals(uri) ){
            logger.severe("unknown element! "+localName+" or uri: "+uri);
            currentState = S2S_DIALBACK_STATE.ERROR;
            return;
        }
        dialbackVerifyElement =
                createElement(dialbackVerifyElement, uri, qName, attributes); //localName,
        logger.fine("Receiving server received a dialback Verify: "+dialbackVerifyElement.asXML());
        extractToFromSessionId(attributes);
        currentElement = dialbackVerifyElement;
    }

    private void handleDialbackVerifyEndElement(String uri, String localName) throws IOException {
        //verify hash key
        if( (!"verify".equals(localName) )
                || !CONSTANTS.GATEWAY.SERVER_DIALBACK_NAMESPACE.equals(uri) ){
            logger.severe("unknown element! localName:"+localName+", uri: "+uri);
            currentState = S2S_DIALBACK_STATE.ERROR;
            return;
        }

        if( !ServerDialbackSession.authenticatedReceivingServerNames.contains(remoteHost) ){
            String sessionId = sessionIds.get(xopGatewayDomainName +"=="+remoteHost);
            logger.fine("Validating dialback key rcv sessionId: "+sessionId);
            //generate key from stream info and make sure it matches
            logger.finer("hashkey is ______: " + hashKey);
            String key = xopGatewayDomainName +"=="+remoteHost;
            String savedHash = ServerDialbackSession.initiatingServerKeys.get(key);
            logger.finer("saved hashkey ___: " + savedHash);

            if (hashKey != null && hashKey.equals(savedHash)) {
                //validate key
                if (logger.isLoggable(Level.FINE)) logger.fine("Key is valid: " + hashKey);
                String validationMessage =
                        "<db:verify from='" + xopGatewayDomainName + "' id='" + sessionId+ "'"
                                + " to='" + remoteHost + "' type='valid'>" + hashKey + "</db:verify>";
                logger.fine("Sending validation message: "+validationMessage);
                receivingGatewayConnection.writeRaw(validationMessage.getBytes());
                ServerDialbackSession.authenticatedReceivingServerNames.add(remoteHost);
            } else {
                String invalidMessage =
                        "<db:verify from='" + xopGatewayDomainName + "' id='" + sessionId + "'"
                                +" to='" + remoteHost + "' type='invalid'>"
                                + hashKey + "</db:verify>";
                logger.info("Null or mismatched hashkey. Sending invalid message as response: "+invalidMessage);
                receivingGatewayConnection.writeRaw(invalidMessage.getBytes());
                //send error message
            }
            // currentState = S2S_DIALBACK_STATE.RCV_DIALBACK_RESULT_KEY;
            currentState = S2S_DIALBACK_STATE.AWAIT_CLOSE_STREAM;
        } else {
            logger.info("Receiving Gateway not set as authoritative server");
        }
    }

    /**
     * state: RCV_DIALBACK_RESULT_KEY
     * @param uri the uri
     * @param localName the localName
     * @param qName qName
     * @param attr attributes
     */
    private void handleRcvDialbackResultStartElement(String uri, String localName,
                                                     String qName, Attributes attr)
    {
        // DIALBACK protocol state
        if (!localName.equals("result")) {
            logger.severe("incorrect local name element received: "+localName);
            currentState = S2S_DIALBACK_STATE.ERROR;
            return;
        }
        dialbackResultElement = createElement(dialbackResultElement, uri, qName,attr); //localName,
        logger.fine("Receiving server received a dialback RESULT: "+dialbackResultElement.asXML());
        extractToFromSessionId(attr);
        currentElement = dialbackResultElement;
    }

    /**
     * state: RCV_DIALBACK_RESULT_KEY
     *
     * handle Step 1 (receiving the db:result)
     * Sends a verification of the hashkey over the INITIATING gateway connection.
     */
    private void handleRcvDialbackResultEndElement() throws IOException {
        String sessionId = sessionIds.get(xopGatewayDomainName + "==" + remoteHost);
        logger.fine("Validating dialback key rcv sessionId: " + sessionId);
        //generate key from stream info and make sure it matches
        logger.finer("hashkey is ______: " + hashKey);
        logger.fine("sessionId is now: " + sessionId);

        if( multiplexedAuth ){
            // initiate a new connection to the authoritative server
            try {
                AuthorizingGatewayConnection authConnection =
                        new AuthorizingGatewayConnection(remoteHost, XOP.GATEWAY.PORT,
                                xopGatewayDomainName, this.sessionId, hashKey, receivingGatewayConnection);
                Thread t = new Thread(authConnection);
                t.start();
            } catch (IOException e){
                logger.severe("Unable to initiate new connection to "+remoteHost);
            }
            logger.fine("created new authorizing thread for sending a dialback key");

            currentState = S2S_DIALBACK_STATE.AUTHENTICATED;
            currentElement = null;
        } else {
            String verificationMessage =
                    "<db:verify "
                            + "from='" + xopGatewayDomainName + "' "
                            + "id='" + sessionId + "' "
                            + "to='" + remoteHost + "'>"
                            + hashKey
                            + "</db:verify>";
            logger.fine("Sending hashkey as validation to " + remoteHost + " Authoritative Server using initiating connection");

            ServerDialbackSession.initiatingGatewayConnection.writeRaw(verificationMessage.getBytes());
            currentState = S2S_DIALBACK_STATE.AUTHENTICATED;
            currentElement = null;
            logger.info("Receiving server is authenticated.");
        }
    }

    private void handleRcvStarttlsStartElement(String uri, String localName,
                                               String qName, Attributes attr) {
        if( "result".equals(localName) ){
            logger.fine("result element received before TLS, remote server trying dialback before TLS enabled.");
            currentState = S2S_DIALBACK_STATE.RCV_DIALBACK_RESULT_KEY;
            handleRcvDialbackResultStartElement(uri, localName, qName, attr);
            return;
        }

        if( !"starttls".equals(localName) ){
            logger.warning("received incorrect element");
            currentState = S2S_DIALBACK_STATE.ERROR;
            return;
        }
        starttlsElement = createElement(starttlsElement, uri, qName, attr); //localName,
        logger.fine("TLS start element received: "+starttlsElement.asXML());
        currentElement = starttlsElement;
    }

    private void handleRcvStarttlsEndElement() throws IOException {

        // RFC6120 9.2.1 step 5: send TLS Proceed
        logger.fine("Sending TLS proceed");
        String tlsProceed = CONSTANTS.AUTH.STARTTLS_PROCEED;
        receivingGatewayConnection.writeRaw(tlsProceed.getBytes());

        tlsEnabled = true;
        logger.info("Switching to TLS.");
        receivingGatewayConnection.enableSSL();
        if( tlsEnabled ) {
            logger.info("TLS is enabled, now awaiting a stream open.");
            currentState = S2S_DIALBACK_STATE.RCV_STREAM_OPEN;
        } else {
            logger.warning("TLS is not enabled. Sending TLS Fail and Closing Stream");
            receivingGatewayConnection.writeRaw(
                    (CONSTANTS.AUTH.STARTTLS_FAIL+CONSTANTS.AUTH.STREAM_CLOSE).getBytes());
            currentState = S2S_DIALBACK_STATE.ERROR;
        }
    }

    private void handleCloseStream(String localName)
            throws IOException {
        if( (!"stream".equals(localName))){
            logger.severe("unhandled element end");
            currentState = S2S_DIALBACK_STATE.ERROR;
            return;
        }

        logger.info("Remote connection is closing the stream. Send a closing stream and remove from received authenticated sessions");
        receivingGatewayConnection.writeRaw("</stream:stream>".getBytes());

        receivingGatewayConnection.processCloseStream();
        try {
            long time = 500;
            logger.fine("sleeping for " + time + "ms before closing the stream.");
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //throw new SAXTerminatorException("Stream closed after successful dialback");
    }

    private Element createElement(Element element, String uri, String qName, Attributes attr){ // String localName,
        if (element == null) {
            logger.finest("Creating new Element.");
            //create a new currentElement if there isn't a currently opened one
            element = new DefaultElement(new QName(qName, new Namespace(null, uri)));
        } else {
            logger.finest("Adding an currentElement to existing currentElement with qName, " + element.getQName());
            //if there is an existing currentElement, add this one to it
            Element newElement = new DefaultElement(new QName(qName, new Namespace(null, uri)));
            element.add(newElement);
            element = newElement;
        }

        if( attr.getLength() > 0){
            if(logger.isLoggable(Level.FINEST))
                logger.finest("adding " + attr.getLength() + " attributes");
            for (int i = 0; i < attr.getLength(); i++) {
                element.addAttribute(attr.getLocalName(i), attr.getValue(i));
            }
        }
        if(logger.isLoggable(Level.FINEST))
            logger.finest("element: " + element.asXML());
        return element;
    }

    private void handleAuthenticatedStartElement(String uri, String localName, String qName, Attributes attr){
        if( logger.isLoggable(Level.FINER) ){
            logger.finer("Start element in authenticated state");
        }
        currentElement = createElement(currentElement, uri,  qName, attr);//localName,
        logger.finer("created start element for authenticated STATE: "+currentElement.asXML());

        if( "result".equals(localName) && "jabber:server:dialback".equals(uri) ){
            currentState = S2S_DIALBACK_STATE.RCV_DIALBACK_RESULT_KEY;
            logger.fine("Received new dialback result. Setting current State to "+currentState);
            String sessionId = sessionIds.get(xopGatewayDomainName +"=="+remoteHost);
            logger.fine("redirecting to handle dialback result element sessionId: "+sessionId);
            logger.fine("this.sessionId: "+this.sessionId);
            this.sessionId = sessionId;
            handleRcvDialbackResultStartElement(uri, localName, qName, attr);
            multiplexedAuth = true;
        }

    }

    private void handleAuthenticatedEndElement(String uri, String localName, String qName)
            throws SAXTerminatorException {

        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Handling element for authenticated receiving server.");
            logger.finer("RemoteHost: " + remoteHost + " localName: " + localName);
            logger.finer("authenticatedReceivingServerNames: " + ServerDialbackSession.authenticatedReceivingServerNames);
        }

        if (!ServerDialbackSession.authenticatedReceivingServerNames.contains(remoteHost)) {
            logger.severe("No authenticated servers found for host:" + remoteHost + ". Closing!");
            throw new SAXTerminatorException("No authenticated servers found for host:" + remoteHost);
        }

        //handle end of stream
        if (localName.equals("stream")) {
            logger.info("Received close stream tag.");
            throw new SAXTerminatorException("gateway closing");
        }

        //create packets from complete xml elements
        if (currentElement != null) {
            if (currentElement.getParent() != null) {
                if (logger.isLoggable(Level.FINER))
                    logger.finer("we have a parent: " + currentElement.getParent().asXML());
                currentElement = currentElement.getParent();
            } else {
                if (logger.isLoggable(Level.FINER))
                    logger.finer("process the currentElement: " + currentElement.asXML() + "localName: " + localName);
                switch(localName){
                    case "iq":
                        IQ iq = new IQ(currentElement);
                        handleIqEndElement(iq);
                        break;
                    case "presence":
//                        if( delayEnabled ) {
//                            currentElement = addDelay(currentElement);
//                        }
                        Presence p = new Presence(currentElement);
                        handlePresenceEndElement(p);
                        break;
                    case "message":
//                        if( delayEnabled ) {
//                            currentElement = addDelay(currentElement);
//                        }
                        Message m = new Message(currentElement);
                        handleMessageEndElement(m);
                        break;
                    default:
                        logger.warning("Error parsing packet: " + localName +
                                " : unknown packet type. Also, qName: " + qName + " and uri: " + uri);
                        logger.warning("Element: " + currentElement.toString());
                }
                currentElement = null;
            }
        } else {
            logger.finer("currentElement is null");
        }
    }

    /**
     * Process Presence messages from the gateway connection. Advertise the presence message.
     *
     * @param p the presence mssage to be advertised or removed
     */
    private void handlePresenceEndElement(Presence p) {
        if (logger.isLoggable(Level.FINE)) logger.fine("handling a presence message: " + p);
        // Advertise presence message if this is an occupant joining the room
        if (comingFromMucOccupant(p)) {
            if (p.isAvailable()) {
                processAvailableMUCPresence(p);
            } else { // occupant is unavailable
                processUnavailableMUCPresence(p);
            }
        } else {
            if (logger.isLoggable(Level.FINE))
                logger.fine("Presence not destined for a MUC room. " + p);
            // TODO 2018-02-09 handle adding users
            localPacketProcessor.processPacket(p.getFrom(), p);
        }
    }

    /**
     * process an available MUC presence message from the gateway
     *
     * @param p the available presence
     */
    private void processAvailableMUCPresence(Presence p) {
        JID remoteClientJID = null; //new JID(p.getFrom().getResource(), XOP.GATEWAY.SERVER, null);

        PacketExtension pe = p.getExtension("x", "http://jabber.org/protocol/muc#user");
        if( pe != null ){
            // Otherwise, this is a presence message from occupants registered with a MUC
            Iterator iter = pe.getElement().elementIterator("item");
            while (iter.hasNext()) {
                Element elem = (Element) iter.next();
                Attribute jidAttr = elem.attribute("jid");
                logger.finer(elem.asXML() + " jid attr " + jidAttr);

                if (jidAttr == null) {
                    remoteClientJID = new JID(p.getFrom().getResource(), XOP.DOMAIN, null);
                    logger.warning("jidAttr in packet extension is null! using: " + remoteClientJID);
                } else {
                    remoteClientJID = new JID(elem.attribute("jid").getValue());
                    if (XOP.GATEWAY.REWRITEDOMAIN) {
                        JID tempRemoteJID = new JID(elem.attribute("jid").getValue());
                        remoteClientJID = Utils.rewriteDomain(tempRemoteJID, XOP.DOMAIN);
                    }
                }
            }
        }

        JID mucOccupantJID = p.getFrom();
        if (XOP.GATEWAY.REWRITEDOMAIN) {
            JID tempMucOccJID = p.getFrom();
            mucOccupantJID = Utils.rewriteDomain(tempMucOccJID, "conference." + XOP.DOMAIN);
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("remoteClientJID: " + remoteClientJID
                    + ", mucOccupantJID: " + mucOccupantJID);
            logger.fine("XOProxy.getRoomOccupantIds() [["
                    + XOProxy.getInstance().getRoomOccupantIds() + "]]");
            logger.fine("XOProxy.getRoomIds() [["
                    + XOProxy.getInstance().getRoomIds() + "]]");
        }

        if (!XOProxy.getInstance().getRoomOccupantIds().contains(mucOccupantJID)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("remoteClientJID: " + remoteClientJID
                        + ", mucOccupant, " + mucOccupantJID
                        + ", has NOT been added to the room yet add now.");
            }
            Presence mucOccPresence = new Presence();
            mucOccPresence.setTo(mucOccupantJID);
            mucOccPresence.setFrom(remoteClientJID);
            mucOccPresence.setID(p.getID());
            mucOccPresence.addChildElement("x", "http://jabber.org/protocol/muc");
            if(logger.isLoggable(Level.FINER))
                logger.finer("generated mucOccupantPresence: {{"+mucOccPresence+"}}");
            XOProxy.getInstance().getXopNet().getSDManager().advertiseMucOccupant(mucOccPresence);

            // usually addClientToRoom(p) also
            XOProxy.getInstance().addClientToRoom(mucOccPresence, true);

            // Add the room to set of MUC rooms
            JID mucRoom = new JID(mucOccupantJID.getNode(), mucOccupantJID.getDomain(), null);
            ServerDialbackSession.gatewayMUC.add(mucRoom);
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("remoteClientJID: " + remoteClientJID
                        + ", mucOccupant, " + mucOccupantJID
                        + ", has ALREADY been added to the room.");
            }
        }
        ServerDialbackSession.remoteMUCOccupants.add(mucOccupantJID);
        logger.fine("remoteMUCOccupants: [["+ServerDialbackSession.remoteMUCOccupants+"]]");
    }

    /**
     * process incoming Unavailable MUC Presence from the gateway
     *
     * @param p the presence message
     */
    private void processUnavailableMUCPresence(Presence p) {
        JID mucOccupantJID = p.getFrom();
        // JID remoteClientJID = p.getTo();
        if (XOP.GATEWAY.REWRITEDOMAIN) {
            mucOccupantJID = Utils.rewriteDomain(mucOccupantJID, "conference." + XOP.DOMAIN);
            p.setFrom(mucOccupantJID);
        }

        if( XOProxy.getInstance().getRoomOccupantIds().contains(mucOccupantJID)) {
            JID roomJID = mucOccupantJID.asBareJID();
            logger.fine("Calling removeFromRoom " + roomJID + " on :"+p);
            XOProxy.getInstance().removeFromRoom(roomJID, p, true);
        } else {
            logger.fine(mucOccupantJID + " not in set of RoomOccupantIds, not removing from rooms");
        }
        /*
            2019-06-15 The above will not remove muc occupant on openfire servers because the S2S is sending
            unavailable presence TO the full JID of the
         */
        ServerDialbackSession.remoteMUCOccupants.remove(mucOccupantJID);
        logger.fine("remoteMUCOccupants: [["+ServerDialbackSession.remoteMUCOccupants+"]]");

        // Note: We don't want to remove MUC since there could be users.
    }

    /**
     * send message to XOP "cloud" if it is coming from openfire XMPP clients in a muc room.
     *
     * @param p the message to process
     */
    private void handleMessageEndElement(Message p) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("handling message from openfire muc room: " + p);
        }
        if (p.getType() == null || !"groupchat".equals(p.getType().name())) {
            if (logger.isLoggable(Level.FINE)) logger.fine("A one-to-one chat message. " + p);
            // TODO send over one-to-one transport TEST this 2018-02-13
            XOProxy.getInstance().getXopNet().sendToOneToOneTransport(p);
            return;
        }

        Utils.rewritePacketFromGateway(p);
        localPacketProcessor.processPacketFromGateway(p.getFrom(), p);
    }

    /*
    private Element addDelay(Element e) {
        Element delay = new DefaultElement(new QName("delay", new Namespace(null, CONSTANTS.MISC.DELAY_NAMESPACE)));
        DateFormat df = new SimpleDateFormat(CONSTANTS.MISC.XMPP_DATETIME_FORMAT, Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        delay.addAttribute("stamp", df.format(new Date()));
        e.add(delay);
        return e;
    }
    */

    /**
     * Tests whether this presence message from Openfire is coming from a MUC Occupant
     * so we should create an advert or remove advert from SD system.
     *
     * @param p the presence message to test
     * @return true if this is coming from a muc occupant, false otherwise.
     */
    private boolean comingFromMucOccupant(Packet p) {
        boolean retVal = false;
        if (p.getFrom() != null) {
            if (p.getFrom().toString().contains("conference")) {
                retVal = true;
            }
        }

        if (p.getTo() != null) {
            if (p.getTo().toString().contains("conference")) {
                retVal = true;
            }
        }

        return retVal;
    }

    private void handleIqEndElement(IQ iq){
        // TODO 2016-06-03 dnn: handle iq messages coming from remote server
        if (logger.isLoggable(Level.FINER)) logger.finer("processing iq: " + iq.toXML());
        if( iq.getType() == IQ.Type.result
                && xopGatewayDomainName.equals(iq.getTo().toString())){
            logger.finest("handling an iq result to proxy by sending an iq set");
            String id = iq.getID();
            logger.fine("handling IQ ping: "+iq);
            ServerDialbackSession.gatewayPing.handlePing(id);
        } else {
            // TODO 2018-03-06 handle IQ messages from remote host
            logger.fine("Processing IQ message from: " + remoteHost);
            localPacketProcessor.processPacket(iq.getFrom(), iq);
        }
    }
}
