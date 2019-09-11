/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.core;

/**
 * XMPP Proxy Related Exception
 * 
 * @author David Millar
 */
public class XOPException extends Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public XOPException() {
        super();
    }

    public XOPException(String msg) {
        super(msg);
    }

    public XOPException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
