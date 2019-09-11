package edu.drexel.xop;

import edu.drexel.xop.core.XOProxy;
import edu.drexel.xop.net.MockXopNet;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.util.logger.LogUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

import java.util.logging.Logger;

/**
 * Using the SMACK API, test XOP by establishing connections and
 * Created by duc on 11/17/16.
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class ClientTest {
    private static Logger logger = Logger.getLogger("ClientTest");
    private XOProxy mockXOProxy;
    private XopNet mockXopNet;

    @BeforeEach
    public void setup(){
        logger.info("Initializing XOP");
        // initialize XOP
        LogUtils.loadLoggingProperties(null);
        mockXopNet = new MockXopNet();
        mockXOProxy = XOProxy.getInstance();
        mockXOProxy.init(mockXopNet);
    }

    @AfterEach
    public void teardown(){
        logger.info("Shutting down XOP instance");
        // shut down XOP
        mockXOProxy.stop();
    }

//    /**
//     * Connect
//     */
//    @Test
//    public void testConnectToXOP(){
//
//        //
//
//        // Establish a connection to XOP
//        // Create a connection to the jabber.org server.
//        try {
//            XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
//                    .setUsernameAndPassword("username", "password")
//                    .setResource("testConnect")
//                    .setServiceName("proxy")
//                    .setHost("127.0.0.1")
//                    .setPort(5222)
//                    .build();
//            AbstractXMPPConnection conn1 = new XMPPTCPConnection(config);
//            conn1.connect();
//            conn1.login();
//            conn1.disconnect();
//        } catch (SmackException | IOException | XMPPException e) {
//            e.printStackTrace();
//            fail(e);
//        }
//    }

    /**
     * Request roster
     */
                /*
    @Test
    public void testRequestRoster(){
        // Create a connection to the jabber.org server.
        AbstractXMPPConnection conn1 = null;
        try {
            XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                    .setUsernameAndPassword("username", "password")
                    .setResource("testConnect")
                    .setServiceName("proxy")
                    .setHost("127.0.0.1")
                    .setPort(5222)
                    .build();
            conn1 = new XMPPTCPConnection(config);
            conn1.connect();
            conn1.login();

            IQ rosterRequest = new RosterPacket();
//
//            TestCase.assertTrue("roster reqeust is not a request IQs", rosterRequest.isRequestIQ());
//
//
//            System.out.println("=======> sending iq: "+rosterRequest);
//            final IQ resultPacket = null;
//            conn1.sendIqWithResponseCallback(rosterRequest, packet -> {
//                System.out.println("Stanza: " + packet);
//                TestCase.assertTrue("result packet is null!" , packet != null);
//            });
//            try {
//                Thread.sleep(2000);
//            } catch(InterruptedException e){
//                e.printStackTrace();
//            }
        } catch (SmackException | IOException | XMPPException e) {
            e.printStackTrace();
            fail(e);
        } finally{
            if( conn1 != null ){
                conn1.disconnect();
            }
        }
    }
            */
}
