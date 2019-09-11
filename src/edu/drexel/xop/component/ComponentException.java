/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component;

import edu.drexel.xop.core.XOPException;

/**
 * Component Related Exception
 * 
 * @author David Millar
 */
public class ComponentException extends XOPException {

    private static final long serialVersionUID = 1L;

    public ComponentException(String msg) {
        super(msg);
    }
}