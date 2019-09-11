package edu.drexel.xop.util.logger;

/**
 * (c) 2013 Drexel University
 */

import java.util.HashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;


/**
 * Passes logging information through to the real logger but also stores it
 * for access by XOP and can use callbacks to notify LogListeners
 *
 * @author Rob Taglang
 */
public class LogRead extends Handler {
    private static HashMap<LogListener, Level> listeners = new HashMap<LogListener, Level>();

    @Override
    public void close() {
        for(LogListener listener : listeners.keySet()) {
            listener.close();
        }
    }

    @Override
    public void flush() {
        for(LogListener listener : listeners.keySet()) {
            listener.flush();
        }
    }
    
    @Override
    public void publish(LogRecord record) {
    	for(LogListener listener : listeners.keySet()) {
            if(listeners.get(listener).intValue() <= record.getLevel().intValue()) {
            	// for writing to text view
            	listener.processLogMessage(record.getLoggerName(), record.getLevel(), record.getMessage());
            }
        }
    }
     
    public static void addListener(LogListener listener, Level level) {
        listeners.put(listener, level);
    }
}
