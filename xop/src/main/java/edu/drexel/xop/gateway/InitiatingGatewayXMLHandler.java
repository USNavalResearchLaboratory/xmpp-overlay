package edu.drexel.xop.gateway;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.tree.DefaultElement;
import org.xml.sax.Attributes;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.client.SAXTerminatorException;
import edu.drexel.xop.util.CONSTANTS;
import edu.drexel.xop.util.XOP;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * The XML parser and stream negotiation state machine that generates XMPP stanzas to be
 * processed by xop.
 * Created by duc on 5/16/16.
 */
class InitiatingGatewayXMLHandler extends GatewayXMLHandler {
    private static final Logger logger =
            LogUtils.getLogger(InitiatingGatewayXMLHandler.class.getName());

    enum S2S_STATE {
        STEP_1_SND_STREAM_OPEN,
        STEP_2_RCV_STREAM_OPEN,
        STEP_3_RCV_FEATURES,
        STEP_4_ENABLE_TLS_OR_SEND_DIALBACK,
        STEP_5_RCV_TLS_PROCEED,
        STEP_6_ENABLE_SSL,
        STEP_7_DIALBACK_INITIATION,
        RCV_DIALBACK_VERIFY,
        RCV_VERIFY_VALID,
        VERIFIED_AWAITING_DIALBACK,
        VERIFIED,
        DIALBACK_INITIALIZATION_CONFERENCE_SUBDOMAIN,
        RCV_DIALBACK_VERIFY_CONFERENCE_SUBDOMAIN, // begin SASL negotiation
        AUTHENTICATED_AWAITING_DIALBACK_CONFERENCE_SUBDOMAIN,
        END_CLOSE,
        ERROR
    }

    private Element step2_stream;
    private Element step3_features;
    private Element dialbackResultElement;

    private Element verifyResultElement;
    private String verifyFrom;
    private String verifyTo;

    private Element verifyValidResultElement;
    private String verifyValidFrom;
    private String verifyValidTo;

    private S2S_STATE currentState = S2S_STATE.STEP_2_RCV_STREAM_OPEN;
    private Element currentElement = step2_stream;

    private Deque<Element> featuresElementDeque = new LinkedList<>();

    private InitiatingGatewayConnection initiatingConnection;
    private String remoteHost = null;
    private String sessionId;

    // private boolean dialbackErrors = false; // true if support XEP-0220 Section 2.4.2-2.5
    private boolean tlsAvailable = false;
    private boolean tlsRequired = false;

    // needs to be static because multiple threads are accessing this
    static boolean tlsEnabled = false;

    private boolean pingStarted = false;

    /**
     * default constructor
     * @param initiatingConnection the XOP connection used (initiating)
     */
    InitiatingGatewayXMLHandler(InitiatingGatewayConnection initiatingConnection) {
        super();
        this.initiatingConnection = initiatingConnection;
        logger.info("Constructed InitiatingGatewayXMLHandler");
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attr) {
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
                sb.append("(").append(attr.getQName(i)).append(',')
                        .append(attr.getValue(i)).append("), ");
            }
            sb.append("]");
            sb.append("--]]");
            logger.finest(sb.toString());
            logger.finest("Current State: "+currentState);
        }

        switch(currentState){
            case STEP_1_SND_STREAM_OPEN: // already sent
                //handleSndStreamOpenStartElement( uri, localName, qName, attr);
                break;
            case STEP_2_RCV_STREAM_OPEN:
                handleRcvStreamOpenStartElement( uri, localName, qName, attr);
                break;
            case STEP_3_RCV_FEATURES:
                handleRcvFeaturesStartElement( uri, localName, qName, attr);
                break;
            case STEP_5_RCV_TLS_PROCEED:
                handleRcvTlsProceedStartElement( localName);
                break;
            case RCV_DIALBACK_VERIFY:
                handleRcvDialbackResultStartElement(uri,localName,qName,attr);
                break;
            case RCV_VERIFY_VALID:
                handleRcvVerifyResultStartElement(uri, localName, qName, attr);
                break;
            case VERIFIED_AWAITING_DIALBACK:
                handleVerifyResultStartElement(uri, localName, qName, attr);
                break;
            case VERIFIED:
                handleAuthenticatedStartElement(uri, localName, qName, attr);
                break;
            case ERROR:
                logger.severe("Error state");
                break;
            default:
                logger.info("unhandled start element for current state "+currentState);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        String chars = new String(ch, start, length);
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Current State: "+currentState+". Received: [[chars: " + chars+"]]");
        }
        switch(currentState){
            case STEP_3_RCV_FEATURES:
                handleRcvFeaturesText(ch, start, length);
                break;
            case VERIFIED_AWAITING_DIALBACK:
            case VERIFIED:
                /*if (ClientManager.getInstance().getGateway(new JID(remoteHost)) != null) {
                    if (element != null) {
                        element.setText(chars);
                    }
                }*/
                break;
            case ERROR:
                logger.severe("Error state");
                break;
        }
    }

    /**
     * overridden an end element is encountered.
     * @param uri the uri namespace
     * @param localName name of the element
     * @param qName name of the element
     * @throws SAXTerminatorException if the tag is terminated too soon
     */
    @Override
    public void endElement(String uri, String localName,
                           String qName) throws SAXTerminatorException {
        if (logger.isLoggable(Level.FINEST)) {
            String sb = "Received: [[--" + "localName: "+localName+", "+"qName: "+qName
                            +", "+"uri: "+uri+"--]";
            logger.log(Level.FINEST, sb);
        }

        try {
            switch (currentState) {
                case STEP_3_RCV_FEATURES:
                    handleRcvFeaturesEndElement( localName );
                    break;
                case STEP_5_RCV_TLS_PROCEED:
                    handleRcvTlsProceedEndElement( localName);
                    break;
                case RCV_DIALBACK_VERIFY:
                    handleRcvDialbackResultEndElement( localName );
                    break;
                case RCV_VERIFY_VALID:
                    handleRcvVerifyResultEndElement( uri, localName, qName);
                    break;
                case VERIFIED_AWAITING_DIALBACK:
                    handleVerifyResultEndElement();
                    break;
                case VERIFIED:
                    handleAuthenticatedEndElement(uri, localName, qName);
                    break;
                case END_CLOSE:
                    logger.info("Close stream encountered.");
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Last element processed: [[[" + currentElement + "]]]");
                    }
                    throw new SAXTerminatorException("close stream encountered");
                case ERROR:
                    logger.severe("Error state");
                    break;
                default:
                    logger.severe("unknown state!" + currentState);
            }
        } catch(IOException ioe){
            logger.log(Level.SEVERE, "IOException caught writing bytes", ioe);
            throw new Error(ioe);
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "END element whole xml element: [[[" + currentElement + "]]]");
            logger.log(Level.FINEST, "current state: " + currentState);
        }
    }

    private void handleRcvStreamOpenStartElement(String uri, String localName, String qName,
                                                 Attributes attr){
        logger.finer("handleRcvStreamOpenStartElement");
        if ("stream".equals(localName)) {
            step2_stream = createElement(step2_stream, uri, localName, qName, attr);
            if (attr.getValue("id") != null) {
                sessionId = attr.getValue("id");
            }
            if (attr.getValue("from") != null) {
                String fromAttr = attr.getValue("from");

                JID fromJID = new JID(fromAttr);
                remoteHost = fromJID.getDomain();
            } else {
                remoteHost = ServerDialbackSession.newToHost;
                logger.fine("NO from on open stream. remoteHost set to: "+remoteHost);
            }
            currentState = S2S_STATE.STEP_3_RCV_FEATURES;
        }
    }

    private void handleRcvFeaturesStartElement(String uri, String localName, String qName,
                                               Attributes attr) {

        Element featuresElement = new DefaultElement(
                new QName(qName, new Namespace(null, uri)));

        switch( localName ){
            case "features":
                step3_features = createElement(step3_features, uri, localName, qName, attr);
                currentElement = step3_features;
                break;
            case "starttls":
                step3_features.add(featuresElement);
                featuresElementDeque.addFirst(featuresElement);
                this.tlsAvailable = true;
                break;
            case "required":
                Element starttls = step3_features.element("starttls");
                if( starttls != null ){
                    starttls.add(featuresElement);
                }
                logger.fine("TLS is required by receiving server");
                this.tlsRequired = true;
                break;
            case "mechanisms":
                step3_features.add(featuresElement);
                break;
            case "mechanism":
                logger.fine("mechanism: ");
                Element mechanisms = step3_features.element("mechanisms");
                if( mechanisms != null ){
                    step3_features.add(featuresElement);
                }
                break;
            case "dialback":
                logger.fine("start element for dialback received");
                step3_features.add(featuresElement);
                featuresElementDeque.addFirst(featuresElement);
                break;
            case "errors":
                Element dialbackElement = step3_features.element("dialback");
                dialbackElement.add(featuresElement);
                break;
            default:
                logger.info("localname received: "+localName);
                featuresElementDeque.addFirst(featuresElement);
        }

    }

    private void handleRcvFeaturesText(char[] ch, int start, int length){
        String mechanism = new String(ch, start, length);
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "handleRcvFeaturesText: currstate: "+currentState+". [[chars: " + mechanism+"]]");
        }
        Element mechanisms = step3_features.element("mechanisms");
        for( Object elemObj : mechanisms.elements()){
            Element elem = (Element) elemObj;
            if( elem.getText() != null ) {
                logger.fine("setting mechanism text, " + mechanism + ", for " + elem.getQName());
                elem.setText(mechanism);
            }
        }
    }

    private void handleRcvFeaturesEndElement(String localName) throws IOException{
        switch( localName ){
            case "features":
                logger.fine("reached end of features, proceeding to step 4: " +
                        "enable TLS or send dialback");
                if( logger.isLoggable(Level.FINER) ){
                    logger.log(Level.FINER, "final element: [["+step3_features.asXML()+"]]");
                }
                currentState = S2S_STATE.STEP_4_ENABLE_TLS_OR_SEND_DIALBACK;
                handleTlsOrDialback();
                break;
            case "starttls":
                logger.finest("reached end of starttls");
                featuresElementDeque.removeFirst();

                break;
            case "required":
                logger.finest("reached end of required");
                break;
            case "mechanisms":
                logger.finest("reached end of mechanisms");
                /*
                if( CONSTANTS.GATEWAY.SASL_NAMESPACE.equals(uri) ){
                    logger.fine("proceeding to step 9.");
                    // next step
                    currentState = S2S_STATE.STEP_9;
                    handleStep9();
                } else {
                    logger.severe("Unhandled uri: "+uri);
                    currentState = S2S_STATE.ERROR;
                }
                */
                break;
            case "dialback":
                logger.finest("received end element for dialback");
                featuresElementDeque.removeFirst();

                break;
            case "errors":
                logger.finest("received end errors element. added dialback");
                break;
            default:
                logger.info("unknown end element localname received: " + localName);
                Element prevElem = featuresElementDeque.peekFirst();
                if( prevElem != null ){
                    Element elem = featuresElementDeque.removeFirst();
                    prevElem.add(elem);
			    } else {
                    step3_features.add(prevElem);
                }
        }
    }

    private void handleTlsOrDialback() throws IOException{
        if( logger.isLoggable(Level.FINE))
            logger.fine("IN STEP 4, send message and proceed to step 5 or others");
        if (tlsEnabled
                    || (tlsAvailable && !XOP.GATEWAY.TLSAUTH)
                    || (!tlsAvailable && !XOP.GATEWAY.TLSAUTH) ){
            logger.info("Sending Dialback key. tlsEnabled: " + tlsEnabled
                    + ", tlsAvailable: " + tlsAvailable
                    + ", XOP.GATEWAY.TLSAUTH: " + XOP.GATEWAY.TLSAUTH);
            sendDialbackKey(this.initiatingConnection.xopGatewayDomainName,
                    this.remoteHost, this.sessionId);
        } else if (tlsRequired && !XOP.GATEWAY.TLSAUTH ){
            logger.severe("TLS AUTH not supported on XO Gateway!");
            this.initiatingConnection.writeRaw(CONSTANTS.AUTH.STREAM_CLOSE.getBytes());
            currentState = S2S_STATE.END_CLOSE;
        } else if( XOP.GATEWAY.TLSAUTH ){ //(tlsAvailable || tlsRequired) &&
                logger.info("TLS required and enabled in XOG. Sending TLS request.");
                this.initiatingConnection.writeRaw(
                        CONSTANTS.GATEWAY.STARTTLS_REQUEST.getBytes());

                currentState = S2S_STATE.STEP_5_RCV_TLS_PROCEED;
                logger.finer("updated state to "+S2S_STATE.STEP_5_RCV_TLS_PROCEED);
        } else {
            logger.severe("unhandled TLS state");
            currentState = S2S_STATE.ERROR;
        }
    }

    /**
     * Initiate dialback for a multiplexed domain on this connection. E.g. conference.openfire on the openfire connection
     * See XEP-0220 Section 2.6.2
     * @param from the from domain (this domain)
     * @param to the domain to validate
     * @param sessionId the sessionId to use (can be passed in as a random string
     */
    void initiateDialback(String from, String to, String sessionId) throws IOException{
        logger.fine("Generating new dialback key for: from:"+from+", to: "+to+", sessionId: "+sessionId);
        ServerDialbackSession.newFromHost = from;
        ServerDialbackSession.newToHost = to;
        sendDialbackKey(from, to, sessionId);
    }

    /**
     * Generates and sends a dialback key to the remoteHost
     * @param thisHost the string domain of this host
     * @param remoteHost The string domain of this host
     * @param sessionId the sessionId to use
     */
    private void sendDialbackKey(String thisHost, String remoteHost,
                                 String sessionId) throws IOException{
        if (sessionId != null) {
            logger.info("Sending Dialback request for sessionId "+sessionId+" to "+remoteHost+", from "+thisHost);
            String dialbackKey = GatewayUtil.generateDialbackKey(thisHost, remoteHost, sessionId);
            ServerDialbackSession.initiatingServerKeys.put(thisHost+"=="+remoteHost, dialbackKey);
            String dialbackRequest = GatewayUtil.generateDialbackRequest(thisHost, remoteHost, dialbackKey);
            logger.fine("Dialback string being sent: [["+dialbackRequest+"]]");
            ServerDialbackSession.nextIncomingStreamIsForAuthoritativeServer = true; // We expect the server to contact us as the Authoritative Server
            this.initiatingConnection.writeRaw(dialbackRequest.getBytes());
            currentState = S2S_STATE.RCV_DIALBACK_VERIFY;
        } else {
            logger.severe("Unable to send dialback request, session id was null");
        }
    }

    /**
     * Generates and sends a dialback key to the remoteHost
     * @param thisHost the string domain of this host
     * @param remoteHost The string domain of this host
     * @param sessionId the receivingConnectionSessionId to use
     * @param hashKey the hashkey to send
     */
    private void sendVerifyKey(String remoteHost, String thisHost,
                               String sessionId, String hashKey) throws IOException {
        if (sessionId != null) {
            logger.fine("Sending Dialback verify key for receivingConnectionSessionId "+sessionId+" to "+remoteHost+", from "+thisHost);
            String dialbackRequest = GatewayUtil.generateVerifyRequest(thisHost, remoteHost, hashKey, sessionId);
            logger.finer("Dialback string being sent: [["+dialbackRequest+"]]");
            this.initiatingConnection.writeRaw(dialbackRequest.getBytes());
            currentState = S2S_STATE.RCV_VERIFY_VALID;
        } else {
            logger.severe("Unable to send dialback request, session id was null");
        }
    }

    private void handleRcvTlsProceedStartElement(String localName) {
        //<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>
        if( "proceed".equals(localName)){
            logger.fine("Proceed received, moving to step 6");
            currentState = S2S_STATE.STEP_5_RCV_TLS_PROCEED;
        } else if( "failure".equals(localName)) {
            logger.severe("");
            currentState = S2S_STATE.STEP_5_RCV_TLS_PROCEED;
        } else {
            logger.severe("unknown localname received: "+localName);
            currentState = S2S_STATE.ERROR;
        }
    }

    private void handleRcvTlsProceedEndElement(String localName) throws SAXTerminatorException{
        switch( localName ){
            case "proceed":
                currentState = S2S_STATE.STEP_3_RCV_FEATURES;
                // start tls negotiation as per step 6
                handleEnableSSLStep6();
                break;
            case "stream": // failure and now handling closing stream tag
                //handle end of stream
                if (localName.equals("stream")) {
                    logger.info("Received close stream tag.");
                    throw new SAXTerminatorException("gateway closing");
                }
                break;
            default:
                logger.severe("unknown localname received: "+localName);
        }
    }

    private void handleEnableSSLStep6(){
        logger.fine("Received TLS proceed");
        if (initiatingConnection != null) {
            tlsEnabled=true;
            logger.fine("SSL enabled, proceed to Step 7");
            initiatingConnection.enableSSL();
            //TODO 2016-06-03 dnn: handle SSL handshake failure
            currentState = S2S_STATE.STEP_7_DIALBACK_INITIATION;
            handleStep7();
        } else {
            logger.severe( "Initiating Gateway Connection is null");
            currentState = S2S_STATE.ERROR;
        }

    }

    private void handleStep7(){
        /*
        <stream:stream
          from='im.example.com'
          to='example.net'
          version='1.0'
          xmlns='jabber:server'
          xmlns:stream='http://etherx.jabber.org/streams'>
         */
    }

    /**
     * In state: RCV_DIALBACK_VERIFY
     * @param uri the uri
     * @param localName localname: "result"
     * @param qName qualified name
     * @param attr any attributes
     */
    private void handleRcvDialbackResultStartElement(String uri, String localName,
                                                     String qName, Attributes attr) {
        // DIALBACK protocol state
        if (!localName.equals("result")) {
            logger.severe("incorrect local name element received: "+localName);
            currentState = S2S_STATE.ERROR;
            return;
        }
        dialbackResultElement = createElement(dialbackResultElement, uri,localName,qName,attr);
        remoteHost = attr.getValue("from");
        
        currentElement = dialbackResultElement;
    }

    private void handleRcvDialbackResultEndElement(String localName) throws IOException {
        if( "stream".equals(localName)){
             logger.warning("Received close stream");
             currentState = S2S_STATE.ERROR;
             return;
        }        
        String typeAttrValue = dialbackResultElement.attributeValue("type");
        switch(typeAttrValue){
            case "valid":
                // logger.info("Receiver validated Dialback key, Initiating sending db:result");
                if( !initiatingConnection.isDomainVerified(remoteHost)){
                    logger.fine(remoteHost+" not an authenticated Initiating server, adding to initiating verified domains.");
                    initiatingConnection.addVerifiedDomain(remoteHost);
                } else {
                    logger.fine(remoteHost+" not adding to verified domains");
                }

                logger.info("Receiver validated Dialback key.");
                if( !pingStarted ) {
                    logger.info("Starting ping thread");
                    GatewayPing gatewayPing = initiatingConnection.getGatewayPing();
                    gatewayPing.start();
                    pingStarted = true;
                }

                // TODO 2018-03-02 move into ServerDialbackSession
                if( initiatingConnection.hasQueuedPackets() ){
                    logger.info("There are queued packets, sending!");
                    initiatingConnection.sendQueuedPackets();
                }
                currentState = S2S_STATE.VERIFIED_AWAITING_DIALBACK;
                break;
            case "invalid":
                logger.info("Receiver received invalid key and closing connection. Sending stream close");
                currentState = S2S_STATE.ERROR;
                initiatingConnection.writeRaw(
                        CONSTANTS.AUTH.STREAM_CLOSE.getBytes());
                break;
            case "error":
                // TODO: 2016-06-03 handle errors on dialback gracefully
                break;
            default:
                logger.severe("unhandled type attribute value: "+typeAttrValue);
                currentState = S2S_STATE.ERROR;
        }
    }

    /**
     * in RCV_VERIFY_VALID
     * if receive valid response, then handle
     * @param uri
     * @param localName
     * @param qName
     * @param attr
     */
    private void handleRcvVerifyResultStartElement(String uri, String localName, String qName,
                                                   Attributes attr){
        if(!"verify".equals(localName)){
            logger.severe("incorrect local name element received: "+localName);
            currentState = S2S_STATE.ERROR;
            return;
        }

        verifyValidResultElement = createElement(verifyValidResultElement, uri, localName, qName, attr);
        verifyValidFrom = attr.getValue("from");
        verifyValidTo = attr.getValue("to");
        currentElement = verifyValidResultElement;
    }

    private void handleRcvVerifyResultEndElement(String uri, String localName, String qName){
        if( "stream".equals(localName)){
            logger.warning("Received close stream");
            currentState = S2S_STATE.ERROR;
            return;
        }

        String typeAttrValue = verifyValidResultElement.attributeValue("type");
        switch(typeAttrValue){
            case "valid":
                 logger.info("Initiating received valid key");
                if(!ServerDialbackSession.authenticatedReceivingServerNames.contains(verifyValidFrom)){
                    logger.fine(remoteHost+" not an authenticated Initiating, adding to initiating verified domains.");
                    ServerDialbackSession.authenticatedReceivingServerNames.add(verifyValidFrom);
                }
                currentState = S2S_STATE.VERIFIED;
                break;
            case "invalid":
                logger.info("State: "+currentState+" Receiver received invalid key");
                currentState = S2S_STATE.ERROR;
                break;
            case "error":
                // TODO: 2016-06-03 handle errors on dialback gracefully
                break;
            default:
                logger.severe("unhandled type attribute value: "+typeAttrValue);
                currentState = S2S_STATE.ERROR;
        }
    }


    /**
     * in state: VERIFIED_AWAITING_DIALBACK
     * @param uri the uri
     * @param localName = "verify"
     * @param qName qualified name
     * @param attr attributes contain 'to' and 'from'
     */
    private void handleVerifyResultStartElement(String uri, String localName,
                                                     String qName, Attributes attr) {
        if (!localName.equals("verify")) {
            logger.severe("incorrect local name element received: "+localName);
            currentState = S2S_STATE.ERROR;
            return;
        }
        verifyResultElement = createElement(verifyResultElement, uri, localName, qName, attr);
        verifyFrom = attr.getValue("from");
        verifyTo = attr.getValue("to");

        currentElement = verifyResultElement;
    }

    /**
     * VERIFIED_AWAITING_DIALBACK
     */
    private void handleVerifyResultEndElement( ) throws IOException {
        Attribute typeAttr = verifyResultElement.attribute("type");
        if( typeAttr == null ){
            logger.severe("No type attribute for verifyResultElement: "
                    + verifyResultElement.asXML());
            currentState = S2S_STATE.ERROR;
            return;
        }
        String key = verifyTo+"=="+verifyFrom;
        ReceivingGatewayConnection receivingGatewayConnection =
                ServerDialbackSession.receivingGatewayConnections.get(key);

        // This is XEP-0220 Ex 2,3/11,12 (Step 4)
        if("valid".equals(typeAttr.getValue())) {

            logger.info(this.initiatingConnection.xopGatewayDomainName
                    + " as Authoritative Server received VALID verification of " +
                    "receiver dialback key.");
            //String rcvResultValidString = "<db:result from='" + this.initiatingConnection.xopGatewayDomainName + "' to='"+ remoteHost + "' type='valid' />";
            String rcvResultValidString = "<db:result from='" + this.verifyTo + "' to='"+ this.verifyFrom + "' type='valid' />";
            logger.fine("Receiving connection is sending result message: "+rcvResultValidString);
            receivingGatewayConnection.writeRaw(rcvResultValidString.getBytes());

            logger.info("Receiving and Initiating connection is authenticated");
            // this was surrounding the verify before and I am not sure why
            if( ServerDialbackSession.authenticatedReceivingServerNames.contains(verifyFrom) ) {
                logger.fine("Adding "+verifyFrom+" to as an authenticated Receiver.");
                ServerDialbackSession.authenticatedReceivingServerNames.add(verifyFrom);
            }
            currentState = S2S_STATE.VERIFIED;
            initiatingConnection.setDialbackComplete(true, verifyFrom);

            currentElement = null;
        } else if( "invalid".equals(typeAttr.getValue())){
            logger.info(this.initiatingConnection.xopGatewayDomainName+ " as Authoritative Server received INVALID verification of receiver dialback key.");
            String rcvResultValidString = "<db:result from='" + this.initiatingConnection.xopGatewayDomainName + "' to='"+ remoteHost + "' type='invalid' />";
            logger.fine("Receiving connection is sending result message: "+rcvResultValidString);
            receivingGatewayConnection.writeRaw(rcvResultValidString.getBytes());

        } else {
            logger.severe("This shouldn't happen!");
        }
    }


    /**
     * VERIFIED state
     * @param uri the uri
     * @param localName the localname
     * @param qName the qName
     * @param attributes attributes
     */
    private void handleAuthenticatedStartElement(String uri, String localName, String qName, Attributes attributes){
        logger.fine("creating new element in VERIFIED STATE.");
        currentElement = createElement(currentElement, uri, localName, qName, attributes);

        if("verify".equals(localName)){
            currentState = S2S_STATE.VERIFIED_AWAITING_DIALBACK;
            logger.fine("Handling new verification request. changing state to: "+currentState);
            handleVerifyResultStartElement(uri,localName,qName,attributes);
        }
    }

    private void handleAuthenticatedEndElement(String uri, String localName, String qName)
            throws SAXTerminatorException {

        if (localName.equals("stream")) {
            logger.info("Received close stream tag.");
            throw new SAXTerminatorException("gateway closing");
        }

        logger.fine("handling end element for INITIATING GATEWAY for VERIFIED state.");
        if (currentElement == null) {
            logger.fine("currentElement is null");
            return;
        }

        if (currentElement.getParent() != null) {
            logger.fine("we have a parent: " + currentElement.getParent().asXML());
            currentElement = currentElement.getParent();
        } else {
            logger.fine("process the currentElement: "+currentElement.asXML()+"localName: "+localName);
            logger.warning("Error parsing packet: " + localName +
                    " : unknown packet type. qName: " + qName + " and uri: " + uri);
            logger.warning("Element: " + currentElement.toString());
            /* outgoing connection should not be processing incoming messages */
            currentElement = null; // this element has been processed, free it up.
        } //
    } // end handleAuthenticatedEndElement
} // end InitiatingGatewayXMLHandler
