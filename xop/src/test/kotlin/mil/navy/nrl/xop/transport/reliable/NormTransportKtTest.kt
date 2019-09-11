package mil.navy.nrl.xop.transport.reliable

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NormTransportKtTest {

    @Test
    fun testTransportMetadataToBytes() {
        val longNum = 1000000000L
        val transportType = NormTransport.TransportType.MessageTransport
        val transportMetadata = TransportMetadata(longNum, transportType)

        val bytes = transportMetadataToBytes(transportMetadata)

        val regen = getTransportMetadata(bytes)
        assertTrue(regen.timestamp == longNum, "regen.timestamp: $longNum != ${regen.timestamp}") //
        assertTrue(
            regen.transportType == NormTransport.TransportType.MessageTransport,
            "transportType ${regen.transportType} == $transportType"
        )
    }
}