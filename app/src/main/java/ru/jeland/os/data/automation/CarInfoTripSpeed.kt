package ru.jeland.os.data.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import com.desaysv.ivi.vdb.IVDBus
import com.desaysv.ivi.vdb.IVDBusNotify
import com.desaysv.ivi.vdb.event.VDEvent
import ru.jeland.os.core.VehicleSpeedSample
import ru.jeland.os.core.VerifiedVehicleProtocol
import java.util.concurrent.Executor

/** Separate read-only subscription so manual trip recording also works in the background. */
internal class CarInfoTripSpeed(
    private val app: Context,
    private val main: Handler,
    private val worker: Executor,
    private val report: (String) -> Unit,
    private val receive: (VehicleSpeedSample, Long) -> Unit
) {
    private var connection: ServiceConnection? = null
    private var closeSubscription: (() -> Unit)? = null
    private var generation = 0

    fun start() {
        if (connection != null) return
        val client = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (connection !== this) return
                closeSubscription?.invoke()
                val token = ++generation
                val bus = IVDBus.Stub.asInterface(binder)
                val request = VDEvent(VerifiedVehicleProtocol.INFO, Bundle().apply {
                    putIntArray("CMD_ID_ARRAY", intArrayOf(119))
                }).apply { createTimeMillis() }
                val callback = object : IVDBusNotify.Stub() {
                    override fun onVDBusNotify(value: VDEvent?) {
                        if (value?.id != VerifiedVehicleProtocol.INFO) return
                        val elapsed = SystemClock.elapsedRealtime()
                        val wall = System.currentTimeMillis()
                        val sample = runCatching {
                            val payload = value.payload ?: return@runCatching null
                            if (payload.getInt("CMD_ID", -1) != 119 || payload.getBoolean("IS_NULL", false)) return@runCatching null
                            val raw = payload.getIntArray("VALUE") ?: return@runCatching null
                            VehicleSpeedSample.fromCarInfo(raw, elapsed)
                        }.getOrNull() ?: return
                        main.post { if (generation == token) receive(sample, wall) }
                    }
                }
                val unsubscribe: () -> Unit = {
                    worker.execute { runCatching { bus.unsubscribeCustomizedEvent(request, callback) } }
                }
                closeSubscription = unsubscribe
                worker.execute {
                    runCatching { bus.subscribeCustomizedEvent(request, callback) }
                        .onSuccess { report("TRIP_CARINFO_SPEED subscribed=119") }
                        .onFailure { report("TRIP_CARINFO_SPEED subscribe failed ${it.javaClass.simpleName}") }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (connection === this) {
                    generation++
                    closeSubscription?.invoke()
                    closeSubscription = null
                }
            }

            override fun onBindingDied(name: ComponentName) {
                if (connection === this) stop()
            }

            override fun onNullBinding(name: ComponentName) {
                if (connection === this) stop()
            }
        }
        val intent = Intent("action.desaysv.ivi.vds.carinfo.SERVICE").setComponent(ComponentName(
            "com.desaysv.ivi.vds.carinfo", "com.desaysv.ivi.vds.carinfo.service.CarInfoService"
        ))
        if (runCatching { app.bindService(intent, client, Context.BIND_AUTO_CREATE) }.getOrDefault(false)) {
            connection = client
        } else report("TRIP_CARINFO_SPEED bind failed")
    }

    fun stop() {
        val client = connection ?: return
        connection = null
        generation++
        closeSubscription?.invoke()
        closeSubscription = null
        runCatching { app.unbindService(client) }
    }
}
