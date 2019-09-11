package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.net.transport.XOPTransportService
import edu.drexel.xop.packet.TransportPacketProcessor
import edu.drexel.xop.util.MessageCompressionUtils
import edu.drexel.xop.util.XOP
import edu.drexel.xop.util.logger.LogUtils
import mil.navy.nrl.norm.NormObject
import mil.navy.nrl.norm.NormSession
import org.xmpp.packet.JID
import org.xmpp.packet.Packet
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.logging.Level

open class NormTransport internal constructor(
    internal val normSession: NormSession,
    private val address: InetAddress,
    private val port: Int,
    private val transportPacketProcessor: TransportPacketProcessor,
    protected val compression: Boolean
) : XOPTransportService {
    /*
    TODO: 20180928 unnecessarily passes in the address and port into the constructor for compatibility with
      interface that ProtoSD uses.
    */


    private val randGen = Random(System.currentTimeMillis())

    companion object {
        @JvmStatic
        private var logger = LogUtils.getLogger(NormTransport::class.java.name)
    }

    private var roomJID: JID? = null // if this is null, this is the one-to-one NORM session

    protected var running = true

    enum class TransportType {
        Control,
        PresenceTransport,
        PresenceProbe,
        MUCPresence,
        MessageTransport,
        IQTransport,
        Unknown
    }

    init {
        logger.fine("compression enabled: $compression")
    }

    protected fun startNormSession(session: NormSession) {
        val randInt = randGen.nextInt()

        logger.fine("-- creating new session: ${session.localNodeId} with randInt: $randInt")
        val receiverBufferSpace = XOP.TRANSPORT.NORM.RCVBUFFERSPACE //256 * 256;

        val senderBufferSpace = XOP.TRANSPORT.NORM.SENDBUFFERSPACE //256 * 256;
        val segmentSize = XOP.TRANSPORT.NORM.SEGMENTSIZE //1400;
        val blockSize = XOP.TRANSPORT.NORM.BLOCKSIZE //64;
        val numParity = XOP.TRANSPORT.NORM.NUMPARITY //16;
        session.startSender(randInt, senderBufferSpace, segmentSize, blockSize, numParity)

        //session.setRxCacheLimit(txCacheMax)

        session.startReceiver(receiverBufferSpace)
        logger.fine("Started new NORM session.")
    }

    override fun sendPacket(packet: Packet) {
        val transportType = TransportType.MessageTransport
        logger.fine("Sending packet {{${packet.toXML()}}} from normSession: $normSession")
        val dataBytes = packet.toXML().toByteArray()
        sendData(dataBytes, transportType)
    }

    /**
     * for sending presence messages
     * @param data the data to be sent
     * @param transportType either Presence or Message transport types
     */
    fun sendData(data: ByteArray, transportType: TransportType): NormObject? {
        var data1 = data
        val transportMetadata = TransportMetadata(System.currentTimeMillis(), transportType)
        try {
            if (compression) {
                logger.finer("length before compression " + data1.size)
                data1 = MessageCompressionUtils.compressBytes(data)
                logger.finer("length after compression " + data1.size)
            }
            val bb = ByteBuffer.allocateDirect(data1.size + 1)
            bb.put(data1)

            val info = transportMetadataToBytes(transportMetadata)
            return normSession.dataEnqueue(bb, 0, data.size, info, 0, info.size)
        } catch (e: IOException) {
            logger.log(Level.SEVERE, "Could not send data with NormTransport.", e)
        }
        return null
    }

    override fun processIncomingPacket(packet: Packet) {
        transportPacketProcessor.processPacket(packet.from, packet)
        //
        //        if (roomJID != null) {
        //
        //            //IncomingPacketProcessor.processPacketForRoom(roomJID, packet)
        //            // room.processIncomingPacket((Message) packet);
        //
        //            // The MUC to: field was the generic room
        //            val roomManager = XOProxy.getInstance().getRoomManager(roomJID!!.getDomain())
        //            val room = roomManager.getRoom(roomJID)
        //            room.handleIncomingMessage(packet)
        //
        //        } else { // send one-to-one chat or presence or an IQ
        //            val clientManager = XOProxy.getInstance().clientManager
        //            logger.finer("Processing incoming packet: {{$packet}}")
        //            logger.fine("Local ClientJIDs: " + clientManager.localClientJIDs)
        //            //only forward to local members if they are on this local XOP instance
        //            if (packet is Message) {
        //                if (clientManager.isLocal(packet.getTo())) {
        //                    logger.finer("Incoming XMPP one-to-one message Transport: $packet")
        //                }
        //            } else {
        //                // if (packet is Presence)
        //                //             } else if (packet is IQ) {
        //                packetProcessor.processPacket(packet.from, packet, false)
        //            }
        //        }
    }

    override fun close() {
        logger.fine("stopping sender and receivers in norm session")
        normSession.stopSender()
        normSession.stopReceiver()
        normSession.destroySession()
        logger.fine("stopped.")
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