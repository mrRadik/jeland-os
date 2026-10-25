package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.*

class MockRepositoriesTest {
    @Test fun temperatureUsesHalfDegreeStepsAndClamps() {
        val r=MockClimateRepository(MemorySettingsStore())
        r.adjustTemperature(true,.5);assertEquals(22.5,r.state.value.driver,0.0)
        repeat(100){r.adjustTemperature(true, .5)};assertEquals(32.0,r.state.value.driver,0.0)
        repeat(100){r.adjustTemperature(false,-.5)};assertEquals(16.0,r.state.value.passenger,0.0)
    }
    @Test fun syncCopiesDriverThenAdjustsBothZones() {
        val r=MockClimateRepository(MemorySettingsStore())
        r.adjustTemperature(true,2.0);r.toggleSync()
        assertEquals(r.state.value.driver,r.state.value.passenger,0.0)
        r.adjustTemperature(false,-.5)
        assertEquals(23.5,r.state.value.driver,0.0);assertEquals(23.5,r.state.value.passenger,0.0)
    }
    @Test fun climatePersistenceAndFanBounds() {
        val store=MemorySettingsStore();val r=MockClimateRepository(store)
        r.setFan(900);r.toggleAc();r.setAirflow(Airflow.FEET)
        val restored=MockClimateRepository(store).state.value
        assertEquals(10,restored.fan);assertFalse(restored.auto);assertFalse(restored.ac);assertEquals(Airflow.FEET,restored.airflow)
        r.setFan(-1);assertEquals(0,r.state.value.fan)
    }
    @Test fun invalidStoredTemperatureIsRejected() {
        val store=MemorySettingsStore();store.put("climate.driver","NaN");store.put("climate.passenger","Infinity")
        val r=MockClimateRepository(store)
        assertEquals(22.0,r.state.value.driver,0.0);assertEquals(22.0,r.state.value.passenger,0.0)
    }
    @Test fun demoSeatsAreIndependentAndMutuallyExclusive() {
        val r=MockSeatRepository(MemorySettingsStore())
        r.setLevel(SeatSide.DRIVER,SeatMode.HEAT,2);r.setLevel(SeatSide.PASSENGER,SeatMode.HEAT,1)
        r.setLevel(SeatSide.DRIVER,SeatMode.VENTILATE,99)
        assertEquals(SeatChannel(0,3),r.state.value.driver);assertEquals(SeatChannel(1,0),r.state.value.passenger)
        r.setLevel(SeatSide.DRIVER,SeatMode.VENTILATE,-5);assertEquals(0,r.state.value.driver.ventilation)
    }
    @Test fun seatAndShadeStatePersist() {
        val store=MemorySettingsStore();val seats=MockSeatRepository(store);val vehicle=MockVehicleRepository(store)
        seats.setRear(RearHeat.BOTH);seats.setLevel(SeatSide.PASSENGER,SeatMode.VENTILATE,2);vehicle.setShade(Shade.HALF)
        assertEquals(RearHeat.BOTH,MockSeatRepository(store).state.value.rear)
        assertEquals(2,MockSeatRepository(store).state.value.passenger.ventilation)
        assertEquals(Shade.HALF,MockVehicleRepository(store).state.value.shade)
    }
    @Test fun mediaDoesNotTickWhenPausedAndWrapsTracks() {
        val r=MockMediaRepository(MemorySettingsStore());val start=r.state.value.position
        r.tick();assertEquals(start,r.state.value.position)
        r.togglePlay();r.tick();assertEquals(start+1,r.state.value.position)
        r.selectTrack(demoTracks.lastIndex);r.next();assertEquals(0,r.state.value.index)
        r.previous();assertEquals(demoTracks.lastIndex,r.state.value.index)
        r.seek(9999);assertEquals(r.state.value.track.duration,r.state.value.position)
    }
    @Test fun navigationAndClusterUseSameImmutableState() {
        val r=MockNavigationRepository(MemorySettingsStore());val bridge=MockClusterBridge()
        r.setDestination("Домой");r.nextManeuver();r.zoom(99f);bridge.publish(r.state.value)
        assertEquals(r.state.value,bridge.lastNavigation.value);assertEquals(2.5f,r.state.value.zoom,0f)
        r.zoom(-99f);assertEquals(.7f,r.state.value.zoom,0f)
    }
    @Test fun autoThemeUsesDayNightProviderAndPersists() {
        val store=MemorySettingsStore();val theme=MockThemeRepository(store);val provider=MockDayNightProvider(store)
        theme.setMode(ThemeMode.AUTO)
        assertTrue(UiState(theme=theme.mode.value,night=provider.isNight.value).dark)
        provider.toggle();assertFalse(UiState(theme=theme.mode.value,night=provider.isNight.value).dark)
        assertEquals(ThemeMode.AUTO,MockThemeRepository(store).mode.value);assertFalse(MockDayNightProvider(store).isNight.value)
    }
}
