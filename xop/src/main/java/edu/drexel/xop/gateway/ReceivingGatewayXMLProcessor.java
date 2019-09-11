package edu.drexel.xop.gateway;

import edu.drexel.xop.packet.LocalPacketProcessor;
import edu.drexel.xop.util.logger.LogUtils;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.util.logging.Logger;

/**
 * Authenticates a server-to-server connection and generates stanzas
 * from the stream
 *
 */
class ReceivingGatewayXMLProcessor extends GatewayXMLProcessor {
    private static final Logger logger = LogUtils.getLogger(ReceivingGatewayXMLProcessor.class.getName());
    private LocalPacketProcessor localPacketProcessor;

    ReceivingGatewayXMLProcessor(ReceivingGatewayConnection clientConnection, LocalPacketProcessor packetProcessor) {
        super(clientConnection);
        this.localPacketProcessor = packetProcessor;
        logger.info("CONSTRUCTING Receiving GATEWAY CONNECTION: ");
    }

    public void setInputSource(InputSource source) {
        //only do this if we are initiating the connection
        super.setInputSource(source);
    }

    public DefaultHandler getHandler() {
        return new ReceivingGatewayXMLHandler((ReceivingGatewayConnection) this.xopConnection, localPacketProcessor);
    }

}
