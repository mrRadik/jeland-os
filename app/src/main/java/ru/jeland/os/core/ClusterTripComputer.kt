package ru.jeland.os.core

enum class ClusterTripPeriod(val title:String) {
    SINCE_START("С момента запуска"), SINCE_RESET("С момента сброса");
    companion object {
        fun fromStored(value:String)=entries.firstOrNull {it.name==value}?:SINCE_START
    }
}

/** SOP4 a001 / RcDataSet(type=1), 51 bytes. OEM AfterStart binds ShortTermMileage
 * (getTripA, +4), ShortAverageFuelConsumption (getAvgFuelCons0, +20) and
 * AfterStartHour/Minute (getTravelTimeB, +28). AfterReset binds LongTermMileage
 * (getTripB, +8), LongAverageFuelConsumption (getAvgFuelCons1, +32) and
 * AfterResetHour/Minute (getTravelTimeA, +16). The A/B names are not UI periods.
 * Native source SHA256: be5033c9ecb0212076940382b60b0ae34b48df5c852e05f7694c510bc9e7fe6c.
 */
data class ClusterTripReadings(val distanceKm:Double?,val elapsedMillis:Long?,val consumption:Double?)
object ClusterTripComputer {
    fun decode(bytes:List<Int>?,period:ClusterTripPeriod=ClusterTripPeriod.SINCE_START):ClusterTripReadings? {
        if(bytes?.size!=51||bytes.any {it !in 0..255})return null
        val (distanceAt,fuelAt,timeAt)=when(period) {
            ClusterTripPeriod.SINCE_START->Triple(4,20,28)
            ClusterTripPeriod.SINCE_RESET->Triple(8,32,16)
        }
        fun word(at:Int)=bytes[at] or (bytes[at+1] shl 8)
        val distance=(0..3).fold(0L){n,i->n or (bytes[distanceAt+i].toLong() shl (i*8))}
            .takeUnless {it==0xffffffffL}?.div(10.0)?.coerceAtMost(9999.9)
        val fuel=word(fuelAt).takeIf {it and 0xf000==0}?.div(10.0)
        val hours=word(timeAt);val minutes=bytes[timeAt+2]
        val time=if(hours!=65535&&minutes in 0..59)(hours*60L+minutes)*60_000 else null
        return ClusterTripReadings(distance,time,fuel)
    }
}
