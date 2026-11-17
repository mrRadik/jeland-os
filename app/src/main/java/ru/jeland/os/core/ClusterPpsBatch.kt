package ru.jeland.os.core

data class ClusterPpsChannel(val bytes:List<Int>?,val error:String?,val snapshot:String)
data class ClusterPpsBatch(val gauge:ClusterPpsChannel,val warnings:ClusterPpsChannel,val computer:ClusterPpsChannel=ClusterPpsChannel(null,null,""),val auxiliary:ClusterPpsChannel=ClusterPpsChannel(null,null,""),val tripComputer:ClusterPpsChannel=ClusterPpsChannel(null,null,"")) {
    fun telemetry(at:Long)=QnxClusterTelemetry(
        gauge.bytes?.let {RawVehicleSample(it,at)},warnings.bytes?.let {RawVehicleSample(it,at)},
        attemptedAtMillis=at,error=listOfNotNull(tripComputer.error?.let {"a001: $it"},auxiliary.error?.let {"a005: $it"},computer.error?.let {"a006: $it"},gauge.error?.let {"a000: $it"},warnings.error?.let {"a00d: $it"}).takeIf {it.isNotEmpty()}?.joinToString("; "),computer=computer.bytes?.let {RawVehicleSample(it,at)},auxiliary=auxiliary.bytes?.let {RawVehicleSample(it,at)},tripComputer=tripComputer.bytes?.let {RawVehicleSample(it,at)})
    companion object {
        fun parse(output:String):ClusterPpsBatch {
            val text=output.replace("\r\n","\n")
            fun channel(name:String,size:Int):ClusterPpsChannel {
                val start="__JOS_${name}__\n";val end="__JOS_${name}_EXIT__:"
                if(text.split(start).size!=2||text.split(end).size!=2)
                    return ClusterPpsChannel(null,"missing/duplicate channel framing","")
                val begin=text.indexOf(start)+start.length;val finish=text.indexOf(end)
                if(finish<begin)return ClusterPpsChannel(null,"channel framing order","")
                val body=text.substring(begin,finish).trim()
                val exit=text.substring(finish+end.length).substringBefore('\n').trim().toIntOrNull()
                    ?:return ClusterPpsChannel(null,"missing command status",body)
                if(exit!=0)return ClusterPpsChannel(null,"read command exit=$exit",body)
                val bytes=ClusterPpsSnapshot.parse(body,size)
                return ClusterPpsChannel(bytes,if(bytes==null)"invalid PPS object/type/length"else null,body)
            }
            return ClusterPpsBatch(channel("GAUGE",26),channel("WARNINGS",49),
                if("__JOS_COMPUTER__" in text)channel("COMPUTER",42)else ClusterPpsChannel(null,null,""),
                if("__JOS_AUXILIARY__" in text)channel("AUXILIARY",52)else ClusterPpsChannel(null,null,""),
                if("__JOS_TRIP__" in text)channel("TRIP",51)else ClusterPpsChannel(null,null,""))
        }
    }
}

/** Receipt status distinguishes missing native packets from absent individual measurements. */
data class ClusterNativeFeedHealth(val gaugePackets:Boolean,val warningPackets:Boolean)
fun VehicleLinkState.clusterNativeFeedHealth(now:Long):ClusterNativeFeedHealth {
    val live=connected&&!demo
    fun fresh(sample:RawVehicleSample?)=sample?.takeIf {live&&now-it.receivedMillis in 0..3000}
    return ClusterNativeFeedHealth(
        fresh(clusterTelemetry.gauge)?.values?.let(QnxClusterTelemetryDecoder::gauge)!=null,
        fresh(clusterTelemetry.warnings)?.values?.let(QnxClusterTelemetryDecoder::warnings)!=null)
}
