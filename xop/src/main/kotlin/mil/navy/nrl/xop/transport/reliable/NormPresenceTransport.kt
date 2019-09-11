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
import org.dom4j.DocumentException
import org.json.JSONObject
import org.xmpp.packet.JID
import org.xmpp.packet.Presence
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level

/**
 * NormTransport for sending Presences
 */
internal class NormPresenceTransport(
    senderNormSessions: MutableMap<String, NormSession>,
    receivingNormSessions: MutableMap<String, NormSession>,
    address: InetAddress, // This is only used for getAddressStr
    port: Int,
    nodeId: Long,
    packetProcessor: TransportPacketProcessor,
    private val sdListener: SDListener,
    compression: Boolean,
    private val normServiceCoroutineScope: CoroutineScope,
    private val nodePresenceInterval: Long,
    private val nodeTimeoutInterval: Int,
    grttMultiplier: Int = XOP.TRANSPORT.NORM.GRTT_MULTIPLIER
) : NormTransport(TransportType.PresenceTransport, TransportSubType.Presence, senderNormSessions, receivingNormSessions,
    address, port, packetProcessor, compression, grttMultiplier) {
    private val logger = LogUtils.getLogger(NormPresenceTransport::class.java.name)

    private val presenceLoggerName = "${NormPresenceTransport::class.java.name}.PRESENCE"
    private val presenceLogger = LogUtils.getLogger(
        presenceLoggerName,
        XopLogFormatter("PRESENCE")
    )
    private val presenceRcvrLoggerName = "${NormPresenceTransport::class.java.name}.PRESENCERCVR"
    private val presenceRcvrLogger = LogUtils.getLogger(
        presenceRcvrLoggerName,
        XopLogFormatter("PRESENCERCVR")
    )
    private val presenceSndrLoggerName = "${NormPresenceTransport::class.java.name}.PRESENCESNDR"
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

    private val thisNode = NORMNode(mutableMapOf(), nodeId, 0, mutableMapOf(), mutableSetOf(), null)

    // <room name, RoomDetails>
    val mucRooms = mutableMapOf<JID, NORMRoom>()

    // TODO 20181216 move to NormPresenceTransport for transport bookkeeping
    private val remoteNodeCounters: ConcurrentMap<Long, AtomicInteger> = ConcurrentHashMap()
    private val remoteNodes: ConcurrentMap<Long, NORMNode> = ConcurrentHashMap()

    // private val monitorRemoteNodeCountersMap: MutableMap<Long, Job> = mutableMapOf()
    // private val broadcastJobsMap: MutableMap<NormSession,Job> = mutableMapOf()
    // private val grttEstimates: MutableMap<Long, AtomicDouble> = mutableMapOf()

    private val remoteJIDLastPresence: ConcurrentMap<JID, Presence> = ConcurrentHashMap()

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
            // Make a copy of the presence object stripping it of the ns="jabber:client"
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

        presenceLogger.info("Starting Receiver session")

        presenceLogger.fine("FINE presenceLogger. sendingNormSessions $senderNormSessions")
        // for( (_, normSession) in senderNormSessions ) {
        //     presenceLogger.info("Creating Sender normSession $normSession")
        //     normSession.setTrackingStatus(NormTrackingStatus.TRACK_ALL)
        //     normSession.setTxCacheBounds(10, 10, 20)
        //
        //     logger.finer("Setting initial grttEstimate to ${nodePresenceInterval.toDouble() / 1000} on session ")
        //     normSession.grttEstimate = nodePresenceInterval.toDouble() / 1000
        //
        //     startNormSession(normSession)
        //
        //     Start coroutine for periodically broadcasting nodeId, clientlists, joined mucrooms
        //     broadcastJobsMap[normSession] = normServiceCoroutineScope.launch {
        //         broadcastRoutine(normSession)
        //     }
        //     presenceLogger.info("Started broadcast routine for $normSession with initial interval of $nodePresenceInterval ")
        //
        //     sendPresenceProbes(null)
        // }

    }

    /** sends presence packets to the network */
    private fun sendPresencePacket(presence: Presence) {
        logger.fine("sending new Presence message from local client: ${presence.from}")
        logger.finer("presence: $presence")
        updateThisNode(thisNode, presence)
        sendPresenceData(presence, TransportSubType.Presence)
    }

    /** sends MUC presence packets to the network */
    private fun sendMUCPresencePacket(presence: Presence) {
        logger.fine("sending MUC Presence from local client: ${presence.from}")
        updateThisNodeMucOccupant(thisNode, presence)
        sendPresenceData(presence, TransportSubType.MUCPresence)
    }

    private fun advertiseMucRoom(roomJid: JID, description: String, domain: String) {
        thisNode.mucRooms.add(roomJid.toString())
    }

    private fun sendPresenceData(presence: Presence, transportSubType: TransportSubType) {
        val dataBytes = presence.toXML().toByteArray(Charsets.UTF_8)
        var transport: NormTransport = this
        if (presence.to != null && presence.to.asBareJID() in mucRooms)
            transport = mucRooms[presence.to.asBareJID()]!!.normTransport!!
        logger.finer("sending as bytes in UTF_8 [[${presence.toXML()}]]")
        for((_, normSession) in sendingNormSessions) {
            transport.sendData(dataBytes, transportType, transportSubType, normSession, normSession.localNodeId)
        }
    }

    override fun toString(): String {
        return "Transport:${NormPresenceTransport::class.java.name}"
    }

    /** adds the transport to the room Object */
    fun addTransportForRoom(roomJID: JID, transport: NormTransport) {
        mucRooms[roomJID] = NORMRoom(roomJID, mutableMapOf(), transport)
    }

    override fun close() {
        logger.info("closing NormPresenceTransport")

        // for (monitorJob in monitorRemoteNodeCountersMap.values){
        //     logger.info("Canceling monitor $monitorJob")
        //     monitorJob.cancel()
        // }
        // monitorRemoteNodeCountersMap.clear()

        // for (broadcastJob in broadcastJobsMap.values) {
        //     logger.info("canceling broadcast job")
        //     broadcastJob.cancel()
        // }
        // broadcastJobsMap.clear()

        // Closes sending and receiving norm sessions
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

    internal fun updateGrtt(normSession: NormSession, nodeId: Long, grttEstimate: Double) {
        // if (grttEstimate <= (nodePresenceInterval/1000.0) ) {
        //     logger.fine("grttEstimate is $grttEstimate < ${nodePresenceInterval/1000.0}, not updating GRTT")
        //     return
        // }
        // val oldVal = grttEstimates[nodeId]?.getAndSet(grttEstimate) ?: AtomicDouble(grttEstimate)
        // logger.finer("updated grtt for $normSession from $oldVal to $grttEstimate")
    }

    /**
     * Overrides NormTransport.handleTransportData()
     */
    override fun handleTransportData(senderNodeId: Long, receivingNormSession: NormSession,
                                     transportMetadata: TransportMetadata, msgString: String, dataBytes: ByteArray)
    {
        logger.fine("Received data on ${ifacesForReceivingSessions[receivingNormSession]} form session $receivingNormSession " +
                "of subtype: ${transportMetadata.transportSubType}")
        if (isRedirect(transportMetadata) && isDuplicate(dataBytes)) {
            logger.finer("msg is redirect and duplicate, ")
            return
        }
        when(transportMetadata.transportSubType) {
            TransportSubType.Initialization -> {
                val senderId = if (transportMetadata.transportType == TransportType.PresenceInit) {
                    senderNodeId
                } else {
                    transportMetadata.origSenderId
                }
                val remoteNormNode = fromJSONStr(msgString, receivingNormSession)
                handleRemoteNodeInitData(senderId, receivingNormSession, remoteNormNode)
            }
            TransportSubType.Presence ->  {
                val senderId = if (transportMetadata.transportType == TransportType.PresenceTransport) {
                    senderNodeId
                } else {
                    transportMetadata.origSenderId
                }
                handlePresenceTransportData(senderId, receivingNormSession, msgString)
            }
            TransportSubType.MUCPresence -> {
                val senderId = if (transportMetadata.transportType == TransportType.MUCPresence) {
                    senderNodeId
                } else {
                    transportMetadata.origSenderId
                }
                handleMUCPresence(senderId, receivingNormSession, msgString)
            }
            TransportSubType.JSON -> {
                val probeNORMNode = fromJSONStr(msgString, receivingNormSession)
                handlePresenceProbe(probeNORMNode.nodeId, probeNORMNode)
            }
            else -> {
                logger.warning("Unhandled Transport SubType: ${transportMetadata.transportSubType}")
            }
        }


        if (sendingNormSessions.size > 1) {
            presenceRcvrLogger.fine("Redirecting data string to other sessions, not $receivingNormSession")

            redirectData(msgString.toByteArray(Charsets.UTF_8), transportMetadata, receivingNormSession, transportMetadata.origSenderId)
        }
    }

    /**
     * Incoming NormNode data as initial synchronization data.
     */
    private fun handleRemoteNodeInitData(senderNodeId: Long, receiverSession: NormSession, remoteNormNode: NORMNode) {
        presenceRcvrLogger.fine("handling init from $senderNodeId from ${ifacesForReceivingSessions[receiverSession]}" +
                " session $receiverSession")
        if (remoteNormNode.nodeId in remoteNodes) {
            if (remoteNodes[remoteNormNode.nodeId]!!.seq < remoteNormNode.seq){
                presenceRcvrLogger.fine("Updating remoteNode ${remoteNormNode.nodeId} from $senderNodeId; seq ${remoteNormNode.seq}")
                remoteNodes[remoteNormNode.nodeId] = remoteNormNode
            } else {
                presenceRcvrLogger.fine("remoteNode from ${remoteNormNode.nodeId} from $senderNodeId already in remoteNodes")
            }
        } else {
            presenceRcvrLogger.fine("Adding new remoteNormNode from ${remoteNormNode.nodeId} from sender $senderNodeId.")
            remoteNodes[remoteNormNode.nodeId] = remoteNormNode
        }

        // notify XOP of remote nodes
        updateClients(senderNodeId, remoteNormNode)
        updateRooms(senderNodeId, remoteNormNode.mucRooms, remoteNormNode.mucOccupants)
    }

    /**
     * Looks up remoteNode from the [senderNodeId], Extracts the presence packet (a string) from the [msgString],
     * and uses sdListener to send the packet to
     * XO Proxy for sending to local clients
     */
    private fun handlePresenceTransportData(senderNodeId: Long, receiverSession: NormSession, msgString: String) {
        val remoteNode = if (senderNodeId in remoteNodes) {
            remoteNodes[senderNodeId]!!
        } else {
            remoteNodes[senderNodeId] = NORMNode(
                mutableMapOf(),
                senderNodeId, 0, mutableMapOf(),
                mutableSetOf(), receiverSession, true
            )
            remoteNodes[senderNodeId]!!
        }

        presenceRcvrLogger.finer("msgString: $msgString")
        try {
            val packet = Utils.packetFromString(msgString)
            val presence = packet as Presence
            presenceRcvrLogger.fine("Updating remote node for this presence")

            if (presence.type == Presence.Type.probe) {
                presenceRcvrLogger.fine("presence is a probe, XOP should handle")
                sdListener.clientUpdated(presence)
                return
            }

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

                // val job = monitorRemoteNodeCountersMap.remove(nodeId)
                // job?.cancel()
                presenceRcvrLogger.fine("canceled remote node monitor for $nodeId")

                sdListener.clientRemoved(presence)
            }
        } catch (e: DocumentException) {
            logger.warning("Unable to parse msgString $msgString")
            logger.log(Level.WARNING, "DocumentException: ${e.message}.", e)
        }
    }

    private fun handleProbeNORMNode(eventNodeId: Long, probeNORMNode: NORMNode, grttEstimate: Double) {
        presenceRcvrLogger.finer("current grttEstimate $grttEstimate")
        // grttEstimates[sendingNormSessions].set(grttEstimate)
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

        // NOTE no need to redirect data here, it is done in handlePresenceProbe
    }

    /**
     * Sender Id [senderNodeId] sends presence in form of [msgString] from a MUC Occupant.
     * Redirect over other sessions if multiple sendingNormSessions configured
     */
    private fun handleMUCPresence(senderNodeId: Long, senderSession: NormSession, msgString: String) {
        presenceRcvrLogger.fine("mucPresence from senderId $senderNodeId, str: $msgString")
        val packet = Utils.packetFromString(msgString)
        val presence = packet as Presence

        val clientJID = presence.from!!
        val mucOccupantJID = presence.to!!
        val roomJID = presence.to.asBareJID()!!

        val room = mucRooms[roomJID] ?: NORMRoom(roomJID, mutableMapOf(), null)
        mucRooms[roomJID] = room
        val mucOccupant = MUCOccupant(clientJID, mucOccupantJID.resource ?: "", senderNodeId, presence)
        if (presence.isAvailable) {
            // add/update the existing presence for this occupant
            if (clientJID in room.occupants) {
                presenceRcvrLogger.fine("$clientJID already an occupant of room. MUC Occupant: $presence. Do nothing")
                // TODO 2019-06-12 This can be triggered in lossy environments when presence offline/online messages are received out of order
                // sdListener.mucOccupantUpdated(presence)
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
     * Called from RX_CMD_NEW events
     */
    private fun handlePresenceProbe(remoteNodeId: Long, probeNORMNode: NORMNode) {
        logger.fine("Handle remoteNodeId $remoteNodeId, presenceProbe: $probeNORMNode")
        updateClients(remoteNodeId, probeNORMNode)

        // called from handlePresenceProbe() to process new/removed MUCOccupants and new rooms
        updateRooms(remoteNodeId, probeNORMNode.mucRooms, probeNORMNode.mucOccupants)
    }

    /**
     * updates NormPresenceTransport and signals if sdListener needs to add/remove discovered clients
     */
    private fun updateClients(remoteNodeId: Long, probeNORMNode: NORMNode) {
        val sendPresenceProbes = fun(jid: JID) {
            presenceRcvrLogger.finer("remote node $remoteNodeId, sending probe request to $jid from ${thisNode.jidMap}")
            // send a PresenceProbe for each locally connected clients
            sendPresenceProbes(jid)
            logger.fine("Adding client $jid")
            val presence = Presence()
            presence.from = jid
            sdListener.clientDiscovered(presence)
        }

        val iterateOverJidMap = fun (currentRemoteNORMNode: NORMNode,
                                     newRemoteNORMNode: NORMNode)
        {
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
                presenceRcvrLogger.fine("$remoteNodeId now reconnected, send presence updates to connected clients: ${remoteNode.jidMap}")
                for ( probeJID in remoteNode.jidMap.keys) {
                    val presence = remoteJIDLastPresence[probeJID]?.createCopy() ?: Presence()
                    if(presence.from == null)
                        presence.from = probeJID
                    val prevStatus = presence.status ?: ""
                    presence.status = "(reconnected) $prevStatus"
                    sdListener.clientReconnected(probeJID)
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

    private fun sendPresenceProbes(toJid: JID?) {
        for (thisNodeJID in thisNode.jidMap.keys) {
            val probePresence = Presence(Presence.Type.probe)
            probePresence.to = toJid
            probePresence.from = thisNodeJID
            sendPresencePacket(probePresence)
            presenceRcvrLogger.fine("sent presence probe to $toJid from $thisNodeJID ")
        }
    }

    /** updates the rooms datastructure and notify XO of any room updates */
    private fun updateRooms(
        remoteNodeId: Long, mucRoomSet: Set<String>,
        mucOccupants: Map<JID, MutableSet<String>>
    ) {

        val updateDiscoveredRooms = fun (remoteMucRooms: Set<String>) {
            logger.fine("remote MUC Rooms: $remoteMucRooms, known mucRooms $mucRooms")
            for (roomJIDStr in remoteMucRooms) {
                val roomJID = JID(roomJIDStr)
                if (roomJID !in mucRooms.keys) {
                    mucRooms[roomJID] = NORMRoom(roomJID, mutableMapOf(), null)
                    logger.fine("Adding new room $roomJID")
                    sdListener.roomAdded(roomJID)
                }
            }
        }

        val updateMUCOccupants = fun (
            remoteNodeId: Long,
            remoteMUCOccupants: Map<JID, MutableSet<String>>
        ) {
            val removeOccupants = fun (
                remoteNodeId: Long,
                remoteMUCOccupants: Map<JID, MutableSet<String>>
            ) {
                logger.finer("Determine which discovered remoteMUCOccupants $remoteMUCOccupants to remove")
                for ((mucRoomJID, localDiscMucOccupants) in mucRooms) {
                    val probeRoomOccupants = remoteMUCOccupants.entries.flatMap {
                            (_, mucOccupantJIDs) ->
                        //logger.fine("probeRoomOccupants: mucOccupantJIDs $mucOccupantJIDs")
                        mucOccupantJIDs.filter { jidStr ->
                            JID(jidStr).asBareJID() == mucRoomJID
                        }
                    }.toSet()
                    logger.finer("$mucRoomJID removing occupants in $probeRoomOccupants")
                    logger.fine("localDiscMucOccupants occupants ${localDiscMucOccupants.occupants}")
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
                        val mucOccupant = MUCOccupant( mucOccupantJID, mucOccupantJID.resource, remoteNodeId, mucPresence)
                        mucRooms[mucOccupantJID.asBareJID()]!!.occupants[clientJID] = mucOccupant
                        sdListener.mucOccupantJoined(mucPresence)
                    }
                }
            }
        }

        presenceRcvrLogger.fine("known mucRooms: ${this.mucRooms.keys}")
        // check if new rooms
        updateDiscoveredRooms(mucRoomSet)


        // add any new muc Occupants
        presenceRcvrLogger.fine("mucOccupants: $mucOccupants")
        updateMUCOccupants(remoteNodeId, mucOccupants)

    }


    private suspend fun broadcastRoutine(normSession: NormSession) {
        // send a client update to the network with this [node] object
        val sendUpdateToNetwork = fun (normSession: NormSession, node: NORMNode) {
            val jsonObject = JSONObject(node)
            val jsonStr = jsonObject.toString()
            presenceSndrLogger.finer("send to network: $jsonObject jsonStr encoding ")
            val nodeData = jsonStr.toByteArray(Charsets.UTF_8)

            // Send XOP Presence Node as CMDStrings
            normSession.sendCommand(nodeData, 0, nodeData.size, false)
            presenceSndrLogger.finer("sent successfully on port $port $jsonStr")
        }

        while (running) {
            presenceSndrLogger.finest("Sending periodic presence probe")
            thisNode.seq += 1
            sendUpdateToNetwork(normSession, thisNode)
            presenceSndrLogger.finer("sent update with hashcode ${thisNode.hashCode()}, thisNode $thisNode  ")
            // val delayVal = (normSession.grttEstimate * 1000 * grttMultiplier).roundToLong()
            // val est = grttEstimates.getOrPut(nodeId) { AtomicDouble(nodePresenceInterval.toDouble()) }.getAndSet(normSession.grttEstimate)

            val delayVal = nodePresenceInterval
            presenceSndrLogger.finer("sleeping for $delayVal rounded")
            delay(delayVal)
        }
        presenceSndrLogger.fine("Exiting broadcast coroutine")
    }

    /**
     * Add a new remote node and start a monitor to determine connectivity.
     */
    override fun addRemoteNode(receiverSession: NormSession, nodeId: Long): NORMNode {
        // (grttEstimates.getOrPut(nodeId) { AtomicDouble(normSession.grttEstimate) }).set(normSession.grttEstimate)
        if (nodeId !in remoteNodes) {
            remoteNodeCounters[nodeId] = AtomicInteger(nodeTimeoutInterval)
            remoteNodes[nodeId] =
                NORMNode(mutableMapOf(), nodeId, 0, mutableMapOf(), mutableSetOf(), receiverSession)
            // monitorRemoteNodeCountersMap[nodeId] =
            //     normServiceCoroutineScope.launch { monitorRemoteNodeCounters(normSession, nodeId) }
            // logger.fine("Added remote node $nodeId for monitoring")

            logger.fine("Added new remoteNode for this sender. Responding with {$thisNode} information on session $receiverSession")
            val jsonObject = JSONObject(thisNode)
            val jsonStr = jsonObject.toString()
            presenceSndrLogger.finer("send to network: $jsonObject jsonStr encoding ")
            val nodeData = jsonStr.toByteArray(Charsets.UTF_8)

            // for ((iface, senderSession) in sendingNormSessions.filterKeys {
            //         sendIface -> ifacesForReceivingSessions[receiverSession] != sendIface } )
            // {
            for ((iface, senderSession) in sendingNormSessions) {
                logger.fine("sending normNode to $iface session $senderSession")
                sendData(nodeData, TransportType.PresenceInit, TransportSubType.Initialization, senderSession, senderSession.localNodeId)
            }
        } else {
            logger.fine("Node already discovered")
        }
        return remoteNodes[nodeId]!!
    }

    /**
     * periodically check all remoteNodeCounters to ensure
     */
    private suspend fun monitorRemoteNodeCounters(normSession: NormSession, nodeId: Long) {
        presenceLogger.fine("Running remote node monitor for $nodeId")

        val nodeDisconnected = fun(remoteNode: NORMNode) = runBlocking {
            presenceLogger.fine("detected node $nodeId is disconnected, signal XOP with updated presence")

            // grttEstimates[nodeId].let { logger.fine("current est ${it?.get() ?: "not initialized"}") }
            // val est = grttEstimates.getOrPut(nodeId) { AtomicDouble(nodePresenceInterval.toDouble()) }.getAndSet(normSession.grttEstimate)
            // presenceLogger.fine("grttEstimate $est")
            // val delayTime: Long = (est * 1000 * grttMultiplier).roundToLong()
            val delayTime = nodePresenceInterval
            val ct = remoteNodeCounters[nodeId]!!.get()
            if (ct < 0) {
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
            // grttEstimates[nodeId].let { logger.fine("current est ${it?.get() ?: "not initialized"}") }
            // val est = grttEstimates.getOrPut(nodeId) { AtomicDouble(nodePresenceInterval.toDouble()) }.getAndSet(normSession.grttEstimate)
            // presenceLogger.fine("grttEstimate $est")
            // val delayTime: Long = (est * 1000 * grttMultiplier).roundToLong()
            val delayTime = nodePresenceInterval
            presenceLogger.fine("delay for $delayTime")
            delay(delayTime)

            if (nodeId in remoteNodeCounters) {
                remoteNodeCounters[nodeId]?.let {
                    val ct = it.decrementAndGet()
                    presenceLogger.fine("updating counters for $nodeId ct $ct, threshold $nodeTimeoutInterval")
                    if (ct <= 0) {
                        presenceLogger.fine("$nodeId has reached threshold ct $ct")
                        if ( nodeId in remoteNodes && remoteNodes[nodeId]!!.connected) {
                            presenceLogger.fine("remoteNode with $nodeId detected as not connected setting to false")
                            remoteNodes[nodeId]!!.connected = false
                            nodeDisconnected(remoteNodes[nodeId]!!)
                        }
                    }
                    presenceLogger.fine("updated counters ct ${remoteNodeCounters[nodeId]!!.get()}")
                }
            } else {
                presenceLogger.fine("no remoteNodeCounter for $nodeId")
            }
        }
        presenceLogger.fine("Exiting remoteNodeCounter for remote node $nodeId")
    }
}
