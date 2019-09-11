package edu.drexel.xop.client.local;

import edu.drexel.xop.client.XMLProcessor;
import edu.drexel.xop.core.ClientManager;
import edu.drexel.xop.packet.LocalPacketProcessor;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Handles the parsing of the xml stream
 * 
 * @author David Millar
 * @author Duc Nguyen
 * @author Rob Taglang
 *
 * @deprecated
 */
class ClientXMLProcessor extends XMLProcessor {
    // private static final Logger logger = LogUtils.getLogger(ClientXMLProcessor.class.getName());


    private boolean authenticated = false;

    private UnauthenticatedClientHandler unauthenticatedClientHandler;
    private StanzaHandler stanzaHandler;

    /**
     * The main constructor for an XMLInputProcessor
     *
     * @param clientConnection the LocalClientConnection
     */
    public ClientXMLProcessor(LocalClientConnection clientConnection,
                              ClientManager clientManager, LocalPacketProcessor localPacketProcessor) {
        super(clientConnection, clientManager);
        unauthenticatedClientHandler = new UnauthenticatedClientHandler(clientConnection,
                clientManager, localPacketProcessor);
        stanzaHandler = new StanzaHandler();
    }

    /**
     * @return a clientHandler
     */
    public DefaultHandler getHandler() {
        if (authenticated) {
            return stanzaHandler;
        } else {
            return unauthenticatedClientHandler;
        }
    }

    private class StanzaHandler extends DefaultHandler {

    }
}