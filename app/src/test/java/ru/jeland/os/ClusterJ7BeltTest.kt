package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class ClusterJ7BeltTest {
    private fun native(vararg bits:Int)=ClusterIndicatorCatalog.definitions.filter {it.bit in bits}
    @Test fun driverPassengerAndCombinedStateUseExactlyOneOriginalIconWithoutSeatCaptions() {
        for(bits in listOf(intArrayOf(107),intArrayOf(108),intArrayOf(200),intArrayOf(107,108,200,9,10))) {
            val icons=ClusterJ7Indicators.presentation(native(*bits))
            assertEquals(1,icons.size)
            assertEquals("cluster/oem/indicator/LED_SeatBelt-Red.png",icons.single().asset)
            assertNull(icons.single().captionRu);assertNull(icons.single().captionEn)
        }
    }
    @Test fun passengerWarningFlashesSharedIconAndDriverAloneIsSteady() {
        assertFalse(ClusterJ7Indicators.passengerWarning(native(107)))
        assertTrue(ClusterJ7Indicators.passengerWarning(native(108)))
        assertTrue(ClusterJ7Indicators.passengerWarning(native(200)))
        assertTrue(ClusterJ7Indicators.passengerWarning(native(107,108)))
        assertFalse(ClusterJ7Indicators.passengerWarning(native()))
        assertTrue(ClusterJ7Indicators.presentation(native()).isEmpty())
    }
    @Test fun capturedPacketDoesNotProduceAnExtraSecondRowSeatIcon() {
        val text=javaClass.getResource("/cluster-pps/field-warm.txt")!!.readText()
        val decoded=QnxClusterTelemetryDecoder.warnings(ClusterPpsBatch.parse(text).warnings.bytes)!!
        assertTrue(decoded.indicators.any {it.bit==9}) // captured generic slot, NOT a J7 rear-seat telltale
        val icons=ClusterJ7Indicators.presentation(decoded.indicators)
        assertEquals(1,icons.count {it.key.contains("SeatBelt")})
        assertFalse(icons.any {it.bit==9})
        assertTrue(icons.any {it.bit==18}) // EPB remains independent
    }
}
