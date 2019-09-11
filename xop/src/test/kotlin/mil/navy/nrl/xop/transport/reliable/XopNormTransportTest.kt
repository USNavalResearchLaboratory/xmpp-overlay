package mil.navy.nrl.xop.transport.reliable

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.xmpp.packet.JID
import org.xmpp.packet.Message

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XopNormTransportTest {

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun testTransportService() {
        //val packetProcessor = MockPacketProcessor()
        //val testTransport: XopNormService

        repeat(5) {
            val message = Message()
            message.to = JID("node@proxy")
        }
    }

}