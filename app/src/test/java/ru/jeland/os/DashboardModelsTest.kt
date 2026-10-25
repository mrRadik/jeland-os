package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.*

class DashboardModelsTest {
    @Test fun fuelUses55LitresAndNeverNegativeToFull() {
        assertEquals(31,FuelState(31.0).displayLiters);assertEquals(24,FuelState(31.0).litersToFull)
        assertEquals(0,FuelState(99.0).litersToFull);assertEquals(55,FuelState(-4.0).litersToFull)
    }
    @Test fun fuelUnknownAndNonFiniteAreNotInvented() {
        listOf(null,Double.NaN,Double.POSITIVE_INFINITY).forEach {assertNull(FuelState(it).displayLiters);assertNull(FuelState(it).litersToFull)}
    }
    @Test fun relativeFuelIsExplicitlyEstimatedAndRounded() {
        val f=FuelState.fromRelative(.56);assertTrue(f.estimated);assertEquals(31,f.displayLiters);assertEquals(24,f.litersToFull)
        assertNull(FuelState.fromRelative(1.5).displayLiters)
    }
    @Test fun demoTireThresholdsHaveAllFourStates() {
        val t=TireThresholds()
        assertEquals(TireSeverity.NORMAL,t.severity(TireReading(2.4)))
        assertEquals(TireSeverity.WARNING,t.severity(TireReading(1.9)))
        assertEquals(TireSeverity.CRITICAL,t.severity(TireReading(1.5)))
        listOf(null,-1.0,Double.NaN).forEach {assertEquals(TireSeverity.UNKNOWN,t.severity(TireReading(it)))}
    }
    @Test fun tireThresholdsCanChangeAndPersist() {
        val store=MemorySettingsStore();val r=MockVehicleRepository(store)
        val test=TireThresholds(1.8,2.3,2.7,3.1);r.setTireThresholds(test)
        assertEquals(test,MockVehicleRepository(store).state.value.tireThresholds)
    }
    @Test fun wheelLocationsAndReadingsRemainIndependent() {
        val r=MockVehicleRepository(MemorySettingsStore())
        r.simulateTire(Wheel.FRONT_LEFT,TireReading(1.5,20));r.simulateTire(Wheel.REAR_RIGHT,TireReading())
        assertEquals(1.5,r.state.value.tires[Wheel.FRONT_LEFT]!!.validBar!!,0.0)
        assertEquals(2.4,r.state.value.tires[Wheel.FRONT_RIGHT]!!.validBar!!,0.0)
        assertNull(r.state.value.tires[Wheel.REAR_RIGHT]!!.validBar)
        assertTrue(Wheel.FRONT_LEFT.front&&Wheel.FRONT_LEFT.left)
        assertFalse(Wheel.REAR_RIGHT.front||Wheel.REAR_RIGHT.left)
    }
    @Test fun multipleOpeningsAndMotionStayMock() {
        val r=MockVehicleRepository(MemorySettingsStore())
        r.simulateOpening(VehicleOpening.FRONT_LEFT);r.simulateOpening(VehicleOpening.REAR_RIGHT);r.simulateMoving(true)
        assertEquals(2,r.state.value.openings.size);assertTrue(r.state.value.mockMoving)
        r.closeAllOpenings();assertTrue(r.state.value.openings.isEmpty())
    }
    @Test fun favoritesNeverExposeAnUninstalledAppAsInstalled() {
        val app=LauncherApp("p/A","p","A","Test")
        val state=AppsState(installed=listOf(app),favorites=listOf("missing/A",app.id))
        assertEquals(listOf(app),state.favoriteApps)
    }
    @Test fun interfacePreferencesAndMediaLikePersist() {
        val store=MemorySettingsStore();val prefs=PersistentInterfaceRepository(store)
        prefs.change(InterfacePreferences(showFuel=false));assertFalse(PersistentInterfaceRepository(store).state.value.showFuel)
        val media=MockMediaRepository(store);media.toggleLike();assertTrue(MockMediaRepository(store).state.value.liked)
    }
    @Test fun everyVehicleTabOptionIsIndependentAndPersists() {
        val store=MemorySettingsStore();val vehicle=MockVehicleRepository(store)
        for(option in VehicleOption.entries)vehicle.toggleOption(option)
        assertTrue(MockVehicleRepository(store).state.value.options.values.all {it})
        vehicle.toggleOption(VehicleOption.AUTO_LIGHT)
        val restored=MockVehicleRepository(store).state.value
        assertFalse(restored.options.getValue(VehicleOption.AUTO_LIGHT))
        assertTrue(restored.options.getValue(VehicleOption.FOLLOW_HOME))
        assertTrue(restored.openings.isEmpty())
    }
    @Test fun allInterfacePreferencesPersistTogether() {
        val store=MemorySettingsStore();val preferences=PersistentInterfaceRepository(store)
        val desired=InterfacePreferences(showFuel=false,showTires=false,showFavorites=false,developerPanel=true)
        preferences.change(desired)
        assertEquals(desired,PersistentInterfaceRepository(store).state.value)
    }
}
