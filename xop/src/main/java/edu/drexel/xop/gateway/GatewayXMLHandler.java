package edu.drexel.xop.gateway;

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.tree.DefaultElement;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.util.logger.LogUtils;

/**
 * parent xmlhandler class for gateway incoming and outgoing connections.
 * Created by duc on 6/6/16.
 */
abstract class GatewayXMLHandler  extends DefaultHandler {
    private static final Logger logger = LogUtils.getLogger(GatewayXMLHandler.class.getName());

    String remoteHost;

    /**
     * creates aan element to be added to or processed by the gatewayxmlhandler
     * @param element the existing element (or null)
     * @param uri uri name
     * @param localName not used
     * @param qName qname
     * @param attr attr
     * @return the passed in element (or new eleemnt if passed a null element) with the new sub element or attributes as needed
     */
    Element createElement(Element element, String uri, String localName, String qName, Attributes attr) {
        if (element == null) {
            if( logger.isLoggable(Level.FINER))
                logger.finer("Creating new Element.");
            //create a new currentElement if there isn't a currently opened one
            element = new DefaultElement(new QName(qName, new Namespace(null, uri)));
        } else {
            if( logger.isLoggable(Level.FINER))
                logger.finer("Adding an currentElement to existing currentElement with qName, "+element.getQName());
            //if there is an existing currentElement, add this one to it
            Element newElement = new DefaultElement(new QName(qName, new Namespace(null, uri)));
            element.add(newElement);
            element = newElement;
        }

        if( attr.getLength() > 0){
            if( logger.isLoggable(Level.FINER))
                logger.finer("adding "+attr.getLength()+" attributes");
            for (int i = 0; i < attr.getLength(); i++) {
                element.addAttribute(attr.getLocalName(i), attr.getValue(i));
            }
        }

        if( logger.isLoggable(Level.FINEST))
            logger.finest("new element: "+element.asXML());
        return element;
    }
}
