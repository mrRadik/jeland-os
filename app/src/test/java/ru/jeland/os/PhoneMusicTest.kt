package ru.jeland.os

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.media.*

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneMusicTest {
    private val sample=PhoneMusicSample(true,Track("Real title","Real artist",249),3,80,5000)
    @Test fun liveNeverShowsDemoTracksOrGuessesUnknownTime() {
        val link=VehicleLinkState(demo=false,writesEnabled=true)
        val unknown=LiveMediaPolicy.render(PhoneMusicSource.CARPLAY,PhoneMusicSample(),link,true,false,5000)
        assertTrue(unknown.live);assertEquals("Музыка",unknown.track.title);assertFalse(unknown.controlsEnabled)
        assertFalse(unknown.positionKnown);assertEquals(0,unknown.track.duration)
        val known=LiveMediaPolicy.render(PhoneMusicSource.CARPLAY,sample,link,true,false,5000)
        assertEquals("Real title",known.track.title);assertTrue(known.playing);assertTrue(known.controlsEnabled);assertEquals(80,known.position)
        assertFalse(LiveMediaPolicy.render(PhoneMusicSource.CARPLAY,sample,link,true,false,20001).controlsEnabled)
        assertFalse(LiveMediaPolicy.render(PhoneMusicSource.CARPLAY,sample,link,false,false,5000).controlsEnabled)
        assertFalse(LiveMediaPolicy.render(PhoneMusicSource.CARPLAY,sample,link,true,true,5000).controlsEnabled)
        assertFalse(LiveMediaPolicy.render(PhoneMusicSource.CARPLAY,sample,link.copy(writesEnabled=false),true,false,5000).controlsEnabled)
        assertFalse(LiveMediaPolicy.render(PhoneMusicSource.CARPLAY,sample.copy(ready=false),link,true,false,5000).controlsEnabled)
        assertFalse(LiveMediaPolicy.render(PhoneMusicSource.CARPLAY,sample.copy(playback=0),link,true,false,5000).controlsEnabled)
        assertNull(LiveMediaPolicy.seconds(-1));assertNull(LiveMediaPolicy.seconds(249000));assertEquals(249,LiveMediaPolicy.seconds(249))
        assertEquals(54,LiveMediaPolicy.milliseconds(54575));assertEquals(509,LiveMediaPolicy.milliseconds(509306))
        assertNull(LiveMediaPolicy.milliseconds(-1));assertNull(LiveMediaPolicy.milliseconds(Long.MAX_VALUE))
    }
    private class Wire:PhoneMusicTransport {
        var receive:((PhoneMusicSample)->Unit)?=null;var source:PhoneMusicSource?=null
        var route:((PhoneMusicSource?)->Unit)?=null
        override fun watchSource(receive:(PhoneMusicSource?)->Unit){route=receive}
        override fun stopWatching(){route=null}
        val sends=mutableListOf<Int>();var permit:(()->Boolean)?=null;var complete:((String?)->Unit)?=null
        val selections=mutableListOf<MediaQueueItem>()
        override fun select(item:MediaQueueItem,permit:()->Boolean,complete:(String?)->Unit){selections+=item;this.permit=permit;this.complete=complete}
        override fun start(source:PhoneMusicSource,receive:(PhoneMusicSample)->Unit){this.source=source;this.receive=receive}
        override fun stop(){receive=null;source=null}
        override fun refresh(){}
        override fun send(action:Int,permit:()->Boolean,complete:(String?)->Unit){sends+=action;this.permit=permit;this.complete=complete}
    }
    @Test fun sourceSelectionDoesNotSendAndNeverFallsBackOrMutatesFeedback()=runTest {
        val link=MutableStateFlow(VehicleLinkState(demo=false,writesEnabled=true));val wire=Wire()
        val repo=VehicleMediaRepository(MemorySettingsStore(),link,wire,{}, {5000},StandardTestDispatcher(testScheduler))
        runCurrent();repo.start();wire.route!!(PhoneMusicSource.BLUETOOTH);runCurrent();wire.receive!!(sample);runCurrent()
        assertEquals(PhoneMusicSource.BLUETOOTH,wire.source);assertTrue(wire.sends.isEmpty())
        repo.togglePlay();assertEquals(listOf(2),wire.sends);assertTrue(repo.state.value.playing)
        repo.next();assertEquals(listOf(2),wire.sends) // in-flight action is not duplicated
        wire.complete!!(null);runCurrent()
        val oldReceive=wire.receive!!
        repo.setSource("CarPlay");assertEquals(PhoneMusicSource.BLUETOOTH,wire.source)
        wire.route!!(PhoneMusicSource.CARPLAY);runCurrent();assertTrue(wire.sends==listOf(2));assertEquals(PhoneMusicSource.CARPLAY,wire.source)
        oldReceive(sample);runCurrent();assertFalse(repo.state.value.controlsEnabled)
        assertEquals("Музыка",repo.state.value.track.title)
        repo.next();assertEquals(listOf(2),wire.sends)
        wire.receive!!(sample);runCurrent();repo.next();assertEquals(listOf(2,5),wire.sends)
        assertEquals("Real title",repo.state.value.track.title)
        repo.stop();assertFalse(wire.permit!!());assertNull(wire.source)
        wire.complete!!(null);runCurrent();assertFalse(repo.state.value.controlsEnabled)
    }
    @Test fun writeRevocationAndDemoCannotReplayRemoteCommands()=runTest {
        val link=MutableStateFlow(VehicleLinkState(demo=true));val wire=Wire();val store=MemorySettingsStore()
        val repo=VehicleMediaRepository(store,link,wire,{}, {5000},StandardTestDispatcher(testScheduler))
        runCurrent();repo.start();repo.togglePlay();repo.next();assertTrue(wire.sends.isEmpty())
        link.value=VehicleLinkState(demo=false,writesEnabled=true);runCurrent()
        wire.route!!(PhoneMusicSource.BLUETOOTH);runCurrent();wire.receive!!(sample);runCurrent();assertEquals("Real title",repo.state.value.track.title)
        repo.previous();assertEquals(listOf(4),wire.sends);assertTrue(wire.permit!!())
        link.value=link.value.copy(writesEnabled=false);assertFalse(wire.permit!!());runCurrent()
        wire.complete!!(null);runCurrent();repo.next();assertEquals(listOf(4),wire.sends)
        repo.seek(100);repo.setVolume(99);repo.selectTrack(0);repo.toggleLike()
        assertEquals(80,repo.state.value.position);assertEquals("Real title",repo.state.value.track.title)
        link.value=VehicleLinkState(demo=true);runCurrent();assertFalse(repo.state.value.live);assertNull(wire.source)
        link.value=VehicleLinkState(demo=false,writesEnabled=false);runCurrent();assertEquals(listOf(4),wire.sends)
    }
    @Test fun sourceAuthorityExpiresAndUnknownSourceNeverFallsBackToPhone()=runTest {
        assertEquals(PhoneMusicSource.BLUETOOTH,PhoneMusicSource.fromOem(13))
        assertEquals(PhoneMusicSource.CARPLAY,PhoneMusicSource.fromOem(18))
        assertEquals(PhoneMusicSource.USB0,PhoneMusicSource.fromOem(3))
        assertEquals(PhoneMusicSource.USB1,PhoneMusicSource.fromOem(4))
        for(id in listOf(0,2,9,10,20,-1))assertNull(PhoneMusicSource.fromOem(id))
        var now=5000L;val wire=Wire();val link=MutableStateFlow(VehicleLinkState(demo=false,writesEnabled=true))
        val repo=VehicleMediaRepository(MemorySettingsStore(),link,wire,{}, {now},StandardTestDispatcher(testScheduler))
        runCurrent();repo.start();assertNull(wire.source);repo.next();assertTrue(wire.sends.isEmpty())
        wire.route!!(PhoneMusicSource.CARPLAY);runCurrent();wire.receive!!(sample);runCurrent()
        repo.next();assertTrue(wire.permit!!());wire.complete!!(null);runCurrent()
        now=8001;repo.tick();assertFalse(repo.state.value.controlsEnabled);assertNull(repo.state.value.artwork)
        repo.previous();assertEquals(listOf(5),wire.sends)
        wire.route!!(null);runCurrent();assertNull(wire.source);assertEquals("—",repo.state.value.source)
        val old=wire.route!!;repo.stop();repo.start();old(PhoneMusicSource.BLUETOOTH);runCurrent();assertNull(wire.source)
        assertEquals(listOf(5),wire.sends)
    }
    @Test fun usbSelectionUsesOnlyActualQueueAndIsRevokedOnSourceChange()=runTest {
        val wire=Wire();val link=MutableStateFlow(VehicleLinkState(demo=false,writesEnabled=true))
        val repo=VehicleMediaRepository(MemorySettingsStore(),link,wire,{}, {5000},StandardTestDispatcher(testScheduler))
        runCurrent();repo.start();wire.route!!(PhoneMusicSource.USB0);runCurrent()
        val item=MediaQueueItem("/storage/usb/test.mp3",Track("USB track","Artist",200))
        wire.receive!!(sample.copy(playlist=listOf(item),currentId=item.id));runCurrent()
        repo.selectTrack(-1);repo.selectTrack(1);assertTrue(wire.selections.isEmpty())
        repo.selectTrack(0);assertEquals(listOf(item),wire.selections);assertTrue(wire.permit!!())
        assertEquals("Real title",repo.state.value.track.title) // wait for real feedback, not an optimistic title
        wire.route!!(PhoneMusicSource.CARPLAY);runCurrent();assertFalse(wire.permit!!())
        wire.complete!!(null);runCurrent();assertTrue(repo.state.value.playlist.isEmpty())
        repo.selectTrack(0);assertEquals(1,wire.selections.size)
    }
}
