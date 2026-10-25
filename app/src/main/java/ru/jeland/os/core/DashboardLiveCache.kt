package ru.jeland.os.core

/** Preserve a last valid outdoor sample during a refresh gap, never indefinitely or across DEMO. */
class OutsideReadingCache(private val holdMillis:Long=600_000) {
    private var value:Double?=null
    private var received=0L
    fun update(candidate:Double?,now:Long):Double? {
        if(candidate!=null&&candidate.isFinite()){value=candidate;received=now}
        return value.takeIf {now-received in 0..holdMillis}
    }
    fun clear(){value=null;received=0}
}

/** Short-lived command projection, distinct from measured VDS feedback. Only used for rapid relative taps. */
class CommandProjection(private val holdMillis:Long=2500) {
    private val values=mutableMapOf<VdsKey,Pair<Int,Long>>()
    @Synchronized fun record(key:VdsKey,value:Int,now:Long){values[key]=value to now}
    @Synchronized fun value(key:VdsKey,now:Long):Int?=values[key]?.takeIf {now-it.second in 0..holdMillis}?.first
    @Synchronized fun clear(){values.clear()}
}
