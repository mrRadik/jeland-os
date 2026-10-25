package ru.jeland.os.data

import ru.jeland.os.core.*
import android.os.SystemClock
import java.io.IOException
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Foreground VEHICLE-only reader. Never writes files or vehicle parameters on the QNX host. */
class QnxTyreTemperatureReader(private val report:(String)->Unit,private val sample:(QnxTyreTemperatureFrame)->Unit,
    private val diagnostics:(TyreTemperatureDiagnostics)->Unit={}) {
    private val worker=Executors.newSingleThreadScheduledExecutor()
    private val active=AtomicReference<Socket?>()
    @Volatile private var generation=0
    private var task:ScheduledFuture<*>?=null
    fun start() {
        stop();val token=generation
        var client:QnxTyreReadProtocol?=null;var socket:Socket?=null;var iface:String?=null
        var retryAt=0L;var deadline=0L;var last:QnxTyreTemperatureFrame?=null
        var debug=TyreTemperatureDiagnostics(active=true)
        fun update(value:TyreTemperatureDiagnostics){debug=value;if(token==generation)diagnostics(value)}
        fun guard() {
            if(token!=generation)throw IOException("QNX tyre read cancelled")
            if(SystemClock.elapsedRealtime()>deadline)throw IOException("QNX tyre operation deadline")
        }
        task=worker.scheduleWithFixedDelay({
            if(token!=generation||SystemClock.elapsedRealtime()<retryAt)return@scheduleWithFixedDelay
            try {
                deadline=SystemClock.elapsedRealtime()+4000;guard()
                if(client==null) {
                    update(debug.copy(phase=TyreReadPhase.INTERFACE,attempts=debug.attempts+1,retryAtMillis=null,
                        tcpConnected=false,interfaceName="",trace=TyreProtocolTrace()))
                    val ni=NetworkInterface.getNetworkInterfaces()?.toList()?.firstOrNull {it.isUp&&!it.isLoopback&&!it.isPointToPoint&&
                        it.name.matches(Regex("eth[0-9]+"))&&it.interfaceAddresses.any {a->a.networkPrefixLength==24.toShort()&&a.address.hostAddress=="192.168.8.2"}}
                        ?:throw IOException("No internal Ethernet 192.168.8.2/24; no Wi-Fi fallback")
                    iface=ni.name
                    update(debug.copy(phase=TyreReadPhase.CONNECT,interfaceName=ni.name))
                    val next=Socket();socket=next;active.set(next);guard()
                    next.bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(192.toByte(),168.toByte(),8,2)),0))
                    next.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(192.toByte(),168.toByte(),8,1)),8000),800)
                    next.soTimeout=800
                    update(debug.copy(tcpConnected=true))
                    val protocol=QnxTyreReadProtocol(next.getInputStream(),next.getOutputStream(),::guard) {
                        update(debug.copy(phase=it.phase,trace=it))
                    }
                    client=protocol // Keep partial greeting/response available even when begin() times out.
                    protocol.begin();guard()
                    report("TPMS_QNX file protocol identified on $iface; ONLY read ${QnxTyreTemperatureDecoder.PPS_PATH}; not field-calibrated")
                }
                val ni=NetworkInterface.getByName(iface)
                if(ni==null||!ni.isUp||ni.interfaceAddresses.none {it.networkPrefixLength==24.toShort()&&it.address.hostAddress=="192.168.8.2"})throw IOException("Internal interface changed")
                val payload=client!!.readTpms();guard()
                update(debug.copy(phase=TyreReadPhase.PARSE,trace=client!!.diagnosticSnapshot(),payload=payload.take(2048)))
                if(payload.isNotEmpty()) {
                    val frame=QnxTyreTemperatureDecoder.parse(payload)?:throw IOException("Unexpected a005 schema: ${payload.take(160).replace(Regex("[^ -~]")," ")}; no synthetic temperature")
                    if(last!=frame){report("TPMS_QNX_SAMPLE wheel=${frame.wheel} raw=${frame.raw} status=${frame.status} celsius=${frame.celsius}");last=frame}
                    update(debug.copy(packetCount=debug.packetCount+1,lastPacketAtMillis=SystemClock.elapsedRealtime(),
                        lastError="",failedPhase=null,errorAtMillis=null,
                        samples=debug.samples+(frame.wheel to TyreDebugSample(frame.raw,frame.status,frame.celsius,SystemClock.elapsedRealtime()))))
                    guard();sample(frame)
                }
                update(debug.copy(phase=TyreReadPhase.POLLING))
            }catch(e:Exception) {
                val trace=client?.diagnosticSnapshot()?:debug.trace
                val failedAt=SystemClock.elapsedRealtime()
                val error="${e.javaClass.simpleName}: ${e.message?.take(220)}"
                update(debug.copy(tcpConnected=false,trace=trace,lastError=error,failedPhase=debug.phase,
                    errorAtMillis=failedAt,retryAtMillis=failedAt+30_000))
                runCatching {socket?.close()};active.compareAndSet(socket,null);socket=null;client=null;last=null
                retryAt=SystemClock.elapsedRealtime()+30_000
                if(token==generation)report("TPMS_QNX_UNAVAILABLE stage=${debug.failedPhase} iface=$iface attempt=${debug.attempts} rx=${trace.receivedBytes} tx=${trace.sentBytes} command=${trace.command} tail=${trace.textTail} hex=${trace.hexTail}; $error; no data published; retry after 30s")
            }
        },0,700,TimeUnit.MILLISECONDS)
    }
    fun stop() {
        generation++;task?.cancel(false);task=null
        runCatching {active.getAndSet(null)?.close()}
    }
    fun close(){stop();worker.shutdownNow()}
}
