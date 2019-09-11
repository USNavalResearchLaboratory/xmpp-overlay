package edu.drexel.xop.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.xmpp.packet.Message;

import edu.drexel.xop.core.ClientProxy;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;

class Connection implements Runnable {
    private static final Logger logger = LogUtils.getLogger(Connection.class.getName());

    private boolean run = true;
    protected Socket client;
    protected BufferedReader in;
    protected PrintStream out;
    protected String httpRootDir;
    protected String requestedFile;

    protected ClientProxy proxy = ClientProxy.getInstance();

    private static enum WEB_QUERY_RESOURCE {
        PROPERTIES, MUC, CHAT
    }

    public Connection(Socket client_socket, String httpRoot) {
        httpRootDir = httpRoot;
        client = client_socket;

        try {
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            out = new PrintStream(client.getOutputStream());
        } catch (IOException e) {
            System.err.println(e);
            logger.info("Closing the socket");
            stop();
        }
    }

    public Boolean isRunning() {
        return run;
    }

    public void stop() {
        logger.finer("Stop was called");
        try {
            client.close();
        } catch (IOException e) {
            logger.severe("Error closing web server socket.");
            e.printStackTrace();
        }
        run = false;
    }

    @Override
    public void run() {
        while (run && !Thread.currentThread().isInterrupted()) {
            logger.finer("New connection started");
            String line;
            String req;

            try {
                req = in.readLine();
                line = req;
                while (line.length() > 0) {
                    line = in.readLine();
                }
                StringTokenizer st = new StringTokenizer(req);
                st.nextToken();
                requestedFile = st.nextToken();
                logger.fine("Request for: " + requestedFile);
                if (requestedFile.equals("/")) {
                    requestedFile = "/index.html";
                }
                File f = new File(httpRootDir + requestedFile);
                if (requestedFile.contains("?")) { // This is some sort of query
                    Map<String, String> params = getParams(requestedFile);
                    String resource = getResource(requestedFile);
                    logger.finer("Params: " + params);
                    logger.finer("URL Resource: " + resource);
                    logger.finer("Got request for server information");
                    WEB_QUERY_RESOURCE q = WEB_QUERY_RESOURCE.valueOf(resource.toUpperCase());
                    switch (q) {
                    case PROPERTIES:
                        if (params.get("query") != null) {
                            singleJSON(XopProperties.getInstance().getProperty(params.get("query")));
                        } else {
                            logger.warning("Got a PROPERTIES request without a QUERY parameter");
                        }
                        stop();
                        break;
                    case MUC:
                        if (params.get("node") != null) {
                            // TODO: Need to set the room name to check the connection later
                            return; // Leave the connection open
                        } else if (params.get("query") != null) {
                            if (params.get("query").equals("rooms")) {
                                logger.info("Responding to 'rooms' query");
                                handleRoomQuery();
                            } else {
                                logger.warning("Got a MUC request with a QUERY parameter I don't understand");
                            }
                        } else {
                            logger.warning("Got a MUC request without a NODE parameter");
                        }
                        stop();
                        break;
                    case CHAT:
                        if (params.get("room") != null) {
                            serveFile(new File(httpRootDir + "/chat.html"));
                        } else {
                            logger.warning("Got a CHAT request without a ROOM parameter");
                        }
                        stop();
                        break;
                    default:
                        logger.warning("Don't know how to respond to this resource: " + q);
                        stop();
                        break;
                    }
                } else if (f.canRead()) { // This is just a regular html file
                    serveFile(f);
                    stop();
                } else {
                    sendResponseHeader("text/plain");
                    sendString("404: not found: " + httpRootDir + requestedFile);
                    logger.info("404: not found: " + f.getCanonicalPath());
                    stop();
                }
            } catch (IOException e) {
                System.out.println(e);
                stop();
            }
        }
    }

    private void handleRoomQuery() {
        String rooms = proxy.getRooms();
        // Warning: Super hack to follow
        logger.info("Rooms: " + rooms);
        rooms = rooms.substring(rooms.indexOf("[") + 1, rooms.indexOf("]"));
        String retval = "{\"result\" : [";
        String delim = "";
        for (String room : rooms.split(", ")) {
            retval += delim + "{ \"room\" : \"" + room + "\"}";
            delim = ", ";
        }
        retval += "]}";
        out.print(retval);
        logger.fine("Responded with: " + retval);
    }

    private String extension(String f) {
        return f.substring(f.lastIndexOf(".") + 1);
    }

    private void serveFile(File f) {
        try {
            logger.fine("Served: " + f.getCanonicalPath());
            String ext = extension(f.getCanonicalPath());
            if (ext.equals("html") || ext.equals("htm")) {
                sendResponseHeader("text/html");
            } else if (ext.equals("css")) {
                sendResponseHeader("text/css");
            } else if (ext.equals("js")) {
                sendResponseHeader("text/javascript");
            } else if (ext.equals("png")) {
                sendResponseHeader("image/png");
            } else {
                logger.warning("Don't know the content-type of this request!");
                sendResponseHeader("text/plain");
            }

            FileInputStream fis = new FileInputStream(f);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            String line = br.readLine();
            while (line != null) {
                sendString(line + "\n");
                line = br.readLine();
            }
            br.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getResource(String req) {
        String[] spl = req.split("\\?");
        if (spl.length > 0) {
            spl = spl[0].split("/");
            if (spl.length > 0) {
                return spl[spl.length - 1];
            }
        }
        return "";
    }

    private void singleJSON(String property) {
        logger.fine("Responded with: " + "{\"result\" : \"" + property + "\"}");
        out.print("{\"result\" : \"" + property + "\"}");
    }

    public void consume(Message m) {
        logger.finer("Consumed a message: " + m);
        out.print("\n" + "HTTP/1.0 200 OK\n" + "Content-type: text/event-stream\n" + "Cache-Control: no-cache\n\n"
            + "id: " + m.getID() + "\n" + "data: " + "{\n" + "data: \"jid\": \"" + m.getFrom().getResource() + "\",\n"
            + "data: \"msg\": \"" + m.getBody() + "\"\n" + "data: " + "}\n" + "\n");
    }

    Map<String, String> getParams(String URL) {
        Map<String, String> params = new HashMap<>();
        for (String param : URL.split("\\?")[1].split("&")) {
            String[] spl = param.split("=");
            params.put(spl[0], spl[1]);
        }
        return params;
    }

    void sendResponseHeader(String type) {
        out.println("HTTP/1.0 200 OK");
        out.println("Content-type: " + type + "\n\n");
    }

    void sendString(String str) {
        out.print(str);
    }
}