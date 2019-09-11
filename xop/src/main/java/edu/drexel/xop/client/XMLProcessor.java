package edu.drexel.xop.client;

import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.util.logger.LogUtils;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A generic XMLProcessor
 * By itself, it does nothing, make a subclass and override getHandler() with your own DefaultHandler implementation
 *
 */
public abstract class XMLProcessor {
    private static final Logger logger = LogUtils.getLogger(XMLProcessor.class.getName());

    private InputSource source;
    private SAXParserFactory factory;
    protected XOPConnection xopConnection;
    protected ClientManager clientManager;

    public XMLProcessor(XOPConnection connection, ClientManager clientManager) {
        this.xopConnection = connection;
        this.clientManager = clientManager;
        factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        // factory.setXIncludeAware(false);
        // try {
        //     factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        // } catch (ParserConfigurationException | SAXNotRecognizedException
        //         | SAXNotSupportedException e) {
        //     e.printStackTrace();
        // }
    }

    /**
     * Sets the input source of the processor
     * @param source the input source
     */
    public void setInputSource(InputSource source) {
        this.source = source;
    }

    private class ErrorHandler extends DefaultHandler {
        private Logger logger = LogUtils.getLogger(ErrorHandler.class.getName());

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attr) {
            logger.fine("startElement " + uri + " " + localName + " ");
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            logger.fine("characters " + length + " " + new String(ch, start, length) + " ");

        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            logger.fine("endElement " + uri + " " + localName + " ");
        }
    }

    /**
     * Adds a new handler to the current input source
     * Note, throw a SAXTerminatorException in the body of a handler in order to stop parsing
     * @param handler - the handler to add
     */
    public void beginProcessing(DefaultHandler handler) {
        if (source != null) {
            try {
                SAXParser parser = factory.newSAXParser();
                final XMLReader xmlReader = parser.getXMLReader();
                xmlReader.setContentHandler(handler);
                xmlReader.setErrorHandler(new ErrorHandler());
                try {
                    xmlReader.parse(source);
                } catch (SocketException se){
                    logger.log(Level.INFO, "SocketException: " + se.getMessage() + "; xopConnection: " + xopConnection.toString());
                    if (!"Socket closed".equals(se.getMessage())) {
                        se.printStackTrace();
                    } else {
                        logger.info("Socket is closed, do nothing.");
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
                    logger.log(Level.WARNING, "SAXTerminatorException", e);
                    xopConnection.processCloseStream();
                } catch (SAXException e) {
                    if(e.getMessage().toLowerCase().contains("an invalid xml character")){
                        if( logger.isLoggable(Level.FINEST) ) logger.finest("SAX exception: " + e.getMessage() + " expected and ignored");
                    } else {
                    	logger.info("closing xopConnection in XMLProcessor.java");
                        logger.log(Level.SEVERE, "SAX exception while parsing stream: " + e.getMessage(), e);
                        xopConnection.processCloseStream();
                    }
                }
            } catch (ParserConfigurationException e) {
                logger.severe("Error while configuring parser: " + e.getMessage());
            } catch (SAXException e) {
                logger.severe("SAX Exception while setting up stream: " + e.getMessage());
            }
        } else {
            logger.warning("Input source is uninitialized.");
        }
    }

    /**
     * Override this in subclasses for your specific XML handler
     * @return a handler for processing xmpp packets
     */
    public abstract DefaultHandler getHandler();

}