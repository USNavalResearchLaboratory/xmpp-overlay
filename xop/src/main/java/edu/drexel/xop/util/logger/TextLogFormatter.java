package edu.drexel.xop.util.logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * @author Nick Gunia
 *         Description: Stripped down logging format to match up with the text view logging
 */

public class TextLogFormatter extends Formatter {
    private DateFormat dateFormat;

	public TextLogFormatter() {
        this.dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder sbuf = new StringBuilder();

        sbuf.append("[").append(record.getLevel().toString());
        
        sbuf.append(" (")
        .append(dateFormat.format(new Date(record.getMillis())))
        .append(")] ");
        
        sbuf.append(record.getLoggerName()).append(": ");
        
        sbuf.append(record.getMessage()).append("\n");
            	
        return sbuf.toString();
    }
}
