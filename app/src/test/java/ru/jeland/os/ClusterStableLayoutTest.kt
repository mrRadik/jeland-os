package ru.jeland.os
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class ClusterStableLayoutTest {
    private fun icons(vararg bits:Int)=ClusterJ7Indicators.presentation(ClusterIndicatorCatalog.definitions.filter {it.bit in bits})
    private fun position(list:List<ClusterIndicatorSpec>,bit:Int,design:ClusterDesign,message:Boolean=false)=
        ClusterIndicatorLayout.place(list,message,design).single {it.indicator.bit==bit}.let {Triple(it.x,it.y,it.size)}
    @Test fun permanentIconsNeverMoveWhenOtherLampsMessagesOrDesignsChange() {
        val stable=listOf(2,3,7,84,123,18,107,37,151,137,27,38,42,45,52,59,22,32,21,17,23)
        for(bit in stable) {
            val alone=position(icons(bit),bit,ClusterDesign.CLASSIC)
            for(design in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION))for(message in listOf(false,true)) {
                val all=icons(*stable.toIntArray())
                assertEquals("$bit $design $message",alone,position(all,bit,design,message))
                assertEquals(alone,position(all.reversed(),bit,design,message))
            }
        }
        // Belt flash off is absent, not replaced by a neighbour sliding into its slot.
        val on=icons(18,107,37);val off=icons(18,37)
        for(bit in listOf(18,37))assertEquals(position(on,bit,ClusterDesign.CLASSIC),position(off,bit,ClusterDesign.CLASSIC))
    }
    @Test fun oemStateVariantsShareTheirFamilySlot() {
        for(family in listOf(listOf(18,19),listOf(36,37),listOf(20,151,152),listOf(4,5,6),
            listOf(38,39),listOf(40,41,42,204),listOf(43,44,45,143,203),listOf(50,51,52,53,54,73,146))) {
            val expected=position(icons(family.first()),family.first(),ClusterDesign.CLASSIC)
            for(bit in family)assertEquals(expected,position(icons(bit),bit,ClusterDesign.CLASSIC))
        }
    }
    @Test fun fallbackAndNativeUseTheSameBeltParkingBrakeAndFogSlots() {
        val lamps=setOf(ClusterLamp.BELT,ClusterLamp.EPB,ClusterLamp.REAR_FOG)
        val fallback=ClusterJ7Indicators.presentation(ClusterJ7Indicators.fallback(lamps))
        val native=icons(107,18,7)
        for(bit in listOf(107,18,7))assertEquals(position(native,bit,ClusterDesign.CLASSIC),position(fallback,bit,ClusterDesign.CLASSIC))
    }
    @Test fun startupGoesToMaximumAndReturnsToLiveTargetsWithoutChangingDigitalValues() {
        assertEquals(ClusterStartupNeedles(0f,0f),ClusterStartupSweep.sample(0,90f,1600f))
        assertEquals(ClusterStartupNeedles(220f,8000f),ClusterStartupSweep.sample(650,90f,1600f))
        val returning=ClusterStartupSweep.sample(1200,90f,1600f)!!
        assertEquals(155f,returning.speed,.001f);assertEquals(4800f,returning.rpm,.001f)
        assertNull(ClusterStartupSweep.sample(1600,90f,1600f));assertNull(ClusterStartupSweep.sample(-1,0f,0f))
        val late=ClusterStartupSweep.sample(1599,30f,800f)!!
        assertEquals(30f,late.speed,.01f);assertEquals(800f,late.rpm,.05f)
    }
}
