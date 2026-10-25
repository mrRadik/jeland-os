package ru.jeland.os.data.media

import android.content.*
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.*
import com.desaysv.ivi.vdb.IVDBus
import com.desaysv.ivi.vdb.event.id.bt.bean.*
import ru.jeland.os.core.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Only Probe's four verified commands. No global media keys, audio focus, pairing or source switch. */
class OemPhoneMusicTransport(context:Context,private val log:(String)->Unit):PhoneMusicTransport {
    private val context=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val reading=AtomicBoolean(false)
    private val sources=OemSourceMonitor(this.context,log)
    @Volatile private var usb:IBinder?=null
    @Volatile private var playlist:List<MediaQueueItem> = emptyList()
    private var playlistAt=0L
    private var coverKey:String?=null
    private var cover:MediaArtwork?=null
    private var coverAt=0L
    override fun watchSource(receive:(PhoneMusicSource?)->Unit)=sources.start(receive)
    override fun stopWatching()=sources.stop()
    @Volatile private var epoch=0
    @Volatile private var source=PhoneMusicSource.BLUETOOTH
    @Volatile private var bt:IVDBus?=null
    @Volatile private var cls:IBinder?=null
    private var receive:((PhoneMusicSample)->Unit)?=null
    private var binding:ServiceConnection?=null
    private var browser:MediaBrowser?=null
    private var controller:MediaController?=null
    private var callback:MediaController.Callback?=null
    private var lastLogged:PhoneMusicSample?=null
    private fun delivered(token:Int,sample:PhoneMusicSample) {main.post {if(token==epoch){
        if(sample.copy(receivedAt=0)!=lastLogged){log("MEDIA_FEEDBACK source=${source.title} ready=${sample.ready} state=${sample.playback} title=${sample.track?.title?.take(160)} position=${sample.position}");lastLogged=sample.copy(receivedAt=0)}
        receive?.invoke(sample)
    }}}
    override fun start(source:PhoneMusicSource,receive:(PhoneMusicSample)->Unit) {
        stop();this.source=source;this.receive=receive;val token=epoch
        if(source==PhoneMusicSource.USB0||source==PhoneMusicSource.USB1){connectUsb(token);return}
        if(source==PhoneMusicSource.CARPLAY)connectBrowser(token)
        val component=if(source==PhoneMusicSource.BLUETOOTH)ComponentName(BtProtocol.PACKAGE,BtProtocol.SERVICE)
            else ComponentName(CARPLAY_PACKAGE,CarPlayClsProtocol.SERVICE)
        try {
            @Suppress("DEPRECATION") val info=context.packageManager.getServiceInfo(component,0)
            check(info.enabled&&info.exported){"OEM media service not exported/enabled"}
            val connection=object:ServiceConnection {
                override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                    if(token!=epoch)return
                    try {
                        if(source==PhoneMusicSource.BLUETOOTH){check(binder.interfaceDescriptor=="com.desaysv.ivi.vdb.IVDBus");bt=IVDBus.Stub.asInterface(binder)}
                        else {check(binder.interfaceDescriptor==CarPlayClsProtocol.DESCRIPTOR);cls=binder}
                        log("MEDIA_BIND source=${source.title} connected; no command");refresh()
                    }catch(error:Exception){log("MEDIA_BIND_ERROR ${error.javaClass.simpleName}");stop()}
                }
                override fun onServiceDisconnected(name:ComponentName){if(token==epoch)lost("MEDIA_DISCONNECTED")}
                override fun onBindingDied(name:ComponentName){if(token==epoch)lost("MEDIA_BIND_DIED")}
                override fun onNullBinding(name:ComponentName){if(token==epoch)lost("MEDIA_NULL_BINDING")}
            }
            val intent=if(source==PhoneMusicSource.BLUETOOTH)Intent(BtProtocol.ACTION).setComponent(component)
                else Intent("com.desaysv.service.action.CARPLAY_SERVICE").setComponent(component).putExtra("EXTRA_CLIENT_PACKAGE",context.packageName)
            // CarPlay only attaches to an existing phone-link process, never starts auto-connect.
            binding=connection
            if(!context.bindService(intent,connection,if(source==PhoneMusicSource.BLUETOOTH)Context.BIND_AUTO_CREATE else 0)){
                binding=null;log("MEDIA_BIND_REJECTED source=${source.title}")
            }
        }catch(error:Exception){log("MEDIA_UNAVAILABLE source=${source.title} ${error.javaClass.simpleName}")}
    }
    private fun lost(reason:String) {
        val notify=receive;stop();log("$reason; no auto-reconnect")
        notify?.invoke(PhoneMusicSample(receivedAt=SystemClock.elapsedRealtime()))
    }
    private fun connectBrowser(token:Int) {
        try {
            val target=MediaBrowser(context,ComponentName(CARPLAY_PACKAGE,"com.desaysv.platform.MediaSessionService"),object:MediaBrowser.ConnectionCallback(){
                override fun onConnected(){if(token!=epoch)return
                    try {
                        val current=MediaController(context,browser!!.sessionToken);check(current.packageName==CARPLAY_PACKAGE)
                        controller=current
                        val cb=object:MediaController.Callback(){
                            override fun onMetadataChanged(metadata:MediaMetadata?){if(token==epoch)refresh()}
                            override fun onPlaybackStateChanged(state:PlaybackState?){if(token==epoch)refresh()}
                            override fun onSessionDestroyed(){if(token==epoch){controller=null;delivered(token,PhoneMusicSample(receivedAt=SystemClock.elapsedRealtime()))}}
                        }
                        callback=cb;current.registerCallback(cb,main);refresh()
                    }catch(error:Exception){log("MEDIA_BROWSER_ERROR ${error.javaClass.simpleName}")}
                }
                override fun onConnectionFailed(){if(token==epoch){log("MEDIA_BROWSER_REJECTED");delivered(token,PhoneMusicSample(receivedAt=SystemClock.elapsedRealtime()))}}
                override fun onConnectionSuspended(){if(token==epoch){controller=null;log("MEDIA_BROWSER_SUSPENDED");delivered(token,PhoneMusicSample(receivedAt=SystemClock.elapsedRealtime()))}}
            },null)
            browser=target;target.connect()
        }catch(error:Exception){log("MEDIA_BROWSER_UNAVAILABLE ${error.javaClass.simpleName}")}
    }
    override fun refresh() {
        sources.refresh()
        val token=epoch
        if(source==PhoneMusicSource.USB0||source==PhoneMusicSource.USB1){refreshUsb(token);return}
        if(source==PhoneMusicSource.CARPLAY) {
            val current=controller?:return
            try {
                val m=current.metadata;val p=current.playbackState
                val title=m?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf {it.isNotEmpty()}
                val duration=LiveMediaPolicy.seconds(m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?:-1)?:0
                val rawArt=m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?:m?.getBitmap(MediaMetadata.METADATA_KEY_ART)?:m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                val artUri=m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)?:m?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                val artKey="$title|${m?.getString(MediaMetadata.METADATA_KEY_ARTIST)}|${rawArt?.generationId}|$artUri"
                val sample=PhoneMusicSample(cls!=null&&current.packageName==CARPLAY_PACKAGE,
                    title?.let {Track(it.take(250),m?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.take(250)?:"",duration)},
                    p?.state?:-1,LiveMediaPolicy.seconds(p?.position?:-1),SystemClock.elapsedRealtime())
                if(reading.compareAndSet(false,true))worker.execute {try {
                    if(token!=epoch)return@execute
                    val artwork=artwork(artKey){MediaArtworkLoader.bitmap(rawArt)?:MediaArtworkLoader.local(context,artUri)?:unifiedCover(source)}
                    delivered(token,sample.copy(artwork=artwork))
                }finally {reading.set(false)}}
            }catch(error:Exception){log("MEDIA_CARPLAY_READ_ERROR ${error.javaClass.simpleName}");delivered(token,PhoneMusicSample(receivedAt=SystemClock.elapsedRealtime()))}
            return
        }
        val bus=bt?:return
        if(!reading.compareAndSet(false,true))return
        worker.execute {try {
            if(token!=epoch)return@execute
            fun <T> get(id:Int,type:Class<T>)=BtProtocol.value(bus.get(BtProtocol.read(id)),id,type)
            val a2dp=get(BtProtocol.A2DP,VDBTConnect::class.java)
            val avrcp=get(BtProtocol.AVRCP,VDBTConnect::class.java)
            val info=get(BtProtocol.INFO,VDBTMusicInfo::class.java)
            val progress=get(BtProtocol.PROGRESS,VDBTMusicProgress::class.java)
            val duration=LiveMediaPolicy.milliseconds(progress?.duration?:info?.duration?:-1)?:0
            val title=info?.title?.trim()?.takeIf {it.isNotEmpty()}
            val artwork=artwork("${info?.mediaId}|$title|${info?.artist}|${info?.artwork?.contentHashCode()}"){MediaArtworkLoader.encoded(info?.artwork)?:MediaArtworkLoader.local(context,info?.artworkUri)?:unifiedCover(source)}
            delivered(token,PhoneMusicSample(a2dp?.status==2&&(avrcp==null||avrcp.status==2),
                title?.let {Track(it.take(250),info.artist?.take(250)?:"",duration)},info?.state?:-1,
                LiveMediaPolicy.milliseconds(progress?.position?:info?.position?:-1),SystemClock.elapsedRealtime(),artwork))
        }catch(error:Exception){log("MEDIA_BT_READ_ERROR ${error.javaClass.simpleName}");delivered(token,PhoneMusicSample(receivedAt=SystemClock.elapsedRealtime()))}
        finally{reading.set(false)}}
    }
    override fun send(action:Int,permit:()->Boolean,complete:(String?)->Unit) {
        require(LiveMediaPolicy.validAction(action))
        val token=epoch;val target=source;val oldBt=bt;val oldCls=cls
        worker.execute {
            val error=runCatching {
                check(token==epoch&&permit()&&sources.permits(target)){"Media authorization expired"}
                log("MEDIA_REQUEST source=${target.title} action=$action")
                if(target==PhoneMusicSource.USB0||target==PhoneMusicSource.USB1){
                    val binder=checkNotNull(usb);check(token==epoch&&permit()&&sources.permits(target));OemMediaProtocol.usbCommand(binder,target,action)
                }else if(target==PhoneMusicSource.BLUETOOTH) {
                    val bus=checkNotNull(oldBt)
                    val a2dp=BtProtocol.value(bus.get(BtProtocol.read(BtProtocol.A2DP)),BtProtocol.A2DP,VDBTConnect::class.java)
                    val avrcp=BtProtocol.value(bus.get(BtProtocol.read(BtProtocol.AVRCP)),BtProtocol.AVRCP,VDBTConnect::class.java)
                    check(a2dp?.status==2&&(avrcp==null||avrcp.status==2)){"BT audio not connected"}
                    check(token==epoch&&oldBt===bt&&permit()&&sources.permits(target)){"Media authorization expired"}
                    bus.set(BtProtocol.command(action))
                }else CarPlayClsProtocol.sendPair(checkNotNull(oldCls),action){token==epoch&&oldCls===cls&&permit()&&sources.permits(target)}
            }.exceptionOrNull()
            main.post {complete(error?.javaClass?.simpleName)}
        }
    }
    private fun artwork(key:String,load:()->MediaArtwork?):MediaArtwork? {
        val now=SystemClock.elapsedRealtime()
        if(key!=coverKey||(cover==null&&now-coverAt>=5000)){cover=runCatching(load).getOrNull();coverKey=key;coverAt=now}
        return cover
    }
    private fun unifiedCover(target:PhoneMusicSource):MediaArtwork? {
        val info=sources.bus?.let {OemMediaProtocol.item(it,target)}?:return null
        return MediaArtworkLoader.encoded(info.albumBuffer)?:MediaArtworkLoader.local(context,info.albumImgUrl)
    }
    private fun connectUsb(token:Int) {
        for(pkg in listOf("com.desaysv.svmusicapp","com.desaysv.svaudioapp")) {
            val component=ComponentName(pkg,OemMediaProtocol.USB_SERVICE)
            try {
                @Suppress("DEPRECATION") val info=context.packageManager.getServiceInfo(component,0)
                if(!info.enabled||!info.exported)continue
                val c=object:ServiceConnection {
                    override fun onServiceConnected(name:ComponentName,binder:IBinder){
                        if(token!=epoch)return
                        if(binder.interfaceDescriptor==OemMediaProtocol.USB_DESCRIPTOR){usb=binder;log("MEDIA_USB_BOUND $pkg");refresh()}
                    }
                    override fun onServiceDisconnected(name:ComponentName){if(token==epoch)lost("MEDIA_USB_DISCONNECTED")}
                    override fun onBindingDied(name:ComponentName){onServiceDisconnected(name)}
                    override fun onNullBinding(name:ComponentName){onServiceDisconnected(name)}
                }
                if(context.bindService(Intent().setComponent(component),c,Context.BIND_AUTO_CREATE)){binding=c;return}
            }catch(error:Exception){log("MEDIA_USB_BIND $pkg ${error.javaClass.simpleName}")}
        }
        log("MEDIA_USB_UNAVAILABLE")
    }
    private fun refreshUsb(token:Int) {
        val bus=sources.bus?:return;val binder=usb?:return;val target=source
        if(!reading.compareAndSet(false,true))return
        worker.execute {try {
            if(token!=epoch)return@execute
            check(sources.permits(target))
            val sample=OemMediaProtocol.usbSample(bus,target,SystemClock.elapsedRealtime())
            val now=SystemClock.elapsedRealtime()
            if(now-playlistAt>5000||playlistAt==0L){
                playlist=runCatching {OemMediaProtocol.playlist(binder,target)}.getOrElse {log("MEDIA_USB_LIST_ERROR ${it.javaClass.simpleName}");emptyList()}
                playlistAt=now;log("MEDIA_USB_LIST count=${playlist.size}")
            }
            val art=artwork("${sample.currentId}|${sample.track?.title}"){MediaArtworkLoader.encoded(OemMediaProtocol.cover(binder,target))?:unifiedCover(target)}
            delivered(token,sample.copy(artwork=art,playlist=playlist))
        }catch(error:Exception){log("MEDIA_USB_READ_ERROR ${error.javaClass.simpleName}");delivered(token,PhoneMusicSample(receivedAt=SystemClock.elapsedRealtime()))}
        finally {reading.set(false)}}
    }
    override fun select(item:MediaQueueItem,permit:()->Boolean,complete:(String?)->Unit) {
        val token=epoch;val target=source;val binder=usb
        worker.execute {
            val error=runCatching {
                check(token==epoch&&permit()&&sources.permits(target))
                val fresh=OemMediaProtocol.playlist(checkNotNull(binder),target)
                val current=fresh.firstOrNull {it.id==item.id}?:error("USB item removed")
                check(token==epoch&&permit()&&binder===usb&&sources.permits(target))
                OemMediaProtocol.usbCommand(binder,target,1,current)
            }.exceptionOrNull()
            main.post {complete(error?.javaClass?.simpleName)}
        }
    }
    override fun stop() {
        epoch++;receive=null;lastLogged=null;bt=null;cls=null;usb=null;playlist=emptyList();playlistAt=0;coverKey=null;cover=null
        val previous=controller;val cb=callback;controller=null;callback=null
        if(cb!=null)runCatching {previous?.unregisterCallback(cb)}
        val b=browser;browser=null;runCatching {b?.disconnect()}
        val connection=binding;binding=null;if(connection!=null)runCatching {context.unbindService(connection)}
        // Never send PAUSE, STOP, source changes or a new connection on cleanup.
    }
    companion object {const val CARPLAY_PACKAGE="com.desaysv.vehicle.carplayapp"}
}
