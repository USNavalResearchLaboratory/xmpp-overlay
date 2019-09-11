/*
 * Copyright (c) 2011 Drexel University
 */

package edu.drexel.xop.properties;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author duc
 */
public class XopPropertiesTest {

    @Test
    public void testGetSystemProperties(){

        //System.out.println("testGetSystemPRoperties: "+TestXopProperties.class.getName());

        // tests that the system property for -DXOP.xop.bind.interface is set.
        // In this case it's in the test target of build.xml
        String bindInterface = XopProperties.getInstance().getProperty(XopProperties.BIND_INTERFACE);
        assertTrue("System property set in the ant target as XOP.xop.bind.interface: "+bindInterface, "test.bind.interface".equals(bindInterface));

        //
        XopProperties.getInstance().setProperty(XopProperties.BIND_INTERFACE, "new.bind.iface");
        bindInterface = XopProperties.getInstance().getProperty(XopProperties.BIND_INTERFACE);
        assertTrue("setting bind.iface directly","new.bind.iface".equals(bindInterface));
    }
}
