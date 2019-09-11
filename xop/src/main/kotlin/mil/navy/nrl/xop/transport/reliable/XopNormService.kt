package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.net.SDListener
import edu.drexel.xop.net.SDManager
import edu.drexel.xop.packet.TransportPacketProcessor
import edu.drexel.xop.util.MessageCompressionUtils
import edu.drexel.xop.util.Utils
import edu.drexel.xop.util.Utils.addDelay
import edu.drexel.xop.util.XOP
import edu.drexel.xop.util.logger.LogUtils
import edu.drexel.xop.util.logger.XopLogFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import mil.navy.nrl.norm.*
import mil.navy.nrl.norm.enums.NormEventType
import mil.navy.nrl.norm.enums.NormObjectType
import mil.navy.nrl.xop.util.addressing.getBindAddress
import mil.navy.nrl.xop.util.addressing.getNodeId
import mil.navy.nrl.xop.util.addressing.getPort
import org.dom4j.DocumentException
import org.json.JSONObject
import org.xmpp.packet.JID
import org.xmpp.packet.Presence
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
    private val ifaceName: String,
    private val multicastGroup: InetAddress,
    portRange: String,
    private val transportPacketProcessor: TransportPacketProcessor
) : CoroutineScope {

    internal data class NormSessionObj internal constructor(
        val msgString: String,
        val normSession: NormSession,
        val senderNodeId: Long,
        val transportMetadata: TransportMetadata?
    )

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

    private var startPort: Int = portRange.split("-")[0].toInt()
    private val endPort: Int = portRange.split("-")[1].toInt()

    private val normSessions: MutableMap<Int, NormSession> = mutableMapOf()
    // private val normStreamSessions: MutableMap<Int, NormSession> = mutableMapOf()
    private val transportSessions = mutableMapOf<NormSession, NormTransport>()
    private var enableCompression: Boolean = XOP.ENABLE.COMPRESSION
    private val nodeId: Long

    private var oneToOneTransport: NormTransport? = null

    // TODO 20180928 think this needs to be changed to be threadsafe etc
    private val thisNode: NORMNode

    // NORM Presence Transport Variables:
    // TODO 20181128 streamline this
    private var normPresenceTransport: NormPresenceTransport? = null
    private var presencePort: Int? = 0

    // private var normSdManager: NORMSDManager? = null
    // private var sdListener: SDListener? = null

    // TODO 20190225: do I need to move the monitorRemoteNodeCountersMap
    // private var monitorRemoteNodeCountersMap: MutableMap<Long, Job> = mutableMapOf()
    // private val remoteJIDLastPresence: ConcurrentMap<JID, Presence> = ConcurrentHashMap()

    private var broadcastJob: Job? = null
    private var presenceNormSession: NormSession? = null

    private val grttMultiplier = XOP.TRANSPORT.NORM.GRTT_MULTIPLIER

    init {
        val ifaceAddress = getBindAddress(ifaceName)
        nodeId = getNodeId(ifaceAddress.hostAddress)
        thisNode = NORMNode(mutableMapOf(), nodeId, 0, mutableMapOf(), mutableSetOf())

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
        // eventHandlerLoopJob = launch { runNormEventHandlerLoop() }
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
                    NormEventType.NORM_REMOTE_SENDER_NEW ->
                        handleRemoteSenderNew(event)
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
                        logger.fine("REMOTE SENDER ACTIVE: ${event.node?.id}")
                    NormEventType.NORM_REMOTE_SENDER_INACTIVE ->
                        logger.fine("REMOTE SENDER INACTIVE: ${event.node?.id}")
                    NormEventType.NORM_RX_OBJECT_INFO ->
                        logger.fine("OBJECT INFO: ${event.node?.id}")
                    NormEventType.NORM_TX_CMD_SENT -> {
                        logger.finer("TX CMD SENT: from ${event.node}")
                    }
                    NormEventType.NORM_RX_CMD_NEW -> {
                        logger.fine("NORM_RX_CMD_NEW from ${event.node?.id} $event")
                        handleRxCmd(event)
                    }
                    NormEventType.NORM_REMOTE_SENDER_RESET -> {
                        handleRemoteSenderReset(event)
                    }
                    NormEventType.NORM_REMOTE_SENDER_ADDRESS ->
                        logger.fine("REMOTE SENDER ADDRESS: ${event.node?.id}")
                    NormEventType.NORM_USER_TIMEOUT ->
                        logger.fine("NORM USER TIMEOUT: ${event.node?.id}")
                    NormEventType.NORM_ACKING_NODE_NEW -> {
                        logger.fine("NORM_ACKING_NODE_NEW: ${event.node?.id} $event")
                    }
                    NormEventType.NORM_GRTT_UPDATED -> {
                        logger.fine("NORM_GRTT_UPDATED $event")
                    }
                    else -> {
                        logger.finest("Unhandled eventType: ${event.type}")
                    }
                }
                normEvent = normInstance.nextEvent
            }
        } catch (ex: IOException) {
            logger.warning("NormGetNextEvent IOException")
            logger.throwing(
                "normEventLoop",
                "run", ex
            )
        } finally {
            logger.fine("finally reached. exiting NormEventHandler ...")
        }
        logger.fine("exiting NormEventHandler ...")
    }
    //
    // private suspend fun runNormEventHandlerLoop() {
    //     for( event in normEventChannel ) {
    //         when (event.type) {
    //             NormEventType.NORM_REMOTE_SENDER_NEW ->
    //                 handleRemoteSenderNew(event)
    //             NormEventType.NORM_TX_WATERMARK_COMPLETED ->
    //                 handleTxWatermarkCompleted(event)
    //             NormEventType.NORM_RX_OBJECT_UPDATED ->
    //                 handleRxObjUpdated(event)
    //             NormEventType.NORM_RX_OBJECT_COMPLETED ->
    //                 handleRxObjCompleted(event)
    //             NormEventType.NORM_REMOTE_SENDER_PURGED -> {
    //                 logger.fine("REMOTESENDER PURGED: ${event.node?.id}")
    //             }
    //             NormEventType.NORM_REMOTE_SENDER_ACTIVE ->
    //                 logger.fine("REMOTE SENDER ACTIVE: ${event.node?.id}")
    //             NormEventType.NORM_REMOTE_SENDER_INACTIVE ->
    //                 logger.fine("REMOTE SENDER INACTIVE: ${event.node?.id}")
    //             NormEventType.NORM_RX_OBJECT_INFO ->
    //                 logger.fine("OBJECT INFO: ${event.node?.id}")
    //             NormEventType.NORM_TX_CMD_SENT -> {
    //                 logger.finer("TX CMD SENT: from ${event.node}")
    //             }
    //             NormEventType.NORM_RX_CMD_NEW -> {
    //                 logger.fine("NORM_RX_CMD_NEW from ${event.node?.id} $event")
    //                 handleRxCmd(event)
    //             }
    //             NormEventType.NORM_REMOTE_SENDER_RESET -> {
    //                 handleRemoteSenderReset(event)
    //             }
    //             NormEventType.NORM_REMOTE_SENDER_ADDRESS ->
    //                 logger.fine("REMOTE SENDER ADDRESS: ${event.node?.id}")
    //             NormEventType.NORM_USER_TIMEOUT ->
    //                 logger.fine("NORM USER TIMEOUT: ${event.node?.id}")
    //             NormEventType.NORM_ACKING_NODE_NEW -> {
    //                 logger.fine("NORM_ACKING_NODE_NEW: ${event.node?.id} $event")
    //             }
    //             NormEventType.NORM_GRTT_UPDATED -> {
    //                 logger.fine("NORM_GRTT_UPDATED $event")
    //             }
    //             else -> {
    //                 logger.finest("Unhandled eventType: ${event.type}")
    //             }
    //         }
    //     }
    // }



    private fun handleRxCmd(event: NORMEventProc) {
        if( event.node == null ) {
            logger.warning("event node is null!")
            return
        }
        val node = event.node!!

        logger.finer(
            "NORM_RX_CMD: node from node id ${node.id} ${node.address.hostString}")
        val byteArray = ByteArray(XOP.TRANSPORT.NORM.SEGMENTSIZE)
        node.getCommand(byteArray, 0, XOP.TRANSPORT.NORM.SEGMENTSIZE)

        val dataStr = getDataString(byteArray, false)
        logger.finer("NORM_RX_CMD_NEW: command datastr: $dataStr")
        val probeNORMNode = fromJSONStr(dataStr)

        val eventNodeId = node.id
        normPresenceTransport?.processProbeNORMNode(eventNodeId, probeNORMNode)


    }

    private fun handleRemoteSenderReset(event: NORMEventProc) {
        logger.fine("REMOTE SENDER RESET: ${event.node?.id}")
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
                val node = fromJSONStr(jsonObj.toString())
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
        val nodeId = event.node?.id ?: 0L
        logger.fine("Adding a new remote sender $nodeId to SDManager")
        normPresenceTransport?.addRemoteNode(nodeId)
    }


    private fun handleRxObjCompleted(event: NORMEventProc) {
        logger.fine("NORM Object completed for session node id " + event.session?.localNodeId)
        val normObject = event.eventObject!!
        if (normObject.type == NormObjectType.NORM_OBJECT_DATA) {
            val sender = normObject.sender
            val transportMetadata = getTransportMetadata(normObject.info)
            val data = normObject as NormData
            logger.fine("data from sender ${sender.id} transportType ${transportMetadata.transportType}")
            val dataString = getDataString(data.data, enableCompression)
            val normSessionObj = NormSessionObj(
                dataString, event.session!!,
                sender.id, transportMetadata  //transportMetadata.transportType
            )
            when (transportMetadata.transportType) {
                NormTransport.TransportType.PresenceTransport ->  {

                    normPresenceTransport?.handlePresenceTransportData(normSessionObj.senderNodeId, dataString)
                }
                NormTransport.TransportType.PresenceProbe -> launch {
                    val probeNORMNode = fromJSONStr(dataString)
                    normPresenceTransport?.handlePresenceProbe(probeNORMNode.nodeId, probeNORMNode)
                }
                NormTransport.TransportType.MUCPresence -> launch {
                    normPresenceTransport?.handleMUCPresence(normSessionObj.senderNodeId, dataString)
                }
                NormTransport.TransportType.MessageTransport -> launch {
                    handleMessageTransportData(normSessionObj)
                }
                else -> {
                    logger.severe("Unsuported transportType ${transportMetadata.transportType}")
                }
            }
        } else {
            logger.fine("completed object is not NORM_OBJECT_DATA")
        }
    }

    private fun handleMessageTransportData(messageObj: NormSessionObj) {
        val (msgString, session, nodeId) = messageObj
        logger.fine("$nodeId, ${session.localNodeId},  msgString: {{$msgString}}")
        try {
            val packet = Utils.packetFromString(msgString)
            val tsDifference =
                (System.currentTimeMillis() - messageObj.transportMetadata!!.timestamp)
            if (tsDifference > ((session.grttEstimate * 1000) * grttMultiplier)) {
                logger.fine("Adding delay element: ts_diff:$tsDifference, grtt:${session.grttEstimate * 1000}")
                addDelay(
                    packet,
                    messageObj.transportMetadata.timestamp,
                    packet.from,
                    "Offline Message"
                )
                logger.finer("message with delay: $packet")
            }
            if (packet is Presence) {
                logger.warning("THIS THREAD should not be handling any presence messages! $packet")
                // processPresence(packet, nodeId)
                // oneToOneTransport.processIncomingPacket(packet);
            } else {
                logger.fine("Incoming normSession Obj, remote nodeId $nodeId, session nodeId ${session.localNodeId}")
                val ts = transportSessions[session]
                if (ts != null) {
                    logger.fine("handle received XMPP message")
                    ts.processIncomingPacket(packet)
                } else {
                    logger.fine("one to one transport handling this packet")
                    oneToOneTransport!!.processIncomingPacket(packet)
                }
            }
        } catch (e: DocumentException) {
            logger.log(Level.SEVERE, "Unable to marshal a stanza from $msgString", e)
        }
    }

    private fun getDataString(data: ByteArray, compression: Boolean): String {
        val byteBuffer = ByteBuffer.wrap(data, 0, data.size)
        var dataBytes = byteBuffer.array()
        if (compression) {
            logger.finer("length BEFORE decompression $data.size")
            dataBytes = MessageCompressionUtils.decompressBytes(dataBytes)
            logger.finer("length AFTER decompression $data.size")
        }
        return String(dataBytes, 0, byteBuffer.capacity())
    }

    /**
     * Create and return a new NormSession using the supplied [multicastGroup]:[port] and
     * a generated [nodeId].
     *
     * @throws IOException
     * if the session cannot be created
     */
    @Throws(IOException::class)
    private fun createNormSession(multicastGroup: InetAddress, port: Int): NormSession {
        logger.info(
            "Creating NORM Session on ${multicastGroup.hostAddress}:$port, nodeId: $nodeId "
                    + " node_any: ${NormNode.NORM_NODE_ANY}"
        )
        val session = normInstance.createSession(
            multicastGroup.hostAddress,
            port,
            NormNode.NORM_NODE_ANY
        )
        session.setMulticastInterface(ifaceName)
        logger.info("Set multicast socket on: $ifaceName")

        // Adding the multicast group host address ensures only messages to this multicast
        // group is sent to the session.
        session.setRxPortReuse(true, multicastGroup.hostAddress, null, 0)

        // session.setGrttProbingMode(NormProbingMode.NORM_PROBE_ACTIVE);
        // session.setReportInterval(10.0);

        return session
    }

    private fun startNormSession(session: NormSession) {
        val randInt = randGen.nextInt()

        logger.fine(
            "-- creating new session: " + session.localNodeId
                    + " with randInt: " + randInt
        )
        val receiverBufferSpace = XOP.TRANSPORT.NORM.RCVBUFFERSPACE //256 * 256;

        val senderBufferSpace = XOP.TRANSPORT.NORM.SENDBUFFERSPACE //256 * 256;
        val segmentSize = XOP.TRANSPORT.NORM.SEGMENTSIZE //1400;
        val blockSize = XOP.TRANSPORT.NORM.BLOCKSIZE //64;
        val numParity = XOP.TRANSPORT.NORM.NUMPARITY //16;
        session.startSender(randInt, senderBufferSpace, segmentSize, blockSize, numParity)

        //session.setRxCacheLimit(txCacheMax)

        session.startReceiver(receiverBufferSpace)
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
        presenceNormSession = createNormSession(multicastGroup, presencePort!!)

        logger.finer("Setting initial grttEstimate to $presenceInterval")
        presenceNormSession!!.grttEstimate = presenceInterval.toDouble() / 1000

        normPresenceTransport = NormPresenceTransport(
            presenceNormSession!!, multicastGroup, presencePort!!, thisNode,
            transportPacketProcessor, sdListener, enableCompression, this,
            presenceInterval, presenceTimeout
        )
        logger.fine("created presence transport on $presencePort. $normPresenceTransport")

        return normPresenceTransport!!.sdManager
    }


    /**
     * Create NormTransport
     */
    private fun createNormTransport(
        idStr: String, groupAddress: InetAddress, groupPort: Int,
        transportPacketProcessor: TransportPacketProcessor,
        compression: Boolean
    ): NormTransport {
        logger.fine("create NormTransport for $idStr. normSessions $normSessions")
        return if (!normSessions.containsKey(groupPort)) {
            logger.fine("Creating new Norm Transport on $groupAddress:$groupPort ")
            // val nodeId = thisNode.nodeId
            val session = createNormSession(groupAddress, groupPort)
            val normTransport = NormTransport(
                session, groupAddress, groupPort,
                transportPacketProcessor, compression
            )
            transportSessions[session] = normTransport
            normSessions[groupPort] = session
            startNormSession(session)
            normTransport

        } else {
            logger.fine("$groupPort Port already assigned and session created. retrieving NormTransport")
            val session = normSessions[groupPort]!!
            transportSessions[session]!!
        }
    }

    /**
     * Create a normTransport for the given [roomJID] with [compression] parameter. If supplied
     * [roomJID] is null, then assumed to be one-to-one transport.
     */
    fun createNormTransport(roomJID: JID?, compression: Boolean): NormTransport {
        var roomStr = "oneToOne"
        if (roomJID != null) {
            roomStr = roomJID.toString()
        }
        val port = getPort(roomStr, startPort, endPort)
        logger.fine("Creating NormTransport for $roomStr using port: $port")

        val transport = createNormTransport(
            roomStr, multicastGroup, port,
            transportPacketProcessor, compression
        )

        if (roomJID == null) {
            this.oneToOneTransport = transport
        } else {
            // So SDManager sends Presence messages over the NORM session for the room
            normPresenceTransport?.addTransportForRoom(roomJID, transport)
        }
        return transport
    }

    fun shutdown() {
        logger.info("shutting down normPresenceTransport if necessary")
        broadcastJob?.cancel()

        normPresenceTransport?.close()

        for ((session, transport) in transportSessions) {
            logger.info("Shutting down transport session: $session")
            transport.close()
        }
        transportSessions.clear()
        normSessions.clear()

        logger.info("Canceling NORM Instance event loop")
        eventLoopJob.cancel()
        //eventLoopJob.join()


        running = false // signal to threads to stop running

        logger.info("destroying NORM instance")
        normInstance.destroyInstance()
    }
}

