package ru.jeland.os.data

import android.bluetooth.*
import android.content.*
import android.net.*
import android.net.wifi.*
import android.os.*
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.core.*

/** Observes radios only. No discovery, pairing, connect/disconnect, network request or Internet permission. */
class AndroidConnectivityRepository(context:Context,private val report:(String)->Unit={}):ConnectivityRepository {
    private val context=context.applicationContext
    private val cm=context.getSystemService(ConnectivityManager::class.java)
    private val wifi=context.applicationContext.getSystemService(WifiManager::class.java)
    private val tm=context.getSystemService(TelephonyManager::class.java)
    private val bt=context.getSystemService(BluetoothManager::class.java)?.adapter
    private val main=Handler(Looper.getMainLooper())
    private val flow=MutableStateFlow(ConnectivityState());override val state=flow.asStateFlow()
    private var started=false;private var networkRegistered=false;private var receiverRegistered=false;private var epoch=0
    private val profiles=mutableMapOf<Int,BluetoothProfile>();private val pending=mutableSetOf<Int>()
    private val tbox=OemMobileCache(this.context,{update()},report)
    private val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(n:Network){main.post {if(started)update()}}
        override fun onLost(n:Network){main.post {if(started)update()}}
        override fun onCapabilitiesChanged(n:Network,c:NetworkCapabilities){main.post {if(started)update()}}
    }
    private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(started)update()}}
    private val tick=object:Runnable {override fun run(){if(started){update();main.postDelayed(this,3000)}}}
    override fun start() {
        if(started){update();return};started=true;epoch++
        runCatching {cm.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities().build(),callback);networkRegistered=true}
        val filter=IntentFilter().apply {
            listOf(WifiManager.WIFI_STATE_CHANGED_ACTION,WifiManager.NETWORK_STATE_CHANGED_ACTION,WifiManager.RSSI_CHANGED_ACTION,
                BluetoothAdapter.ACTION_STATE_CHANGED,BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED","android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED")
                .forEach(::addAction)
        }
        runCatching {if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED)
            else @Suppress("DEPRECATION") context.registerReceiver(receiver,filter);receiverRegistered=true}
        tbox.start();main.post(tick)
    }
    override fun stop() {
        started=false;epoch++;main.removeCallbacks(tick)
        if(networkRegistered)runCatching {cm.unregisterNetworkCallback(callback)};networkRegistered=false
        if(receiverRegistered)runCatching {context.unregisterReceiver(receiver)};receiverRegistered=false
        profiles.forEach {(id,proxy)->runCatching {bt?.closeProfileProxy(id,proxy)}};profiles.clear();pending.clear();tbox.stop()
    }
    @Suppress("MissingPermission") private fun requestProfiles() {
        val token=epoch
        for(id in listOf(11,16))if(id !in profiles&&pending.add(id)) {
            val listener=object:BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile:Int,proxy:BluetoothProfile) {
                    main.post {
                        if(!started||token!=epoch||runCatching {bt?.isEnabled}.getOrNull()!=true){runCatching {bt?.closeProfileProxy(profile,proxy)}}
                        else {pending.remove(profile);profiles[profile]=proxy;update()}
                    }
                }
                override fun onServiceDisconnected(profile:Int){main.post {if(started&&token==epoch){profiles.remove(profile);pending.remove(profile)}}}
            }
            runCatching {if(bt?.getProfileProxy(context,listener,id)!=true)pending.remove(id)}.onFailure {pending.remove(id)}
        }
    }
    @Suppress("DEPRECATION","MissingPermission") private fun update() {
        if(!started)return
        val caps=runCatching {cm.allNetworks.mapNotNull(cm::getNetworkCapabilities)}.getOrDefault(emptyList())
        val wifiCaps=caps.firstOrNull {it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}
        val info=runCatching {wifi?.connectionInfo}.getOrNull()
        val wifiOn=runCatching {wifi?.isWifiEnabled}.getOrNull()
        val connected=wifiCaps!=null||(wifiOn==true&&info?.supplicantState==SupplicantState.COMPLETED)
        val level=if(connected)RadioReadings.wifiLevel(info?.rssi)?:RadioReadings.wifiLevel(wifiCaps?.signalStrength)else null
        val mobile=tbox.value?.takeIf {SystemClock.elapsedRealtime()-it.at in 0..30000}
        val cellular=caps.any {it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}||mobile?.connected==true
        val mobileLevel=mobile?.level?.takeIf {mobile.registered}?:if(cellular)runCatching {RadioReadings.mobileLevel(tm.signalStrength?.level)}.getOrNull()else null
        val type=mobile?.type?:if(cellular)runCatching {when(tm.dataNetworkType){13->"4G";20->"5G";3,8,9,10,15->"3G";1,2->"2G";else->null}}.getOrNull()else null
        val bluetooth=runCatching {bt?.isEnabled}.getOrNull()
        if(bluetooth==true)requestProfiles()else {profiles.forEach {(id,proxy)->runCatching {bt?.closeProfileProxy(id,proxy)}};profiles.clear();pending.clear()}
        fun profile(id:Int):Boolean?=if(bluetooth==false)false else profiles[id]?.let {runCatching {it.connectedDevices.isNotEmpty()}.getOrNull()}
        val audio=profile(11);val phone=profile(16)
        val paired=when {bluetooth==false->false;audio==true||phone==true->true;audio==false&&phone==false->false;else->null}
        val next=ConnectivityState(connected,level,cellular,mobileLevel,type,available=true,wifiEnabled=wifiOn,
            bluetoothEnabled=bluetooth,bluetoothConnected=paired,bluetoothAudio=audio,bluetoothPhone=phone,
            internetValidated=caps.any {it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)})
        if(next!=flow.value){flow.value=next;report("RADIOS wifi=$connected level=$level mobile=$cellular level=$mobileLevel type=$type bt=$bluetooth audio=$audio phone=$phone internet=${next.internetValidated}")}
    }
}
