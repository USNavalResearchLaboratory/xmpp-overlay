/**
 * Copyright 2010: Duc N. Nguyen
 */
package edu.drexel.xop.util.logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * @author Duc N. Nguyen (dn53@drexel.edu)
 *         Description: a basic formatter for logger
 */

public class XopLogFormatter extends Formatter {
    private DateFormat dateFormat;
    private String logFilterString;

    public XopLogFormatter(DateFormat dateFormat, String logFilterString) {
        this.dateFormat = dateFormat;
        this.logFilterString = logFilterString;
    }

    public XopLogFormatter(String logFilterString) {
        this(new SimpleDateFormat("yyyyMMdd-HHmmss.SSS", Locale.ROOT), logFilterString);
    }

    public XopLogFormatter() {
        this.dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS", Locale.ROOT);
        this.logFilterString = null;
    }

    @Override
    public String format(LogRecord record) {

        String className = "";
        String[] splitName = record.getSourceClassName().split("\\.");
        if (splitName.length > 0)
            className = splitName[splitName.length - 1];

        StringBuilder sbuf = new StringBuilder();
        sbuf.append("[").append(record.getLevel());
        sbuf.append(" (")
                .append(dateFormat.format(new Date(record.getMillis())))
                .append(") "); 
        sbuf.append(className).append(".")
                .append(record.getSourceMethodName()).append("()");
        sbuf.append(" t: ").append(record.getThreadID());
        if (logFilterString != null) {
            sbuf.append(". ").append(logFilterString);
        }
        sbuf.append("] ");
        if (record.getLevel() == Level.SEVERE || record.getLevel() == Level.WARNING) {

            sbuf.append("exception message: ").append((record.getThrown() != null) ? (record.getThrown().getMessage()) : "").append("\n");
            if (record.getThrown() != null)
                record.getThrown().printStackTrace();
        }
        sbuf.append(record.getMessage()).append("\n");
        return sbuf.toString();
    }

    public static void main(String[] args){
        XopLogFormatter df = new XopLogFormatter();
        LogRecord lr = new LogRecord(Level.INFO, "Info Test");
        lr.setSourceClassName("edu.drexel.xop.util.logger.XopLogFormatter");
        System.out.println(df.format(lr));
    }

}