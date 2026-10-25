package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class DashboardLiveCacheTest {
    @Test fun outdoorRefreshKeepsLastReadingButExpiresAndResets() {
        val cache=OutsideReadingCache(1000)
        assertNull(cache.update(null,0));assertEquals(12.5,cache.update(12.5,100)!!,0.0)
        assertEquals(12.5,cache.update(null,500)!!,0.0)
        assertEquals(12.5,cache.update(Double.NaN,600)!!,0.0)
        assertEquals(11.0,cache.update(11.0,800)!!,0.0)
        assertNull(cache.update(null,1801));cache.clear();assertNull(cache.update(null,1802))
    }
    @Test fun rapidCommandsUseShortProjectionWithoutMutatingMeasuredState() {
        val p=CommandProjection(2500);val key=VdsKey(VerifiedVehicleProtocol.HVAC,10)
        assertNull(p.value(key,100));p.record(key,6,100);p.record(key,7,110)
        assertEquals(7,p.value(key,120));assertNull(p.value(key,2611))
        p.record(key,8,3000);p.clear();assertNull(p.value(key,3001))
    }
    @Test fun onlyConfirmedLiveBtConnectionMakesRadioConnected() {
        val radio=ConnectivityState(bluetoothEnabled=false,bluetoothConnected=null)
        val bt=MediaState(live=true,source="Bluetooth",bluetoothConnected=true)
        assertEquals(true,RadioReadings.withMedia(radio,bt).bluetoothConnected)
        assertEquals(true,RadioReadings.withMedia(radio,bt).bluetoothEnabled)
        assertEquals(radio,RadioReadings.withMedia(radio,bt.copy(source="CarPlay")))
        assertEquals(radio,RadioReadings.withMedia(radio,bt.copy(live=false)))
        assertEquals(radio,RadioReadings.withMedia(radio,bt.copy(bluetoothConnected=null)))
        assertEquals(false,RadioReadings.withMedia(radio,bt.copy(bluetoothConnected=false)).bluetoothConnected)
    }
}
