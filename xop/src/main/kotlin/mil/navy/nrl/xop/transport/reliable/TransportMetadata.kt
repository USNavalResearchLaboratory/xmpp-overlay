package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.util.MessageCompressionUtils
import mil.navy.nrl.norm.NormSession
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.*

internal data class NormSessionObj internal constructor(
    val msgString: String,
    val normSession: NormSession,
    val senderNodeId: Long,
    val transportMetadata: TransportMetadata?
)

internal enum class TransportType {
    Control,
    PresenceInit,
    PresenceInitRedirect,
    PresenceTransport,
    PresenceTransportRedirect,
    PresenceProbe,
    PresenceProbeRedirect,
    MUCPresence,
    MUCPresenceRedirect,
    OneToOneTransport,
    MessageTransport,
    MessageTransportRedirect,
    IQTransport,
    Unknown
}

internal enum class TransportSubType {
    Initialization,
    Redirect,
    JSON,
    Presence,
    MUCPresence,
    Chat,
    GroupChat,
    IQ,
    Unknown
}

internal data class TransportMetadata(
    val timestamp: Long,
    val transportType: TransportType,
    val transportSubType: TransportSubType,
    val origSenderId: Long = -1
)

internal fun getTransportMetadata(normInfoBytes: ByteArray?, compression: Boolean): TransportMetadata {
    if (normInfoBytes == null) {
        return TransportMetadata(0L, TransportType.Unknown, TransportSubType.Unknown)
    }

    val byteBuffer = ByteBuffer.wrap(normInfoBytes, 0, normInfoBytes.size)
    var dataBytes = byteBuffer.array()

    if (compression) {
        logger.finer("length BEFORE decompression ${normInfoBytes.size}")
        dataBytes = MessageCompressionUtils.decompressBytes(dataBytes)
        logger.finer("length AFTER decompression ${normInfoBytes.size}")
    }


    val jsonObject = JSONObject(String(dataBytes, 0, byteBuffer.capacity()))
    val transportType = TransportType.valueOf(jsonObject.optString("transportType", "Unknown"))
    val transportSubType = TransportSubType.valueOf(jsonObject.optString("transportSubType", "Unknown"))
    val origSenderId = jsonObject.optLong("origSenderId", -1)
    val ts = jsonObject.optLong("timestamp", 0)
    return TransportMetadata(ts, transportType, transportSubType, origSenderId)
}

internal fun transportMetadataToBytes(transportMetadata: TransportMetadata, compression: Boolean): ByteArray {
    val jsonObject = JSONObject(transportMetadata)
    var dataBytes = jsonObject.toString().toByteArray()
    if (compression) {
        logger.finer("info length before compression " + dataBytes.size)
        dataBytes = MessageCompressionUtils.compressBytes(dataBytes)
        logger.finer("info length after compression " + dataBytes.size)
    }
    return dataBytes
}

internal fun transportMetadataTimestamp(transportMetadata: TransportMetadata): Date {
    return Date(transportMetadata.timestamp)
}