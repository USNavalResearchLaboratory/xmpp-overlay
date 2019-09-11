/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

/**
 *
 * @author charlesr
 */
public enum State {
    START_STREAM,
    REQUEST_AUTH,
    WAIT_FOR_AUTH,
    DONE,
    WAIT_FOR_STREAM,
    WAIT_FOR_SUCCESS,
    SEND_STREAM,
    CLOSE
}
