package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class ClusterTelemetryTest {
    private fun gauge(rpm:Int=920,speed:Int=0,temp:Int=12)=MutableList(26){0}.apply {
        word(2,rpm);word(6,speed);word(8,temp);word(10,temp)
    }
    private fun MutableList<Int>.word(at:Int,value:Int){this[at]=value and 255;this[at+1]=value ushr 8}
    private fun warning(id:Int=65535,vararg bits:Int)=MutableList(49){0}.apply {
        word(39,id);for(i in bits)this[i/8]=this[i/8] or (1 shl(i%8))
    }
    private fun state(g:List<Int>?,w:List<Int>?,at:Long=19_900)=UiState(car=VehicleLinkState(
        demo=false,connected=true,clusterTelemetry=QnxClusterTelemetry(
            g?.let {RawVehicleSample(it,at)},w?.let {RawVehicleSample(it,at)})))

    @Test fun nativeGaugeUsesDigitalLittleEndianWordsAndValidZero() {
        val s=ClusterPanelState.from(state(gauge(),warning()),null,null,20_000)
        assertEquals(0.0,s.speed!!,.001);assertEquals(920.0,s.rpm!!,.001);assertNull(s.coolant);assertEquals(.012,s.coolantLevel!!,.00001)
        assertNull(s.message)
        val accelerating=ClusterPanelState.from(state(gauge(3650,97,91),warning()),null,null,20_000)
        assertEquals(3650.0,accelerating.rpm!!,.001);assertEquals(97.0,accelerating.speed!!,.001)
        assertNull(accelerating.coolant);assertEquals(.091,accelerating.coolantLevel!!,.00001)
    }
    @Test fun rawAndroidRpmHypothesisIsNotUsedWhenNativeRpmIsMissing() {
        val s=state(null,null).let {it.copy(car=it.car.copy(raw=mapOf(
            VdsKey(VerifiedVehicleProtocol.INFO,62) to RawVehicleSample(listOf(0,239),19_900)) ))}
        assertNull(ClusterPanelState.from(s,null,null,20_000).rpm)
    }
    @Test fun capturedStockIndicatorsKeepTheirSeparateColoursAndClear() {
        val active=ClusterPanelState.from(state(gauge(),warning(65535,2,18,31,37,107)),null,null,20_000)
        assertEquals(setOf(ClusterLamp.POSITION,ClusterLamp.EPB,ClusterLamp.START_STOP_UNAVAILABLE,ClusterLamp.BELT),active.lamps)
        assertEquals(ClusterCoolantCondition.COLD,active.coolantCondition)
        assertFalse(ClusterLamp.START_STOP_READY in active.lamps)
        val cleared=ClusterPanelState.from(state(gauge(),warning()),null,null,20_000)
        assertTrue(cleared.lamps.isEmpty());assertEquals(ClusterCoolantCondition.UNKNOWN,cleared.coolantCondition)
    }
    @Test fun safetyIndicatorsDecodeIndependentlyWithoutMappingAndroidFaultFragments() {
        val w=warning(65535,19,22,23,24,25,26,30,32,145,152)
        val result=QnxClusterTelemetryDecoder.warnings(w)!!
        assertTrue(result.lamps.containsAll(setOf(ClusterLamp.EPB_FAULT,ClusterLamp.BRAKE,ClusterLamp.ABS,
            ClusterLamp.ESP,ClusterLamp.ESP_OFF,ClusterLamp.AIRBAG,ClusterLamp.COOLANT_HOT,
            ClusterLamp.OIL_PRESSURE,ClusterLamp.EPS_RED,ClusterLamp.AUTO_HOLD_FAULT)))
        assertEquals(ClusterCoolantCondition.HOT,result.coolant)
        assertNull(QnxClusterTelemetryDecoder.warnings(listOf(0,160)))
    }
    @Test fun nativeWarningIdUsesTheOemIndirectionAndExplicitClear() {
        val notification=ClusterPanelState.from(state(null,warning(178)),null,null,20_000).message!!
        assertEquals("Пожалуйста, нажмите на педаль тормоза",notification.russian)
        assertEquals("Please Step On Brake",notification.english)
        // Native 0 is a real remote-start message, not a 'no messages' sentinel.
        assertTrue(QnxClusterTelemetryDecoder.warnings(warning(0))!!.message!!.russian.startsWith("ДВС запущен"))
        assertNull(QnxClusterTelemetryDecoder.warnings(warning(65535))!!.message)
        assertNull(QnxClusterTelemetryDecoder.warnings(warning(65400))!!.message)
    }
    @Test fun staleDisconnectedAndIncompleteNativeFramesDoNotStickOrBecomeNormal() {
        for(s in listOf(state(gauge(),warning(178,18),at=1000),
            state(gauge(),warning(178,18)).let {it.copy(car=it.car.copy(connected=false))},
            state(gauge().dropLast(1),warning(178,18).dropLast(1)))) {
            val p=ClusterPanelState.from(s,null,null,20_000)
            assertNull(p.speed);assertNull(p.rpm);assertNull(p.coolant);assertNull(p.message)
            assertTrue(p.lamps.isEmpty())
        }
        val invalid=gauge().apply {word(2,65535);word(6,65535);word(10,65535)}
        val p=ClusterPanelState.from(state(invalid,warning()),null,null,20_000)
        assertNull(p.speed);assertNull(p.rpm);assertNull(p.coolant)
    }
    @Test fun cachedCarInfoZeroGearAndDistancesSurviveBetweenUiTicks() {
        val readings=mapOf(119 to listOf(0,0),26 to listOf(1),71 to listOf(0,0,6,204),72 to listOf(1,121),1 to listOf(1))
        for(age in listOf(0L,1L,999L,4999L,7999L)) {
            val raw=readings.mapKeys {VdsKey(VerifiedVehicleProtocol.INFO,it.key)}.mapValues {RawVehicleSample(it.value,20_000)}
            val p=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,raw=raw)),null,null,20_000+age)
            assertEquals(0.0,p.speed!!,.001);assertEquals("P",p.gear)
            assertEquals(1740.0,p.odometer!!,.001);assertEquals(377.0,p.rangeKm!!,.001)
            assertTrue(ClusterLamp.POSITION in p.lamps)
        }
    }
    @Test fun ppsRequiresOneCompleteTypedNativePrefix() {
        val bytes=gauge();val pps="@a000\ndata:json:[${bytes.joinToString(",")}]\n"
        assertEquals(bytes,ClusterPpsSnapshot.parse(pps,26))
        for(bad in listOf(pps+pps,pps.replace("data:json:","data::"),pps.replace("data:json:[0,","data:json:[256,"),"data:json:[1,2"))
            assertNull(ClusterPpsSnapshot.parse(bad,26))
    }
}
