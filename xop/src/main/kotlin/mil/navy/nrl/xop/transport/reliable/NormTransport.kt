package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.net.transport.XOPTransportService
import edu.drexel.xop.packet.TransportPacketProcessor
import edu.drexel.xop.util.MessageCompressionUtils
import edu.drexel.xop.util.Utils
import edu.drexel.xop.util.logger.LogUtils
import mil.navy.nrl.norm.NormObject
import mil.navy.nrl.norm.NormSession
import org.dom4j.DocumentException
import org.xmpp.packet.*
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level

internal open class NormTransport internal constructor(
    protected val transportType: TransportType,
    protected val transportSubType: TransportSubType,
    protected val sendingNormSessions: MutableMap<String, NormSession>,
    private val receivingNormSessions: MutableMap<String, NormSession>,
    private val address: InetAddress,
    private val port: Int,
    private val transportPacketProcessor: TransportPacketProcessor,
    protected val compression: Boolean,
    private val grttMultiplier: Int = 1
) : XOPTransportService {
    /*
      NOTE 20180928 unnecessarily passes in the address and port into the constructor for compatibility with
      interface that ProtoSD uses.
    */
    // private val randGen = Random(System.currentTimeMillis())

    companion object {
        @JvmStatic
        private var logger = LogUtils.getLogger(NormTransport::class.java.name)
    }

    private var roomJID: JID? = null // if this is null, this is the one-to-one NORM session

    protected var running = true

    protected val ifacesForReceivingSessions = receivingNormSessions.entries.associate{(k,v)-> v to k}
    protected val ifacesForSenderSessions = sendingNormSessions.entries.associate{(k,v)-> v to k}

    init {
        logger.fine("compression enabled: $compression")

    }

    // protected fun startNormSession(session: NormSession) {
    //     val randInt = randGen.nextInt()
    //
    //     logger.fine("-- creating new session: ${session.localNodeId} with randInt: $randInt")
    //     val receiverBufferSpace = XOP.TRANSPORT.NORM.RCVBUFFERSPACE //256 * 256;
    //
    //     val senderBufferSpace = XOP.TRANSPORT.NORM.SENDBUFFERSPACE //256 * 256;
    //     val segmentSize = XOP.TRANSPORT.NORM.SEGMENTSIZE //1400;
    //     val blockSize = XOP.TRANSPORT.NORM.BLOCKSIZE //64;
    //     val numParity = XOP.TRANSPORT.NORM.NUMPARITY //16;
    //     session.startSender(randInt, senderBufferSpace, segmentSize, blockSize, numParity)
    //
    //     //session.setRxCacheLimit(txCacheMax)
    //
    //     session.startReceiver(receiverBufferSpace)
    //     logger.fine("Started new NORM session.")
    // }

    /**
     * Only used for sending XMPP Message types and the occasional IQ message
     */
    override fun sendPacket(packet: Packet) {
        // TODO 2019-05-23 Support IQ message types
        val transportType = TransportType.MessageTransport
        var transportSubType = TransportSubType.IQ
        if (packet is Message) {
            transportSubType = when {
                packet.type == Message.Type.groupchat -> TransportSubType.GroupChat
                else -> TransportSubType.Chat
            }
        }

        for((iface, normSession) in sendingNormSessions) {
            logger.fine("Sending packet {{${packet.toXML()}}} over $iface from normSession: $normSession, " +
                    "nodeId ${normSession.localNodeId}")
            val dataBytes = packet.toXML().toByteArray(Charsets.UTF_8)
            sendData(dataBytes, transportType, transportSubType, normSession, normSession.localNodeId)
        }
    }

    internal fun redirectData(data: ByteArray, transportMetadata: TransportMetadata, fromNormSession: NormSession,
                              redirectId: Long)
    {
        // val ifaceForFromSession = ifacesForReceivingSessions[fromNormSession]
        // for ((iface, normSession) in sendingNormSessions.filter {
        //         (iface, _) -> iface != ifaceForFromSession})
        // {

        val redirectTransportType: TransportType = when(transportType) {
            TransportType.PresenceInit -> TransportType.PresenceInitRedirect
            TransportType.PresenceProbe -> TransportType.PresenceProbeRedirect
            TransportType.PresenceTransport -> TransportType.PresenceTransportRedirect
            else -> TransportType.MessageTransportRedirect
        }

        for ((iface, normSession) in sendingNormSessions) {
            logger.fine("sending data to iface $iface, normSession $normSession")
            sendData(data, redirectTransportType, transportMetadata.transportSubType, normSession, redirectId)
        }
    }

    /**
     * Send [data] to [normSession]
     */
    internal fun sendData(data: ByteArray, transportType: TransportType, transportSubType: TransportSubType,
                          normSession: NormSession, redirectId: Long): NormObject?
    {
        var data1 = data
        sentSessionMessages[data] = AtomicInteger(1)
        val transportMetadata = TransportMetadata(System.currentTimeMillis(), transportType, transportSubType, redirectId)
        try {
            if (compression) {
                logger.finer("datalength before compression " + data1.size)
                data1 = MessageCompressionUtils.compressBytes(data)
                logger.finer("datalength after compression " + data1.size)

            }
            val bb = ByteBuffer.allocateDirect(data1.size + 1)
            bb.put(data1)

            val info = transportMetadataToBytes(transportMetadata, compression)

            return normSession.dataEnqueue(bb, 0, data1.size, info, 0, info.size)
        } catch (e: IOException) {
            logger.log(Level.SEVERE, "Could not send data with NormTransport.", e)
        }
        return null
    }

    internal open fun addRemoteNode(senderSession: NormSession, nodeId: Long) :NORMNode {
        logger.fine("adding remote node for NormTransport nodeId: $nodeId; session: $senderSession ")
        return NORMNode(mutableMapOf(), nodeId, 0, mutableMapOf(), mutableSetOf(), senderSession)
    }

    /**
     * called by the RxObjectCompleted method in XopNormService
     */
    internal open fun handleTransportData(senderNodeId: Long, receivingNormSession: NormSession,
                                     transportMetadata: TransportMetadata, msgString: String, dataBytes: ByteArray)
    {
        if (isRedirect(transportMetadata) && isDuplicate(dataBytes)) {
            logger.finer("msg is redirect and duplicate, ")
            return
        }
        try {
            val packet = Utils.packetFromString(msgString)
            val tsDifference =
                (System.currentTimeMillis() - transportMetadata.timestamp)
            if (tsDifference > ((receivingNormSession.grttEstimate * 1000) * grttMultiplier)) {
                logger.fine("Adding delay element: msgTstamp:${transportMetadata.timestamp} ts_diff:$tsDifference, " +
                        "grtt:${receivingNormSession.grttEstimate * 1000}")
                Utils.addDelay(
                    packet,
                    transportMetadata.timestamp,
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
                logger.fine("Incoming normSession Obj, remote nodeId $senderNodeId, session nodeId $receivingNormSession")
                processIncomingPacket(packet)
            }
            if (sendingNormSessions.size > 1) {
                logger.fine("Redirecting XMPP message string to other sessions, not $receivingNormSession")
                redirectData(msgString.toByteArray(Charsets.UTF_8), transportMetadata,
                    receivingNormSession, transportMetadata.origSenderId)
            }
        } catch (e: DocumentException) {
            logger.log(Level.SEVERE, "Unable to marshal a stanza from $msgString", e)
        }
    }

    protected fun isRedirect(transportMetadata: TransportMetadata): Boolean {
        return when(transportMetadata.transportType) {
            TransportType.PresenceInitRedirect, TransportType.PresenceProbeRedirect,
            TransportType.PresenceTransportRedirect, TransportType.MessageTransportRedirect -> true
            else -> false
        }
    }


    // [ senderNodeId: Set[dataStrings] ]
    private val sentSessionMessages = mutableMapOf<ByteArray, AtomicInteger>()
    protected fun isDuplicate(dataBytes: ByteArray): Boolean {
        return if (dataBytes !in sentSessionMessages) {
            sentSessionMessages[dataBytes] = AtomicInteger(1)
            false
        } else {
            val numMsg = sentSessionMessages[dataBytes]!!.incrementAndGet()
            if( numMsg >= receivingNormSessions.size) {
                sentSessionMessages.remove(dataBytes)
            }
            true
        }
    }

    override fun processIncomingPacket(packet: Packet) {
        transportPacketProcessor.processPacket(packet.from, packet)
    }

    override fun close() {
        logger.fine("stopping normSessions")
        if (sendingNormSessions.isNotEmpty()) {
            logger.fine("stopping Sending NormSessions")
            for((_, normSession) in sendingNormSessions) {
                normSession.stopSender()
                // normSession.destroySession()
            }
            sendingNormSessions.clear()
        }

        if (receivingNormSessions.isNotEmpty()) {
            logger.fine("stopping Receiving NormSessions")
            for((_, normSession) in receivingNormSessions) {
                normSession.stopReceiver()
                // normSession.destroySession()
            }
            receivingNormSessions.clear()
        }
        logger.fine("stopped senders and receivers")
    }

    override fun getAddressStr(): String {
        return address.hostAddress
    }

    override fun getPort(): Int {
        return port
    }

    override fun toString(): String {
        var transportName = "oneToOneTransport"
        if (roomJID != null) {
            transportName = roomJID!!.toString()
        }
        return "NormTransport:$transportName"
    }
}