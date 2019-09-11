/*
 * Copyright (c) 2011 Drexel University
 */

package edu.drexel.xop.util;

import org.junit.*;
import org.xmpp.packet.Packet;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author duc
 */
public class UtilsTest {

    public UtilsTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of packetFromString method, of class Utils.
     */
    @Test
    public void testPacketFromString() throws Exception {
        String s = "<iq type='set' id='purple37db8c49'>"
                + "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>"
                + "</iq>";
        Packet result = Utils.packetFromString(s);
        String id = result.getID();
        assertEquals(id, "purple37db8c49");
    }

}