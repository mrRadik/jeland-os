package ru.jeland.os.core

/** Snapshots of the OEM PPS records, never Android MeterMalfunction fragments. */
data class QnxClusterTelemetry(val gauge:RawVehicleSample?=null,val warnings:RawVehicleSample?=null,
    val attemptedAtMillis:Long?=null,val error:String?=null,val computer:RawVehicleSample?=null,val auxiliary:RawVehicleSample?=null,val tripComputer:RawVehicleSample?=null)
data class ClusterGaugeReadings(val rpm:Double?,val speed:Double?,val coolant:Double?,val coolantLevel:Double?=null,val coolantRaw:Int?=null)
data class ClusterWarningReadings(val lamps:Set<ClusterLamp>,val coolant:ClusterCoolantCondition,val message:ClusterOemMessage?,val messageId:Int,val indicators:List<ClusterIndicatorSpec> = emptyList())

/** SOP4/02.00.08 native ABI, decoded from the installed sva.cluster binary.
 * a000: 6 pairs of little-endian uint16 analog/digital values, then two flags.
 * a00d: 39 indicator bytes, uint16 warning ID at 39, count/belt metadata,
 *       and OEM flash state at 48. No guessed Android/raw*4 RPM conversion.
 */
object QnxClusterTelemetryDecoder {
    private fun valid(bytes:List<Int>?,size:Int)=bytes?.size==size&&bytes.all {it in 0..255}
    private fun word(bytes:List<Int>,at:Int)=bytes[at] or (bytes[at+1] shl 8)
    fun gauge(bytes:List<Int>?):ClusterGaugeReadings? {
        if(!valid(bytes,26))return null
        val b=bytes!!
        fun value(at:Int,max:Int)=word(b,at).takeIf {it<=max}?.toDouble()
        // OEM RcMClusterCoolantTempUpdate reads analog index 2 and divides by 1000.
        // J7 captures: digital 0..220 is NOT a validated Celsius sensor value.
        return ClusterGaugeReadings(value(2,8000),value(6,360),null,
            value(8,1000)?.div(1000.0),word(b,10).takeUnless {it==65535})
    }
    fun warnings(bytes:List<Int>?):ClusterWarningReadings? {
        if(!valid(bytes,49))return null
        val b=bytes!!
        fun bit(index:Int)=b[index/8] and (1 shl (index%8))!=0
        val lamps=buildSet {
            if(bit(0))add(ClusterLamp.LEFT)
            if(bit(1))add(ClusterLamp.RIGHT)
            for((index,lamp) in indicatorBits)if(bit(index))add(lamp)
            if(bit(107)||bit(108)||bit(200))add(ClusterLamp.BELT)
        }
        val coolant=when {bit(30)->ClusterCoolantCondition.HOT;bit(31)->ClusterCoolantCondition.COLD
            bit(75)->ClusterCoolantCondition.NORMAL;else->ClusterCoolantCondition.UNKNOWN}
        val id=word(b,39)
        return ClusterWarningReadings(lamps,coolant,ClusterMessageCatalog.find(id),id,ClusterIndicatorCatalog.active(b))
    }
    private val indicatorBits=mapOf(
        2 to ClusterLamp.POSITION,3 to ClusterLamp.HIGH_BEAM,7 to ClusterLamp.REAR_FOG,
        16 to ClusterLamp.EPC,17 to ClusterLamp.CHECK_ENGINE,18 to ClusterLamp.EPB,19 to ClusterLamp.EPB_FAULT,
        20 to ClusterLamp.AUTO_HOLD,21 to ClusterLamp.CHARGING,22 to ClusterLamp.BRAKE,23 to ClusterLamp.ABS,
        24 to ClusterLamp.ESP,25 to ClusterLamp.ESP_OFF,26 to ClusterLamp.AIRBAG,27 to ClusterLamp.TPMS,
        28 to ClusterLamp.GEARBOX,29 to ClusterLamp.EPS,30 to ClusterLamp.COOLANT_HOT,
        32 to ClusterLamp.OIL_PRESSURE,33 to ClusterLamp.RESERVE,34 to ClusterLamp.HDC_ACTIVE,35 to ClusterLamp.HDC,
        36 to ClusterLamp.START_STOP_READY,37 to ClusterLamp.START_STOP_UNAVAILABLE,
        38 to ClusterLamp.BSD_ACTIVE,39 to ClusterLamp.BSD_WARNING,
        40 to ClusterLamp.LDW_ACTIVE,41 to ClusterLamp.LDW_WARNING,42 to ClusterLamp.LDW_STANDBY,
        43 to ClusterLamp.LANE_ACTIVE,44 to ClusterLamp.LANE_WARNING,45 to ClusterLamp.LANE_STANDBY,
        66 to ClusterLamp.GENERAL_WARNING,84 to ClusterLamp.FRONT_FOG,
        104 to ClusterLamp.GEARBOX_RED,137 to ClusterLamp.ICE,140 to ClusterLamp.WASHER,
        143 to ClusterLamp.LANE_RED,145 to ClusterLamp.EPS_RED,151 to ClusterLamp.AUTO_HOLD_READY,
        152 to ClusterLamp.AUTO_HOLD_FAULT
    )
}

/** PPS wire format from field captures; a00d is rearranged by OEM RcDataSet(type=5).
 * The warning catalog consumes the internal ABI, not the wire bytes.
 */
object ClusterPpsSnapshot {
    private val integer=Regex("-?(?:0|[1-9][0-9]{0,2})")
    private val envelope=Regex("""\{\s*"len"\s*:\s*([0-9]{1,4})\s*,\s*"data"\s*:\s*(\[[0-9,\s-]*\])\s*\}""")
    private val reverseEnvelope=Regex("""\{\s*"data"\s*:\s*(\[[0-9,\s-]*\])\s*,\s*"len"\s*:\s*([0-9]{1,4})\s*\}""")
    fun parse(text:String,size:Int):List<Int>? {
        if(text.length>8192||'\u0000' in text)return null
        val objectName=when(size){26->"a000";49->"a00d";42->"a006";52->"a005";51->"a001";else->return null}
        val qualifier=Regex("^\\[(?:-?[niprd]|[0-7])+\\]")
        val lines=text.lineSequence().map {it.trim().replaceFirst(qualifier,"")}.filter {it.isNotEmpty()}.toList()
        if(lines.any {it.startsWith("-data")||it.startsWith("#data")})return null
        if(lines.firstOrNull()!="@$objectName"||lines.count {it.startsWith('@')}!=1)return null
        val field=lines.filter {it.startsWith("data:")}.singleOrNull()?:return null
        if(!field.startsWith("data:json:"))return null
        val body=field.removePrefix("data:json:").trim()
        val forward=envelope.matchEntire(body);val reverse=reverseEnvelope.matchEntire(body)
        val wire=forward!=null||reverse!=null
        val array=forward?.groupValues?.get(2)?:reverse?.groupValues?.get(1)?:body
        val declared=(forward?.groupValues?.get(1)?:reverse?.groupValues?.get(2))?.toIntOrNull()
        if(!array.startsWith('[')||!array.endsWith(']'))return null
        val fields=array.substring(1,array.length-1).split(',')
        val minimum=if(wire&&size==49)52 else size
        if(fields.size<minimum||declared!=null&&declared!=fields.size)return null
        val bytes=fields.map {it.trim().takeIf(integer::matches)?.toIntOrNull()
            ?.takeIf {n->n in -128..255}?.and(255)?:return null}
        return if(wire&&size==49)warningAbi(bytes)else bytes.take(size)
    }
    /** sva.cluster 0x171de8..0x171e98; offsets are relative to wire payload, no message/header bits as lamps. */
    private fun warningAbi(b:List<Int>):List<Int> = buildList {
        addAll(b.subList(0,20));addAll(b.subList(29,39));addAll(b.subList(43,52))
        addAll(b.subList(20,29));add(b[39])
    }
}
