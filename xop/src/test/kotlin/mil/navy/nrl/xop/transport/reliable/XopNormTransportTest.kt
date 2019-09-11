package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.core.ClientManager
import edu.drexel.xop.packet.TransportPacketProcessor
import mil.navy.nrl.norm.NormInstance
import mil.navy.nrl.norm.NormNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.xmpp.packet.JID
import org.xmpp.packet.Message
import org.xmpp.packet.Packet
import java.net.InetAddress

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XopNormTransportTest {

    private val user1 = JID("user1@proxy/rsc")
    private val user2 = JID("user2@proxy/rsc")
    private val room = JID("room@conference.proxy")

    private var normInstance: NormInstance? = null

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun testTransportService() {
        println("starting normInstance")
        normInstance = NormInstance()

        println("creating normSession")
        val mockNormSession =  normInstance!!.createSession(
            "225.1.2.5", 10000,
            NormNode.NORM_NODE_ANY
        )

        val msg = "message"
        val groupchat = "Group chat message"

        val clientManager = ClientManager()
        println("creating transport packet processor")
        val transportPacketProcessor = object : TransportPacketProcessor(clientManager) {
            override fun processPacket(fromJID: JID, p: Packet) {
                println("----> ----> $fromJID, [[$p]]")

                when(fromJID) {
                    user1 -> {
                        if (p is Message) {
                            assertTrue(p.to == user2)
                            assertTrue(p.body == msg)
                        } else {
                            fail("packet is not an XMPP Message")
                        }
                    }
                    user2 -> {
                        if (p is Message) {
                            assertTrue(p.type == Message.Type.groupchat)
                            assertTrue(p.to == room)
                            assertTrue(p.body == groupchat)
                        } else {
                            fail("packet is not an XMPP Message")
                        }
                    }
                    else -> {
                        fail("unhandled message")
                    }
                }
            }
        }

        println("creating normTransport")
        val normTransport = NormTransport(
            TransportType.MessageTransport,
            TransportSubType.Chat,
            mutableMapOf("lo" to mockNormSession),
            mutableMapOf("lo" to mockNormSession),
            InetAddress.getLocalHost(), 10000,
            transportPacketProcessor, false, 1)

        val chatMessage = Message()
        chatMessage.from = user1
        chatMessage.body = msg
        chatMessage.to = user2

        val groupChat = Message()
        groupChat.type = Message.Type.groupchat
        groupChat.from = user2
        groupChat.to = room
        groupChat.body = groupchat

        println("testing handleTransportData")
        var transportMetadata = TransportMetadata(
            0L, TransportType.MessageTransport, TransportSubType.Chat, 0L
        )
        normTransport.handleTransportData(
            100, mockNormSession, transportMetadata, chatMessage.toXML(),
            chatMessage.toXML().toByteArray(Charsets.UTF_8)
        )

        val groupChatTransport = NormTransport(TransportType.MessageTransport, TransportSubType.GroupChat,
            mutableMapOf("lo" to mockNormSession),
            mutableMapOf("lo" to mockNormSession),
            InetAddress.getLoopbackAddress(), 10000,
            transportPacketProcessor, compression = false)



        transportMetadata = TransportMetadata(
            0L, TransportType.MessageTransport, TransportSubType.GroupChat, 0L
        )
        groupChatTransport.handleTransportData(
            100, mockNormSession, transportMetadata, groupChat.toXML(),
            groupChat.toXML().toByteArray(Charsets.UTF_8)
        )

        // repeat(5) {
        //     val message = Message()
        //     message.to = JID("node@proxy")
        // }
        println("destroying normInstance")
        normInstance!!.destroyInstance()
    }

}