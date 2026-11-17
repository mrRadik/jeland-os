package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.PersistentInterfaceRepository

class ClusterColorTest {
    @Test fun exactRgbAndModePersistWithoutLosingExistingPreferences() {
        val store=MemorySettingsStore()
        store.put("ui.cluster-accent","GREEN")
        val repository=PersistentInterfaceRepository(store)
        assertEquals(ClusterAccent.GREEN,repository.state.value.clusterAccent)
        assertEquals(ClusterColorMode.MANUAL,repository.state.value.clusterColorMode)
        for(rgb in listOf(0x000000,0xffffff,0xa435f0,0x05a9bc)) {
            val color=ClusterAccent.custom(rgb)
            assertEquals(rgb or (0xff shl 24),color.darkArgb)
            assertEquals(color,ClusterAccent.valueOf(color.name))
            val prefs=repository.state.value.copy(clusterAccent=color,clusterColorMode=ClusterColorMode.DRIVE_MODE,showFuel=false)
            repository.change(prefs)
            assertEquals(prefs,PersistentInterfaceRepository(store).state.value)
        }
        store.put("ui.cluster-accent","CUSTOM_NOT_A_COLOR")
        store.put("ui.cluster-color-mode","future-mode")
        val recovered=PersistentInterfaceRepository(store).state.value
        assertEquals(ClusterAccent.CYAN,recovered.clusterAccent)
        assertEquals(ClusterColorMode.MANUAL,recovered.clusterColorMode)
    }
    private fun link(values:List<Int>?,time:Long=1000)=VehicleLinkState(demo=false,connected=true,
        raw=mapOf(VdsKey(ClusterDriveMode.MODULE,ClusterDriveMode.COMMAND) to RawVehicleSample(values,time)))
    @Test fun stockRawModesDriveColoursAndLivePanel() {
        val prefs=InterfacePreferences(clusterAccent=ClusterAccent.custom(0x663399),clusterColorMode=ClusterColorMode.DRIVE_MODE)
        assertEquals("Стандарт",ClusterDriveMode.COMFORT.title)
        assertEquals("Standard",ClusterDriveMode.COMFORT.englishTitle)
        assertEquals(1,ClusterDriveMode.COMFORT.raw)
        assertEquals("COMFORT",ClusterDriveMode.COMFORT.name)
        val expected=listOf(ClusterDriveMode.ECO,ClusterDriveMode.COMFORT,ClusterDriveMode.SPORT,
            ClusterDriveMode.SNOW,ClusterDriveMode.MUD,ClusterDriveMode.SAND,ClusterDriveMode.OFFROAD)
        expected.forEachIndexed {code,mode->
            val car=link(listOf(code))
            val panel=ClusterPanelState.from(UiState(car=car),null,null,2000)
            assertEquals(mode,panel.driveMode)
            assertEquals(mode.accent,prefs.clusterAccentFor(panel.driveMode))
        }
        assertEquals(ClusterAccent.GREEN,prefs.clusterAccentFor(ClusterDriveMode.ECO))
        assertEquals(ClusterAccent.CYAN,prefs.clusterAccentFor(ClusterDriveMode.COMFORT))
        assertEquals(ClusterAccent.RED,prefs.clusterAccentFor(ClusterDriveMode.SPORT))
        assertEquals(prefs.clusterAccent,prefs.copy(clusterColorMode=ClusterColorMode.MANUAL).clusterAccentFor(ClusterDriveMode.SPORT))
        assertTrue(VerifiedVehicleProtocol.subscriptions.getValue(ClusterDriveMode.MODULE).contains(ClusterDriveMode.COMMAND))
    }
    @Test fun absentStaleAndMalformedModesUseManualColour() {
        val prefs=InterfacePreferences(clusterAccent=ClusterAccent.custom(0xabcdef),clusterColorMode=ClusterColorMode.DRIVE_MODE)
        val bad=listOf(link(null),link(emptyList()),link(listOf(1,2)),link(listOf(-1)),link(listOf(255)),link(listOf(7)),
            link(listOf(1),0),link(listOf(1),20000),link(listOf(1)).copy(demo=true),link(listOf(1)).copy(connected=false))
        bad.forEach {car->
            val mode=ClusterDriveMode.read(car,9000)
            assertNull(mode);assertEquals(prefs.clusterAccent,prefs.clusterAccentFor(mode))
        }
    }
}
