/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.component.s2s;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.dom4j.Element;
import org.xmpp.packet.Packet;

import edu.drexel.xop.util.logger.LogUtils;

/**
 *
 * @author tradams
 */
public class XMPPStream {
    private static final Logger logger = LogUtils.getLogger(XMPPStream.class.getName());

    private Socket sock;
    
    protected InputStream istream;
    protected OutputStream ostream;
    protected String streamID;
    protected boolean writable = true;
    protected boolean valid = false;

    /**
     * 
     * @param hostname
     * @param port
     * @throws UnknownHostException
     * @throws IOException
     */
    XMPPStream(String hostname, int port) throws UnknownHostException, IOException{
        sock = new Socket(hostname, port);
    }
    
//    public XMPPStream(Socket socket) {
//        try {
//            istream = socket.getInputStream();
//            ostream = socket.getOutputStream();
//        } catch (IOException ex) {
//            logger.log(Level.SEVERE, "Exception creating input and output streams", ex);
//        }
//
//    }
//
    XMPPStream(InputStream is, OutputStream os) {
        istream = is;
        ostream = os;
    }

    OutputStream getOutputStream(){
        return ostream;
    }
    InputStream getInputStream(){
        return istream;
    }
    void setValid(boolean valid) {
        this.valid = valid;
    }

    void setStreamID(String id) {
        streamID = id;
    }

    public String getStreamID() {
        return streamID;
    }

    public boolean valid() {
        return valid;
    }

    void setWritable(boolean write) {
        writable = write;
    }

    public boolean writable() {
        return writable;
    }

    public void write(Element e) throws IOException {
        if (writable) {
         //   logger.logger(Level.INFO,e.asXML());
            byte[] bytes = e.asXML().getBytes();
            ostream.write(bytes);
        } else {
            logger.log(Level.SEVERE, "XMPPStream not set to writable. Unable to write <{0}>", e.asXML());
        }

    }

    public void write(Packet p) throws IOException {
        write(p.getElement());
    }

    public void open() throws IOException {
        String tmp = "<stream:stream"
                + " xmlns:stream='http://etherx.jabber.org/streams'"
                + " xmlns='jabber:server'"
                + " xmlns:db='jabber:server:dialback' version='1.0'>";
        logger.log(Level.FINE,tmp);
        ostream.write(tmp.getBytes());
    }

    public void open(String id) throws IOException {
        streamID = id;
//        version = false;
        String tmp = "<stream:stream"
                + " xmlns:stream='http://etherx.jabber.org/streams'"
                + " xmlns='jabber:server'"
                + " xmlns:db='jabber:server:dialback'"
                + " id='" + streamID + "' >";
        logger.log(Level.FINE,tmp);
        ostream.write(tmp.getBytes());
    }

    public void close() throws IOException {

        ostream.write("</stream:stream>".getBytes());

    }

    /**
     * close the streams and the associated socket
     * @throws IOException
     */
    void closeSocket() throws IOException {
        istream.close();
        ostream.close();
        if( sock != null )
            sock.close();
    
    }
    
    /**
     * replaces the output stream with a new one to the given hostname/port
     * @param outputStream
     */
    public OutputStream createNewOutputStream(String hostname, int port) throws IOException {
        try {
            ostream.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "unable to close output stream to external server.",e);
        }
        logger.fine("replacing outputstream");
        sock = new Socket(hostname, port);
        
        ostream = sock.getOutputStream();
        
        return ostream;
    }
}
