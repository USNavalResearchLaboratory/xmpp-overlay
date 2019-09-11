package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.core.ClientManager
import edu.drexel.xop.packet.TransportPacketProcessor
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.xmpp.packet.JID
import org.xmpp.packet.Packet
import java.net.InetAddress

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XopNormServiceTest {
    @Test
    fun testEnablePresenceTransport() {
        val args = arrayOf("lo", "225.0.1.2", "10000-20000")

        if (args.size < 3) {
            println("Usage: XopNormService <iface> <multicastGroup <portRange>")
            return
        }
        // val iface = args[0]
        // val multicastGroup = InetAddress.getByName(args[1])
        // val portRange = args[2]
        //
        // val clientManager = ClientManager()
        // val transportPacketProcessor = object : TransportPacketProcessor(clientManager) {
        //     override fun processPacket(fromJID: JID, p: Packet) {
        //         println("MOCK PP: $fromJID, [[$p]]")
        //     }
        // }

        // //Create a XopNormService object as a receiver
        // val xopNormService = XopNormService(iface, multicastGroup, portRange, transportPacketProcessor)
        // val sdListener = object : SDListener {
        //     override fun gatewayAdded(address: InetAddress, domain: JID) {}
        //     override fun gatewayRemoved(domain: JID) {}
        //     override fun clientDiscovered(presence: Presence) {}
        //     override fun clientRemoved(presence: Presence) {}
        //     override fun clientUpdated(presence: Presence?) {}
        //     override fun clientDisconnected(clientJID: JID?) {}
        //     override fun mucOccupantJoined(presence: Presence) {}
        //     override fun mucOccupantExited(presence: Presence) {}
        //     override fun mucOccupantUpdated(presence: Presence?) {}
        //     override fun roomAdded(roomJID: JID?) {}
        //     override fun roomRemoved(roomJID: JID?) {}
        // }

        // val thisNode =  ClientNormNode(HashSet(), 100, 0, HashMap(), HashMap())
        // val presenceInterval = 5000L
        // val timeoutThreshold = 3
        // val sdManager = xopNormService.enablePresenceTransport(presenceInterval, timeoutThreshold, sdListener)
        //
        // val presence = Presence()
        // presence.to = JID("duc@proxy")
        // sdManager.advertiseClient(presence)
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
        val xopNormService = XopNormService(iface, multicastGroup, portRange, transportPacketProcessor)
        // // val remoteNodeId = 167772929L
        // // val remoteNode = xopNormService.addRemoteNode(remoteNodeId)
        // remoteNode.jidMap[JID("duc@proxy")] = "deadbeef1"
        //
        // println("remoteNode $remoteNode")
        val dataStr = "{\"jidMap\":{\"duc@proxy/rsrc\":\"deadbeef\"},\"connected\":true," +
                "\"mucOccupants\":{\"duc@proxy/rsrc\":[\"room@conference.proxy/duc\",\"room2@conference.proxy/duc\"]}," +
                "\"nodeId\":100," +
                "\"mucRooms\":[\"room@conference.proxy\",\"room2@conference.proxy\"]}"

        // val normTransport = xopNormService.createNormTransport(null, false)
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
