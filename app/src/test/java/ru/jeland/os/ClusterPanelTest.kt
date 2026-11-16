package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.PersistentInterfaceRepository

class ClusterPanelTest {
    @Test fun unavailableDoesNotBecomeReferenceValuesOrDemoDefaults() {
        val s=UiState(car=VehicleLinkState(demo=false,connected=false))
        val panel=ClusterPanelState.from(s,null,null,20_000)
        assertNull(panel.speed);assertNull(panel.rpm);assertNull(panel.gear)
        assertNull(panel.fuel);assertNull(panel.coolant);assertNull(panel.outside)
        assertNull(panel.odometer);assertNull(panel.rangeKm);assertNull(panel.navigation)
        assertTrue(panel.lamps.isEmpty())
    }
    @Test fun freshSharedReadingsAndTripAreUsedWithoutSyntheticMissingFields() {
        val raw=mapOf(119 to listOf(0,78),26 to listOf(4),62 to listOf(0,215),1 to listOf(1))
            .mapKeys {VdsKey(VerifiedVehicleProtocol.INFO,it.key)}.mapValues {RawVehicleSample(it.value,19_900)}
        val s=UiState(car=VehicleLinkState(demo=false,connected=true,raw=raw),
            vehicle=VehicleState(engineRpm=860,fuel=FuelState(22.0)),weather=WeatherState(demo=false,outsideCelsius=12.5))
        val trip=TripSummary(1000,distanceMetres=12500.0,elapsedMillis=90000)
        val panel=ClusterPanelState.from(s,trip,null,20_000)
        assertEquals(78.0,panel.speed!!,.001);assertEquals(860.0,panel.rpm!!,.001)
        assertEquals("D",panel.gear);assertEquals(12.5,panel.outside!!,.001)
        assertNull(panel.tripKm);assertNull(panel.tripMillis) // Recording is a different period from OEM since-start counters.
        assertTrue(ClusterLamp.POSITION in panel.lamps)
        assertNull(panel.coolant);assertNull(panel.rangeKm);assertNull(panel.consumption)
    }
    @Test fun staleUnknownAndMalformedSignalsAreNotShownAsValid() {
        fun panel(bytes:List<Int>,gear:Int,at:Long)=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,raw=mapOf(
            VdsKey(VerifiedVehicleProtocol.INFO,119) to RawVehicleSample(bytes,at),
            VdsKey(VerifiedVehicleProtocol.INFO,26) to RawVehicleSample(listOf(gear),at)))),null,null,20_000)
        assertNull(panel(listOf(255,255),3,19_999).speed)
        assertEquals("N",panel(listOf(0,45),3,19_999).gear)
        assertNull(panel(listOf(0,45),5,1000).speed)
        assertNull(panel(listOf(0,45),5,1000).gear)
    }
    @Test fun factoryRangeAndOdometerMatchTheFieldCaptureWithoutConvertingLitres() {
        val raw=mapOf(72 to listOf(1,186),71 to listOf(0,0,6,172))
            .mapKeys {VdsKey(VerifiedVehicleProtocol.INFO,it.key)}.mapValues {RawVehicleSample(it.value,19_900)}
        val panel=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,raw=raw)),null,null,20_000)
        assertEquals(442.0,panel.rangeKm!!,.001);assertEquals(1708.0,panel.odometer!!,.001)
        assertTrue(72 in VerifiedVehicleProtocol.subscriptions.getValue(VerifiedVehicleProtocol.INFO))
        assertTrue(71 in VerifiedVehicleProtocol.subscriptions.getValue(VerifiedVehicleProtocol.INFO))
    }
    @Test fun invalidOrStaleFactoryDistancesStayAbsent() {
        for((range,odo,at) in listOf(
            Triple(listOf(255,255),listOf(255,255,255,255),19_900L),
            Triple(listOf(442),listOf(6,172),19_900L),
            Triple(listOf(1,186),listOf(0,0,6,172),1000L))) {
            val raw=mapOf(72 to range,71 to odo).mapKeys {VdsKey(VerifiedVehicleProtocol.INFO,it.key)}
                .mapValues {RawVehicleSample(it.value,at)}
            val panel=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,raw=raw)),null,null,20_000)
            assertNull(panel.rangeKm);assertNull(panel.odometer)
        }
    }
    @Test fun selectedDesignPersistsAndOldPreferencesRemainOriginal() {
        val store=MemorySettingsStore();val prefs=PersistentInterfaceRepository(store)
        assertEquals(ClusterDesign.ORIGINAL,prefs.state.value.clusterDesign)
        val before=prefs.state.value.copy(showFuel=false,style=DashboardStyle.MINIMAL,clusterAccent=ClusterAccent.GREEN)
        for(design in ClusterDesign.entries) {
            prefs.change(before.copy(clusterDesign=design))
            assertEquals(before.copy(clusterDesign=design),PersistentInterfaceRepository(store).state.value)
        }
        store.put("ui.cluster-design","future-format")
        assertEquals(ClusterDesign.ORIGINAL,PersistentInterfaceRepository(store).state.value.clusterDesign)
    }
    @Test fun retiredThemeNamesMigrateWithoutResettingTheSelectedPanel() {
        for(design in listOf(ClusterDesign.CLASSIC,ClusterDesign.NAVIGATION,ClusterDesign.SPORT)) {
            val store=MemorySettingsStore()
            store.put("ui.cluster-design",design.name+"_CARBON")
            store.put("ui.cluster-rendering","CARBON")
            store.put("ui.cluster-accent","GREEN")
            val repository=PersistentInterfaceRepository(store)
            assertEquals(design,repository.state.value.clusterDesign)
            assertEquals(ClusterAccent.GREEN,repository.state.value.clusterAccent)
            repository.change(repository.state.value)
            assertEquals(design.name,store.string("ui.cluster-design",""))
            assertEquals(repository.state.value,PersistentInterfaceRepository(store).state.value)
        }
    }

}
