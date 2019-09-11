package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.core.ClientManager
import edu.drexel.xop.net.SDListener
import edu.drexel.xop.packet.TransportPacketProcessor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.xmpp.packet.JID
import org.xmpp.packet.Packet
import org.xmpp.packet.Presence
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class XopNormServiceTest {

    private val args = arrayOf("lo", "225.0.1.2", "10000-20000")
    private val sdListener = object : SDListener {
        override fun clientDisconnected(clientJID: JID?) {
            println("----> CLIENT DISCONNECTED $clientJID")
        }
        override fun clientReconnected(clientJID: JID?) {
            println("----> CLIENT RECONNECTED $clientJID")
        }

        override fun gatewayAdded(address: InetAddress, domain: JID) {}
        override fun gatewayRemoved(domain: JID) {}
        override fun clientDiscovered(presence: Presence) {
            println("t: ${Thread.currentThread().id} ----> CLIENT JOINED ${presence.from} [[${presence.toXML()}]]")
        }

        override fun clientRemoved(presence: Presence) {
            println("t: ${Thread.currentThread().id} ----> CLIENT EXITED ${presence.from} [[${presence.toXML()}]]")
        }

        override fun clientUpdated(presence: Presence) {
            println("t: ${Thread.currentThread().id} ----> CLIENT ${presence.from} UPDATED: ${presence.status}")
        }

        override fun mucOccupantJoined(presence: Presence) {
            println("t: ${Thread.currentThread().id} ----> MUC OCCUPANT JOINED [[${presence.toXML()}]]")
        }

        override fun mucOccupantExited(presence: Presence) {
            println("t: ${Thread.currentThread().id} ----> MUC OCCUPANT EXITED [[${presence.toXML()}]]")

        }

        override fun mucOccupantUpdated(presence: Presence) {
            println("t: ${Thread.currentThread().id} ----> MUC OCCUPANT UPDATED [[${presence.toXML()}]]")
        }

        override fun roomAdded(roomJID: JID) {
            println("t: ${Thread.currentThread().id} ----> roomAdded [[$roomJID]]")

        }

        override fun roomRemoved(roomJID: JID) {}
    }
    private val presenceInterval = 5000L
    private val timeoutThreshold = 3


    @Test
    fun testEnablePresenceTransport() = runBlocking {

        if (args.size < 3) {
            println("Usage: XopNormService <iface> <multicastGroup <portRange>")
            return@runBlocking
        }
        val iface = args[0]
        val multicastGroup = InetAddress.getByName(args[1])
        val portRange = args[2]
        //
        val clientManager = ClientManager()
        val transportPacketProcessor = object : TransportPacketProcessor(clientManager) {
            override fun processPacket(fromJID: JID, p: Packet) {
                println("MOCK PP: $fromJID, [[$p]]")
            }
        }

        // //Create a XopNormService object as a receiver
        // val xopNormService = XopNormService(1L, iface, multicastGroup, portRange, transportPacketProcessor)
        //
        // // val thisNode =  ClientNormNode(HashSet(), 100, 0, HashMap(), HashMap())
        // val sdManager = xopNormService.enablePresenceTransport(presenceInterval, timeoutThreshold, sdListener)
        //
        // val presence = Presence()
        // presence.from = JID("duc@proxy")
        // sdManager.advertiseClient(presence)
        //
        // xopNormService.shutdown()
    }


    // [ senderNodeId: Set[dataStrings] ]


    @Test
    fun testIsDuplicate() = runBlocking {
        val interfaces = listOf("eth0", "eth1", "eth3")

        val receivedSessionMessages
                = mutableMapOf<Long, MutableMap<String, AtomicInteger>>()

        fun isDuplicate(senderNodeId: Long, normSessionObj: String): Boolean {
            return if (senderNodeId !in receivedSessionMessages) {
                println("new senderNodeId $senderNodeId ct 1")
                receivedSessionMessages[senderNodeId] = mutableMapOf(normSessionObj to AtomicInteger(1))
                false
            } else {
                if (normSessionObj !in receivedSessionMessages[senderNodeId]!!) {
                    receivedSessionMessages[senderNodeId]!![normSessionObj] = AtomicInteger(1)
                    println("id: $senderNodeId new msg $normSessionObj ct ${receivedSessionMessages[senderNodeId]!![normSessionObj]!!.get()}")
                    false
                } else {
                    val ct = receivedSessionMessages[senderNodeId]!![normSessionObj]!!.incrementAndGet()
                    println("id: $senderNodeId dupe msg $normSessionObj ct $ct")
                    if (ct >= interfaces.size) {
                        receivedSessionMessages[senderNodeId]!!.remove(normSessionObj)
                        println("id: $senderNodeId dupe msg reach num ifaces ${interfaces.size}")
                    }
                    true
                }
            }
        }
        // test local isDuplicate

        assertFalse(isDuplicate(1, "msg1"))
        assertTrue(isDuplicate(1, "msg1"))
        assertFalse(isDuplicate(2,"msg1"))
        assertTrue(isDuplicate(2, "msg1"))

        assertTrue(isDuplicate(1, "msg1"))
        assertFalse(isDuplicate(1,"msg1"))
        assertTrue(isDuplicate(1, "msg1"))

        assertFalse(isDuplicate(3, "3msg 1"))
        assertFalse(isDuplicate(4, "3msg 1"))
        assertTrue(isDuplicate(3, "3msg 1"))
        assertTrue(isDuplicate(4, "3msg 1"))
        assertTrue(isDuplicate(3, "3msg 1"))
        assertFalse(isDuplicate(3, "3msg 1"))


    }

    @Test
    fun testHandlePresenceProbe() = runBlocking {
        val args = arrayOf("lo", "225.0.1.2", "10000-20000")

        val iface = args[0]
        val multicastGroup = InetAddress.getByName(args[1])
        val portRange = args[2]

        val clientManager = ClientManager()
        val transportPacketProcessor = object : TransportPacketProcessor(clientManager) {
            override fun processPacket(fromJID: JID, p: Packet) {
                println("MOCK PP: $fromJID, [[$p]]")
            }
        }

        /*
        jsonObject {"jidMap":{"duc@proxy":0},"ctr":0,"mucRooms":{},"nodeId":100}
        jsonObject {"jidMap":{"duc@proxy":0},"ctr":0,"mucRooms":{},"nodeId":100}
        {"jidMap":{"duc@proxy":0},"ctr":0,"mucRooms":{},"nodeId":100}
        fromJSONStr   NORMNode(jidMap={duc@proxy=0}, nodeId=100, ctr=0, mucRooms={})
        client     NORMNode(jidMap={duc@proxy=0}, nodeId=100, ctr=0, mucRooms={})
        client NORMNode(jidMap={duc@proxy=0, blah@who/rsrc=100}, nodeId=100, ctr=0, mucRooms={})
        json:  {"jidMap":{"duc@proxy":0,"blah@who/rsrc":100},"ctr":0,"mucRooms":{},"nodeId":100}
        jsonObject {"jidMap":{"duc@proxy":0,"blah@who/rsrc":100},"ctr":0,"mucRooms":{},"nodeId":100}
        {"jidMap":{"duc@proxy":0,"blah@who/rsrc":100},"ctr":0,"mucRooms":{},"nodeId":100}
         */

        // Create a XopNormService object as a receiver
        val xopNormService = XopNormService(1L, iface, iface, multicastGroup, portRange, transportPacketProcessor, false)
        // // val remoteNodeId = 167772929L
        // // val remoteNode = xopNormService.addRemoteNode(remoteNodeId)
        // remoteNode.jidMap[JID("duc@proxy")] = "deadbeef1"
        //
        // println("remoteNode $remoteNode")
        val dataStr = "{\"jidMap\":{\"duc@proxy/rsrc\":\"deadbeef\"},\"connected\":true," +
                "\"mucOccupants\":{\"duc@proxy/rsrc\":[\"room@conference.proxy/duc\",\"room2@conference.proxy/duc\"]}," +
                "\"nodeId\":100," +
                "\"mucRooms\":[\"room@conference.proxy\",\"room2@conference.proxy\"]}"

        // val normTransport = xopNormService.createRoomTransport(null, false)
        // val normSession = normTransport.normSession
        //
        // val presenceProbObj = XopNormService.NormSessionObj(
        //    dataStr, normSession,
        //    remoteNodeId, null  // NormTransport.TransportType.PresenceProbe
        // )

        // TODO 2019-02-26: move handlePresenceProbe tests to NormPresenceTransportTest
        // val normNode = fromJSONStr(dataStr)
        // println("hashcode changed handlePresenceProbe START")
        // var job = async { xopNormService.handlePresenceProbe(remoteNodeId, normNode) }
        // println("handlePresenceProbe END")
        // job.join()
        // // xopNormService.shutdown()
        //
        // // // no change
        // // xopNormService = XopNormService(iface, multicastGroup, portRange, packetProcessor)
        // // remoteNode = xopNormService.addRemoteNode(remoteNodeId)
        // remoteNode.jidMap[JID("duc@proxy")] = "deadbeef"
        //
        // println("NO CHANGE handlePresenceProbe START")
        // job = async { xopNormService.handlePresenceProbe(remoteNodeId, normNode) }
        // println("handlePresenceProbe END")
        // job.join()
        //
        //
        // println("3rd time handlePresenceProbe START")
        // job = async { xopNormService.handlePresenceProbe(remoteNodeId, normNode) }
        // println("handlePresenceProbe END")
        // job.join()

        xopNormService.shutdown()
    }
}
