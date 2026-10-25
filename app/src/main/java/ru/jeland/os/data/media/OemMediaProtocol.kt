package ru.jeland.os.data.media

import android.os.*
import com.desaysv.ivi.vdb.IVDBus
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.media.bean.*
import com.desaysv.localmediasdk.bean.RemoteBeanList
import ru.jeland.os.core.*
import org.json.JSONObject

/** Read-only source authority + the stock USB SDK's explicit user commands. */
object OemMediaProtocol {
    const val PACKAGE="com.desaysv.ivi.vds.media"
    const val SERVICE="com.desaysv.ivi.vds.media.service.MediaService"
    const val ACTION="action.desaysv.ivi.vds.media.SERVICE"
    const val CURRENT=393217
    const val INFO=393218
    const val STATUS=393220
    const val TIME=393221
    const val USB_DESCRIPTOR="com.desaysv.localmediasdk.IAIDLMediaManager"
    const val USB_SERVICE="com.desaysv.moduleusbmusic.service.MusicAidlService"
    fun active(bus:IVDBus):PhoneMusicSource?=value<VDMediaType>(bus.get(VDEvent(CURRENT)),CURRENT,"type")?.mediaType?.let(PhoneMusicSource::fromOem)
    inline fun <reified T:Parcelable> value(event:VDEvent?,id:Int,key:String):T? {
        if(event?.id!=id)return null
        val b=event.payload?:return null;b.classLoader=T::class.java.classLoader
        if(b.getBoolean("IS_NULL",false))return null
        @Suppress("DEPRECATION") return b.getParcelable<Parcelable>(key) as? T
    }
    fun item(bus:IVDBus,source:PhoneMusicSource):VDMediaInfo? {
        val b=Bundle().apply {putParcelable("info",VDMediaItem(source.oemId))}
        return value<VDMediaItem>(bus.get(VDEvent(INFO,b)),INFO,"info")?.takeIf {it.mediaType==source.oemId}?.mediaInfo
    }
    fun usbSample(bus:IVDBus,source:PhoneMusicSource,now:Long):PhoneMusicSample {
        require(source==PhoneMusicSource.USB0||source==PhoneMusicSource.USB1)
        val item=item(bus,source)
        val ps=VDMediaPlay().apply {putMediaType(source.oemId)}
        val ts=VDMediaPlayTime().apply {putMediaType(source.oemId)}
        val status=value<VDMediaPlay>(bus.get(VDMediaPlay.createEvent(STATUS,ps)),STATUS,"status")?.takeIf {it.mediaType==source.oemId}
        val time=value<VDMediaPlayTime>(bus.get(VDMediaPlayTime.createEvent(TIME,ts)),TIME,"value")?.takeIf {it.mediaType==source.oemId}
        val title=item?.title?.takeIf {it.isNotBlank()}
        return PhoneMusicSample(title!=null,title?.let {Track(it.take(250),item.artist?.take(250)?:"",LiveMediaPolicy.milliseconds(time?.duration?.toLong()?:-1)?:0)},
            when(status?.playStatus){1->3;2->2;3->1;else->-1},LiveMediaPolicy.milliseconds(time?.position?.toLong()?:-1),now,currentId=item?.fileUrl)
    }
    fun usbName(source:PhoneMusicSource)=when(source){PhoneMusicSource.USB0->"usb0";PhoneMusicSource.USB1->"usb1";else->error("Not USB")}
    private fun <T> call(binder:IBinder,code:Int,source:PhoneMusicSource,argument:String?=null,read:(Parcel)->T):T {
        check(binder.interfaceDescriptor==USB_DESCRIPTOR)
        val data=Parcel.obtain();val reply=Parcel.obtain()
        try {
            data.writeInterfaceToken(USB_DESCRIPTOR);data.writeString(usbName(source));if(argument!=null)data.writeString(argument)
            check(binder.transact(code,data,reply,0));reply.readException();return read(reply)
        }finally {data.recycle();reply.recycle()}
    }
    fun playlist(binder:IBinder,source:PhoneMusicSource):List<MediaQueueItem> = call(binder,6,source,"list_all"){p->
        val list=if(p.readInt()!=0)RemoteBeanList.CREATOR.createFromParcel(p)else null
        if(list?.source!=usbName(source)||list.type!="list_all")emptyList()else list.remoteBeanList.orEmpty().take(2000).mapNotNull {
            val id=it.path?.takeIf {path->path.isNotBlank()&&path.length<=2048}?:return@mapNotNull null
            if(it.source!=null&&it.source!=usbName(source))return@mapNotNull null
            MediaQueueItem(id,Track(it.title?.take(250)?.takeIf(String::isNotBlank)?:id.substringAfterLast('/'),it.artist?.take(250)?:"",LiveMediaPolicy.milliseconds(it.duration.toLong())?:0))
        }.distinctBy {it.id}
    }
    fun cover(binder:IBinder,source:PhoneMusicSource):ByteArray?=call(binder,3,source){it.createByteArray()}
    fun usbCommand(binder:IBinder,source:PhoneMusicSource,action:Int,item:MediaQueueItem?=null) {
        val command=if(item!=null)"set_media" else when(action){1->"play";2->"pause";4->"pre";5->"next";else->error("Unsupported USB command")}
        val json=if(item!=null)JSONObject().put("source",usbName(source)).put("mPath",item.id).put("mTitle",item.track.title).toString()else ""
        check(binder.interfaceDescriptor==USB_DESCRIPTOR)
        val data=Parcel.obtain()
        try {data.writeInterfaceToken(USB_DESCRIPTOR);data.writeString(usbName(source));data.writeString(command);data.writeString(json)
            check(binder.transact(1,data,null,IBinder.FLAG_ONEWAY))
        }finally {data.recycle()}
    }
}
