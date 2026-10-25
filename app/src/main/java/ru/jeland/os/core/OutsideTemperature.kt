package ru.jeland.os.core

/** OEM CarPlay BaseVehicleDataImpl (q1/k): HVAC /19 * 0.5 - 55; never AAOS's fixed 25. */
object OutsideTemperature {
    val key=VdsKey(VerifiedVehicleProtocol.HVAC,19)
    fun celsius(link:VehicleLinkState,now:Long):Double? {
        if(link.demo||!link.connected)return null
        val sample=link.raw[key]?:return null
        if(now-sample.receivedMillis !in 0..15_000)return null
        return sample.values?.singleOrNull()?.takeIf {it in 0..254}?.let {it*.5-55}
    }
    fun label(value:Double?):String=value?.takeIf {it.isFinite()}?.let {
        val number=if(it==kotlin.math.round(it))it.toInt().toString()else java.lang.String.format(java.util.Locale.ROOT,"%.1f",it).replace('.',',')
        "${if(it>0)"+"else""}$number°"
    }?:"—°"
}
