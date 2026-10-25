package ru.jeland.os.core

/** Static firmware evidence: sva.cluster BCM_5 (PPS a005), NOT a CarInfo pressure vector. */
data class QnxTyreTemperatureFrame(val wheel:Wheel,val raw:Int,val status:Int) {
    val celsius:Int? get()=raw.takeIf {it in 0..254&&status in 0..5}?.let {(it*.65-40.0).toInt()}
}

object QnxTyreTemperatureDecoder {
    const val PPS_PATH="/tmp/pps/spi_service/a005"
    fun parse(text:String):QnxTyreTemperatureFrame? {
        if(text.length>2048||text.indexOf('\u0000')>=0)return null
        val lines=text.lineSequence().map {it.trim()}.filter {it.isNotEmpty()}.toList()
        if(lines.firstOrNull()!="@a005")return null
        val fields=lines.filter {it.startsWith("data:")}
        if(fields.size!=1)return null
        val payload=Regex("data:json:\\[([0-9, \\t\\r\\n]+)]").matchEntire(fields.single())?.groupValues?.get(1)?:return null
        val parts=payload.split(',').map {it.trim().toIntOrNull()?:return null}
        if(parts.size!=8||parts.any {it !in 0..255}||parts.all {it==0}||parts.all {it==255})return null
        return decode(parts)
    }
    fun decode(b:List<Int>):QnxTyreTemperatureFrame? {
        if(b.size!=8||b.any {it !in 0..255}||b.all {it==0}||b.all {it==255})return null
        // 0x1a99e8 / jump table 0x87d0d8: 0=LF, 1=RF, 2=LR, 3=RR.
        val wheel=when(b[2] and 3){0->Wheel.FRONT_LEFT;1->Wheel.FRONT_RIGHT;2->Wheel.REAR_LEFT;else->Wheel.REAR_RIGHT}
        val status=when(wheel){
            Wheel.FRONT_LEFT->(b[0] ushr 1) and 7
            Wheel.FRONT_RIGHT->((b[0] and 1) shl 2) or ((b[1] ushr 6) and 3)
            Wheel.REAR_LEFT->(b[1] ushr 3) and 7
            Wheel.REAR_RIGHT->b[1] and 7
        }
        // Main update routine 0x1a6d78 uses signed truncation, raw 0xff / status 6 are invalid.
        // Reserved status 7 is also withheld conservatively until field verification.
        return QnxTyreTemperatureFrame(wheel,b[3],status)
    }
}

class QnxTyreTemperatureCache {
    private data class Reading(val value:Int?,val at:Long)
    private val readings=mutableMapOf<Wheel,Reading>()
    fun accept(frame:QnxTyreTemperatureFrame,now:Long){readings[frame.wheel]=Reading(frame.celsius,now)}
    fun values(now:Long):Map<Wheel,Int> = readings.mapNotNull {(wheel,r)->
        r.value?.takeIf {now-r.at in 0..30_000}?.let {wheel to it}
    }.toMap()
    fun clear(){readings.clear()}
}
