package ru.jeland.os.core.automation

import ru.jeland.os.core.SettingsStore
import ru.jeland.os.core.DurableSettingsStore
import java.util.UUID

enum class TripStartPhase { WAIT_STOP, READY, CONFIRMING, STARTED }
data class AutomationTripState(val enabled:Boolean=false,val phase:TripStartPhase=TripStartPhase.WAIT_STOP,
    val tripId:String?=null,val detail:String="Ждём подключения ГУ и подтверждённого начала движения.")

/** Motion is an explicit input, never inferred from application, engine or connectivity lifecycle. */
class AutomationTripGate(private val store:SettingsStore) {
    private fun persist(key:String,value:String){if(store is DurableSettingsStore)store.putDurable(key,value)else store.put(key,value)}
    private var active=store.string(ACTIVE,"").takeIf {it.isNotEmpty()}
    var phase=if(active!=null)TripStartPhase.STARTED else TripStartPhase.WAIT_STOP
        private set
    val tripId get()=active
    private var since:Long?=null
    private var last:Long?=null
    private var samples=0
    fun interrupted() {since=null;last=null;samples=0;if(active==null)phase=TripStartPhase.WAIT_STOP}
    /** OEM SLEEP/STR, not a traffic-light stop, display-off, disconnect or app close. */
    fun vehicleSlept(){persist(ACTIVE,"");active=null;interrupted()}
    fun boot(identity:String?) {
        if(identity.isNullOrBlank())return
        val previous=store.string(BOOT,"")
        if(previous.isNotEmpty()&&previous!=identity)vehicleSlept()
        persist(BOOT,identity)
    }
    fun speed(metresPerSecond:Double,at:Long,now:Long):String? {
        if(active!=null)return null
        if(!metresPerSecond.isFinite()||kotlin.math.abs(metresPerSecond)>100||at<0||now-at !in 0..3000){interrupted();return null}
        if(last!=null&&at<=last!!)return null // A replayed sample cannot supply independent confirmation.
        if(last!=null&&at-last!!>1500)interrupted()
        last=at
        val speed=kotlin.math.abs(metresPerSecond)
        if(speed<=.2){since=null;samples=0;phase=TripStartPhase.READY;return null}
        if(phase==TripStartPhase.WAIT_STOP)return null // Opening while already moving never starts scenarios.
        if(speed<1.5){since=null;samples=0;phase=TripStartPhase.READY;return null}
        if(since==null)since=at
        samples++;phase=TripStartPhase.CONFIRMING
        if(samples<3||at-since!!<2500)return null
        val id=UUID.randomUUID().toString()
        persist(ACTIVE,id) // Claim before delivering event; a failed durable write emits no event.
        active=id;phase=TripStartPhase.STARTED
        return id
    }
    companion object {
        const val ENABLED="automation.tripDetection.enabled"
        private const val ACTIVE="automation.tripDetection.active"
        private const val BOOT="automation.tripDetection.boot"
    }
}
