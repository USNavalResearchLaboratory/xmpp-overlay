package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.core.ClientManager
import edu.drexel.xop.net.SDListener
import edu.drexel.xop.packet.TransportPacketProcessor
import kotlinx.coroutines.runBlocking
import mil.navy.nrl.norm.NormInstance
import mil.navy.nrl.norm.NormNode
import mil.navy.nrl.norm.NormSession
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.xmpp.packet.JID
import org.xmpp.packet.Packet
import org.xmpp.packet.Presence
import java.net.InetAddress
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
internal class NormPresenceTransportTest {
    private var clientManager = ClientManager()
    private var transportPacketProcessor = object : TransportPacketProcessor(clientManager) {
        override fun processPacket(fromJID: JID, p: Packet) {
            println("----> ----> $fromJID, [[$p]]")
        }
    }

    private val user1 = JID("user1@proxy")
    private val user2 = JID("user2@proxy")

    private val room = JID("room@conference.proxy")
    private val roomUser1Str = "room@conference.proxy/user1"
    private val roomUser2Str = "room@conference.proxy/user2"
    private val roomUser1 = JID(roomUser1Str)
    private val roomUser2 = JID(roomUser2Str)

    private val room2 = JID("room2@conference.proxy")
    private val room2User1Str = "room2@conference.proxy/user1"
    private val room2User2Str = "room2@conference.proxy/user2"
    private val room2User1 = JID(room2User1Str)
    private val room2User2 = JID(room2User2Str)

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
            println("----> new client from ${presence.from} [[$presence]]")
        }

        override fun clientRemoved(presence: Presence) {
            println("----> new client left! ${presence.from} [[$presence]]")
        }

        override fun clientUpdated(presence: Presence) {
            println("----> client ${presence.from} updated: ${presence.status}")
        }

        override fun mucOccupantJoined(presence: Presence) {
            println("----> MUC OCCUPANT JOINED [[${presence.toXML()}]]")
        }

        override fun mucOccupantExited(presence: Presence) {
            println("----> MUC OCCUPANT EXITED [[${presence.toXML()}]]")

        }

        override fun mucOccupantUpdated(presence: Presence) {
            println("----> MUC OCCUPANT updated [[${presence.toXML()}]]")
        }

        override fun roomAdded(roomJID: JID) {
            println("----> roomAdded [[$roomJID]]")

        }

        override fun roomRemoved(roomJID: JID) {}
    }

    private val normInstance = NormInstance()

    private var mockSession: NormSession? = null
    // private var normPresenceTransport: NormPresenceTransport? = null

    @BeforeEach
    fun setUp() {
        println("before Each setup")
        normInstance.restartInstance()
    }

    @AfterEach
    fun teardown() {
        println("teardown after Each")
    }

    // @AfterAll
    // fun teardownTest(){
    //     println("destroy normInstance")
    //     normInstance.destroyInstance()
    // }


    private fun startSenderReceiverSessions(senderSession: NormSession, receiverSession: NormSession) {
        val randGen = Random(System.currentTimeMillis())

        val randInt = randGen.nextInt()
        logger.fine("-- Creating new senderSession nodeId ${senderSession.localNodeId}; randInt $randInt")
        val senderBufferSpace:Long = 256 * 256L
        val segmentSize = 1400
        val blockSize:Short = 64
        val numParity:Short = 16

        // senderSession.setGrttProbingMode(NormProbingMode.NORM_PROBE_ACTIVE);
        // senderSession.setReportInterval(10.0);
        senderSession.setRxPortReuse(true)
        senderSession.setMulticastInterface("lo")

        senderSession.startSender(randInt, senderBufferSpace, segmentSize, blockSize, numParity)

        logger.fine("-- Creating new receiverSession nodeId ${receiverSession.localNodeId}")
        val receiverBufferSpace:Long = 256 * 256;
        // receiverSession.setRxCacheLimit(rxCacheMax)
        receiverSession.setRxPortReuse(true)
        receiverSession.setMulticastInterface("lo")
        receiverSession.startReceiver(receiverBufferSpace)
    }

    @Test
    fun testUpdateMUCOccupants() = runBlocking {
        val iface = "lo"

        mockSession = normInstance.createSession(
            "225.1.2.5", 10000,
            NormNode.NORM_NODE_ANY
        )
        startSenderReceiverSessions(mockSession!!, mockSession!!)

        // val normNode = NORMNode(mutableMapOf(), 10, 0, mutableMapOf(), mutableSetOf(), mockSession, true)
        val normPresenceTransport = NormPresenceTransport(
            mutableMapOf(iface to mockSession!!),
            mutableMapOf(iface to mockSession!!),
            InetAddress.getLocalHost(), 10000,
            10,
            transportPacketProcessor, sdListener, false, this,
            10, 15
        )
        println("Created $normPresenceTransport")
        // val normPresenceTransport = normPresenceTransport!!

        val makeAssertions = {
                room: JID, src: JID, srcOccJID: JID ->
                assertTrue(
                    normPresenceTransport.mucRooms.containsKey(room),
                    "$room not in ${normPresenceTransport.mucRooms}"
                )
                assertTrue(normPresenceTransport.mucRooms[room]!!.occupants.containsKey(src))
                assertTrue(
                    normPresenceTransport.mucRooms[room]!!.occupants[src]!!.nick == srcOccJID.resource,
                    "nick not found ${srcOccJID.resource}, occupants: ${normPresenceTransport.mucRooms[room]!!.occupants[src]}"
                )
            }

        // Updating mucOccupants
        val mucPresence = Presence()
        mucPresence.from = user1
        mucPresence.to = roomUser1
        mucPresence.addChildElement("x", "http://jabber.org/protocol/muc")
        val transportMetadata = TransportMetadata(
            0L, TransportType.PresenceTransport,
            TransportSubType.MUCPresence, 0L
        )

        normPresenceTransport.handleTransportData(1L, mockSession!!, transportMetadata, mucPresence.toXML(),
            mucPresence.toXML().toByteArray(Charsets.UTF_8))
        println("Added $user1 = $roomUser1Str : ${normPresenceTransport.mucRooms}\n\n")
        makeAssertions(room, user1, roomUser1)

        // -- Adding user2, removing user1
        val unavailPresence = mucPresence.createCopy()
        assertTrue(unavailPresence.deleteExtension("x", "http://jabber.org/protocol/muc"))
        unavailPresence.type = Presence.Type.unavailable
        normPresenceTransport.handleTransportData(1L, mockSession!!, transportMetadata,
            unavailPresence.toXML(),
            unavailPresence.toXML().toByteArray(Charsets.UTF_8))

        mucPresence.from = user2
        mucPresence.to = roomUser2
        normPresenceTransport.handleTransportData(1L, mockSession!!, transportMetadata, mucPresence.toXML(),
            mucPresence.toXML().toByteArray(Charsets.UTF_8))
        // val replacedMUCOccupants = mapOf(
        //     Pair(
        //         user2,
        //         mutableSetOf(roomUser2Str)
        //     )
        // )
        // normPresenceTransport.updateMUCOccupants(1, replacedMUCOccupants)
        println(
            "Added $user2 = $roomUser2Str, removed $user1 : " +
                    "${normPresenceTransport.mucRooms}\n\n"
        )

        assertTrue(!normPresenceTransport.mucRooms[room]!!.occupants.containsKey(user1))
        makeAssertions(room, user2, roomUser2)

        // -- Removing all users
        assertTrue(mucPresence.deleteExtension("x", "http://jabber.org/protocol/muc"))
        mucPresence.type = Presence.Type.unavailable
        normPresenceTransport.handleTransportData(1L, mockSession!!, transportMetadata, mucPresence.toXML(),
            mucPresence.toXML().toByteArray(Charsets.UTF_8))

        mucPresence.from = user1
        mucPresence.to = roomUser1
        normPresenceTransport.handleTransportData(1L, mockSession!!, transportMetadata, mucPresence.toXML(),
            mucPresence.toXML().toByteArray(Charsets.UTF_8))
        // val removedMUCOccupants = mapOf<JID, MutableSet<String>>()
        // normPresenceTransport.updateMUCOccupants(1, removedMUCOccupants)
        println(
            "removed all from $room: ${normPresenceTransport.mucRooms}\n" +
                    "\n"
        )
        assertTrue(normPresenceTransport.mucRooms[room]!!.occupants.isEmpty())
        //
        // // -- re-adding user1
        // normPresenceTransport.updateMUCOccupants(1, remoteMUCOccupants)
        // println("Added $user1 = $roomUser1Str : ${normPresenceTransport.mucRooms} \n\n")
        // makeAssertions(room, user1, roomUser1)
        //
        // // -- no change (user1 still in room)
        // normPresenceTransport.updateMUCOccupants(1, remoteMUCOccupants)
        // println("Stays the same : ${normPresenceTransport.mucRooms} \n\n")
        // makeAssertions(room, user1, roomUser1)
        //
        // // -- Adding user2 to the room
        // val bothMUCOccupants = mapOf(
        //     Pair(user2, mutableSetOf(roomUser2Str)),
        //     Pair(user1, mutableSetOf(roomUser1Str))
        // )
        // normPresenceTransport.updateMUCOccupants(1, bothMUCOccupants)
        // println(
        //     "Added $user2 = $roomUser2Str, with $user1 = $roomUser1Str : " +
        //             "${normPresenceTransport.mucRooms} \n\n"
        // )
        // makeAssertions(room, user1, roomUser1)
        // makeAssertions(room, user2, roomUser2)

        val redirectTransportMetadata = TransportMetadata(
            0L, TransportType.PresenceTransportRedirect,
            TransportSubType.Redirect, origSenderId = 100L
        )


        println("Closing $normPresenceTransport")
        normPresenceTransport.close()
        println("Closed")

    }

    @Test
    fun testRemoveMUCOccupants() = runBlocking {
        // val user1Rooms = mutableSetOf(
        //     roomUser1Str, "room2@conference.proxy/user1", "room3@conference.proxy/user1nick"
        // )
        //
        // val user1NotInRoom1 = mutableSetOf(
        //     "room2@conference.proxy/user1", "room3@conference.proxy/user1nick"
        // )
        //
        // val user2Rooms = mutableSetOf(
        //     roomUser2Str, "room2@conference.proxy/user2nick"
        // )
        //
        // val remoteMUCOccupants: Map<JID, MutableSet<String>> = mapOf(
        //     Pair(user1, user1NotInRoom1), Pair(user2, user2Rooms)
        // )
        //
        // val user1RoomsExisting = mutableSetOf(
        //     "room@conference.proxy/user1", "room3@conference.proxy/user1nick"
        // )
        //
        // val existingMucRooms: Map<JID, MutableSet<String>> = mapOf(
        //     Pair(user1, user1RoomsExisting), Pair(user2, user2Rooms)
        // )
        mockSession = normInstance.createSession(
            "225.1.2.5", 10000,
            NormNode.NORM_NODE_ANY
        )

        startSenderReceiverSessions(mockSession!!, mockSession!!)

        val iface = "lo"
        val normNode = NORMNode(mutableMapOf(), 10, 0, mutableMapOf(), mutableSetOf(), mockSession, true)
        val normPresenceTransport = NormPresenceTransport(
            mutableMapOf(iface to mockSession!!),
            mutableMapOf(iface to mockSession!!),
            InetAddress.getLocalHost(), 10000,
            10,
            transportPacketProcessor, sdListener, false, this,
            10, 15
        )

        // val normPresenceTransport = normPresenceTransport!!

        normPresenceTransport.mucRooms[room] = NormPresenceTransport.NORMRoom(
            room, mutableMapOf(
                Pair(
                    user1, NormPresenceTransport.MUCOccupant(
                        roomUser1, roomUser1.resource,
                        2, Presence()
                    )
                )
                , Pair(
                    user2, NormPresenceTransport.MUCOccupant(
                        roomUser2, roomUser2.resource,
                        2, Presence()
                    )
                )
            ),
            null
        )

        // clientManager = ClientManager()

        assertTrue(
            normPresenceTransport.mucRooms[room]!!.occupants.containsKey(user1),
            "$user1 NOT in $room before"
        )
        assertTrue(
            normPresenceTransport.mucRooms[room]!!.occupants.containsKey(user2),
            "$user2 NOT in $room before"
        )

        // normPresenceTransport.removeOccupants(remoteMUCOccupants)
        //
        // assertFalse(normPresenceTransport.mucRooms[room]!!.occupants.containsKey(user1),
        //     "$user1 IS IN $room AFTER")
        // assertTrue(normPresenceTransport.mucRooms[room]!!.occupants.containsKey(user2),
        //     "$user2 NOT IN $room AFTER")

        println("Closing $normPresenceTransport")
        // mockSession is closed in the test case
        normPresenceTransport.close()
        println("Closed")
    }
}