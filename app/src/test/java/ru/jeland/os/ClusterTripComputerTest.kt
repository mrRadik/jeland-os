package ru.jeland.os
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

/** ABI example from native getters/prefab bindings, not claimed as a new vehicle capture. */
class ClusterTripComputerTest {
    private val fixture get()=javaClass.getResource("/cluster-pps/oem-a001-since-start.pps")!!.readText()
    private fun packet(body:String=fixture,status:Int=0)="__JOS_TRIP__\n$body\n__JOS_TRIP_EXIT__:$status\n"
    @Test fun oemSinceStartBindingsReachThePanelWithoutRecording() {
        val batch=ClusterPpsBatch.parse(packet());assertNull(batch.tripComputer.error)
        val p=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=batch.telemetry(19900))),null,null,20000)
        assertEquals(120.6,p.tripKm!!,.0001);assertEquals(6840000L,p.tripMillis);assertEquals(7.2,p.consumption!!,.0001)
        // The fixture deliberately also contains 9876.5 km, 5:22 and 9.8 from other periods.
    }
    @Test fun sourceZerosAreRealValuesRatherThanMissingData() {
        val v=ClusterTripComputer.decode(List(51){0})!!
        assertEquals(0.0,v.distanceKm!!,0.0);assertEquals(0L,v.elapsedMillis);assertEquals(0.0,v.consumption!!,0.0)
    }
    @Test fun nativeInvalidSentinelsDoNotBecomePlausibleMeasurements() {
        val b=ClusterPpsSnapshot.parse(fixture,51)!!.toMutableList()
        for(i in 4..7)b[i]=255
        b[20]=255;b[21]=255;b[28]=255;b[29]=255;b[30]=255
        val v=ClusterTripComputer.decode(b)!!
        assertNull(v.distanceKm);assertNull(v.elapsedMillis);assertNull(v.consumption)
        b[28]=0;b[29]=0;b[30]=60;assertNull(ClusterTripComputer.decode(b)!!.elapsedMillis)
        assertNull(ClusterTripComputer.decode(b.take(42)));assertNull(ClusterTripComputer.decode(b.toMutableList().apply {this[0]=256}))
    }
    @Test fun staleOrFailedOdoFeedNeverBorrowsManualRecordingCounters() {
        for(sample in listOf(ClusterPpsBatch.parse(packet()).telemetry(1000),ClusterPpsBatch.parse(packet(status=1)).telemetry(19900))) {
            val p=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=sample)),
                TripSummary(0,distanceMetres=456000.0,elapsedMillis=999999),null,20000)
            assertNull(p.tripKm);assertNull(p.tripMillis);assertNull(p.consumption)
        }
    }
    @Test fun channelObjectAndCommandCompletionAreValidatedIndependently() {
        for(bad in listOf(packet(fixture.replace("@a001","@a006")),packet(status=1),packet(fixture.replace("\"len\":54","\"len\":53")))) {
            val p=ClusterPpsBatch.parse(bad);assertNull(p.tripComputer.bytes);assertNotNull(p.tripComputer.error)
        }
        val v=ClusterTripComputer.decode(ClusterPpsSnapshot.parse(fixture,51))!!
        assertEquals(6840000L,v.elapsedMillis)
    }
}
