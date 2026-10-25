package ru.jeland.os.core

/** Null means unavailable, malformed or stale; never a synthetic zero. */
data class BatteryReading(
    val voltage:Double?=null,val chargePercent:Int?=null,val healthPercent:Int?=null,
    val ibsVoltage:Double?=null,val temperatureCelsius:Int?=null,val currentAmps:Double?=null,
    val startingVoltage:Double?=null,val quiescentAmps:Double?=null,
) {
    companion object {val DEMO=BatteryReading(14.6,84,100,14.13,13,1.8,8.69,.02)}
}

/** Formulas from Desay EngMode BatteryInfoActivityModel, applied to CarInfo payloads.
 * Cross-transport mapping is source-backed but still needs comparison on this HU. */
object BatteryDecoder {
    val commands=listOf(76,96,97,109,110,111,113,115)
    private fun bytes(value:List<Int>?,size:Int)=value?.takeIf {it.size==size&&it.all {v->v in 0..255}}
    private fun word(value:List<Int>?)=bytes(value,2)?.let {(it[0] shl 8)or it[1]}
    private fun byte(value:List<Int>?)=bytes(value,1)?.singleOrNull()?.takeIf {it!=255}
    fun decode(get:(Int)->List<Int>?):BatteryReading {
        val current=bytes(get(109),4)?.let {
            val raw=(it[2] shl 8)or it[3]
            if(raw==65535)null else when(it[1]){0->raw/1024.0-32;1->raw/128.0-256;2->raw/16.0-2048;else->null}
        }
        return BatteryReading(
            voltage=word(get(76))?.div(10.0)?.takeIf {it in 0.0..32.0},
            chargePercent=byte(get(96))?.takeIf {it in 0..100},
            healthPercent=byte(get(97))?.takeIf {it in 0..100},
            ibsVoltage=word(get(110))?.let {it/1024.0+3}?.takeIf {it in 0.0..32.0},
            temperatureCelsius=byte(get(111))?.minus(40)?.takeIf {it in -40..125},
            currentAmps=current,
            startingVoltage=byte(get(113))?.times(.0625),
            quiescentAmps=byte(get(115))?.div(256.0),
        )
    }
}
