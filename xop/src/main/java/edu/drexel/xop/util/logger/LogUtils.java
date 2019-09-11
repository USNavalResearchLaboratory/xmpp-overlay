package edu.drexel.xop.util.logger;

/*
 * (c) 2013 Drexel University
 */

import edu.drexel.xop.util.XOP;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.*;

/**
 * utilities for Logger
 */
public class LogUtils {
    private static Logger logger = Logger.getLogger(LogUtils.class.getName());
    private static String fileLocation = "config/logging.properties";
    private static Handler androidViewHandler;
    private static FileHandler textFileHandler;

    public static Logger getLogger(String loggerName) {
        return getLogger(loggerName, new XopLogFormatter(), null);
    }

    public static Logger getLogger(String loggerName, Formatter formatter) {
        return getLogger(loggerName, formatter, null);
    }

    /**
     * @param loggerName the name of the class
     * @return logger object
     */
    public static Logger getLogger(String loggerName, String rootDirectory) {
        return getLogger(loggerName, new XopLogFormatter(), rootDirectory);
    }

    public static Logger getLogger(String loggerName, Formatter formatter, String rootDirectory) {
        Logger ret = Logger.getLogger(loggerName);

        ret.setUseParentHandlers(false);
        for (Handler handler : ret.getHandlers()) {
            ret.removeHandler(handler);
        }

        if (XOP.ENABLE.ANDROIDLOGGING) {
            if (androidViewHandler == null) {
                androidViewHandler = new LogRead();
            }
            ret.addHandler(androidViewHandler);
        }

        if (XOP.ENABLE.DEVICELOGGING) {
            // TODO 2018-10-25 refactor this. It's not used but would be useful
            if (textFileHandler == null) {
                try {
                    File logPath = new File(rootDirectory + "/XOLogs/");
                    boolean res = logPath.mkdirs();
                    if (!res) {
                        System.err.println("Did not create " + logPath.getAbsolutePath());
                    }
                    textFileHandler = new FileHandler(rootDirectory + "/XOLogs/XOLog", 4 * 1048576, 4); // four 4MB rotating files, last two parameters change this
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            textFileHandler.setFormatter(new TextLogFormatter());
            textFileHandler.setLevel(Level.FINEST);
            ret.addHandler(textFileHandler);
        }

        // System.out.println(loggerName + ": numHandlers: " + ret.getHandlers().length);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(formatter);
        ret.addHandler(handler);
        // for(Handler handler : ret.getHandlers() ){
        //     handler.setFormatter(formatter);
        // }


        return ret;
    }

    /**
     * Loads config/test-logging.properties if it exists.
     * <p>
     * dnguyen 2013-10-07: Other runtime logging configurations are checked here as well. This is a bit of a hack and should be changed to have a more unified runtime configuration checking thing.
     *
     * @param loggerPropertiesFile the file to load.
     */
    public static void loadLoggingProperties(InputStream loggerPropertiesFile) {
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
                fis.close();
                logger.fine("Successfully loaded test-logging.properties from: " + loggingProperties.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            logger.warning("No test-logging.properties found at:" + loggingProperties.getAbsolutePath());
            if (loggerPropertiesFile != null) {
                logger.info("Loading logging from android raw logging.properties");
                try {
                    LogManager logManager = LogManager.getLogManager();
                    logManager.readConfiguration(loggerPropertiesFile);
                    loggerPropertiesFile.close();
                    logger.info("loaded logging.properties from android raw");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
