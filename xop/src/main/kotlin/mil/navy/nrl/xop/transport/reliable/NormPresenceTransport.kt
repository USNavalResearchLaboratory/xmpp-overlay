package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.net.SDListener
import edu.drexel.xop.net.SDManager
import edu.drexel.xop.packet.TransportPacketProcessor
import edu.drexel.xop.room.Room
import edu.drexel.xop.util.Utils
import edu.drexel.xop.util.XOP
import edu.drexel.xop.util.logger.LogUtils
import edu.drexel.xop.util.logger.XopLogFormatter
import kotlinx.coroutines.*
import mil.navy.nrl.norm.NormSession
import mil.navy.nrl.norm.enums.NormTrackingStatus
import org.json.JSONObject
import org.xmpp.packet.JID
import org.xmpp.packet.Presence
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/**
 * NormTransport for sending Presences
 */
internal class NormPresenceTransport(
    normSession: NormSession,
    address: InetAddress, // This is only used for getAddressStr
    port: Int,
    private val thisNode: NORMNode,
    packetProcessor: TransportPacketProcessor,
    private val sdListener: SDListener,
    compression: Boolean,
    private val normServiceCoroutineScope: CoroutineScope,
    private val nodePresenceInterval: Long,
    private val nodeTimeoutInterval: Int,
    private val grttMultiplier: Int = XOP.TRANSPORT.NORM.GRTT_MULTIPLIER
) : NormTransport(normSession, address, port, packetProcessor, compression) {
    companion object {
        @JvmStatic
        val logger = LogUtils.getLogger(NormPresenceTransport::class.java.name)!!
    }

    private val presenceLoggerName = "${NormPresenceTransport::class.java.name}.PRESENCE"
    private val presenceRcvrLoggerName = "${NormPresenceTransport::class.java.name}.PRESENCERCVR"
    private val presenceSndrLoggerName = "${NormPresenceTransport::class.java.name}.PRESENCESNDR"

    private val presenceLogger = LogUtils.getLogger(
        presenceLoggerName,
        XopLogFormatter("PRESENCE")
    )
    private val presenceRcvrLogger = LogUtils.getLogger(
        presenceRcvrLoggerName,
        XopLogFormatter("PRESENCERCVR")
    )
    private val presenceSndrLogger = LogUtils.getLogger(
        presenceSndrLoggerName,
        XopLogFormatter("PRESENCESNDR")
    )


    data class MUCOccupant(
        val occupantJID: JID,
        val nick: String,
        val remoteNodeId: Long,
        val presence: Presence
    )

    data class NORMRoom(
        val roomName: JID,
        val occupants: MutableMap<JID, MUCOccupant>, // clientJID to MUCOccupant
        var normTransport: NormTransport?
    )

    // <room name, RoomDetails>
    val mucRooms = mutableMapOf<JID, NORMRoom>()
    // TODO 20181216 move to NormPresenceTransport for transport bookkeeping
    private val remoteNodeCounters: ConcurrentMap<Long, AtomicInteger> = ConcurrentHashMap()
    private val remoteNodes: ConcurrentMap<Long, NORMNode> = ConcurrentHashMap()

    private var monitorRemoteNodeCountersMap: MutableMap<Long, Job> = mutableMapOf()
    private val remoteJIDLastPresence: ConcurrentMap<JID, Presence> = ConcurrentHashMap()

    private val broadcastJob: Job
    private val nodeSeqNumbers = mutableMapOf<Long, AtomicLong>()

    val sdManager = object: SDManager {
        private var sdListener: SDListener? = null

        override fun addGateway(address: InetAddress?, domain: JID?) {
            TODO("not implemented")
        }

        override fun removeGateway(domain: JID?) {
            TODO("not implemented")
        }

        override fun advertiseClient(presence: Presence) {
            // send the presence message over the transport
            sendPresencePacket(presence)
        }

        override fun removeClient(presence: Presence) {
            // send the presence message over the transport
            sendPresencePacket(presence)
        }

        override fun updateClientStatus(presence: Presence) {
            // send the presence message over the transport
            sendPresencePacket(presence)
        }

        override fun advertiseMucOccupant(presence: Presence) {
            logger.fine("advertising MUC Occupant ${presence.to} from ${presence.from}")
            // send the presence message over the transport
            sendMUCPresencePacket(presence)
        }

        override fun removeMucOccupant(presence: Presence) {
            //updateThisNode(thisNode, presence)
            // send the presence message over the transport
            sendMUCPresencePacket(presence)
        }

        override fun updateMucOccupantStatus(presence: Presence) {
            // send the presence message over the transport
            sendMUCPresencePacket(presence)
        }

        override fun addSDListener(listener: SDListener) {
            logger.info("Adding sdListener: $listener")
            sdListener = listener
        }

        override fun advertiseMucRoom(room: Room) {
            logger.info("Advertising Room: $room.")
            advertiseMucRoom(room.roomJid, room.description, room.domain)
        }

        override fun removeMucRoom(room: Room) {
            logger.info("do nothing, once the room is there, then it's always there")
        }

        override fun start() {
            logger.info("Starting NORMSDManager")
        }

        override fun close() {
            logger.info("Closing NORMSDManager")
        }
    }


    init {
        presenceLogger.info("presenceLogger configured. level: ${presenceLogger.level}")
        presenceLogger.fine("FINE presenceLogger")

        normSession.setTrackingStatus(NormTrackingStatus.TRACK_ALL)
        // normSession.setTxCacheBounds(10, 10, 20)

        startNormSession(normSession)
        // Start coroutine for periodically broadcasting nodeId, clientlists, joined mucrooms

        broadcastJob = normServiceCoroutineScope.launch {
            broadcastRoutine()
        }
    }
    //
    //    fun newClient(node: NORMNode) {
    //        node.nodeId
    //        val jsonObject = JSONObject(node)
    //        val jsonStr = jsonObject.toString()
    //        val nodeData = jsonStr.toByteArray()
    //
    //        val ret = sendData(nodeData, TransportType.PresenceTransport)
    //        normSession.setWatermark(ret)
    //        //normSession.sendCommand(nodeData, 0, nodeData.size, false)
    //        logger.fine("send data$jsonStr")
    //
    //        sendUpdateToNetwork(node)
    //    }

    internal fun updateDiscoveredRooms(remoteMucRooms: Set<String>) {
        logger.fine("remote MUC Rooms: $remoteMucRooms, known mucRooms $mucRooms")
        for (roomJIDStr in remoteMucRooms) {
            val roomJID = JID(roomJIDStr)
            if (roomJID !in mucRooms.keys) {
                mucRooms[roomJID] = NormPresenceTransport.NORMRoom(
                    roomJID,
                    mutableMapOf(), null
                )
                logger.fine("Adding new room $roomJID")
                sdListener.roomAdded(roomJID)
            }
        }
    }

    internal fun updateMUCOccupants(
        remoteNodeId: Long,
        remoteMUCOccupants: Map<JID, MutableSet<String>>
    ) {
        // remove occupants that have left
        removeOccupants(remoteNodeId, remoteMUCOccupants)

        logger.fine("remoteMUCOccupants $remoteMUCOccupants")
        for ((clientJID, mucOccupantJIDs) in remoteMUCOccupants) {
            logger.fine("in mucOccupants loop: $clientJID, $mucOccupantJIDs")
            for (mucOccupantJIDStr in mucOccupantJIDs) {
                val mucOccupantJID = JID(mucOccupantJIDStr)
                // assume that room exists
                logger.fine("mucRooms[${mucOccupantJID.asBareJID()}]:  [[${mucRooms[mucOccupantJID.asBareJID()]}]]")
                if (mucRooms[mucOccupantJID.asBareJID()] != null &&
                    clientJID !in mucRooms[mucOccupantJID.asBareJID()]!!.occupants
                ) {
                    val mucPresence = Presence()
                    mucPresence.from = clientJID
                    mucPresence.to = mucOccupantJID
                    mucPresence.addChildElement("x", "http://jabber.org/protocol/muc")
                    val mucOccupant = NormPresenceTransport.MUCOccupant(
                        mucOccupantJID, mucOccupantJID.resource, remoteNodeId, mucPresence
                    )
                    mucRooms[mucOccupantJID.asBareJID()]!!.occupants[clientJID] = mucOccupant
                    sdListener.mucOccupantJoined(mucPresence)
                }
            }
        }
    }

    private fun removeOccupants(
        remoteNodeId: Long,
        remoteMUCOccupants: Map<JID, MutableSet<String>>
    ) {
        logger.finer("remoteMUCOccupants $remoteMUCOccupants")
        for ((mucRoomJID, localDiscMucOccupants) in mucRooms) {
            val probeRoomOccupants = remoteMUCOccupants.entries.flatMap {
                    (_, mucOccupantJIDs) ->
                    //logger.fine("probeRoomOccupants: mucOccupantJIDs $mucOccupantJIDs")
                        mucOccupantJIDs.filter { jidStr ->
                        JID(jidStr).asBareJID() == mucRoomJID
                }
            }.toSet()
            logger.finer("$mucRoomJID removing occupants in $probeRoomOccupants")
            logger.fine("occupants ${localDiscMucOccupants.occupants}")
            val toRemove = mutableSetOf<JID>()
            for ((clientJID, mucOccupantObj) in localDiscMucOccupants.occupants) {
                if (mucOccupantObj.remoteNodeId == remoteNodeId
                    && mucOccupantObj.occupantJID.toString() !in probeRoomOccupants
                ) {
                    val mucPresence = Presence(Presence.Type.unavailable)
                    mucPresence.to = mucOccupantObj.occupantJID
                    mucPresence.from = clientJID
                    toRemove.add(clientJID)
                    sdListener.mucOccupantExited(mucPresence)
                }
            }

            logger.fine("REMOVING JIDS from localDiscMucOccupants.occupants $toRemove")
            for (clientJID in toRemove) {
                localDiscMucOccupants.occupants.remove(clientJID)
            }

            logger.finer("occupants ${localDiscMucOccupants.occupants}")
        }

        logger.finer("leftover mucRooms: $mucRooms")
    }

    /** sends presence packets to the network */
    internal fun sendPresencePacket(presence: Presence) {
        logger.fine("sending new Presence message from local client: ${presence.from}")
        logger.finer("presence: ${presence}")
        updateThisNode(thisNode, presence)
        sendPresenceData(presence, TransportType.PresenceTransport)
    }

    /** sends MUC presence packets to the network */
    internal fun sendMUCPresencePacket(presence: Presence) {
        logger.fine("sending MUC Presence from local client: ${presence.from}")
        updateThisNodeMucOccupant(thisNode, presence)
        sendPresenceData(presence, TransportType.MUCPresence)
    }

    internal fun advertiseMucRoom(roomJid: JID, description: String, domain: String) {
        thisNode.mucRooms.add(roomJid.toString())
    }

    private fun sendPresenceData(presence: Presence, transportType: NormTransport.TransportType) {
        val dataBytes = presence.toXML().toByteArray(Charsets.UTF_8)
        var transport: NormTransport = this
        if (presence.to != null && presence.to.asBareJID() in mucRooms)
            transport = mucRooms[presence.to.asBareJID()]!!.normTransport!!
        logger.finer("sending as bytes in UTF_8 [[${presence.toXML()}]]")
        transport.sendData(dataBytes, transportType)
    }

    /**
     * send a client update to the network with this [node] object
     */
    private fun sendUpdateToNetwork(node: NORMNode) {
        val jsonObject = JSONObject(node)
        val jsonStr = jsonObject.toString()
        logger.finer("send to network: $jsonObject jsonStr encoding ")
        val nodeData = jsonStr.toByteArray()

        // Send XOP Presence Node as CMDStrings
        val ret = normSession.sendCommand(nodeData, 0, nodeData.size, false)

        //val ret = sendData(nodeData, TransportType.PresenceTransport)
        //normSession.setWatermark(ret)

        logger.finer("sent successfully $ret on port $port $jsonStr")
    }

    override fun toString(): String {
        return "Transport:${NormPresenceTransport::class.java.name}"
    }

    /** adds the transport to the room Object */
    fun addTransportForRoom(roomJID: JID, transport: NormTransport) {
        mucRooms[roomJID] = NormPresenceTransport.NORMRoom(
            roomJID, mutableMapOf(), transport
        )
    }

    internal fun addRemoteNode(nodeId: Long): NORMNode {
        if (nodeId !in remoteNodes) {
            remoteNodeCounters[nodeId] = AtomicInteger(0)
            remoteNodes[nodeId] =
                NORMNode(mutableMapOf(), nodeId, 0, mutableMapOf(), mutableSetOf())
            monitorRemoteNodeCountersMap[nodeId] = normServiceCoroutineScope.launch { monitorRemoteNodeCounters(nodeId) }
        } else {
            logger.fine("node already discovered")
        }
        return remoteNodes[nodeId]!!
    }


    private suspend fun broadcastRoutine() {
        while (running) {
            presenceSndrLogger.finest("Sending periodic presence probe")
            thisNode.seq += 1
            sendUpdateToNetwork(thisNode)
            presenceSndrLogger.fine("sending update of hashcode ${thisNode.hashCode()} thisNode $thisNode  ")
            presenceSndrLogger.finer("sleeping for ${normSession.grttEstimate * 1000 * grttMultiplier} rounded")
            delay((normSession.grttEstimate * 1000 * grttMultiplier).roundToLong())
        }
        presenceSndrLogger.fine("Exiting broadcast coroutine")
    }

    override fun close() {
        logger.info("canceling broadcast job")
        broadcastJob.cancel()
        super.close()
    }

    /**
     * NormTransport calls this method to update the instance [node] with [presence] information
     */
    private fun updateThisNode(node: NORMNode, presence: Presence) {
        // TODO probably need to move
        val from = presence.from

        when(presence.type) {
            Presence.Type.unavailable -> {
                logger.fine("unavailable presence, remove the JID")
                val a = node.jidMap.remove(from)
                logger.finer("node jid $from = $a removed. jidMap: ${node.jidMap} ${node.hashCode()} $node")
            }
            Presence.Type.probe -> {
                logger.fine("not updating md5bytes since presence is probe")
            }
            else -> {
                val md5bytes = Utils.Md5Base64(presence.toXML())
                logger.fine("md5bytes $md5bytes presence: {{$presence}}")
                node.jidMap[from] = md5bytes
                logger.fine("updated node ${node.nodeId}. {$node} hashcode: ${node.jidMap[from]}")
            }
        }
    }

    private fun updateThisNodeMucOccupant(node: NORMNode, presence: Presence) {
        if (presence.isAvailable) {
            if (presence.from !in node.mucOccupants)
                node.mucOccupants[presence.from] = mutableSetOf(presence.to.toString())
            else
                node.mucOccupants[presence.from]!!.add(presence.to.toString())
            logger.fine("Adding mucOccupant ${presence.to} for ${presence.from} for tracking")
            logger.finer("node.mucOccupants: ${node.mucOccupants}")
        } else {
            node.mucOccupants[presence.from]?.remove(presence.to?.toString())

            // node.mucOccupants.remove(presence.from)
            logger.fine("removed mucOccupant ${presence.to} from ${node.mucOccupants[presence.from]} for tracking")
        }
    }

    /**
     * periodically check all remoteNodeCounters to ensure
     */
    private suspend fun monitorRemoteNodeCounters(nodeId: Long) {
        presenceLogger.fine("Running remote node monitor for $nodeId")

        val nodeDisconnected = fun(remoteNode: NORMNode) = runBlocking {
            presenceLogger.fine("detected node $nodeId is disconnected, signal XOP with updated presence")
            val delayTime: Long = (normSession.grttEstimate * 1000 * grttMultiplier).roundToLong()
            presenceLogger.fine("Waiting $delayTime ms for incoming presence")
            delay(delayTime)
            val ct = remoteNodeCounters[nodeId]!!.get()
            if (ct <= 0) {
                presenceLogger.fine("$nodeId has reached threshold ct $ct")
                for ((jid, _) in remoteNode.jidMap) {
                    remoteNode.jidMap[jid]
                    sdListener.clientDisconnected(jid)
                    presenceLogger.fine("Notified XOP $jid is disconnected")
                }
            } else {
                presenceLogger.fine("recieved update from nodeId: ")
            }
        }

        while (running) {
            val delayTime: Long = (normSession.grttEstimate * 1000 * grttMultiplier).roundToLong()
            presenceLogger.fine("delay for $delayTime")
            delay(delayTime)

            if (nodeId in remoteNodeCounters) {
                val ct = remoteNodeCounters[nodeId]!!.decrementAndGet()
                presenceLogger.fine("updating counters for $nodeId ct $ct, threshold $nodeTimeoutInterval")
                if (ct <= 0) {
                    presenceLogger.fine("$nodeId has reached threshold ct $ct")
                    if (remoteNodes[nodeId]?.connected == true) {
                        presenceLogger.fine("remoteNode with $nodeId detected as not connected setting to false")
                        remoteNodes[nodeId]!!.connected = false
                        coroutineScope {
                            launch {
                                nodeDisconnected(remoteNodes[nodeId]!!)
                            }
                        }
                    }
                }
                presenceLogger.fine("updated counters ct ${remoteNodeCounters[nodeId]!!.get()}")
            } else {
                presenceLogger.fine("no remoteNodeCounter for $nodeId")
            }
        }
        presenceLogger.fine("Exiting remoteNodeCounter for remote node $nodeId")
    }

    /**
     * Looks up remoteNode from the [senderNodeId], Extracts the presence packet (a string) from the [msgString],
     * and uses sdListener to send the packet to
     * XO Proxy for sending to local clients
     */
    internal fun handlePresenceTransportData(senderNodeId: Long, msgString: String) {
        val remoteNode = if (senderNodeId in remoteNodes) {
            remoteNodes[senderNodeId]!!
        } else {
            remoteNodes[senderNodeId] = NORMNode(
                mutableMapOf(),
                senderNodeId, 0, mutableMapOf(),
                mutableSetOf(), true
            )
            remoteNodes[senderNodeId]!!
        }

        presenceRcvrLogger.finer("msgString: ${msgString}")
        val packet = Utils.packetFromString(msgString)
        val presence = packet as Presence
        presenceRcvrLogger.fine("Updating remote node for this presence")

        if (presence.type == Presence.Type.probe) {
            presenceRcvrLogger.fine("presence is a probe, XOP should handle")
            sdListener.clientUpdated(presence)
        } else {
            remoteJIDLastPresence[presence.from] = presence

            if (presence.isAvailable) {
                val hashcode = Utils.Md5Base64(packet.toXML())
                presenceRcvrLogger.fine("md5bytes $hashcode presence: {{$presence}}")
                var origHashNull = true
                presenceRcvrLogger.fine("hashcode old: ${remoteNode.jidMap[presence.from]}, new: $hashcode")
                if (remoteNode.jidMap[presence.from] != null) {
                    presenceRcvrLogger.fine("Update the hashcode for remoteNode ${remoteNode.nodeId}, jid ${presence.from}")
                    origHashNull = false
                }
                remoteNode.jidMap[presence.from] = hashcode
                presenceRcvrLogger.fine("Available Presence: calling sdListener on presence object: {{{${presence.toXML()}}}}")

                if (origHashNull)
                    sdListener.clientDiscovered(presence)
                else
                    sdListener.clientUpdated(presence)
            } else { // user offline
                presenceRcvrLogger.fine("UNAVAILABLE Presence: calling sdListener on presence object: {{{${presence.toXML()}}}}")
                val a = remoteNode.jidMap.remove(presence.from)
                presenceRcvrLogger.fine("removed from jidMap key ${presence.from}=$a")

                val nodeId = remoteNode.nodeId
                // presenceRcvrLogger.fine("Removing remote sender with nodeId $nodeId")
                // normSdManager?.removeRemoteSender(nodeId)
                val job = monitorRemoteNodeCountersMap.remove(nodeId)
                job?.cancel()
                presenceRcvrLogger.fine("canceled remote node monitor for $nodeId")

                sdListener.clientRemoved(presence)
            }
        }
    }

    internal fun processProbeNORMNode(eventNodeId: Long, probeNORMNode: NORMNode) {
        val seq = probeNORMNode.seq
        val execHandlePresenceProbe = fun() {
            val ct = remoteNodeCounters[eventNodeId]!!.getAndSet(nodeTimeoutInterval)
            presenceRcvrLogger.fine("Reset remoteNodeCounters[$eventNodeId] $ct -> $nodeTimeoutInterval")
            handlePresenceProbe(eventNodeId, probeNORMNode)
        }

        if (eventNodeId in nodeSeqNumbers.keys) {
            val nodeSeq = nodeSeqNumbers[eventNodeId]!!.get()
            if (seq > nodeSeq) {
                nodeSeqNumbers[eventNodeId]!!.set(seq)
                execHandlePresenceProbe()
            } else {
                presenceRcvrLogger.fine("received probe with seq $seq <= $nodeSeq. not updating")
            }
        } else {
            presenceRcvrLogger.fine("New seq number: $seq. also handle")
            nodeSeqNumbers[eventNodeId] = AtomicLong(seq)
            execHandlePresenceProbe()
        }
    }

    /** Sender Id [senderNodeId] sends presence in form of [msgString] from a MUC Occupant */
    internal fun handleMUCPresence(senderNodeId: Long, msgString: String) {
        presenceRcvrLogger.fine("mucPresence from senderId $senderNodeId, str: $msgString")
        val packet = Utils.packetFromString(msgString)
        val presence = packet as Presence

        val clientJID = presence.from!!
        val mucOccupantJID = presence.to!!
        val roomJID = presence.to.asBareJID()!!

        var room = mucRooms[roomJID]
        if (room == null) {
            room = NormPresenceTransport.NORMRoom(
                roomJID, mutableMapOf(), null
            )
            mucRooms[roomJID] = room
        }
        val mucOccupant = NormPresenceTransport.MUCOccupant(
            clientJID,
            mucOccupantJID.resource, senderNodeId, presence
        )
        if (presence.isAvailable) {
            // add/update the existing presence for this occupant
            if (clientJID in room.occupants) {
                presenceRcvrLogger.fine("Updating MUC Occupant: $presence")
                sdListener.mucOccupantUpdated(presence)
            } else {
                presenceRcvrLogger.fine("Adding MUC Occupant: $presence")
                sdListener.mucOccupantJoined(presence)
            }
            room.occupants[clientJID] = mucOccupant
        } else {
            presenceRcvrLogger.fine("removing MUC Occupant: $presence")
            room.occupants.remove(clientJID)
            sdListener.mucOccupantExited(presence)
        }

    }

    /**
     * called from RX_CMD_NEW events, if the hash code does not match the
     */
    internal fun handlePresenceProbe(remoteNodeId: Long, probeNORMNode: NORMNode) {
        logger.fine("Handle remoteNodeId $remoteNodeId, presenceProbe: $probeNORMNode")
        updateClients(remoteNodeId, probeNORMNode)

        // called from handlePresenceProbe() to process new/removed MUCOccupants and new rooms
        updateRooms(remoteNodeId, probeNORMNode.mucRooms, probeNORMNode.mucOccupants)
    }



    /** updates NormPresenceTransport and signals if sdListener needs to add/remove discovered clients */
    private fun updateClients(remoteNodeId: Long, probeNORMNode: NORMNode) {
        val sendPresenceProbes = fun(jid: JID) {
            presenceRcvrLogger.finer("remote node $remoteNodeId, sending probe request to $jid from ${thisNode.jidMap}")
            // send a PresenceProbe for each locally connected clients
            for (thisNodeJID in thisNode.jidMap.keys) {
                val probePresence = Presence(Presence.Type.probe)
                probePresence.to = jid
                probePresence.from = thisNodeJID
                sendPresencePacket(probePresence)
                presenceRcvrLogger.fine("sent presence probe to $jid from $thisNodeJID ")
            }
            logger.fine("Adding client $jid")
            val presence = Presence()
            presence.from = jid
            sdListener.clientDiscovered(presence)
        }

        val iterateOverJidMap = fun(
            currentRemoteNORMNode: NORMNode,
            newRemoteNORMNode: NORMNode
        ) {
            presenceRcvrLogger.finer("iterating over currentRemoteNORMNode.jidMap {${currentRemoteNORMNode.jidMap}}")
            for ((jid, hashCode) in currentRemoteNORMNode.jidMap) {
                presenceRcvrLogger.fine("jid: $jid hash $hashCode, probeNORMNode $newRemoteNORMNode")
                for ((probeJID, probeHash) in newRemoteNORMNode.jidMap) {
                    presenceRcvrLogger.fine("($probeJID == $jid && $probeHash != $hashCode)")
                    if (probeJID == jid && probeHash != hashCode) {
                        presenceRcvrLogger.info(
                            "hash has changed now $hashCode, was $probeHash, " +
                                    "sending a presence probe to $jid"
                        )
                        newRemoteNORMNode.jidMap[probeJID] = hashCode
                        sendPresenceProbes(probeJID)
                    } else {

                    }
                }

                // newly discovered nodes
                for (probeJID in (currentRemoteNORMNode.jidMap.keys - newRemoteNORMNode.jidMap.keys)) {
                    presenceRcvrLogger.fine("sending presenceProbe to $probeJID")
                    newRemoteNORMNode.jidMap[probeJID] = hashCode
                    sendPresenceProbes(probeJID)
                }
            }
        }

        if (remoteNodeId !in remoteNodes.keys) {
            presenceRcvrLogger.finer("nodeId $remoteNodeId not found in ${remoteNodes.keys}")
            for ((probeJID, _) in probeNORMNode.jidMap) {
                sendPresenceProbes(probeJID)
            }
        } else {
            val remoteNode = remoteNodes[remoteNodeId]!!
            presenceRcvrLogger.fine("checking $remoteNodeId is connected ${remoteNode.connected}")
            if (!remoteNode.connected) {
                remoteNode.connected = true
                presenceRcvrLogger.fine("$remoteNodeId now reconnected, send presence updates to XOP")
                for ((probeJID, _) in remoteNode.jidMap) {
                    val presence = remoteJIDLastPresence[probeJID]!!
                    val prevStatus = presence.status ?: ""
                    presence.status = "(reconnected) $prevStatus"
                    sdListener.clientUpdated(presence)
                }
            }
            remoteNodeCounters[remoteNodeId]?.set(nodeTimeoutInterval)
            presenceRcvrLogger.fine(
                "resetting counter for remoteNode $remoteNodeId," +
                        " now: ${remoteNodeCounters[remoteNodeId]?.get()}"
            )

            presenceRcvrLogger.fine("existing remoteNode: $remoteNode, probeNORMNOde $probeNORMNode")
            presenceRcvrLogger.fine("probeNORMNode.jidMap.keys ${probeNORMNode.jidMap.keys}")
            presenceRcvrLogger.fine("remoteNode.jidMap.keys ${remoteNode.jidMap.keys}")
            if (!(probeNORMNode.jidMap.keys - remoteNode.jidMap.keys).isEmpty()) {
                presenceRcvrLogger.fine("MORE PROBENORMNODES: setsize difference")
                iterateOverJidMap(probeNORMNode, remoteNode)
            } else {
                presenceRcvrLogger.fine("REMOTENODES: setsize difference")
                iterateOverJidMap(remoteNode, probeNORMNode)
            }
        }
    }

    /** updates the rooms datastructure and notify XO of any room updates */
    private fun updateRooms(
        remoteNodeId: Long, mucRoomSet: Set<String>,
        mucOccupants: Map<JID, MutableSet<String>>
    ) {

        presenceRcvrLogger.fine("known mucRooms: ${this.mucRooms.keys}")
        // check if new rooms
        updateDiscoveredRooms(mucRoomSet)


        // add any new muc Occupants
        presenceRcvrLogger.fine("mucOccupants: $mucOccupants")
        updateMUCOccupants(remoteNodeId, mucOccupants)

    }

}