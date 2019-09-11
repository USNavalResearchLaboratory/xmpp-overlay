package edu.drexel.xop.client;

import org.xml.sax.SAXException;

/**
 * Created by duc on 5/16/16.
 */
public class SAXTerminatorException extends SAXException {
    private String message;

    public String getMessage() {
        return message;
    }

    public SAXTerminatorException(String message) {
        this.message = message;
    }
}
