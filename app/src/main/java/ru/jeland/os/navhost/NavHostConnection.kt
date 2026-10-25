package ru.jeland.os.navhost

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.view.MotionEvent
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.core.NavigationProvider

data class NavHostState(val connected:Boolean=false,val displayId:Int=-1,val attached:Boolean=false,val inputs:Int=0,val error:String?=null)

/** Binder capability supplied only by shell through the protected provider. */
object NavHostConnection {
    var report:(String)->Unit={}
    private val flow=MutableStateFlow(NavHostState())
    val state=flow.asStateFlow()
    private val worker=Handler(HandlerThread("Jeland-nav-host-client").apply {start()}.looper)
    private val lifetime=Binder()
    @Volatile private var host:IBinder?=null
    private var owner:Any?=null
    @Synchronized fun register(binder:IBinder):Bundle {
        if(host?.isBinderAlive==true)return Bundle().apply {putBoolean("accepted",false)}
        host=binder
        binder.linkToDeath({synchronized(this) {if(host===binder){host=null;flow.value=NavHostState(error="Помощник отключён. Подключите ADB и запустите ENABLE-NAVIGATION.cmd.")}}},0)
        flow.value=NavHostState(connected=true)
        report("NAV_HOST registered shell helper")
        return Bundle().apply {putBoolean("accepted",true);putBinder("client",lifetime)}
    }
    private fun transact(code:Int,write:(Parcel)->Unit={}) {
        val binder=host?:return
        val data=Parcel.obtain();val reply=Parcel.obtain()
        try {
            data.writeInterfaceToken(NavHostShell.DESCRIPTOR);write(data)
            check(binder.transact(code,data,reply,0)){"Helper protocol unavailable"}
            reply.readException();check(reply.readInt()==1){"Helper version mismatch"}
            val display=reply.readInt();val attached=reply.readInt()==1;val inputs=reply.readInt()
            flow.value=NavHostState(connected=true,displayId=display,attached=attached,inputs=inputs)
            if(code!=NavHostShell.TOUCH)report("NAV_HOST operation=$code display=$display attached=$attached")
        } catch(error:Exception) {report("NAV_HOST failed operation=$code ${error.javaClass.simpleName}");flow.value=flow.value.copy(connected=binder.isBinderAlive,attached=false,error=error.message?.take(200)?:"Ошибка подключения карты")}
        finally {data.recycle();reply.recycle()}
    }
    fun attach(token:Any,provider:NavigationProvider,surface:Surface,width:Int,height:Int,dpi:Int) {worker.post {
        owner=token
        if(surface.isValid)transact(NavHostShell.ATTACH) {p->p.writeInt(if(provider==NavigationProvider.DGIS)0 else 1);p.writeInt(width);p.writeInt(height);p.writeInt(dpi);surface.writeToParcel(p,0)}
    }}
    fun detach(token:Any) {worker.post {if(owner===token){owner=null;transact(NavHostShell.DETACH)}}}
    fun back() {worker.post {transact(NavHostShell.BACK)}}
    fun reopen() {worker.post {transact(NavHostShell.REOPEN)}}
    fun stop() {worker.post {transact(NavHostShell.STOP);host=null;flow.value=NavHostState()}}
    fun touch(event:MotionEvent) {
        val copy=MotionEvent.obtain(event)
        worker.post {try {transact(NavHostShell.TOUCH){copy.writeToParcel(it,0)}} finally {copy.recycle()}}
    }
}

class NavHostProvider:ContentProvider() {
    override fun onCreate()=true
    override fun call(method:String,arg:String?,extras:Bundle?):Bundle {
        if(Binder.getCallingUid()!=2000)throw SecurityException("ADB shell registration only")
        if(method=="status")return Bundle().apply {putBoolean("connected",NavHostConnection.state.value.connected);putString("navigationMode","offline")}
        if(method=="stop"){NavHostConnection.stop();return Bundle().apply {putBoolean("stopping",true)}}
        require(method=="register"){"Unsupported registration"}
        throw IllegalStateException("This version uses its own offline map; the legacy ADB host is disabled")
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
    override fun getType(uri:Uri):String?=null
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
}
