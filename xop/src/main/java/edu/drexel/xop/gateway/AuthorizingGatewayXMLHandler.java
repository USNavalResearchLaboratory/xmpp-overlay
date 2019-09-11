package edu.drexel.xop.gateway;

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.tree.DefaultElement;
import org.xml.sax.Attributes;

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
class AuthorizingGatewayXMLHandler extends GatewayXMLHandler {
    private static final Logger logger = LogUtils.getLogger(AuthorizingGatewayXMLHandler.class.getName());

    //private boolean tlsEnabled = false;

    private boolean dialbackErrors = false; // true if support XEP-0220 Section 2.4.2-2.5

    private AuthorizingGatewayConnection authorizingConnection;
    private ReceivingGatewayConnection receivingGatewayConnection;
    private String localHostName;
    private String hashKey;
    private String receivingConnectionSessionId;
    private String incomingSessionId;
    private String outgoingSessionid;
    private String verifyFrom;
    private String verifyTo;

    enum S2S_STATE {
        STEP_2_RCV_STREAM_OPEN,
        STEP_3_RCV_FEATURES,
        STEP_4_ENABLE_TLS_OR_SEND_DIALBACK,
        STEP_5_RCV_TLS_PROCEED,
        STEP_6_ENABLE_SSL,
        STEP_7_DIALBACK_INITIATION,
        RCV_DIALBACK_VERIFY, // begin SASL negotiation
        AUTHENTICATED_AWAITING_DIALBACK,
        AUTHENTICATED,
        END_CLOSE,
        ERROR
    }

    private Element step2_stream;
    private Element step3_features;
    private Element dialbackResultElement;
    private Element dialbackVerifyElement;
    private Element verifyResultElement;

    S2S_STATE currentState = S2S_STATE.STEP_2_RCV_STREAM_OPEN;
    private Element currentElement = step2_stream;

    private Deque<Element> featuresElementDeque = new LinkedList<>();

    private boolean tlsAvailable = false;
    private boolean tlsRequired = false;
    private static boolean tlsEnabled = false;

    /**
     * default constructor
     * @param authorizingConnection the XOP connection used (initiating)
     */
    public AuthorizingGatewayXMLHandler(AuthorizingGatewayConnection authorizingConnection,
                                        String xopGatewayDomainName,
                                        String remoteGatewayDomainName, String receivingConnectionSessionId,
                                        String outgoingSessionid,
                                        String hashKey,
                                        ReceivingGatewayConnection receivingGatewayConnection) {
        this.authorizingConnection = authorizingConnection;
        this.hashKey = hashKey;
        this.localHostName = xopGatewayDomainName;
        this.remoteHost = remoteGatewayDomainName;
        this.receivingConnectionSessionId = receivingConnectionSessionId;
        this.outgoingSessionid = outgoingSessionid;
        this.receivingGatewayConnection = receivingGatewayConnection;
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
                sb.append("(").append(attr.getQName(i)).append(',').append(attr.getValue(i)).append("), ");
            }
            sb.append("]");
            sb.append("--]]");
            logger.finest(sb.toString());
            logger.finest("Current State: "+currentState);
        }

        switch(currentState){
            case STEP_2_RCV_STREAM_OPEN:
                handleRcvStreamOpenStartElement( uri, localName, qName, attr);
                break;
            case STEP_3_RCV_FEATURES:
                handleRcvFeaturesStartElement( uri, localName, qName, attr);
                break;
            case STEP_5_RCV_TLS_PROCEED:
                handleRcvTlsProceedStartElement( uri, localName, qName, attr);
                break;
            case RCV_DIALBACK_VERIFY:
                handleRcvDialbackVerifyStartElement(uri,localName,qName,attr);
                break;
            case ERROR:
                logger.log(Level.SEVERE, "ERROR state");
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
            case AUTHENTICATED_AWAITING_DIALBACK:
            case AUTHENTICATED:
                /*if (ClientManager.getInstance().getGateway(new JID(remoteHost)) != null) {
                    if (element != null) {
                        element.setText(chars);
                    }
                }*/
                break;
            case ERROR:
                break;
        }
    }

    /**
     * overridden an end element is encountered.
     * @param uri the uri namespace
     * @param localName name of the element
     * @param qName name of the element
     * @throws SAXTerminatorException
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXTerminatorException {
        if (logger.isLoggable(Level.FINEST)) {
            String sb = "Received: [[--" + "localName: "+localName+", "+"qName: "+qName
                            +", "+"uri: "+uri+"--]";
            logger.log(Level.FINEST, sb);
        }

        try {
            switch (currentState) {
                case STEP_3_RCV_FEATURES:
                    handleRcvFeaturesEndElement(uri, localName, qName);
                    break;
                case STEP_5_RCV_TLS_PROCEED:
                    handleRcvTlsProceedEndElement(uri, localName, qName);
                    break;
                case RCV_DIALBACK_VERIFY:
                    handleRcvDialbackVerifyEndElement(uri, localName, qName);
                    break;

                case END_CLOSE:
                    logger.info("Close stream encountered.");
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, "Last element processed: [[[" + currentElement + "]]]");
                    }
                    throw new SAXTerminatorException("close stream encountered");
                default:
                    logger.severe("unknown state!" + currentState);
            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "END element whole xml element: [[[" + currentElement + "]]]");
                logger.log(Level.FINEST, "current state: " + currentState);
            }
        } catch(IOException ioe){
            logger.log(Level.SEVERE, "IOException caught writing bytes", ioe);
            throw new Error(ioe);
        }
    }

    private void handleRcvStreamOpenStartElement(String uri, String localName, String qName, Attributes attr){
        if ("stream".equals(localName)) {
            step2_stream = createElement(step2_stream, uri, localName, qName, attr);
            if (attr.getValue("id") != null) {
                incomingSessionId = attr.getValue("id");
                //receivingConnectionSessionId = attr.getValue("id");
            }
            if (attr.getValue("from") != null) {
                String fromAttr = attr.getValue("from");

                // TODO DNGuyen 2016-08-12 testing this is not needed since we already set the remoteHost in the constructor
                //JID fromJID = new JID(fromAttr);
                //remoteHost = fromJID.getDomain();
            }
            currentState = S2S_STATE.STEP_3_RCV_FEATURES;
        }
    }

    private void handleRcvFeaturesStartElement(String uri, String localName, String qName, Attributes attr) {

        Element featuresElement = new DefaultElement(new QName(qName, new Namespace(null, uri))
                );

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
                //step3_features.add(elem);
                logger.info("localname received: "+localName);
                featuresElementDeque.addFirst(featuresElement);
                //currentState = S2S_STATE.ERROR;
        }

    }

    private void handleRcvFeaturesText(char[] ch, int start, int length){
        String chars = new String(ch, start, length);
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "handleRcvFeaturesText: currstate: "+currentState+". [[chars: " + chars+"]]");
        }
        Element mechanisms = step3_features.element("mechanisms");
        for( Object elemObj : mechanisms.elements()){
            Element elem = (Element) elemObj;
            if( elem.getText() != null ) {
                String mechanism = new String(ch, start, length);
                logger.fine("setting mechanism text, " + mechanism + ", for " + elem.getQName());
                elem.setText(mechanism);
            }
        }
    }

    private void handleRcvFeaturesEndElement(String uri, String localName, String qName) {
        switch( localName ){
            case "features":
                logger.info("reached end of features, proceeding to step 4: enable TLS or send dialback");
                if( logger.isLoggable(Level.FINE) ){
                    logger.log(Level.FINE, "final element: [["+step3_features.asXML()+"]]");
                }
                currentState = S2S_STATE.STEP_4_ENABLE_TLS_OR_SEND_DIALBACK;
                handleTlsOrDialback();
                break;
            case "starttls":
                logger.fine("reached end of starttls");
                featuresElementDeque.removeFirst();

                break;
            case "required":
                logger.fine("reached end of required");
                break;
            case "mechanisms":
                logger.fine("reached end of mechanisms");
//                if( CONSTANTS.GATEWAY.SASL_NAMESPACE.equals(uri) ){
//                    logger.fine("proceeding to step 9.");
//                    // next step
//                    currentState = S2S_STATE.STEP_9;
//                    handleStep9();
//                } else {
//                    logger.severe("Unhandled uri: "+uri);
//                    currentState = S2S_STATE.ERROR;
//                }
                break;
            case "dialback":
                logger.fine("received end element for dialback");
                featuresElementDeque.removeFirst();

                break;
            case "errors":
                logger.fine("received end errors element. added dialback");
                break;
            default:
                logger.info("unknown end element localname received: "+localName);
                Element prevElem = featuresElementDeque.peekFirst();
                if( prevElem != null ){
                    Element elem = featuresElementDeque.removeFirst();
                    prevElem.add(elem);
                } else {
                        step3_features.add(prevElem);
                }
        } // end switch
    }// end handleRcvFeaturesEndElement()

    private void handleTlsOrDialback(){
        if( logger.isLoggable(Level.FINE)) logger.fine("IN STEP 4, send message and proceed to step 5 or others");
        if (tlsEnabled
                    || (tlsAvailable && !XOP.GATEWAY.TLSAUTH)
                    || (!tlsAvailable && !XOP.GATEWAY.TLSAUTH) ){
            sendVerifyKey(this.localHostName, this.remoteHost, this.receivingConnectionSessionId, this.hashKey);
        } else if (tlsRequired && !XOP.GATEWAY.TLSAUTH ){
            logger.severe("TLS AUTH not supported on XO Gateway!");
            this.authorizingConnection.writeRaw(CONSTANTS.AUTH.STREAM_CLOSE.getBytes());
            currentState = S2S_STATE.END_CLOSE;
        } else if( XOP.GATEWAY.TLSAUTH ){ //(tlsAvailable || tlsRequired) &&
                logger.info("TLS required and enabled in XOG. Sending TLS request.");
                this.authorizingConnection.writeRaw(
                        CONSTANTS.GATEWAY.STARTTLS_REQUEST.getBytes());

                currentState = S2S_STATE.STEP_5_RCV_TLS_PROCEED;
                logger.info("updated state to "+ S2S_STATE.STEP_5_RCV_TLS_PROCEED);
        } else {
            logger.warning("unhandled TLS state");
            currentState = S2S_STATE.ERROR;
        }
    }

    /**
     * Generates and sends a dialback key to the remoteHost
     * @param thisHost the string domain of this host
     * @param remoteHost The string domain of this host
     * @param sessionId the receivingConnectionSessionId to use
     */
    private void sendVerifyKey(String thisHost, String remoteHost, String sessionId, String hashKey) {
        if (sessionId != null) {
            logger.info("Sending Dialback verify key for receivingConnectionSessionId "+sessionId+" to "+remoteHost+", from "+thisHost);
            String dialbackRequest = GatewayUtil.generateVerifyRequest(thisHost, remoteHost, hashKey, sessionId);
            logger.fine("Dialback string being sent: [["+dialbackRequest+"]]");
            this.authorizingConnection.writeRaw(dialbackRequest.getBytes());
            currentState = S2S_STATE.RCV_DIALBACK_VERIFY;
        } else {
            logger.severe("Unable to send dialback request, session id was null");
        }
    }


    private void handleRcvTlsProceedStartElement(String uri, String localName, String qName, Attributes attr) {
        //<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>
        if( "proceed".equals(localName)){
            logger.info("Proceed received, moving to step 6");
        } else if( "failure".equals(localName)) {
            logger.severe("Failure state! "+uri+" "+qName+" "+attr);
            currentState = S2S_STATE.STEP_5_RCV_TLS_PROCEED;
        } else {
            logger.severe("unknown localname received: "+localName);
            currentState = S2S_STATE.ERROR;
        }
    }

    private void handleRcvTlsProceedEndElement(String uri, String localName, String qName) throws SAXTerminatorException{
        switch( localName ){
            case "proceed":
                currentState = S2S_STATE.STEP_3_RCV_FEATURES;
                // start tls negotiation as per step 6
                tlsEnabled=true;
                logger.info("SSL enabled, proceed to Step 7");
                authorizingConnection.enableSSL();
                break;
            case "stream": // failure and now handling closing stream tag
                //if (ClientManager.getInstance().getGateway(new JID(remoteHost)) != null) {
                    //handle end of stream
                    if (localName.equals("stream")) {
                        logger.info("Received close stream tag.");
                        throw new SAXTerminatorException("gateway closing");
                    }
                    //create packets from complete xml elements
                    //else if (element != null) {
                    //    logger.severe("InitiatingGatewayXMLProcessor shouldn't be receiving packets!");
                    //}
                //}
                break;
            default:
                logger.severe("unknown localname received: "+localName);
        }
    }

    /**
     * In state: RCV_DIALBACK_VERIFY
     * @param uri the uri
     * @param localName localname: "result"
     * @param qName qualified name
     * @param attr any attributes
     */
    private void handleRcvDialbackVerifyStartElement(String uri, String localName,
                                                     String qName, Attributes attr) {
        // DIALBACK protocol state
        if (!"verify".equals(localName)) {
            logger.severe("incorrect local name element received: "+localName);
            currentState = S2S_STATE.ERROR;
            return;
        }
        dialbackVerifyElement = createElement(dialbackVerifyElement, uri,localName,qName,attr);
        verifyFrom = attr.getValue("from");
        verifyTo = attr.getValue("to");
        currentElement = dialbackVerifyElement;


    }

    private void handleRcvDialbackVerifyEndElement(String uri, String localName,
                                                   String qName) throws IOException {
        if( "stream".equals(localName)){
             logger.warning("Received close stream");
             currentState = S2S_STATE.ERROR;
             return;
        }        
        String typeAttrValue = dialbackVerifyElement.attributeValue("type");
        switch(typeAttrValue){
            case "valid":
                // logger.info("Receiver validated Dialback key, Initiating sending db:result");

                if (!ServerDialbackSession.authenticatedReceivingServerNames.contains(remoteHost)) {
                    logger.fine("Adding "+remoteHost+" to as an authenticated Receiver.");
                    ServerDialbackSession.authenticatedReceivingServerNames.add(remoteHost);
                }

                // send result valid over receiving gateway connection

                logger.info(" AuthorizingGatewayXMLHandler  received VALID verification of receiver dialback key.");
                String rcvResultValidString = "<db:result from='" + this.verifyTo + "' to='"+ this.verifyFrom + "' type='valid' />";
                logger.fine("Receiving connection is sending result message: "+rcvResultValidString);
                receivingGatewayConnection.writeRaw(rcvResultValidString.getBytes());
                //InitiatingGatewayXMLProcessor.initiatingGatewayConnection.setDialbackComplete(true, ReceivingGatewayXMLProcessor.receivingGatewayConnection);

                logger.info("Receiving and Initiating connection is authenticated");
                // this was surrounding the verify before and I am not sure why
                if( ServerDialbackSession.authenticatedReceivingServerNames.contains(verifyFrom) ) {
                    //if (!ReceivingGatewayXMLProcessor.receivingGatewayConnection.isDomainVerified(remoteHost)) {
                    logger.info("Adding "+verifyFrom+" to as an authenticated Receiver.");
                    ServerDialbackSession.authenticatedReceivingServerNames.add(verifyFrom);
                    //ReceivingGatewayXMLProcessor.receivingGatewayConnection.addVerifiedDomain(remoteHost);
                    ServerDialbackSession.initiatingGatewayConnection.setDialbackComplete(true, verifyFrom);
                }
                currentState = S2S_STATE.END_CLOSE;

                break;
            case "invalid":
                logger.info("Receiver received invalid key and closing connection. Sending stream close");
                currentState = S2S_STATE.ERROR;
                authorizingConnection.writeRaw(
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

}
