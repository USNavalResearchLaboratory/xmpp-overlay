package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.net.SDListener
import edu.drexel.xop.net.SDManager
import edu.drexel.xop.net.transport.XOPTransportService
import edu.drexel.xop.packet.TransportPacketProcessor
import edu.drexel.xop.util.MessageCompressionUtils
import edu.drexel.xop.util.XOP
import edu.drexel.xop.util.logger.LogUtils
import edu.drexel.xop.util.logger.XopLogFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mil.navy.nrl.norm.*
import mil.navy.nrl.norm.enums.NormEventType
import mil.navy.nrl.norm.enums.NormObjectType
import mil.navy.nrl.xop.util.addressing.getPort
import org.json.JSONObject
import org.xmpp.packet.JID
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*
import java.util.logging.Level
import kotlin.coroutines.CoroutineContext

/**
 * XO's Transport Service using the NORM protocol.
 */
class XopNormService(
    private val nodeId: Long,
    sendInterfaceNames: String,
    recvInterfaceNames: String,
    private val multicastGroup: InetAddress,
    portRange: String,
    private val transportPacketProcessor: TransportPacketProcessor,
    private var enableCompression: Boolean = XOP.ENABLE.COMPRESSION
) : CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    private val logger = LogUtils.getLogger(XopNormService::class.java.name)

    private val normInstance = NormInstance()
    private val eventLoopJob: Job
    // private val eventHandlerLoopJob: Job
    // private val normEventChannel = Channel<NORMEventProc>()

    private var running = true
    private val randGen = Random(System.currentTimeMillis())

    private val sendInterfaces = sendInterfaceNames.run { split(with(",") { toRegex() }).dropLastWhile { it.isEmpty() }.toTypedArray() }
    private val recvInterfaces = recvInterfaceNames.run { split(with(",") { toRegex() }).dropLastWhile { it.isEmpty() }.toTypedArray() }
    private var startPort: Int = portRange.split("-")[0].toInt()
    private val endPort: Int = portRange.split("-")[1].toInt()

    // Map of <TransportType, NormTransport>
    private val transportSessions = mutableMapOf<NormSession, NormTransport>()
    private val transports = mutableMapOf<TransportType, NormTransport>()
    private val sessionLocalNodeIds = mutableSetOf<Long>()

    private var oneToOneTransport: NormTransport? = null

    // TODO 20180928 think this needs to be changed to be threadsafe etc
    // private val thisNode = NORMNode(mutableMapOf(), nodeId, 0, mutableMapOf(), mutableSetOf(), null)

    // NORM Presence Transport Variables:
    // TODO 20181128 streamline this
    private var normPresenceTransport: NormPresenceTransport? = null
    private var presencePort: Int? = 0


    private var broadcastJob: Job? = null

    private val grttMultiplier = XOP.TRANSPORT.NORM.GRTT_MULTIPLIER

    init {
        val normLogger = LogUtils.getLogger("${XopNormService::class.java.name}.NORM",
            XopLogFormatter("NORM")
            )
        val loggerLevel: Int = if (normLogger.level == null) {
            1
        } else {
            normLogger.level.intValue()
        }
        logger.info("normLogLevel: $loggerLevel")

        val normDebugLevel = 10 - loggerLevel / 100
        logger.fine("Setting NORM Debug Level to: $normDebugLevel")
        normInstance.debugLevel = normDebugLevel

        eventLoopJob = launch { runNormEventLoop() }
    }

    private data class NORMEventProc (
        val type: NormEventType,
        var node: NormNode?,
        var eventObject: NormObject?,
        var session: NormSession?
    )

    private fun runNormEventLoop() {
        try {
            logger.fine("Entering NormEventHandler loop ...")
            var normEvent: NormEvent? = normInstance.nextEvent
            while (running && normEvent != null) {
                logger.finer("eventType: {{${normEvent.type}}}")
                val event = NORMEventProc(normEvent.type,
                    normEvent.node,
                    normEvent.`object`,
                    normEvent.session
                )
                // normEventChannel.send(event)
                when (event.type) {
                    NormEventType.NORM_REMOTE_SENDER_NEW -> {
                        try {
                            handleRemoteSenderNew(event)
                        } catch (e: Exception) {
                            logger.log(Level.WARNING, "Exception adding new Remote Sender. msg: ${e.message}", e)
                        }
                    }
                    NormEventType.NORM_TX_WATERMARK_COMPLETED ->
                        handleTxWatermarkCompleted(event)
                    NormEventType.NORM_RX_OBJECT_UPDATED ->
                        handleRxObjUpdated(event)
                    NormEventType.NORM_RX_OBJECT_COMPLETED ->
                        handleRxObjCompleted(event)
                    NormEventType.NORM_REMOTE_SENDER_PURGED -> {
                        logger.fine("REMOTESENDER PURGED: ${event.node?.id}")
                    }
                    NormEventType.NORM_REMOTE_SENDER_ACTIVE ->
                        logger.finest("REMOTE SENDER ACTIVE: ${event.node?.id}")
                    NormEventType.NORM_REMOTE_SENDER_INACTIVE ->
                        logger.finest("REMOTE SENDER INACTIVE: ${event.node?.id}")
                    NormEventType.NORM_RX_OBJECT_INFO ->
                        logger.finest("OBJECT INFO: ${event.node?.id}")
                    NormEventType.NORM_TX_CMD_SENT -> {
                        logger.finest("TX CMD SENT: from ${event.node}")
                    }
                    NormEventType.NORM_RX_CMD_NEW -> {
                        logger.finest("NORM_RX_CMD_NEW from ${event.node?.id} $event")
                        handleRxCmd(event)
                    }
                    NormEventType.NORM_REMOTE_SENDER_RESET -> {
                        handleRemoteSenderReset(event)
                    }
                    NormEventType.NORM_REMOTE_SENDER_ADDRESS ->
                        logger.finer("REMOTE SENDER ADDRESS: ${event.node?.id}")
                    NormEventType.NORM_USER_TIMEOUT ->
                        logger.finer("NORM USER TIMEOUT: ${event.node?.id ?: "no node"}")
                    NormEventType.NORM_ACKING_NODE_NEW -> {
                        logger.finer("NORM_ACKING_NODE_NEW: ${event.node?.id ?: "no node"} $event")
                    }
                    NormEventType.NORM_GRTT_UPDATED -> {
                        logger.finest("NORM_GRTT_UPDATED $event")
                        handleGrttUpdate(normEvent)
                    }
                    else -> {
                        logger.finest("Unhandled eventType: ${event.type}")
                    }
                }
                normEvent = normInstance.nextEvent
            }
        } catch (ex: IOException) {
            logger.log(Level.WARNING, "NormGetNextEvent IOException", ex)
            // ex.printStackTrace()
        } finally {
            logger.fine("finally reached. exiting NormEventHandler ...")
        }
        logger.fine("exiting NormEventHandler ...")
    }

    private fun handleRxCmd(event: NORMEventProc) {
        if( event.node == null ) {
            logger.warning("event node is null!")
            return
        }
        val node = event.node!!

        logger.finer("NORM_RX_CMD: node from node id ${node.id} ${node.address.hostString}")
        val byteArray = ByteArray(XOP.TRANSPORT.NORM.SEGMENTSIZE)
        node.getCommand(byteArray, 0, XOP.TRANSPORT.NORM.SEGMENTSIZE)

        val pair = getDataString(byteArray, false)
        val dataStr = pair.first
        val dataBytes = pair.second
        logger.finer("NORM_RX_CMD_NEW: command datastr: $dataStr")
        val eventNodeId = node.id
        // val grttEstimate = event.session?.grttEstimate ?: 0.0

        // val probeNORMNode = fromJSONStr(dataStr, event.session)
        // normPresenceTransport?.handleProbeNORMNode(eventNodeId, probeNORMNode, grttEstimate)

        val transportMetadata = TransportMetadata(
            eventNodeId, TransportType.PresenceProbe, TransportSubType.JSON, 0L
        )
        transports[transportMetadata.transportType]?.handleTransportData(eventNodeId,
            event.session!!, transportMetadata, dataStr, dataBytes)
            ?: logger.severe("Unsuported transportType ${transportMetadata.transportType}")
    }

    private fun handleRemoteSenderReset(event: NORMEventProc) {
        logger.finer("REMOTE SENDER RESET: ${event.node?.id}")
    }

    private fun handleRxObjUpdated(event: NORMEventProc) {
        logger.fine("handleRxObjUpdated ${event.node?.id}")
        val normStream = event.eventObject!!
        if (normStream is NormStream) {
            logger.fine("a norm stream detected")
            val byteArray = ByteArray(65536)
            var bytesRead = normStream.read(byteArray, 0, byteArray.size)
            while (bytesRead > 0) {
                logger.info("read stream object $bytesRead byteArray $byteArray")
                bytesRead = normStream.read(byteArray, 0, byteArray.size)
            }
            val strBytes = byteArray.toString(Charset.defaultCharset())
            val jsonObj = JSONObject(strBytes)
            //logger.finer("jsonObj $jsonObj")
            if (!jsonObj.isEmpty) {
                val node = fromJSONStr(jsonObj.toString(), event.session)
                val msgStr = String(byteArray)
                logger.info("msgStr $msgStr, node $node")
            } else {
                logger.info("jsonObj is empty!")
            }
        } else {
            logger.fine("NOT a NORMStream. eventObject: {${event.eventObject}}")
        }
    }

    private fun handleTxWatermarkCompleted(event: NORMEventProc) {
        val normSession = event.session
        logger.fine("Watermark Completed for $normSession")
        //normSession.resetWatermark()
        //logger.fine("Resetting Watermark for the session $normSession")
    }

    private fun handleRemoteSenderNew(event: NORMEventProc) {
        val nodeId = event.node?.id ?: throw Exception("No NODE id for event")
        val normSession = event.session ?: throw Exception("no session included  for nodeId $nodeId")
        logger.fine("Adding a new remote sender $nodeId to normPresenceTransport, $normSession")
        transportSessions[normSession]?.addRemoteNode(normSession, nodeId)
            ?: logger.finer("Transport not found for this session, do nothing.")
        // normPresenceTransport?.addRemoteNode(normSession, nodeId)

    }

    private fun handleGrttUpdate(event: NormEvent) {
        val normSession = event.session ?: return

        val nodeId = event.node?.id ?: return

        logger.finest("updating GRTT session local node id:${normSession.localNodeId} to ${event.session.grttEstimate}")
        val normTransport: NormPresenceTransport? = transportSessions[normSession] as? NormPresenceTransport
        normTransport?.updateGrtt(normSession, nodeId, event.session.grttEstimate)
            ?: logger.finer("${event.session} session not tied a NormPresenceTransport: ${normTransport is NormPresenceTransport}")
    }

    private fun handleRxObjCompleted(event: NORMEventProc) {
        logger.fine("NORM Object completed for session node id " + event.session?.localNodeId)
        val normObject = event.eventObject!!
        if (normObject.type != NormObjectType.NORM_OBJECT_DATA) {
            logger.fine("completed object is not NORM_OBJECT_DATA")
            return
        }

        val sender = normObject.sender
        val transportMetadata = getTransportMetadata(normObject.info, enableCompression)
        val data = normObject as NormData
        logger.fine("data from sender ${sender.id} transportType ${transportMetadata.transportType}")

        val pair = getDataString(data.data, enableCompression)
        val dataString = pair.first
        val dataBytes = pair.second
        val normSessionObj = NormSessionObj(
            dataString, event.session!!,
            sender.id, transportMetadata  //transportMetadata.transportType
        )
        if(isDuplicate(normSessionObj)) {
            logger.fine("duplicate message from: ${sender.id} datastring: $dataString")
            return
        }

        logger.fine("transportType: ${transportMetadata.transportType}")

        // TODO 2019-05-22 Re-introduce TransportSubType for PresenceTransport handling of redirected nodes.
        when (transportMetadata.transportType) {
            TransportType.Control -> {
                logger.finer("Control message")
            }
            // TransportType.MessageTransport -> {
            //     transportSessions[event.session!!]?.handleTransportData(
            //         normSessionObj.senderNodeId,
            //         normSessionObj.normSession, transportMetadata, dataString)
            //         ?: logger.severe("Unable to find transport for session ${event.session}")
            // }
            // TransportType.PresenceTransport -> {
            else -> {
                logger.fine("processing completed NormObject with transportMetadata $transportMetadata")
                transportSessions[event.session!!]?.handleTransportData(
                    normSessionObj.senderNodeId,
                    normSessionObj.normSession, transportMetadata, dataString, dataBytes)
                        ?: logger.severe("Unable to find transport for session ${event.session}")
            }
        }
    }

    // [ senderNodeId: Set[dataStrings] ]
    // private val receivedSessionMessages = mutableMapOf<Long, MutableMap<String, AtomicInteger>>()
    private fun isDuplicate(normSessionObj: NormSessionObj): Boolean {
        val origSenderId = normSessionObj.transportMetadata?.origSenderId ?: -99
        logger.fine("id: ${origSenderId} norm sessionLocalIds $sessionLocalNodeIds")
        return origSenderId in sessionLocalNodeIds
        //
        // val senderNodeId = normSessionObj.senderNodeId
        // return if (senderNodeId !in receivedSessionMessages) {
        //     receivedSessionMessages[senderNodeId] = mutableMapOf(normSessionObj.msgString to AtomicInteger(1))
        //     false
        // } else {
        //     if (normSessionObj.msgString !in receivedSessionMessages[senderNodeId]!!) {
        //         receivedSessionMessages[senderNodeId]!![normSessionObj.msgString] = AtomicInteger(1)
        //         false
        //     } else {
        //         val numMsg = receivedSessionMessages[senderNodeId]!![normSessionObj.msgString]!!.incrementAndGet()
        //         if( numMsg >= sendInterfaces.size) {
        //             receivedSessionMessages[senderNodeId]!!.remove(normSessionObj.msgString)
        //         }
        //         true
        //     }
        // }
    }

    // private fun handleMessageTransportData(messageObj: NormSessionObj) {
    //     val (msgString, session, nodeId, metadata) = messageObj
    //     logger.fine("$nodeId, ${session.localNodeId}, $session,  $metadata, msgString: {{$msgString}}")
    //     try {
    //         val packet = Utils.packetFromString(msgString)
    //         val tsDifference =
    //             (System.currentTimeMillis() - messageObj.transportMetadata!!.timestamp)
    //         if (tsDifference > ((session.grttEstimate * 1000) * grttMultiplier)) {
    //             logger.fine("Adding delay element: msgTstamp:${messageObj.transportMetadata.timestamp} ts_diff:$tsDifference, grtt:${session.grttEstimate * 1000}")
    //             addDelay(
    //                 packet,
    //                 messageObj.transportMetadata.timestamp,
    //                 packet.from,
    //                 "Offline Message"
    //             )
    //             logger.finer("message with delay: $packet")
    //         }
    //         if (packet is Presence) {
    //             logger.warning("THIS THREAD should not be handling any presence messages! $packet")
    //             // processPresence(packet, nodeId)
    //             // oneToOneTransport.processIncomingPacket(packet);
    //         } else {
    //             logger.fine("Incoming normSession Obj, remote nodeId $nodeId, session nodeId ${session.localNodeId}")
    //             val ts = transportSessions[session]
    //             if (ts != null) {
    //                 logger.fine("handle received XMPP message")
    //                 ts.processIncomingPacket(packet)
    //             } else {
    //                 logger.fine("one to one transport handling this packet")
    //                 oneToOneTransport!!.processIncomingPacket(packet)
    //             }
    //         }
    //     } catch (e: DocumentException) {
    //         logger.log(Level.SEVERE, "Unable to marshal a stanza from $msgString", e)
    //     }
    // }

    private fun getDataString(data: ByteArray, compression: Boolean): Pair<String, ByteArray> {
        val byteBuffer = ByteBuffer.wrap(data, 0, data.size)
        var dataBytes = byteBuffer.array()
        if (compression) {
            logger.finer("length BEFORE decompression $data.size")
            dataBytes = MessageCompressionUtils.decompressBytes(dataBytes)
            logger.finer("length AFTER decompression $data.size")
        }
        return Pair(String(dataBytes, 0, byteBuffer.capacity()), dataBytes)
    }

    /**
     * Create and return a new NormSession using the supplied [multicastGroup]:[port] and
     * a generated [nodeId].
     *
     * @throws IOException
     * if the session cannot be created
     */
    @Throws(IOException::class)
    private fun createNormSession(ifaceName: String, multicastGroup: InetAddress, port: Int): NormSession {
        logger.fine(
            "Creating NORM Session on ${multicastGroup.hostAddress}:$port, nodeId: $nodeId "
                    + " node_any: ${NormNode.NORM_NODE_ANY}"
        )
        val session = normInstance.createSession(
            multicastGroup.hostAddress,
            port, NormNode.NORM_NODE_ANY)

        session.setMulticastInterface(ifaceName)
        logger.fine("Set multicast socket on: $ifaceName. session local id: ${session.localNodeId}, session $session")

        // Adding the multicast group host address ensures only messages to this multicast
        // group is sent to the session.
        sessionLocalNodeIds.add(session.localNodeId)
        return session
    }

    private fun startSenderSessions(senderSession: NormSession) {
        val randInt = randGen.nextInt()
        logger.fine("-- Starting new senderSession nodeId ${senderSession.localNodeId}; randInt $randInt, session $senderSession")
        val senderBufferSpace = XOP.TRANSPORT.NORM.SENDBUFFERSPACE //256 * 256;
        val segmentSize = XOP.TRANSPORT.NORM.SEGMENTSIZE //1400;
        val blockSize = XOP.TRANSPORT.NORM.BLOCKSIZE //64;
        val numParity = XOP.TRANSPORT.NORM.NUMPARITY //16;

        // senderSession.setGrttProbingMode(NormProbingMode.NORM_PROBE_ACTIVE);
        // senderSession.setReportInterval(10.0);

        senderSession.setRxPortReuse(true)
        senderSession.startSender(randInt, senderBufferSpace, segmentSize, blockSize, numParity)
        // logger.finer("Setting initial grttEstimate to $presenceInterval on session for interface $iface")
        // senderSession.grttEstimate = presenceInterval.toDouble() / 1000

    }

    private fun startReceiverSession(receiverSession: NormSession) {
        logger.fine("-- Starting new receiverSession nodeId ${receiverSession.localNodeId}, session $receiverSession")
        val receiverBufferSpace = XOP.TRANSPORT.NORM.RCVBUFFERSPACE //256 * 256;
        // receiverSession.setRxCacheLimit(rxCacheMax)
        receiverSession.setRxPortReuse(true)
        receiverSession.startReceiver(receiverBufferSpace)
    }


    /**
     * Creates a NormPresenceTransport object for advertising and discovering client entities.
     * Returns an [SDManager] from the [normPresenceTransport].
     */
    fun enablePresenceTransport(
        presenceInterval: Long,
        presenceTimeout: Int,
        sdListener: SDListener
    ): SDManager {
        logger.info("Added sdListener and starting presence Handler")
        // Create a normSession object
        presencePort = startPort++
        val presenceNormSessions = mutableMapOf<String, NormSession>()
        val receiverSessions = mutableMapOf<String, NormSession>()
        var nodeId = this.nodeId
        for( iface in sendInterfaces ){
            val presenceNormSession = createNormSession(iface, multicastGroup, presencePort!!)
            logger.info("Created presence Sender Session: $presenceNormSession, iface $iface, nodeId ${presenceNormSession.localNodeId}")
            nodeId = presenceNormSession.localNodeId
            startSenderSessions(presenceNormSession)
            presenceNormSessions[iface] = presenceNormSession
        }

        for (iface in recvInterfaces) {
            val recvNormSession = createNormSession(iface, multicastGroup, presencePort!!)
            logger.info("Created presence Receiver Session: $recvNormSession, iface $iface, nodeId ${recvNormSession.localNodeId}")
            startReceiverSession(recvNormSession)
            receiverSessions[iface] = recvNormSession
        }

        normPresenceTransport = NormPresenceTransport(
            presenceNormSessions, receiverSessions, multicastGroup, presencePort!!, nodeId,
            transportPacketProcessor, sdListener, enableCompression, this,
            presenceInterval, presenceTimeout
        )
        logger.fine("created presence transport on $presencePort. $normPresenceTransport")
        for((_, session) in receiverSessions) {
            logger.finer("adding session local node id ${session.localNodeId} to normTransport $normPresenceTransport")
            transportSessions[session] = normPresenceTransport as NormTransport
        }
        return normPresenceTransport!!.sdManager
    }


    /**
     * Create NormTransport for Rooms and one-to-one chat
     */
    private fun createNormTransport( transportType: TransportType, transportSubType: TransportSubType,
        groupAddress: InetAddress, groupPort: Int,
        transportPacketProcessor: TransportPacketProcessor,
        compression: Boolean
    ): NormTransport {
        logger.fine("Creating new Norm Transport on $groupAddress:$groupPort")

        val senderSessions = mutableMapOf<String, NormSession>()
        val receiverSessions = mutableMapOf<String, NormSession>()
        for (iface in sendInterfaces) {
            val session = createNormSession(iface, groupAddress, groupPort)
            startSenderSessions(session)
            senderSessions[iface] = session
        }
        for (iface in recvInterfaces) {
            val rcvSession = createNormSession(iface, groupAddress, groupPort)
            startReceiverSession(rcvSession)
            receiverSessions[iface] = rcvSession
        }

        val normTransport = NormTransport(transportType, transportSubType,
            senderSessions, receiverSessions, groupAddress, groupPort,
            transportPacketProcessor, compression
        )

        for((iface, session) in receiverSessions) {
            logger.finer("adding session $session; iface $iface; localNodeId ${session.localNodeId}" +
                    " to normTransport $normTransport")
            transportSessions[session] = normTransport
        }
        logger.fine("started sessions on all interface transport ")
        return normTransport
    }

    /**
     * Create a normTransport for the given [roomJID] with [compression] parameter.
     *
     * If [roomJID] is null, then assumed to be one-to-one transport.
     */
    fun createRoomTransport(roomJID: JID, compression: Boolean): XOPTransportService {
        val roomStr = roomJID.toString()
        val port = getPort(roomStr, startPort, endPort)
        logger.fine("Creating NormTransport for $roomStr using port: $port")

        val transport = createNormTransport(
            TransportType.MessageTransport, TransportSubType.GroupChat,
            multicastGroup, port, transportPacketProcessor, compression
        )

        // So SDManager sends Presence messages over the NORM session for the room
        normPresenceTransport?.addTransportForRoom(roomJID, transport)
        return transport
    }

    fun createOneToOneTransport(compression: Boolean): XOPTransportService {
        val port = getPort("oneToOne@oneToOne", startPort, endPort)
        logger.fine("Creating NormTransport for One-to-One messages using port: $port")

        this.oneToOneTransport = createNormTransport(TransportType.MessageTransport,
            TransportSubType.Chat,
            multicastGroup, port, transportPacketProcessor, compression
        )

        return this.oneToOneTransport!!
    }

    fun shutdown() {
        logger.info("shutting down XOPNormService")

        broadcastJob?.cancel()
        for ((session, transport) in transportSessions) {
            logger.info("Shutting down transport $transport with session: $session")
            transport.close()
        }
        transportSessions.clear()

        normPresenceTransport?.close() ?: logger.info("Presence Transport not initialized")

        logger.info("Canceling NORM Instance event loop")
        eventLoopJob.cancel()
        //eventLoopJob.join()


        running = false // signal to threads to stop running

        logger.info("destroying NORM instance")
        normInstance.destroyInstance()
    }
}
