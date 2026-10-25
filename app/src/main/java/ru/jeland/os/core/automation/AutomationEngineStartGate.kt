package ru.jeland.os.core.automation

import ru.jeland.os.core.*
import java.util.UUID

/** OEM INFO/74 (0=stopped, 1=running), corroborated by recorded power/engine transitions.
 * Android resume, accessory mode, stale packets and RPM hypotheses are NOT engine starts.
 * First attachment can confirm an already running engine; the durable cycle ID prevents replay.
 */
class AutomationEngineStartGate(private val store:SettingsStore) {
    private fun persist(value:String) {
        if(store is DurableSettingsStore)store.putDurable(ACTIVE,value)else store.put(ACTIVE,value)
    }
    var cycleId=store.string(ACTIVE,"").takeIf {it.isNotBlank()};private set
    private var last:Long?=null
    private var since:Long?=null
    private var stoppedSince:Long?=null
    fun interrupted(){last=null;since=null;stoppedSince=null}
    fun vehicleSlept(){persist("");cycleId=null;interrupted()}
    fun sample(engine:Int?,power:Int?,at:Long,now:Long,enabled:Boolean=true):String? {
        if(engine !in listOf(0,1)||now-at !in 0..15_000||at<0){interrupted();return null}
        if(last!=null&&at<=last!!)return null
        if(last!=null&&at-last!!>10_000){since=null;stoppedSince=null}
        last=at
        if(engine==0) {
            since=null
            // Start-stop or a stall with ignition on does not rearm comfort commands.
            if(power==0) {
                if(stoppedSince==null)stoppedSince=at
                if(at-stoppedSince!!>=1500&&cycleId!=null){persist("");cycleId=null}
            }else stoppedSince=null
            return null
        }
        stoppedSince=null
        if(cycleId!=null||!enabled){since=null;return null}
        if(since==null){since=at;return null}
        if(at-since!!<1000)return null
        val id=UUID.randomUUID().toString()
        persist(id);cycleId=id
        return id
    }
    companion object {private const val ACTIVE="automation.engineStart.active"}
}
