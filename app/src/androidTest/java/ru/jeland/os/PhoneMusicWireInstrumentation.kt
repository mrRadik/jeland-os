package ru.jeland.os

import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.bt.bean.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.data.media.*

@RunWith(AndroidJUnit4::class)
class PhoneMusicWireInstrumentation {
    private class Cls:Binder() {
        val requests=mutableListOf<String>();var failDown=false
        init {attachInterface(null,CarPlayClsProtocol.DESCRIPTOR)}
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            assertEquals(1,code);assertEquals(IBinder.FLAG_ONEWAY,flags)
            data.enforceInterface(CarPlayClsProtocol.DESCRIPTOR)
            assertEquals(1,data.readInt());assertEquals(1,data.readInt());assertEquals(6,data.readInt())
            val command=data.readString()!!;requests+=command
            assertEquals(-1,data.readInt());repeat(5){assertEquals(0,data.readByte().toInt())};assertEquals(1,data.readByte().toInt());assertEquals(0,data.dataAvail())
            if(failDown&&command.endsWith("_DOWN"))throw IllegalStateException("Partial DOWN failure")
            return true
        }
    }
    @Test fun onlyFourClsActionsSendExactPhoneLinkParcelAndRelease() {
        val binder=Cls()
        for(action in listOf(1,2,4,5))CarPlayClsProtocol.sendPair(binder,action){true}
        assertEquals(listOf("MEDIA_PLAY_DOWN","MEDIA_PLAY_UP","MEDIA_PAUSE_DOWN","MEDIA_PAUSE_UP",
            "MEDIA_PREVIOUS_DOWN","MEDIA_PREVIOUS_UP","MEDIA_NEXT_DOWN","MEDIA_NEXT_UP"),binder.requests)
        try {CarPlayClsProtocol.sendPair(binder,3){true};fail()}catch(expected:IllegalArgumentException){}
        try {CarPlayClsProtocol.sendPair(binder,1){false};fail()}catch(expected:SecurityException){}
        assertEquals(8,binder.requests.size)
        val partial=Cls().apply {failDown=true}
        try {CarPlayClsProtocol.sendPair(partial,1){true}}catch(expected:Exception){}
        assertEquals(listOf("MEDIA_PLAY_DOWN","MEDIA_PLAY_UP"),partial.requests)
    }
    @Test fun btCommandsKeepExactEventClassAndPlayTypeZero() {
        for(action in listOf(1,2,4,5)) {
            val source=BtProtocol.command(action);val p=Parcel.obtain()
            try {
                source.writeToParcel(p,0);p.setDataPosition(0)
                val value=VDEvent.CREATOR.createFromParcel(p)
                assertEquals(262948,value.id);value.payload.classLoader=VDBTMusicPlay::class.java.classLoader
                val play=value.payload.getParcelable<VDBTMusicPlay>("status")!!
                assertEquals(action,play.control);assertEquals(0,play.type)
            }finally {p.recycle()}
        }
        try {BtProtocol.command(3);fail()}catch(expected:IllegalArgumentException){}
        val b=Bundle().apply {putParcelable("status",VDBTConnect(2))}
        assertEquals(2,BtProtocol.value(VDEvent(BtProtocol.A2DP,b),BtProtocol.A2DP,VDBTConnect::class.java).status)
        try {BtProtocol.value(VDEvent(123,b),BtProtocol.A2DP,VDBTConnect::class.java);fail()}catch(expected:IllegalArgumentException){}
    }
}
