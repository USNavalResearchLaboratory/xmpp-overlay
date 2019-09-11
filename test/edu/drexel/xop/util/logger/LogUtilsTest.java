/*
 * Copyright (C) Drexel University 2012
 */
package edu.drexel.xop.util.logger;

import static org.junit.Assert.*;

import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Description:
 *
 * @date Apr 13, 2012
 * @author duc
 */
public class LogUtilsTest {

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
       //LogUtils.loadLoggingProperties();
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test method for {@link edu.drexel.xop.util.logger.LogUtils#getLogger(java.lang.String)}.
     */
    @Test
    public void testGetLogger() {
        Level level = Level.FINE;
        
        Logger logger = LogUtils.getLogger(LogUtilsTest.class.getName());
        //Logger logger = Logger.getLogger(LogUtilsTest.class.getName());
        logger.info("info msg 1");
        logger.log(Level.INFO,"info msg 2");
        logger.fine("fine msg 1");
        logger.log(Level.FINE,"fine msg 2");
        logger.finer("fine msg 1");
        logger.log(Level.FINER,"finer msg 2");
        
        LogManager lm = LogManager.getLogManager();
        Enumeration<String> en = lm.getLoggerNames();
        while ( en.hasMoreElements()){
            String loggerName = en.nextElement();
            System.out.println("name: "+loggerName);
            
        }
        
        //assertTrue("actual level: "+logger.getLevel(), logger.getLevel()==Level.FINE);
    }

}
