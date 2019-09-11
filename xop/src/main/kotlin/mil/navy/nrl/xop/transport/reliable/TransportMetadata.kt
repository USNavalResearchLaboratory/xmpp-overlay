package mil.navy.nrl.xop.transport.reliable

import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.*

internal data class TransportMetadata(
    val timestamp: Long,
    val transportType: NormTransport.TransportType
)

internal fun getTransportMetadata(normInfoBytes: ByteArray?): TransportMetadata {
    if (normInfoBytes == null) {
        return TransportMetadata(0L, NormTransport.TransportType.Unknown)
    }

    val byteBuffer = ByteBuffer.wrap(normInfoBytes, 0, normInfoBytes.size)
    val dataBytes = byteBuffer.array()

    val jsonObject = JSONObject(String(dataBytes, 0, byteBuffer.capacity()))
    val transportType = NormTransport.TransportType.valueOf(jsonObject.optString("transportType"))
    val ts = jsonObject.optLong("timestamp")
    return TransportMetadata(ts, transportType)
}

internal fun transportMetadataToBytes(transportMetadata: TransportMetadata): ByteArray {
    val jsonObject = JSONObject(transportMetadata)
    return jsonObject.toString().toByteArray()
}

internal fun transportMetadataTimestamp(transportMetadata: TransportMetadata): Date {
    return Date(transportMetadata.timestamp)
}
