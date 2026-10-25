package ru.jeland.os.core

enum class PhoneMusicSource(val title:String,val oemId:Int) { BLUETOOTH("Bluetooth",13), CARPLAY("CarPlay",18), USB0("USB",3), USB1("USB 2",4);
    companion object {fun parse(title:String)=entries.firstOrNull {it.title==title}; fun fromOem(id:Int)=entries.firstOrNull {it.oemId==id}}
}
data class PhoneMusicSample(val ready:Boolean=false,val track:Track?=null,val playback:Int=-1,
    val position:Int?=null,val receivedAt:Long=0,val artwork:MediaArtwork?=null,
    val playlist:List<MediaQueueItem> = emptyList(),val currentId:String?=null)

data class MediaArtwork(val key:String,val bytes:ByteArray)
data class MediaQueueItem(val id:String,val track:Track)

/** Only the OEM active source selects a route; no inferred fallback: a cached CarPlay title must never steal BT controls. */
object LiveMediaPolicy {
    fun fresh(sample:PhoneMusicSample,now:Long)=now-sample.receivedAt in 0..15_000
    fun ready(sample:PhoneMusicSample,now:Long)=fresh(sample,now)&&sample.ready&&sample.playback in 1..3&&sample.track!=null
    fun render(source:PhoneMusicSource,sample:PhoneMusicSample,link:VehicleLinkState,foreground:Boolean,busy:Boolean,now:Long):MediaState {
        val fresh=fresh(sample,now);val ready=ready(sample,now)
        return MediaState(live=true,liveTrack=sample.track.takeIf {fresh},source=source.title,playing=ready&&sample.playback==3,
            position=sample.position?.takeIf {fresh}?.coerceIn(0,sample.track?.duration?.coerceAtLeast(0)?:0)?:0,
            positionKnown=fresh&&sample.position!=null,controlsEnabled=foreground&&!link.demo&&link.writesEnabled&&ready&&!busy,
            artwork=sample.artwork.takeIf {fresh},playlist=sample.playlist.takeIf {fresh}?:emptyList(),currentId=sample.currentId.takeIf {fresh},bluetoothConnected=if(source==PhoneMusicSource.BLUETOOTH&&fresh)sample.ready else null,
            status=when {!foreground->"${source.title} · отключено";!ready->"${source.title}";
                !link.writesEnabled->"${source.title} · управление заблокировано";busy->"${source.title} · команда отправляется";
                else->"${source.title} · управление телефоном"})
    }
    fun action(playing:Boolean)=if(playing)2 else 1
    fun validAction(action:Int)=action in listOf(1,2,4,5)
    // CarPlay's OEM session publishes seconds; VDBTMusicProgress uses milliseconds. Never guess by magnitude.
    fun seconds(raw:Long):Int?=raw.takeIf {it in 0..86400}?.toInt()
    fun milliseconds(raw:Long):Int?=raw.takeIf {it in 0..86_400_000}?.div(1000)?.toInt()
}
