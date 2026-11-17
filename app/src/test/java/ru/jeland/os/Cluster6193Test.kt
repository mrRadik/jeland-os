package ru.jeland.os
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

/** Actual APK110 trace; target cases separately pinned to the installed OEM binary. */
class Cluster6193Test {
    private fun rows(kind:String)=javaClass.getResource("/cluster-pps/field-6193.csv")!!.readText()
        .lineSequence().filter {it.startsWith("$kind,")}.map {line->line.split(',').drop(2).map(String::toInt)}.toList()
    private fun frame(vararg bits:Int)=MutableList(49){0}.apply {
        this[39]=255;this[40]=255;for(b in bits)this[b/8]=this[b/8] or (1 shl(b%8))
    }
    private fun panel(w:List<Int>,aux:List<Int>?=null,at:Long=19_900):ClusterPanelState {
        val c=VehicleLinkState(demo=false,connected=true,raw=mapOf(
            VdsKey(VerifiedVehicleProtocol.INFO,33) to RawVehicleSample(listOf(1),19_900),
            VdsKey(VerifiedVehicleProtocol.INFO,32) to RawVehicleSample(listOf(1),19_900)),
            clusterTelemetry=QnxClusterTelemetry(warnings=RawVehicleSample(w,at),auxiliary=aux?.let {RawVehicleSample(it,at)}))
        return ClusterPanelState.from(UiState(car=c),null,null,20_000)
    }
    @Test fun capturedLIMFlagsAreNotLostInTheGenericLampCatalogue() {
        val warnings=rows("warnings")
        assertEquals(145,warnings.size)
        assertEquals(53,warnings.count {ClusterLimiter.decode(it,null)!=null})
        for(w in warnings)assertEquals(ClusterLimiter.decode(w,null),panel(w).limiter)
        assertEquals(setOf(false,true),warnings.mapNotNull {ClusterLimiter.decode(it,null)?.flashing}.toSet())
    }
    @Test fun oemAslStatesAndTargetUseSeparateNativeFields() {
        // RcMAdasCruiseLimitUpdate 0x184568; getEMS2 0x1758a8; installed sva.cluster
        // SHA256 be5033c9ecb0212076940382b60b0ae34b48df5c852e05f7694c510bc9e7fe6c.
        // Generated target cases exercise OEM format, they are not physical-capture claims.
        fun aux(packed:Int)=MutableList(52){0}.apply {this[48]=packed ushr 5;this[49]=(packed and 31) shl 3}
        for((bit,state)in listOf(68 to 3,77 to 4,78 to 1)) {
            val lim=ClusterLimiter.decode(frame(bit),aux(90*16))!!
            assertEquals(state,lim.state);assertEquals(90,lim.targetKmh)
            assertEquals(bit==77,lim.flashing)
            assertEquals(150,ClusterLimiter.decode(frame(bit),aux(180*16))!!.targetKmh)
            assertNull(ClusterLimiter.decode(frame(bit),aux(0x1fff))!!.targetKmh)
            assertNull(ClusterLimiter.decode(frame(bit),aux(0))!!.targetKmh)
        }
        assertNull(ClusterLimiter.decode(frame(47,63),aux(90*16)))
        assertNull(ClusterLimiter.decode(frame(78).dropLast(1),aux(90*16)))
        assertNull(panel(frame(78),aux(90*16),1000).limiter)
    }
    @Test fun nativeTurnPhaseOverridesOtherSourcesIncludingExplicitOff() {
        for(w in rows("warnings")) {
            val expected=QnxClusterTelemetryDecoder.warnings(w)!!.lamps.intersect(setOf(ClusterLamp.LEFT,ClusterLamp.RIGHT))
            assertEquals(expected,panel(w).lamps.intersect(setOf(ClusterLamp.LEFT,ClusterLamp.RIGHT)))
        }
        assertTrue(panel(frame()).lamps.intersect(setOf(ClusterLamp.LEFT,ClusterLamp.RIGHT)).isEmpty())
        assertTrue(panel(frame(0,1)).lamps.containsAll(setOf(ClusterLamp.LEFT,ClusterLamp.RIGHT)))
        assertTrue(panel(frame(),at=1000).lamps.containsAll(setOf(ClusterLamp.LEFT,ClusterLamp.RIGHT)))
    }
    @Test fun capturedCoolantIsAnOemScalePositionAndNeverInventedCelsius() {
        val samples=rows("gauge").map {QnxClusterTelemetryDecoder.gauge(it)!!}
        assertEquals(89,samples.size)
        assertTrue(samples.all {it.coolant==null})
        assertEquals(0.0,samples.minOf {it.coolantLevel!!},.00001)
        assertEquals(.220,samples.maxOf {it.coolantLevel!!},.00001)
    }
    @Test fun allOemDrivingModesHaveReadableLabelsInsteadOfSmallEnglishBitmaps() {
        for((bit,mode)in listOf(61 to ClusterDriveMode.ECO,62 to ClusterDriveMode.SPORT,82 to ClusterDriveMode.COMFORT,
            132 to ClusterDriveMode.SNOW,133 to ClusterDriveMode.MUD,134 to ClusterDriveMode.SAND,150 to ClusterDriveMode.OFFROAD)) {
            val p=panel(frame(bit));assertEquals(mode,p.driveMode)
            assertTrue(ClusterJ7Indicators.presentation(p.indicators!!).none {it.key.startsWith("LED_DrivingMode")})
        }
    }
    @Test fun actualDriverAssistanceLampsUseTheFooterWithoutCoveringModeOrMessages() {
        val text=javaClass.getResource("/cluster-pps/field-warm.txt")!!.readText()
        val source=QnxClusterTelemetryDecoder.warnings(ClusterPpsBatch.parse(text).warnings.bytes)!!.indicators
        val icons=ClusterJ7Indicators.presentation(source)
        assertEquals(setOf(38,42,45),icons.filter {it.area==ClusterIndicatorArea.FOOTER}.map {it.bit}.toSet())
        for(d in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
            val normal=ClusterIndicatorLayout.place(icons,false,d)
            val bottom=normal.filter {it.y==694f}
            assertEquals(3,bottom.size)
            assertTrue(bottom.all {!it.header&&it.x>=530&&it.x+it.size<=1518})
            assertTrue(bottom.all {it.x+it.size<862||it.x>1186})
            assertTrue(normal.filter {it.header}.map {it.indicator.bit}.containsAll(listOf(2,18,37,107)))
            val popup=ClusterIndicatorLayout.place(icons,true,d)
            assertEquals(normal.map {it.indicator.key}.toSet(),popup.map {it.indicator.key}.toSet())
            assertEquals(normal,popup)
        }
        val all=ClusterJ7Indicators.presentation(ClusterIndicatorCatalog.definitions.distinctBy {it.key})
        for(message in listOf(false,true))for(d in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
            val slots=ClusterIndicatorLayout.place(all,message,d)
            assertEquals(all.filter {it.area!=ClusterIndicatorArea.AUXILIARY}.map {it.key}.toSet(),slots.map {it.indicator.key}.toSet())
            assertTrue(slots.all {it.x>=0&&it.y>=0&&it.x+it.size<=2048&&it.y+it.size<=768})
            for((i,a)in slots.withIndex())for(b in slots.drop(i+1))assertFalse("Overlapping lamps: ${a.indicator.key} and ${b.indicator.key}",
                a.x<b.x+b.size&&a.x+a.size>b.x&&a.y<b.y+b.size&&a.y+a.size>b.y)
        }
    }
    @Test fun sportLampRackStaysOutsideTheCentralInstrument() {
        val active=ClusterIndicatorCatalog.definitions.filter {it.area==ClusterIndicatorArea.SECONDARY}
            .filter { ClusterIndicatorLayout.place(listOf(it),false,ClusterDesign.SPORT).single().y==628f }.take(14)
        val rack=ClusterIndicatorLayout.place(active,false,ClusterDesign.SPORT)
        assertEquals(14,rack.size)
        assertTrue(rack.all {it.x+it.size<718f||it.x>1330f})
        assertTrue(rack.all {it.y==628f})
    }
}
