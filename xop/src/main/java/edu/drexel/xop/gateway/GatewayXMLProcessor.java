package edu.drexel.xop.gateway;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import edu.drexel.xop.client.SAXTerminatorException;
import edu.drexel.xop.util.logger.LogUtils;

abstract class GatewayXMLProcessor {
    private static final Logger logger = LogUtils.getLogger(GatewayXMLProcessor.class.getName());

    private InputSource source;
    private SAXParserFactory factory;
    protected GatewayConnection xopConnection;

    GatewayXMLProcessor(GatewayConnection XOPConnection) {
        this.xopConnection = XOPConnection;
        factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
    }
    /**
     * Sets the input source of the processor
     * @param source the input source
     */
    public void setInputSource(InputSource source) {
        this.source = source;
        addSourceHandler(getHandler());
    }

    /**
     * Get the input source of the processor
     * @return The input source
     */
    private InputSource getInputSource() {
        return source;
    }

    /**
     * Adds a new handler to the current input source
     * Note, throw a SAXTerminatorException in the body of a handler in order to stop parsing
     * @param handler - the handler to add
     */
    private void addSourceHandler(DefaultHandler handler) {
        if(getInputSource() != null) {
            try {
                SAXParser parser = factory.newSAXParser();
                final XMLReader xmlReader = parser.getXMLReader();
                xmlReader.setContentHandler(handler);
                xmlReader.setErrorHandler(handler);
                try {
                    xmlReader.parse(getInputSource());
                } catch (SocketException se) {
                    logger.log(Level.INFO, "SocketException: " + se.getMessage() + "; xopConnection: " + xopConnection.toString());
                    if (!"Socket closed".equals(se.getMessage())) {
                        se.printStackTrace();
                    } else {
                        logger.info("Socket is closed, do nothing.");
                    }
                } catch (SocketTimeoutException ste){
                    logger.log(Level.WARNING, "Socket timeout, shutting down XOP connection and " +
                            "attempting to reconnect");

                    if(this instanceof InitiatingGatewayXMLProcessor){
                        logger.info("Shutting down Initiating xopConnection");
                        ((InitiatingGatewayXMLProcessor) this).xopConnection.stop();
                        ((InitiatingGatewayXMLProcessor) this).xopConnection.processCloseStream();
                    } else if(this instanceof ReceivingGatewayXMLProcessor){
                        logger.info("Shutting down Initiating Gateway Connection");
                        ServerDialbackSession.initiatingGatewayConnection.stop();
                        ServerDialbackSession.initiatingGatewayConnection.processCloseStream();


                        logger.info("Shutting down Receiving xopConnection");
                        xopConnection.stop();
                        xopConnection.processCloseStream();
                    }
                } catch (IOException e) {
                    if(e.getMessage() != null && e.getMessage().toLowerCase().contains("invalid byte")) {
                        logger.warning(e.getMessage());
                        logger.warning("This exception usually occurs because the input stream wasn't ready");
                    } else {
                        logger.severe("Error while retrieving input source: " + e.getMessage());
                        e.printStackTrace();
                        xopConnection.processCloseStream();
                    }
                } catch (SAXTerminatorException e) {
                    if( logger.isLoggable(Level.FINE) ) logger.fine(e.getMessage());
                    xopConnection.processCloseStream();
                } catch (SAXException e) {
                    if(e.getMessage().toLowerCase().contains("an invalid xml character")){
                        if( logger.isLoggable(Level.FINEST) ) logger.finest("SAX exception: " + e.getMessage() + " expected and ignored");
                    }
                    else {
                        logger.info("closing xopConnection in XMLProcessor.java");
                        logger.severe("SAX exception while parsing stream: " + e.getMessage());
                        xopConnection.processCloseStream();
                    }
                }
            } catch (ParserConfigurationException e) {
                logger.severe("Error while configuring parser: " + e.getMessage());
                if (logger.isLoggable(Level.INFO)) {
                    e.printStackTrace();
                }
            } catch (SAXException e) {
                logger.severe("SAX Exception while setting up stream: " + e.getMessage());
                if (logger.isLoggable(Level.INFO)) {
                    e.printStackTrace();
                }
            }
        } else {
            logger.warning("Unable to add source handler, input source was null");
        }
    }

    /**
     * Override this in subclasses for your specific XML handler
     * @return a handler for processing xmpp packets
     */
    public abstract DefaultHandler getHandler();
}
