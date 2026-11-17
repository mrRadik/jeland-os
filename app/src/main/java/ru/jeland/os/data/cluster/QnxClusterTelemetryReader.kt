package ru.jeland.os.data.cluster

import android.os.SystemClock
import ru.jeland.os.core.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One foreground read-only native feed shared by both displays. */
class QnxClusterTelemetryReader(private val report:(String)->Unit,private val sample:(QnxClusterTelemetry)->Unit):AutoCloseable {
    private val worker=Executors.newSingleThreadScheduledExecutor()
    private val client=QnxClusterWindow()
    private val closed=AtomicBoolean(false)
    private var retryAt=0L
    private var previousGauge:List<Int>?=null
    private var previousWarnings:List<Int>?=null
    private var previousComputer:List<Int>?=null
    private var previousAuxiliary:List<Int>?=null
    private var previousTrip:List<Int>?=null
    private var previousError:String?=null
    private var firstRead=true
    fun start() {
        report("CLUSTER_NATIVE_READER_START protocol=PPS_JSON_V2")
        worker.scheduleWithFixedDelay({
            if(closed.get()||SystemClock.elapsedRealtime()<retryAt)return@scheduleWithFixedDelay
            try {
                val output=client.readTelemetry()
                if(closed.get())return@scheduleWithFixedDelay
                val decoded=ClusterPpsBatch.parse(output)
                val at=SystemClock.elapsedRealtime()
                val reading=decoded.telemetry(at)
                if(firstRead||reading.error!=previousError) {
                    // Capture the exact read result, even when neither parser accepts it.
                    report("CLUSTER_NATIVE_RAW_V2 ${output.take(6000).replace("\n","\\n")}")
                    report("CLUSTER_NATIVE_CHANNELS gauge=${decoded.gauge.bytes!=null} indicators=${decoded.warnings.bytes!=null} error=${reading.error}")
                    firstRead=false;previousError=reading.error
                }
                val gauge=decoded.gauge.bytes;val warnings=decoded.warnings.bytes
                if(gauge!=previousGauge){report("CLUSTER_GAUGE_NATIVE VALUE=$gauge");previousGauge=gauge}
                if(warnings!=previousWarnings){
                    val flags=QnxClusterTelemetryDecoder.warnings(warnings)
                    report("CLUSTER_WARNING_NATIVE VALUE=$warnings id=${flags?.messageId} lamps=${flags?.lamps} coolant=${flags?.coolant}")
                    previousWarnings=warnings
                }
                if(decoded.computer.bytes!=previousComputer) {
                    previousComputer=decoded.computer.bytes
                    report("CLUSTER_COMPUTER_NATIVE VALUE=$previousComputer gear=${previousComputer?.firstOrNull()}")
                }
                if(decoded.auxiliary.bytes!=previousAuxiliary) {
                    previousAuxiliary=decoded.auxiliary.bytes
                    report("CLUSTER_AUXILIARY_NATIVE VALUE=$previousAuxiliary")
                }
                if(decoded.tripComputer.bytes!=previousTrip) {
                    previousTrip=decoded.tripComputer.bytes
                    report("CLUSTER_TRIP_NATIVE VALUE=$previousTrip readings=${ClusterTripComputer.decode(previousTrip)}")
                }
                sample(reading)
                if(gauge==null&&warnings==null)retryAt=at+5000
            }catch(error:Exception) {
                if(!closed.get()) {
                    val detail="${error.javaClass.simpleName}: ${error.message}"
                    report("CLUSTER_NATIVE_READ_ERROR $detail; retry in 5s")
                    val at=SystemClock.elapsedRealtime()
                    sample(QnxClusterTelemetry(attemptedAtMillis=at,error=detail))
                    retryAt=at+5000
                    previousGauge=null;previousWarnings=null;firstRead=true
                }
                client.close()
            }finally {if(closed.get())client.close()}
        },0,80,TimeUnit.MILLISECONDS)
    }
    override fun close(){closed.set(true);client.close();worker.shutdownNow()}
}
