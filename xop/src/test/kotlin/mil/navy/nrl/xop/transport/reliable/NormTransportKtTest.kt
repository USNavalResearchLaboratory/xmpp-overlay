package mil.navy.nrl.xop.transport.reliable

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NormTransportKtTest {

    @Test
    fun testTransportMetadataToBytes() {
        val longNum = 1000000000L
        val transportType = TransportType.MessageTransport
        val transportSubType = TransportSubType.Chat
        val transportMetadata = TransportMetadata(longNum, transportType, transportSubType, -1L)

        var bytes = transportMetadataToBytes(transportMetadata, false)

        var regen = getTransportMetadata(bytes, false)
        assertTrue(regen.timestamp == longNum, "regen.timestamp: $longNum != ${regen.timestamp}") //
        assertTrue(
            regen.transportType == transportType,
            "transportType ${regen.transportType} == $transportType"
        )
        assertTrue(
            regen.transportSubType == transportSubType,
            "transportType ${regen.transportSubType} == $transportSubType"
        )


        bytes = transportMetadataToBytes(transportMetadata, true)

        regen = getTransportMetadata(bytes, true)
        assertTrue(regen.timestamp == longNum, "regen.timestamp: $longNum != ${regen.timestamp}") //
        assertTrue(
            regen.transportType == transportType,
            "transportType ${regen.transportType} == $transportType"
        )
        assertTrue(
            regen.transportSubType == transportSubType,
            "transportType ${regen.transportSubType} == $transportSubType"
        )

    }
}