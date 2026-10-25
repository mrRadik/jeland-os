package ru.jeland.os.data.media

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.jeland.os.core.*
import ru.jeland.os.data.MockMediaRepository

interface PhoneMusicTransport {
    fun watchSource(receive:(PhoneMusicSource?)->Unit) {}
    fun stopWatching() {}
    fun select(item:MediaQueueItem,permit:()->Boolean,complete:(String?)->Unit){complete("Unsupported")}
    fun start(source:PhoneMusicSource,receive:(PhoneMusicSample)->Unit)
    fun stop()
    fun refresh()
    fun send(action:Int,permit:()->Boolean,complete:(String?)->Unit)
}

/** DEMO is isolated; changing source selects a client route, never OEM audio focus/device/source. */
class VehicleMediaRepository(private val store:SettingsStore,private val link:StateFlow<VehicleLinkState>,
    private val transport:PhoneMusicTransport,private val log:(String)->Unit,
    private val clock:()->Long={android.os.SystemClock.elapsedRealtime()},dispatcher:CoroutineDispatcher=Dispatchers.Main.immediate):MediaRepository {
    private val scope=CoroutineScope(SupervisorJob()+dispatcher)
    private val mock=MockMediaRepository(store)
    private val flow=MutableStateFlow(if(link.value.demo)mock.state.value else MediaState(live=true,controlsEnabled=false,position=0,status="",source="—"))
    override val state=flow.asStateFlow()
    @Volatile private var selected:PhoneMusicSource?=null
    @Volatile private var sourceReceivedAt:Long?=null
    private var watchEpoch=0
    @Volatile private var epoch=0
    @Volatile private var foreground=false
    @Volatile private var sample=PhoneMusicSample()
    private var busy=false
    private var connected=false
    init {
        scope.launch {link.map {it.demo}.distinctUntilChanged().collect {watch();restart()}}
        scope.launch {link.collect {publish()}}
        scope.launch {mock.state.collect {if(link.value.demo)flow.value=it}}
    }
    private fun publish(){flow.value=if(link.value.demo)mock.state.value else selected?.takeIf {sourceReceivedAt?.let {time->clock()-time in 0..3000}==true}?.let {LiveMediaPolicy.render(it,sample,link.value,foreground,busy,clock())}?:MediaState(live=true,controlsEnabled=false,position=0,source="—",status="")}
    private fun restart() {
        epoch++;connected=false;busy=false;transport.stop();sample=PhoneMusicSample();publish()
        val target=selected
        if(foreground&&!link.value.demo&&target!=null) {
            val token=epoch;connected=true
            transport.start(target){next->scope.launch {if(token==epoch&&foreground&&!link.value.demo){sample=next;publish()}}}
        }
    }
    override fun start(){if(!foreground){foreground=true;watch();restart()}}
    private fun watch(){
        val token=++watchEpoch
        transport.stopWatching();selected=null;sourceReceivedAt=null
        if(!foreground||link.value.demo)return
        transport.watchSource {next->scope.launch {
            if(token==watchEpoch&&foreground&&!link.value.demo){sourceReceivedAt=clock();if(selected!=next){selected=next;log("MEDIA_AUTO_SOURCE ${next?.title?:"unsupported"}");restart()}else publish()}
        }}
    }
    override fun stop(){foreground=false;watchEpoch++;transport.stopWatching();selected=null;sourceReceivedAt=null;restart()}
    override fun tick(){if(link.value.demo)mock.tick()else {if(foreground)transport.refresh();publish()}}
    private fun control(action:Int) {
        if(link.value.demo){when(action){1,2->mock.togglePlay();4->mock.previous();5->mock.next()};return}
        require(LiveMediaPolicy.validAction(action))
        publish()
        if(!state.value.controlsEnabled){log("MEDIA_BLOCKED source=${selected?.title} action=$action");return}
        val token=epoch;busy=true;publish()
        transport.send(action,{token==epoch&&foreground&&!link.value.demo&&link.value.writesEnabled&&sourceReceivedAt?.let {clock()-it in 0..3000}==true&&LiveMediaPolicy.ready(sample,clock())}){error->
            scope.launch {if(token==epoch){busy=false;log(if(error==null)"MEDIA_SENT source=${selected?.title} action=$action; await feedback, no retry" else "MEDIA_ERROR $error; no retry");publish();transport.refresh()}}
        }
    }
    override fun togglePlay()=control(LiveMediaPolicy.action(state.value.playing))
    override fun next()=control(5)
    override fun previous()=control(4)
    override fun setSource(value:String) {
        if(link.value.demo){mock.setSource(value);return}
        // Live routing is determined solely by CURRENT_SOURCE_TYPE.
    }
    // Unsupported remote features never mutate a DEMO placeholder in live mode.
    override fun seek(seconds:Int){if(link.value.demo)mock.seek(seconds)}
    override fun setVolume(value:Int){if(link.value.demo)mock.setVolume(value)}
    override fun selectTrack(index:Int){
        if(link.value.demo){mock.selectTrack(index);return}
        publish()
        val item=state.value.playlist.getOrNull(index)?:return
        if(!state.value.controlsEnabled||busy)return
        val token=epoch;val target=selected;busy=true;publish()
        transport.select(item,{token==epoch&&selected==target&&foreground&&!link.value.demo&&link.value.writesEnabled&&sourceReceivedAt?.let {clock()-it in 0..3000}==true&&LiveMediaPolicy.ready(sample,clock())}){
            error->scope.launch {if(token==epoch){busy=false;log("MEDIA_USB_SELECT ${error?:"sent"}");publish();transport.refresh()}}
        }
    }
    override fun toggleLike(){if(link.value.demo)mock.toggleLike()}
}
