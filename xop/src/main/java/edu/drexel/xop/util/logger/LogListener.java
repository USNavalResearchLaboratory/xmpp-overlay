package edu.drexel.xop.util.logger;

/**
 * (c) 2013 Drexel University
 */

import java.util.logging.Level;

/**
 * Implement these methods to be alerted when a log message arrives
 *
 * @author Rob Taglang
 */
public interface LogListener {
    public void processLogMessage(String from, Level level, String message);
    public void close();
    public void flush();
}
