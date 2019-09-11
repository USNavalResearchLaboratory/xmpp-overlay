/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package edu.drexel.xop.util;

import edu.drexel.xop.client.ParserException;
import edu.drexel.xop.util.logger.LogUtils;
import org.apache.mina.common.ByteBuffer;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * This is a Light-Weight XML Parser.
 * It read data from a channel and collect data until data are available in
 * the channel.
 * When a message is complete you can retrieve messages invoking the method
 * getMsgs() and you can invoke the method areThereMsgs() to know if at least
 * an message is presents.
 *
 * @author Daniele Piras
 * @author Gaston Dombiak
 */
public class XMLLightweightParser {
    Logger logger = LogUtils.getLogger(XMLLightweightParser.class.getName());

    private static final String END_STREAM = "</stream:stream>";
    private static int maxBufferSize;
    // Chars that represent CDATA section start
    final static char[] CDATA_START = {'<', '!', '[', 'C', 'D', 'A', 'T', 'A', '['};
    // Chars that represent CDATA section end
    final static char[] CDATA_END = {']', ']', '>'};

    // Buffer with all data retrieved
    protected StringBuilder buffer = new StringBuilder();

    // ---- INTERNAL STATUS -------
    // Initial status
    protected static final int INIT = 0;
    // Status used when the first tag name is retrieved
    protected static final int HEAD = 2;
    // Status used when robot is inside the xml and it looking for the tag conclusion
    protected static final int INSIDE = 3;
    // Status used when a '<' is found and try to find the conclusion tag.
    protected static final int PRETAIL = 4;
    // Status used when the ending tag is equal to the head tag
    protected static final int TAIL = 5;
    // Status used when robot is inside the main tag and found an '/' to check '/>'.
    protected static final int VERIFY_CLOSE_TAG = 6;
    //  Status used when you are inside a parameter
    protected static final int INSIDE_PARAM_VALUE = 7;
    //  Status used when you are inside a cdata section
    protected static final int INSIDE_CDATA = 8;
    // Status used when you are outside a tag/reading text
    protected static final int OUTSIDE = 9;


    // Current robot status
    protected int status = XMLLightweightParser.INIT;

    // Index to looking for a CDATA section start or end.
    protected int cdataOffset = 0;

    // Number of chars that machs with the head tag. If the tailCount is equal to
    // the head length so a close tag is found.
    protected int tailCount = 0;
    // Indicate the starting point in the buffer for the next message.
    protected int startLastMsg = 0;
    // Flag used to discover tag in the form <tag />.
    protected boolean insideRootTag = false;
    // Object conteining the head tag
    protected StringBuilder head = new StringBuilder(5);
    // List with all finished messages found.
    protected List<String> msgs = new LinkedList<>();
    private int depth = 0;

    Charset encoder;

    static {
        // Set default max buffer size to 1MB. If limit is reached then close connection
        maxBufferSize = 1048576;
    }

    public XMLLightweightParser(String charset) {
        encoder = Charset.forName(charset);
    }

    /*
    * true if the parser has found some complete xml message.
    */
    public boolean areThereMsgs() {
        // logger.log(Level.INFO,"parser state: {0}",buffer);
        return (msgs.size() > 0);
    }

    /*
    * @return an array with all messages found
    */
    public String[] getMsgs() {
        String[] res = new String[msgs.size()];
        for (int i = 0; i < msgs.size(); i++) {
            res[i] = msgs.get(i);
        }
        msgs = new LinkedList<>();
        invalidateBuffer();
        return res;
    }

    /*
    * Method use to clear parsed data from the buffer
    */
    protected void invalidateBuffer() {
        // Check to see if there is data in the buffer
        if (buffer.length() > 0) {
            // Using startLastMsg as the index of the last, incomplete message in
            // the buffer, remove the substring of from the beginning of this
            // incomplete message to the end of the buffer
            String str = buffer.substring(startLastMsg);
            // Clear the buffer
            buffer.delete(0, buffer.length());
            // Append the string which contains the incomplete message
            buffer.append(str);
            // Trim the buffer to the proper size
            buffer.trimToSize();
        }
        startLastMsg = 0;
    }

    /*
    * Method that add a message to the list and reinit parser.
    */
    protected void foundMsg(String msg) {
        // Add message to the complete message list
        if (msg != null) {
            msgs.add(msg);
        }
        // Move the position into the buffer
        status = XMLLightweightParser.INIT;
        tailCount = 0;
        cdataOffset = 0;
        head.setLength(0);
        insideRootTag = false;
        depth = 0;
    }

    public void read(byte[] bytes, int offset, int length) throws Exception {
        if (length <= 0)
            return;
        read(ByteBuffer.wrap(bytes, offset, length));
    }

    public void read(byte[] bytes) throws ParserException {
        read(ByteBuffer.wrap(bytes));
    }

    /*
    * Main reading method
    */
    public void read(ByteBuffer byteBuffer) throws ParserException {
        invalidateBuffer();

        // Check that the buffer is not bigger than 1 Megabyte. For security reasons
        // we will abort parsing when 1 Mega of queued chars was found.
        if (buffer.length() > maxBufferSize) {
            throw new ParserException("Stopped parsing never ending stanza");
        }
        CharBuffer charBuffer = encoder.decode(byteBuffer.buf());
        char[] buf = charBuffer.array();
        int readByte = charBuffer.remaining();

        // Just return if nothing was read
        if (readByte == 0) {
            return;
        }

        // Verify if the last received byte is an incomplete double byte character
        char lastChar = buf[readByte - 1];
        if (lastChar >= 0xfff0) {
            // Rewind the position one place so the last byte stays in the buffer
            // The missing byte should arrive in the next iteration. Once we have both
            // of bytes we will have the correct character
            byteBuffer.position(byteBuffer.position() - 1);
            // Decrease the number of bytes read by one
            readByte--;
            // Just return if nothing was read
            if (readByte == 0) {
                return;
            }
        }

        buffer.append(buf, 0, readByte);
        // Do nothing if the buffer only contains white spaces
        if (buffer.charAt(0) <= ' ' && buffer.charAt(buffer.length() - 1) <= ' ') {
            if ("".equals(buffer.toString().trim())) {
                // Empty the buffer so there is no memory leak
                buffer.delete(0, buffer.length());
                return;
            }
        }
        // Robot.
        char ch;
        boolean isHighSurrogate = false;
        for (int i = 0; i < readByte; i++) {
            ch = buf[i];
            if (ch < 0x20 && ch != 0x9 && ch != 0xA && ch != 0xD && ch != 0x0) {
                //Unicode characters in the range 0x0000-0x001F other than 9, A, and D are not allowed in XML
                //We need to allow the NULL character, however, for Flash XMLSocket clients to work.
                throw new ParserException("Disallowed character");
            }
            if (isHighSurrogate) {
                if (Character.isLowSurrogate(ch)) {
                    // Everything is fine. Clean up traces for surrogates
                    isHighSurrogate = false;
                } else {
                    // Trigger error. Found high surrogate not followed by low surrogate
                    throw new ParserException("Found high surrogate not followed by low surrogate");
                }
            } else if (Character.isHighSurrogate(ch)) {
                isHighSurrogate = true;
            } else if (Character.isLowSurrogate(ch)) {
                // Trigger error. Found low surrogate char without a preceding high surrogate
                throw new ParserException("Found low surrogate char without a preceding high surrogate");
            }
            if (status == XMLLightweightParser.TAIL) {
                // Looking for the close tag
                if (depth < 1 && ch == head.charAt(tailCount)) {
                    tailCount++;
                    if (tailCount == head.length()) {
                        // Close stanza found!
                        // Calculate the correct start,end position of the message into the buffer
                        int end = buffer.length() - readByte + (i + 1);
                        String msg = buffer.substring(startLastMsg, end);
                        // Add message to the list
                        foundMsg(msg);
                        startLastMsg = end;
                    }
                } else {
                    tailCount = 0;
                    status = XMLLightweightParser.INSIDE;
                }
            } else if (status == XMLLightweightParser.PRETAIL) {
                if (ch == XMLLightweightParser.CDATA_START[cdataOffset]) {
                    cdataOffset++;
                    if (cdataOffset == XMLLightweightParser.CDATA_START.length) {
                        status = XMLLightweightParser.INSIDE_CDATA;
                        cdataOffset = 0;
                        continue;
                    }
                } else {
                    cdataOffset = 0;
                    status = XMLLightweightParser.INSIDE;
                }
                if (ch == '/') {
                    status = XMLLightweightParser.TAIL;
                    depth--;
                } else if (ch == '!') {
                    // This is a <! (comment) so ignore it
                    status = XMLLightweightParser.INSIDE;
                } else {
                    depth++;
                }
            } else if (status == XMLLightweightParser.VERIFY_CLOSE_TAG) {
                if (ch == '>') {
                    depth--;
                    status = XMLLightweightParser.OUTSIDE;
                    if (depth < 1) {
                        // Found a tag in the form <tag />
                        int end = buffer.length() - readByte + (i + 1);
                        String msg = buffer.substring(startLastMsg, end);
                        // Add message to the list
                        foundMsg(msg);
                        startLastMsg = end;
                    }
                } else if (ch == '<') {
                    status = XMLLightweightParser.PRETAIL;
                } else {
                    status = XMLLightweightParser.INSIDE;
                }
            } else if (status == XMLLightweightParser.INSIDE_PARAM_VALUE) {

                if (ch == '"') {
                    status = XMLLightweightParser.INSIDE;
                }
            } else if (status == XMLLightweightParser.INSIDE_CDATA) {
                if (ch == XMLLightweightParser.CDATA_END[cdataOffset]) {
                    cdataOffset++;
                    if (cdataOffset == XMLLightweightParser.CDATA_END.length) {
                        status = XMLLightweightParser.OUTSIDE;
                        cdataOffset = 0;
                    }
                } else {
                    cdataOffset = 0;
                }
            } else if (status == XMLLightweightParser.INSIDE) {
                if (ch == XMLLightweightParser.CDATA_START[cdataOffset]) {
                    cdataOffset++;
                    if (cdataOffset == XMLLightweightParser.CDATA_START.length) {
                        status = XMLLightweightParser.INSIDE_CDATA;
                        cdataOffset = 0;
                        continue;
                    }
                } else {
                    cdataOffset = 0;
                    status = XMLLightweightParser.INSIDE;
                }
                if (ch == '"') {
                    status = XMLLightweightParser.INSIDE_PARAM_VALUE;
                } else if (ch == '>') {
                    status = XMLLightweightParser.OUTSIDE;
                    if (insideRootTag && ("stream:stream>".equals(head.toString()) ||
                            ("?xml>".equals(head.toString())) || ("flash:stream>".equals(head.toString())))) {
                        // Found closing stream:stream
                        int end = buffer.length() - readByte + (i + 1);
                        // Skip LF, CR and other "weird" characters that could appear
                        while (startLastMsg < end && '<' != buffer.charAt(startLastMsg)) {
                            startLastMsg++;
                        }
                        String msg = buffer.substring(startLastMsg, end);
                        foundMsg(msg);
                        startLastMsg = end;
                    }
                    insideRootTag = false;
                } else if (ch == '/') {
                    status = XMLLightweightParser.VERIFY_CLOSE_TAG;
                }
            } else if (status == XMLLightweightParser.HEAD) {
                if (ch == ' ' || ch == '>') {
                    // Append > to head to allow searching </tag>
                    head.append(">");
                    if (ch == '>') {
                        status = XMLLightweightParser.OUTSIDE;
                    } else {
                        status = XMLLightweightParser.INSIDE;
                    }
                    insideRootTag = true;
                    continue;
                } else if (ch == '/' && head.length() > 0) {
                    status = XMLLightweightParser.VERIFY_CLOSE_TAG;
                    depth--;
                }
                head.append(ch);

            } else if (status == XMLLightweightParser.INIT) {
                if (ch == '<') {
                    status = XMLLightweightParser.HEAD;
                    depth = 1;
                } else {
                    startLastMsg++;
                }
            } else if (status == XMLLightweightParser.OUTSIDE) {
                if (ch == '<') {
                    status = XMLLightweightParser.PRETAIL;
                    cdataOffset = 1;
                }
            }
        }
        if (head.length() > 0 &&
                ("/stream>".equals(head.toString()) || "/stream:stream>".equals(head.toString()) || ("/flash:stream>".equals(head.toString())))) {
            // Found closing stream:stream
            logger.fine("found close stream in head" + head);
            foundMsg(END_STREAM);
        }
    }


}
