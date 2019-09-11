/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author David Millar
 */
public class Base64Test {
    private static final String base64 = "SEVMTE8=";
    private static final String decoded = "HELLO";

    @Test
    public void testBase64Encode(){
        assertEquals(Base64.decode(base64), decoded);
    }

    @Test
    public void testBase64Decode(){
        assertEquals(Base64.encode(decoded), base64);
    }
}
