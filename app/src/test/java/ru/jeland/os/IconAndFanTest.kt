package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.*

class IconAndFanTest {
    @Test fun squareIconsAreCenteredWithConsistentVisibleInset() {
        val box=LauncherIconGeometry.fit(100,100,200)
        assertEquals(176f,box.width,.001f);assertEquals(box.width,box.height,0f)
        assertEquals(12f,box.left,.001f);assertEquals(12f,box.top,.001f)
    }
    @Test fun wideAndTallIconsNeverBecomeSquares() {
        val wide=LauncherIconGeometry.fit(160,80,192)
        val tall=LauncherIconGeometry.fit(80,160,192)
        assertEquals(2f,wide.width/wide.height,.001f)
        assertEquals(.5f,tall.width/tall.height,.001f)
        assertEquals(wide.width,tall.height,.001f)
        for(box in listOf(wide,tall)){assertTrue(box.left>=0 && box.top>=0 && box.right<=192 && box.bottom<=192)}
    }
    @Test fun invalidIconBoundsAreRejected() {
        for(size in listOf(0,-1))assertTrue(runCatching {LauncherIconGeometry.fit(size,10,192)}.isFailure)
    }
    @Test fun allTenFanCommandsKeepOemValuesWithoutOffsetOrRemap() {
        for(level in 1..10){
            val w=VerifiedVehicleProtocol.fan(level)
            assertEquals(VdsKey(0x5000A,12),w.key);assertEquals(level,w.value);assertEquals(level,w.expected)
            assertTrue(VerifiedVehicleProtocol.allowed(w))
        }
        for(invalid in listOf(-1,0,11,255)){
            assertTrue(runCatching {VerifiedVehicleProtocol.fan(invalid)}.isFailure)
            assertFalse(VerifiedVehicleProtocol.allowed(VdsWrite(VdsKey(0x5000A,12),invalid,expected=invalid)))
        }
    }
    @Test fun levelTenIsReadAndPersistedInDemoToo() {
        val store=MemorySettingsStore();val mock=MockClimateRepository(store)
        mock.setFan(10);assertEquals(10,MockClimateRepository(store).state.value.fan)
        val live=LiveVehicleReducer();live.accept(VdsKey(0x5000A,12),listOf(10),100)
        assertEquals(10,live.climate(101).fan);assertTrue(live.climate(101).known(12))
    }
}
