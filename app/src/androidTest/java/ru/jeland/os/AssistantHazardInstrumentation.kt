package ru.jeland.os

import android.content.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import com.desaysv.ivi.vdb.*
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.vr.bean.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.data.OemAssistantAvatar
import java.util.concurrent.CopyOnWriteArrayList

/** Fake Context and Binder only: never binds OEM or sends a visibility request to the HU. */
class AssistantHazardInstrumentation {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
    private fun await(condition:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+3000
        while(!condition()&&SystemClock.elapsedRealtime()<deadline){Thread.sleep(10)}
        assertTrue("Timed out waiting for fake transport",condition())
    }
    private class Wire:IVDBus.Stub() {
        val ids=CopyOnWriteArrayList<Int>()
        val removed=CopyOnWriteArrayList<Int>()
        var notify:IVDBusNotify?=null
        override fun get(e:VDEvent?):VDEvent?=error("No ASR polling")
        override fun set(e:VDEvent?)=error("Must never issue an ASR/vehicle SET")
        override fun subscribe(ids:IntArray?,pid:Int,pkg:String?,callback:IVDBusCallback?)=error("Wrong API")
        override fun subscribeCustomizedEvent(e:VDEvent,callback:IVDBusNotify){ids+=e.id;notify=callback}
        override fun unsubscribeCustomizedEvent(e:VDEvent,callback:IVDBusNotify){removed+=e.id}
        fun status(value:Int) {
            val p=Parcel.obtain()
            val bean=try {p.writeString("controlRecorder");p.writeInt(value);p.setDataPosition(0);VDAsr.CREATOR.createFromParcel(p)}finally {p.recycle()}
            notify!!.onVDBusNotify(VDEvent(OemAssistantAvatar.ASR,Bundle().apply {putParcelable("status",bean)}))
        }
    }
    private class FakeContext(base:Context,val wire:Wire,val available:Boolean=true):ContextWrapper(base) {
        val requests=CopyOnWriteArrayList<Intent>()
        override fun getApplicationContext():Context=this
        override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int):Boolean {
            assertEquals("com.desaysv.ivi.vds.vr",intent.component!!.packageName)
            if(available)connection.onServiceConnected(intent.component!!,wire)
            return available
        }
        override fun unbindService(connection:ServiceConnection){}
        override fun startForegroundService(intent:Intent):ComponentName {
            assertEquals("com.iflytek.autofly.sceneengine.DATAUPLOAD",intent.action)
            assertEquals("com.iflytek.autofly.sceneengine",intent.`package`)
            requests+=Intent(intent)
            return ComponentName(intent.`package`!!,"FakeOnly")
        }
    }
    @Test fun avatarHideActiveRestoreAreDeduplicatedAndNeverWriteVehicleOrAsr() {
        val wire=Wire();val fake=FakeContext(instrumentation.targetContext,wire)
        val client=OemAssistantAvatar(fake,{})
        try {
            main {client.start()};await {fake.requests.size==1}
            assertEquals(setOf(0x80002,0x80004),wire.ids.toSet())
            fun shown(index:Int)=JSONObject(fake.requests[index].getStringExtra("value")!!).getBoolean("showAvatar")
            val body=JSONObject(fake.requests.first().getStringExtra("value")!!)
            assertEquals("active_care",body.getString("dt"));assertEquals("view_avatar",body.getString("type"))
            assertEquals("ru.jeland.os.MainActivity",body.getString("activityFullPath"));assertFalse(shown(0))
            main {wire.status(1000)};await {fake.requests.size==2};assertTrue(shown(1))
            main {wire.status(1001);wire.status(1002)};instrumentation.waitForIdleSync();assertEquals(2,fake.requests.size)
            main {wire.status(1003)};await {fake.requests.size==3};assertFalse(shown(2))
            main {client.stop()};await {fake.requests.size==4};assertTrue(shown(3))
            await {wire.removed.size==2}
        }finally {main {client.stop()}}
    }
    @Test fun unavailableStatusServiceDoesNotSendAnyHideRequest() {
        val fake=FakeContext(instrumentation.targetContext,Wire(),available=false)
        val client=OemAssistantAvatar(fake,{})
        main {client.start();client.stop()}
        assertTrue(fake.requests.isEmpty())
    }
    @Test fun parcelTypesConsumeOnlyTheInstalledOemWireFields() {
        val p=Parcel.obtain()
        try {
            p.writeString("controlRecorder");p.writeInt(1003);p.setDataPosition(0)
            val asr=VDAsr.CREATOR.createFromParcel(p)
            assertEquals("controlRecorder",asr.key);assertEquals(1003,asr.value);assertEquals(0,p.dataAvail())
        }finally {p.recycle()}
        val q=Parcel.obtain()
        try {
            q.writeString("iflytek");q.writeString("ttsPlayBegin");q.writeInt(1);q.setDataPosition(0)
            val tts=VDTTS.CREATOR.createFromParcel(q)
            assertEquals("ttsPlayBegin",tts.ttsType);assertEquals(1,tts.ttsStatus);assertEquals(0,q.dataAvail())
        }finally {q.recycle()}
    }
}
