package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.PersistentInterfaceRepository

class ClusterTripPeriodsTest {
    private fun bytes()=ClusterPpsSnapshot.parse(javaClass.getResource("/cluster-pps/oem-a001-since-start.pps")!!.readText(),51)!!
    @Test fun bothPeriodsUseTheSeparateOemGettersAndPrefabBindings() {
        val b=bytes()
        val start=ClusterTripComputer.decode(b,ClusterTripPeriod.SINCE_START)!!
        val reset=ClusterTripComputer.decode(b,ClusterTripPeriod.SINCE_RESET)!!
        assertEquals(120.6,start.distanceKm!!,0.0001);assertEquals(6840000L,start.elapsedMillis);assertEquals(7.2,start.consumption!!,0.0001)
        assertEquals(9876.5,reset.distanceKm!!,0.0001);assertEquals(19320000L,reset.elapsedMillis);assertEquals(9.8,reset.consumption!!,0.0001)
        val s=UiState(preferences=InterfacePreferences(clusterTripPeriod=ClusterTripPeriod.SINCE_RESET),
            car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=QnxClusterTelemetry(tripComputer=RawVehicleSample(b,19900))))
        val panel=ClusterPanelState.from(s,null,null,20000)
        assertEquals(ClusterTripPeriod.SINCE_RESET,panel.tripPeriod)
        assertEquals(reset.distanceKm,panel.tripKm);assertEquals(reset.elapsedMillis,panel.tripMillis);assertEquals(reset.consumption,panel.consumption)
        val applied=ClusterPanelState.from(s,null,null,20000,ClusterTripPeriod.SINCE_START)
        assertEquals(start.distanceKm,applied.tripKm);assertEquals(ClusterTripPeriod.SINCE_START,applied.tripPeriod)
    }
    @Test fun invalidResetValuesNeverBorrowValuesFromSinceStart() {
        val b=bytes().toMutableList()
        for(i in 8..11)b[i]=255
        b[32]=255;b[33]=255;b[16]=255;b[17]=255;b[18]=255
        val reset=ClusterTripComputer.decode(b,ClusterTripPeriod.SINCE_RESET)!!
        assertNull(reset.distanceKm);assertNull(reset.elapsedMillis);assertNull(reset.consumption)
        assertEquals(120.6,ClusterTripComputer.decode(b)!!.distanceKm!!,.0001)
        b[16]=0;b[17]=0;b[18]=60
        assertNull(ClusterTripComputer.decode(b,ClusterTripPeriod.SINCE_RESET)!!.elapsedMillis)
        for(period in ClusterTripPeriod.entries) {
            val zero=ClusterTripComputer.decode(List(51){0},period)!!
            assertEquals(0.0,zero.distanceKm!!,0.0);assertEquals(0L,zero.elapsedMillis);assertEquals(0.0,zero.consumption!!,0.0)
        }
    }
    @Test fun periodSurvivesRepositoryRecreationAndUnknownStoredValuesUseExistingDefault() {
        val store=MemorySettingsStore();val repo=PersistentInterfaceRepository(store)
        assertEquals(ClusterTripPeriod.SINCE_START,repo.state.value.clusterTripPeriod)
        repo.change(repo.state.value.copy(clusterTripPeriod=ClusterTripPeriod.SINCE_RESET,clusterDesign=ClusterDesign.SPORT))
        val loaded=PersistentInterfaceRepository(store).state.value
        assertEquals(ClusterTripPeriod.SINCE_RESET,loaded.clusterTripPeriod);assertEquals(ClusterDesign.SPORT,loaded.clusterDesign)
        store.put("ui.cluster-trip-period","not-an-oem-period")
        assertEquals(ClusterTripPeriod.SINCE_START,PersistentInterfaceRepository(store).state.value.clusterTripPeriod)
    }
}
