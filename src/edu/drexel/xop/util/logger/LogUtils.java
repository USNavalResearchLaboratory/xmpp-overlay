/*
 * (c) 2011 Drexel University
 */
package edu.drexel.xop.util.logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.SocketHandler;
import java.util.HashSet;
import edu.drexel.xop.properties.XopProperties;

/**
 * @author Duc N. Nguyen (dn53@drexel.edu) utilities for listing logger levels,
 *         etc
 */
public class LogUtils {
    public static String fileLocation = "config/logging.properties";
    private static HashSet<String> loggers = new HashSet<String>();
    private static Handler handler = null;

    /**
     * @param loggerName
     * @return logger
     */
    public static Logger getLogger(String loggerName) {
        return Logger.getLogger(loggerName);
    }

    /**
     * Loads config/logging.properties
     */
    public static void loadLoggingProperties() {
        if (System.getProperties().containsKey("java.util.logging.config.file")) {
            System.out.println(" property java.util.logging.config.file exists");
            fileLocation = System.getProperties().getProperty("java.util.logging.config.file");
        }
        File loggingProperties = new File(fileLocation).getAbsoluteFile();

        if (loggingProperties.exists()) {
            FileInputStream fis;
            try {
                fis = new FileInputStream(loggingProperties);
                LogManager logManager = LogManager.getLogManager();
                logManager.readConfiguration(fis);
                //listLoggerLevels();
                fis.close();
                System.out.println("Successfully loaded logging.properties from: " + loggingProperties.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("No logging.properties found at:" + loggingProperties.getAbsolutePath());
        }
    }

    /**
     * Takes in an array of Objects, returns a single string of each objects' toString separated by a delimiter
     *
     * @param sa An array of Objects to call toString on and log
     * @return A concatenated string w/ delimiters to be logged
     */
    public static String arraytoString(Object[] sa) {
        StringBuilder sb = new StringBuilder();
        String delimiter = "\n\t> ";
        for (Object string : sa) {
            sb.append(delimiter);
            sb.append(string.toString());
        }
        return sb.toString();
    }
}
