package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.core.ClientManager
import edu.drexel.xop.net.SDListener
import edu.drexel.xop.packet.TransportPacketProcessor
import edu.drexel.xop.room.Room
import edu.drexel.xop.util.CONSTANTS
import kotlinx.coroutines.runBlocking
import org.xmpp.packet.JID
import org.xmpp.packet.Packet
import org.xmpp.packet.Presence
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

fun main(args: Array<String>) = runBlocking {
    if (args.size < 6) {
        println("Usage: XopNormService <nodeId> <iface> <multicastGroup <portRange> <send|recv> <presenceInterval> <timeout>")
        exitProcess(-1)
    }

    val nodeId = args[0].toLong()
    val iface = args[1]
    val multicastGroup = InetAddress.getByName(args[2])
    val portRange = args[3]
    val send = args[4] == "send"
    val presenceInterval = args[5].toLong()
    val timeout = args[6].toInt()
    val ct = AtomicInteger()
    val clientManager = ClientManager()
    val transportPacketProcessor = object : TransportPacketProcessor(clientManager) {
        override fun processPacket(fromJID: JID, p: Packet) {
            println("----> ----> ${ct.incrementAndGet()}: $fromJID, [[$p]]")
        }
    }

    val sdListener = object : SDListener {
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

    // Create a XopNormService object as a receiver
    val xopNormService = XopNormService(nodeId, iface, iface, multicastGroup, portRange, transportPacketProcessor)

    println("hit enter to continue")
    readLine()
    println("enabling presence transport")
    val sdManager = xopNormService.enablePresenceTransport(presenceInterval,timeout, sdListener)

    if (send) {
        println("sending only")

        val available = Presence()
        available.from = JID("src@proxy/rsc")
        available.status = "I'm here!"
        //delay(2000L)
        sdManager.advertiseClient(available)
        println("advertised client: $available")

        val roomJID = JID("room@conference.proxy")
        val normTransport = xopNormService.createRoomTransport(roomJID, false)
        sdManager.advertiseMucRoom(Room(roomJID, clientManager, normTransport))

        val mucOccupant = Presence()
        mucOccupant.to = JID("room@conference.proxy/src")
        mucOccupant.from = JID("src@proxy/rsc")
        mucOccupant.addChildElement("x", CONSTANTS.DISCO.MUC_NAMESPACE)
        sdManager.advertiseMucOccupant(mucOccupant)
        println("advertised mucOCCUPANT: [[$mucOccupant]]")

        println("hit enter to continue")
        readLine()
        println("moving on, sending update")

        available.status = "update"
        println("sending updated presence status: ${available.status}")
        sdManager.advertiseClient(available)

        //delay(3000L)
        //
        //val message = Message()
        //message.to = JID("room@conference.proxy")
        //message.from = JID("user@proxy")
        //message.body = "body body"
        //normTransport.sendPacket(message)
        //
        //
        //val sendMsgs = launch{
        //    repeat(maxCount){
        //        val m = Message()
        //        m.body = "hello world! $it"
        //        m.to = JID("testroom@conference.proxy")
        //        m.from = JID("src@proxy")
        //        m.type = Message.Type.groupchat
        //        transport.sendPacket(m)
        //        delay(2000)
        //    }
        //}
        //sendMsgs.join()
    } else {
        println("receiving only")
        val available = Presence()
        available.from = JID("rcv${args[0]}@proxy/rsc")
        available.status = "I'm here!"
        //delay(2000L)
        sdManager.advertiseClient(available)
        println("advertised client: $available")
    }

    println("wait for input to shutdown")
    readLine()
    println("done")

    if (send) {
        val mucOccupant = Presence(Presence.Type.unavailable)
        mucOccupant.to = JID("room@conference.proxy/src")
        mucOccupant.from = JID("src@proxy/rsc")
        sdManager.removeMucOccupant(mucOccupant)
        println("=====> removed mucOccupant: ${mucOccupant.to}")

        val unavailable = Presence(Presence.Type.unavailable)
        unavailable.from = JID("src@proxy/rsc")
        //delay(2000L)
        sdManager.removeClient(unavailable)
        println("======> removed client: ${unavailable.from}")
    } else {
        val unavailable = Presence(Presence.Type.unavailable)
        unavailable.from = JID("rcv@proxy/rsc")
        //delay(2000L)
        sdManager.removeClient(unavailable)
        println("======> removed client: ${unavailable.from}")
    }

    println("wait for input to shutdown")
    readLine()

    xopNormService.shutdown()
    println("shutting down")
    //launched.join()

    //    delay(3000L)
}

//
//    val thisNode =  ClientNormNode(HashSet(), 100, 0, HashMap(), HashMap())
//    val presenceInterval = 5000L
//    val timeoutThreshold = 3
//    val sdManager = xopNormService.enablePresenceTransport(sdListener, presenceInterval, timeoutThreshold)
//    val presence = Presence()
//    presence.to = JID("duc@proxy")
//    sdManager.advertiseClient(presence)
