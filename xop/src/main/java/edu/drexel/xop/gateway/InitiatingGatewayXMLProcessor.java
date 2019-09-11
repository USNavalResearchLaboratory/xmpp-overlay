package edu.drexel.xop.gateway;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.util.logging.Logger;

import edu.drexel.xop.util.logger.LogUtils;

/**
 * Authenticates a server-to-server connection and generates stanzas
 * from the stream
 *
 */
class InitiatingGatewayXMLProcessor extends GatewayXMLProcessor {
    private static final Logger logger = LogUtils.getLogger(InitiatingGatewayXMLProcessor.class.getName());
    private InitiatingGatewayXMLHandler initiatingGatewayXMLHandler;

    private String xopGatewayDomainName;
    private String remoteGatewayDomainName;
    private String sessionId;

    InitiatingGatewayXMLProcessor(InitiatingGatewayConnection initiatingGatewayConnection,
                                  String remoteGatewayDomainName, String xopGatewayDomainName,
                                  String sessionId) {
        super(initiatingGatewayConnection);
        this.remoteGatewayDomainName = remoteGatewayDomainName;
        this.xopGatewayDomainName = xopGatewayDomainName;
        this.sessionId = sessionId;
        initiatingGatewayXMLHandler = new InitiatingGatewayXMLHandler(initiatingGatewayConnection);
        InitiatingGatewayXMLHandler.tlsEnabled = false;
        logger.info("Constructed InitiatingGatewayXMLProcessor, remote: " + remoteGatewayDomainName + " xopdomain: " + xopGatewayDomainName);
    }

    @Override
    public void setInputSource(InputSource source) {
        // TODO 2018-03-07 trying to move this to a better place
        //only do this if we are initiating the connection
        logger.info("Sending open stream. Domain: "+xopGatewayDomainName
                + ", Remote gateway domain: "+remoteGatewayDomainName +", Session id: "+sessionId); //S2S Step 1
        String streamOpen = GatewayUtil.generateS2SOpenStream( xopGatewayDomainName, remoteGatewayDomainName, sessionId);
        logger.fine("streamOpen: " + streamOpen);
        try {
            this.xopConnection.writeRaw(streamOpen.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            throw new Error(e);
        }
        super.setInputSource(source);
    }

    @Override
    public DefaultHandler getHandler() {
        return initiatingGatewayXMLHandler;
    }

    /**
     * Initiates a dialback verification for the domain. It is a passthru method for
     * InitiatingGatewayXMLHandler.initiateDialback . After initiating dialback, queued
     * messages are sent.
     * @param from this host
     * @param domain the domain to be verified
     * @param sessionId the id of this new session
     */
    void initiateDialback(String from, String domain, String sessionId) throws IOException {
        initiatingGatewayXMLHandler.initiateDialback(from, domain, sessionId);
    }
}
