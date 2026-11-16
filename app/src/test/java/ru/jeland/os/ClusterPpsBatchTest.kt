package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class ClusterPpsBatchTest {
    private fun fixture(name:String)=javaClass.getResource("/cluster-pps/$name.pps")!!.readText()
    private fun batch(gauge:String=fixture("a000"),warnings:String=fixture("a00d"),gaugeExit:Int=0,warningExit:Int=0)=
        "__JOS_GAUGE__\n$gauge\n__JOS_GAUGE_EXIT__:$gaugeExit\n__JOS_WARNINGS__\n$warnings\n__JOS_WARNINGS_EXIT__:$warningExit\n"
    @Test fun nativeTypedPpsSnapshotReachesTheVisiblePanelIncludingAllFields() {
        val native=ClusterPpsBatch.parse(batch()).telemetry(19_900)
        assertNull(native.error)
        val car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=native)
        val p=ClusterPanelState.from(UiState(car=car),null,null,20_000)
        assertEquals(920.0,p.rpm!!,.001);assertEquals(0.0,p.speed!!,.001);assertNull(p.coolant)
        assertTrue(p.lamps.containsAll(setOf(ClusterLamp.POSITION,ClusterLamp.EPB,ClusterLamp.BELT,ClusterLamp.START_STOP_UNAVAILABLE)))
        assertEquals(5,p.indicators!!.size);assertEquals(ClusterCoolantCondition.COLD,p.coolantCondition)
        assertEquals(178,p.message!!.id)
        assertEquals(ClusterNativeFeedHealth(true,true),car.clusterNativeFeedHealth(20_000))
    }
    @Test fun stringTypedArrayThatTheOldReaderExpectedIsNotTheNativeJsonArray() {
        val native=ClusterPpsBatch.parse(batch(gauge=fixture("a000").replace("data:json:","data::")))
        assertNull(native.gauge.bytes);assertNotNull(native.gauge.error)
        assertNotNull(native.warnings.bytes)
    }
    @Test fun nonpersistentObjectsAndAttributesKeepTheirJsonType() {
        val g=fixture("a000").replace("@a000","[n]@a000").replace("data:json:","[7n]data:json:")
        val w=fixture("a00d").replace("data:json:","[ni]data:json:")
        val native=ClusterPpsBatch.parse(batch(gauge=g,warnings=w))
        assertNotNull(native.gauge.bytes);assertNotNull(native.warnings.bytes)
        assertNull(native.telemetry(20_000).error)
    }
    @Test fun shellReadFailureReportsTheExactChannelAndDoesNotInventData() {
        val result=ClusterPpsBatch.parse(batch(gauge="cat: /tmp/pps/spi_service/a000: Permission denied",gaugeExit=1))
        assertNull(result.gauge.bytes);assertEquals("read command exit=1",result.gauge.error)
        assertTrue(result.gauge.snapshot.contains("Permission denied"));assertNotNull(result.warnings.bytes)
        val car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=result.telemetry(19_900))
        assertEquals(ClusterNativeFeedHealth(false,true),car.clusterNativeFeedHealth(20_000))
        val p=ClusterPanelState.from(UiState(car=car),null,null,20_000)
        assertNull(p.rpm);assertTrue(ClusterLamp.EPB in p.lamps)
    }
    @Test fun wrongObjectTruncationAndDuplicateChannelsAreRejected() {
        assertNull(ClusterPpsBatch.parse(batch(gauge=fixture("a000").replace("@a000","@a00d"))).gauge.bytes)
        assertNull(ClusterPpsBatch.parse(batch()+batch()).gauge.bytes)
        assertNull(ClusterPpsBatch.parse(batch().substringBefore("__JOS_WARNINGS_EXIT__:")).warnings.bytes)
        assertNull(ClusterPpsSnapshot.parse(fixture("a000")+"-data\n",26))
        assertNull(ClusterPpsSnapshot.parse(fixture("a000")+fixture("a000"),26))
    }
    @Test fun CRLFAndExtraAttributesAreAllowedWithoutGuessingByteCounts() {
        val text=batch(gauge=fixture("a000")+"sequence:n:41\n").replace("\n","\r\n")
        assertEquals(26,ClusterPpsBatch.parse(text).gauge.bytes!!.size)
        val invalid=fixture("a00d").replace("data:json:[4,","data:json:[-129,")
        assertNull(ClusterPpsBatch.parse(batch(warnings=invalid)).warnings.bytes)
    }

    @Test fun signedOctetsAndTrailingNativePaddingPreserveTheAbiPrefix() {
        fun signed(pps:String)=Regex("-?[0-9]+").replace(pps.substringAfter("data:json:")) {m->
            val n=m.value.toInt();(if(n in 128..255)n-256 else n).toString()
        }
        val g="@a000\ndata:json:"+signed(fixture("a000")).trim().removeSuffix("]")+",0,0]\n"
        val w="@a00d\ndata:json:"+signed(fixture("a00d")).trim().removeSuffix("]")+",0,0,0]\n"
        val parsed=ClusterPpsBatch.parse(batch(gauge=g,warnings=w)).telemetry(19_900)
        assertNull(parsed.error)
        val p=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=parsed)),null,null,20_000)
        assertEquals(920.0,p.rpm!!,.001);assertEquals(ClusterCoolantCondition.COLD,p.coolantCondition)
        assertTrue(ClusterLamp.START_STOP_UNAVAILABLE in p.lamps);assertEquals(178,p.message!!.id)
    }
}
