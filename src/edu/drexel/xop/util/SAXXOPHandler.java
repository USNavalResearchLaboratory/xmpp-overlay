package edu.drexel.xop.util;

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.tree.DefaultElement;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAXXOPHandler.java
 * Created: May 4, 2011
 * 
 * @author Duc N. Nguyen (dn53@drexel.edu)
 * Description:
 */
class SAXXOPHandler extends DefaultHandler {

    private Element elem;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        Namespace space = new Namespace("", attributes.getValue("xmlns"));

        Element foo = new DefaultElement(qName, space);

        for (int i = 0; i < attributes.getLength(); i++) {
            if (!attributes.getQName(i).equals("xmlns")) {
                foo.addAttribute(attributes.getQName(i), attributes.getValue(i));
            }
        }
        if (elem == null) {
            elem = foo;
        } else {
            elem.add(foo);
            elem = foo;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {

        char[] ch1 = new char[length];
        System.arraycopy(ch, start, ch1, 0, length);
        String s = String.valueOf(ch1);
        if (s.trim().length() != 0 && !s.trim().equals("")) {
            elem.addText(s);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        elem = (elem.getParent() == null) ? elem : elem.getParent();
    }

    public Element getElement() {
        return elem;
    }
}