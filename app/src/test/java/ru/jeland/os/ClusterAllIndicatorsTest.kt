package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class ClusterAllIndicatorsTest {
    private fun frame(vararg bits:Int)=MutableList(49){0}.apply {
        this[39]=255;this[40]=255
        for(i in bits)this[i/8]=this[i/8] or (1 shl(i%8))
    }
    @Test fun everyNamedNativeSlotDecodesIndividuallyAndHasAPresentation() {
        val definitions=ClusterIndicatorCatalog.definitions
        assertEquals(130,definitions.size);assertEquals(130,definitions.map {it.bit}.toSet().size)
        assertEquals(126,definitions.map {it.key}.toSet().size)
        for(icon in definitions) {
            assertTrue(icon.bit in 0..244)
            val decoded=QnxClusterTelemetryDecoder.warnings(frame(icon.bit))!!
            assertEquals("slot ${icon.bit}",listOf(icon),decoded.indicators)
            assertTrue(icon.asset?.startsWith("cluster/oem/")==true||!icon.text.isNullOrBlank())
            if(icon.area!=ClusterIndicatorArea.AUXILIARY) {
                val slots=ClusterIndicatorLayout.place(decoded.indicators,false)
                assertEquals(listOf(icon),slots.map {it.indicator})
            }
        }
    }
    @Test fun duplicatesAreMergedButReservedAndMetadataBitsNeverBecomeIcons() {
        assertEquals(1,ClusterIndicatorCatalog.active(frame(47,63)).size)
        assertTrue(ClusterIndicatorCatalog.active(frame(0,1,49,245,248,310)).isEmpty())
        assertTrue(ClusterIndicatorCatalog.active(frame()).isEmpty())
    }
    @Test fun simultaneousWarningsWrapWithoutDroppingOrClippingAnyNamedState() {
        val active=ClusterIndicatorCatalog.active(frame(*ClusterIndicatorCatalog.definitions.map {it.bit}.toIntArray()))
        assertEquals(126,active.size)
        for(message in listOf(false,true)) {
            val slots=ClusterIndicatorLayout.place(active,message)
            assertEquals(active.filter {it.area!=ClusterIndicatorArea.AUXILIARY}.map {it.key}.toSet(),slots.map {it.indicator.key}.toSet())
            assertEquals(slots.size,slots.map {it.indicator.key}.toSet().size)
            slots.forEach {s->
                assertTrue(s.x>=0&&s.y>=0);assertTrue(s.x+s.size<=2048&&s.y+s.size<=768)
                assertTrue(s.size>=54)
            }
            for((i,a) in slots.withIndex())for(b in slots.drop(i+1))
                assertFalse("${a.indicator.key} overlaps ${b.indicator.key}",
                    a.x<b.x+b.size&&a.x+a.size>b.x&&a.y<b.y+b.size&&a.y+a.size>b.y)
        }
    }
    @Test fun auxiliaryLampsUseTheGaugeRatherThanDuplicateTheFooter() {
        val hot=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,
            clusterTelemetry=QnxClusterTelemetry(warnings=RawVehicleSample(frame(30,33),19_900)))),null,null,20_000)
        assertEquals(ClusterCoolantCondition.HOT,hot.coolantCondition)
        assertTrue(ClusterLamp.COOLANT_HOT in hot.lamps);assertTrue(ClusterLamp.RESERVE in hot.lamps)
        assertTrue(ClusterIndicatorLayout.place(hot.indicators!!,false).isEmpty())
    }
    @Test fun nativeCatalogueDisappearsOnStalenessAndKeepsAnExplicitEmptyFrame() {
        fun panel(bits:List<Int>,at:Long)=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,
            clusterTelemetry=QnxClusterTelemetry(warnings=RawVehicleSample(bits,at)))),null,null,20_000)
        assertEquals(emptyList<ClusterIndicatorSpec>(),panel(frame(),19_900).indicators)
        assertNull(panel(frame(18),1000).indicators)
    }
}
