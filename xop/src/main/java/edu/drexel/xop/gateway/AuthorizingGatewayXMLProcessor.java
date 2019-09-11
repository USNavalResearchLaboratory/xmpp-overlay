package edu.drexel.xop.gateway;

/*
 * (c) 2013 Drexel University
 */

import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * Authenticates a server-to-server connection and generates stanzas
 * from the stream
 *
 */
class AuthorizingGatewayXMLProcessor extends GatewayXMLProcessor {
    private static final Logger logger = LogUtils.getLogger(AuthorizingGatewayXMLProcessor.class.getName());
    private AuthorizingGatewayXMLHandler authorizingGatewayXMLHandler;

    private String xopGatewayDomainName;
    private String remoteGatewayDomainName;
    private String receivingConnectionSessionId;
    private String sessionId = Utils.generateID(8);

    public AuthorizingGatewayXMLProcessor(AuthorizingGatewayConnection clientConnection,
                                          String xopGatewayDomainName,
                                          String remoteGatewayDomainName, String sessionId,
                                          String hashKey,
                                          ReceivingGatewayConnection receivingGatewayConnection) {
        super(clientConnection);
        logger.info("CONSTRUCTING Initiating GATEWAY CONNECTION");
        this.xopGatewayDomainName = xopGatewayDomainName;
        this.remoteGatewayDomainName = remoteGatewayDomainName;
        this.receivingConnectionSessionId = sessionId;

        authorizingGatewayXMLHandler = new AuthorizingGatewayXMLHandler(
                (AuthorizingGatewayConnection) this.xopConnection, xopGatewayDomainName,
                remoteGatewayDomainName, receivingConnectionSessionId, sessionId, hashKey, receivingGatewayConnection);
    }

    @Override
    public void setInputSource(InputSource source) {
        //only do this if we are initiating the connection
        logger.info("Sending open stream"); //S2S Step 1

        String streamOpen = GatewayUtil.generateS2SOpenStream( xopGatewayDomainName, remoteGatewayDomainName, null);
        try{
            this.xopConnection.writeRaw(streamOpen.getBytes());
        } catch(IOException ioe){
            logger.log(Level.SEVERE, "IOException caught writing bytes", ioe);
            throw new Error(ioe);
        }

        super.setInputSource(source);
    }

    @Override
    public DefaultHandler getHandler() {
        return authorizingGatewayXMLHandler;
    }

}
