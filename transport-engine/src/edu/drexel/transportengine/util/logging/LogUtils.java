package edu.drexel.transportengine.util.logging;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

/**
 * Helper class for logging.
 *
 * @author Duc N. Nguyen <dn53@drexel.edu>
 */
public class LogUtils {

    /**
     * Gets a logger with a specified name.
     *
     * @param loggerName logger name.
     * @return the logger.
     */
    public static Logger getLogger(String loggerName) {
        Logger logger = Logger.getLogger(loggerName);
        LogManager logManager = LogManager.getLogManager();
        String levelStr = logManager.getProperty(loggerName.concat(".level"));
        Level level = Level.FINE;
        if (levelStr != null) {
            level = Level.parse(levelStr);
        }

        logger.setUseParentHandlers(false);
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }

        try {
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new DefaultFormatter(new SimpleDateFormat("yyyyMMdd-HHmmss")));
            logger.addHandler(consoleHandler);
            consoleHandler.setLevel(level);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return logger;
    }

    /**
     * Formatter class for logging.
     */
    private static class DefaultFormatter extends Formatter {

        private DateFormat dateFormat;

        /**
         * Instantiate a new formatter.
         *
         * @param dateFormat the date format to use.
         */
        public DefaultFormatter(DateFormat dateFormat) {
            this.dateFormat = dateFormat;
        }

        /**
         * Formats a log message.
         *
         * @param record the message to format.
         * @return the formatted message.
         */
        @Override
        public String format(LogRecord record) {

            String className = "";
            String[] splitName = record.getSourceClassName().split("\\.");
            if (splitName.length > 0) {
                className = splitName[splitName.length - 1];
            }

            StringBuffer sbuf = new StringBuffer();
            sbuf.append("[").append(record.getLevel());
            sbuf.append(" (").append(dateFormat.format(new Date(record.getMillis()))).append(") ");
            sbuf.append(className).append(".").append(record.getSourceMethodName()).append("()] ");
            if (record.getLevel() == Level.SEVERE) {

                sbuf.append("exception message: ").append((record.getThrown() != null) ? (record.getThrown().getMessage()) : "").append("\n");
                if (record.getThrown() != null) {
                    record.getThrown().printStackTrace();
                }
            }
            sbuf.append(record.getMessage()).append("\n");
            return sbuf.toString();
        }
    }
}

