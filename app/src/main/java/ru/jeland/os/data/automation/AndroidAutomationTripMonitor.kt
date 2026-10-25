package ru.jeland.os.data.automation

import android.content.*
import android.os.*
import android.provider.Settings
import com.desaysv.ivi.vdb.*
import com.desaysv.ivi.vdb.event.VDEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.jeland.os.core.*
import ru.jeland.os.core.automation.*
import java.util.concurrent.Executors

/** Reads motion/power only; a newly durably claimed trip is delivered once to the live coordinator. */
class AndroidAutomationTripMonitor(context:Context,private val store:SettingsStore,
    private val link:StateFlow<VehicleLinkState>,private val report:(String)->Unit={},
    private val onTripStarted:(AutomationSession)->Unit={},private val onInvalidated:()->Unit={}) {
    private val app=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor {r->Thread(r,"Automation-trip-read").apply {isDaemon=true}}
    private val gate=AutomationTripGate(store)
    private val engineGate=AutomationEngineStartGate(store)
    @Volatile private var foreground=false
    private var binding=false
    private var carState:IVDBus?=null
    private var speedBinder:IBinder?=null
    private var connecting=false
    private var lastLiveSpeedEvent=Long.MIN_VALUE
    private var lastPowerMode:Int?=null
    @Volatile private var lastSpeedLogAt=Long.MIN_VALUE/2
    @Volatile private var enabled=store.string(LIVE_ENABLED,"true")=="true"
    private val mutable=MutableStateFlow(AutomationTripState(enabled,gate.phase,gate.tripId))
    val state=mutable.asStateFlow()
    init {
        runCatching {gate.boot(runCatching {Settings.Global.getInt(app.contentResolver,Settings.Global.BOOT_COUNT).toString()}.getOrNull())}
            .onFailure {publish("Ошибка сохранения состояния поездки; автоматических действий нет.")}
        CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).launch {
            link.map {it.demo to it.connected}.distinctUntilChanged().collect {checkConnection()}
        }
        CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).launch {
            link.collect {state->
                if(!foreground||state.demo||!state.connected){engineGate.interrupted();return@collect}
                observeEngine(state)
            }
        }
    }
    private fun observeEngine(state:VehicleLinkState) {
        val now=SystemClock.elapsedRealtime()
        val sample=state.raw[VdsKey(VerifiedVehicleProtocol.INFO,74)]?:return
        val power=state.raw[VdsKey(VerifiedVehicleProtocol.INFO,13)]?.takeIf {now-it.receivedMillis in 0..15_000}?.values?.singleOrNull()
        val wasRunning=engineGate.cycleId!=null
        runCatching {engineGate.sample(sample.values?.singleOrNull(),power,sample.receivedMillis,now,enabled)}
            .onSuccess {id->
                if(wasRunning&&engineGate.cycleId==null){
                    gate.vehicleSlept() // Confirmed engine + ignition OFF also ends the movement cycle, even before HU sleep.
                    onInvalidated();publish("Двигатель и зажигание выключены. Ожидаем следующий запуск.")
                }
                if(id!=null) {
                    publish("Работа двигателя подтверждена. Выполняем сценарии запуска двигателя один раз.")
                    onTripStarted(AutomationSession(java.util.UUID.fromString(id),System.currentTimeMillis(),LaunchEvent.ENGINE_START))
                }
            }.onFailure {publish("Не удалось сохранить цикл двигателя. Автоматические команды не запускаются.")}
    }
    private fun publish(detail:String) {
        val next=AutomationTripState(enabled,gate.phase,gate.tripId,detail)
        if(mutable.value==next)return
        mutable.value=next
        report("AUTOMATION_TRIP phase=${gate.phase} enabled=$enabled trip=${gate.tripId} $detail")
    }
    fun setEnabled(value:Boolean) {
        enabled=value;store.put(LIVE_ENABLED,value.toString());gate.interrupted()
        if(!value){onInvalidated();publish("Автоматический запуск выключен. Ручной запуск доступен.")}
        else publish("Автоматический запуск включён. Ожидаем событие, выбранное в сценарии.")
        checkConnection()
    }
    fun resume(){foreground=true;checkConnection()}
    fun pause(){foreground=false;onInvalidated();gate.interrupted();engineGate.interrupted();if(enabled)publish("Приложение в фоне. Подтверждённые циклы сохранены, отправка остановлена.")}
    private val speedCallback=object:IVDBusCallback.Stub() {
        override fun onVDBusCallback(value:VDEvent?){speedEvent(value,false)}
        override fun onVDBusNotify(value:VDEvent?){speedEvent(value,false)}
    }
    private fun speedEvent(event:VDEvent?,initial:Boolean) {
        if(!foreground||link.value.demo||!link.value.connected)return
        val received=SystemClock.elapsedRealtime()
        val sourceTime=event?.timeMillis?:0L
        val speed=runCatching {
            if(event?.id!=519)return@runCatching null
            val bundle=event.payload?:return@runCatching null
            if(bundle.getBoolean("IS_NULL",false))return@runCatching null
            val raw=bundle.getDoubleArray("DoubleVector")?.singleOrNull()?:return@runCatching null
            val eventTime=event.timeMillis
            if(received-lastSpeedLogAt>=1000){lastSpeedLogAt=received;report("AUTOMATION_SPEED eventTime=$eventTime received=$received initial=$initial value=$raw")}
            if(eventTime<=0||System.currentTimeMillis()-eventTime !in 0..3000)return@runCatching null
            // Initial/cached GET can establish stop, never prove motion.
            raw
        }.getOrNull()
        main.post {
            if(!foreground||link.value.demo||!link.value.connected)return@post
            if(!initial&&speed!=null) {
                if(sourceTime<=lastLiveSpeedEvent)return@post
                lastLiveSpeedEvent=sourceTime
            }
            if(!enabled)return@post
            // A cached/polled moving value cannot prove a new trip, nor erase live confirmation.
            if(initial&&speed!=null&&kotlin.math.abs(speed)>.2)return@post
            if(speed==null){gate.interrupted();publish("Нет корректного свежего значения скорости; действий нет.");return@post}
            val previous=gate.phase
            val result=runCatching {gate.speed(speed,received,SystemClock.elapsedRealtime())}
            if(result.isFailure){gate.interrupted();publish("Ошибка сохранения начала поездки. Сценарии не запускаются.");return@post}
            val id=result.getOrNull()
            if(id!=null){
                publish("Начало поездки подтверждено. Выполняем подходящие сценарии один раз.")
                onTripStarted(AutomationSession(java.util.UUID.fromString(id),System.currentTimeMillis()))
            }
            else if(gate.phase!=previous)publish(when(gate.phase) {
                TripStartPhase.READY->"Остановка зафиксирована. Ждём начала движения."
                TripStartPhase.CONFIRMING->"Подтверждаем устойчивое движение: от 5,4 км/ч, не менее 2,5 с."
                TripStartPhase.WAIT_STOP->"Ждём достоверной остановки; открытие приложения на ходу не является стартом."
                TripStartPhase.STARTED->"Поездка уже подтверждена. Остановка и переподключение её не повторяют."
            })
        }
    }
    private val powerCallback=object:IVDBusCallback.Stub() {
        override fun onVDBusCallback(value:VDEvent?){powerEvent(value)}
        override fun onVDBusNotify(value:VDEvent?){powerEvent(value)}
    }
    private fun powerEvent(event:VDEvent?) {
        val mode=runCatching {if(event?.id==196609)event.payload?.getInt("status",-1)else null}.getOrNull()
        main.post {
            val previous=lastPowerMode
            if(mode!=null&&mode>=0)lastPowerMode=mode
            if(mode in listOf(26,40)&&previous !in listOf(26,40)) {
                onInvalidated()
                runCatching {gate.vehicleSlept();engineGate.vehicleSlept()}.onSuccess {publish("Штатная система подтвердила сон ГУ. Ждём следующего запуска.")}
                    .onFailure {publish("Завершение поездки не сохранено; повторный запуск не разрешаем.")}
            }
        }
    }
    private val powerConnection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) {
            carState=IVDBus.Stub.asInterface(binder)
            val current=carState
            worker.execute {runCatching {
                current?.subscribe(intArrayOf(196609),android.os.Process.myPid(),app.packageName,powerCallback)
                powerEvent(current?.get(VDEvent(196609,Bundle())))
            }.onFailure {main.post {if(enabled)publish("Состояние сна ГУ недоступно. Завершение поездки автоматически не угадываем.")}}}
        }
        override fun onServiceDisconnected(name:ComponentName){carState=null}
    }
    private fun checkConnection() {
        if(!foreground||link.value.demo||!link.value.connected){onInvalidated();gate.interrupted();publish("Нужен активный режим ГУ и связь с автомобилем. Открытие приложения ничего не запускает.");return}
        if(!binding)runCatching {
            binding=app.bindService(Intent("action.desaysv.ivi.vds.carstate.SERVICE").setComponent(ComponentName(
                "com.desaysv.ivi.vds.carstate","com.desaysv.ivi.vds.carstate.service.CarStateService")),powerConnection,Context.BIND_AUTO_CREATE)
        }.onFailure {publish("Штатное состояние питания недоступно; поездка не сбрасывается по догадке.")}
        if(connecting)return
        connecting=true
        worker.execute {
            val result=runCatching {
                val binder=Class.forName("android.os.ServiceManager").getMethod("checkService",String::class.java)
                    .invoke(null,"com.desaysv.ivi.vds.vehicle.service.VehicleService") as? IBinder
                    ?:error("VehicleService unavailable")
                check(binder.interfaceDescriptor=="com.desaysv.ivi.vdb.IVDBus")
                val bus=IVDBus.Stub.asInterface(binder)
                if(speedBinder!==binder){bus.subscribe(intArrayOf(519),android.os.Process.myPid(),app.packageName,speedCallback);speedBinder=binder}
                speedEvent(bus.get(VDEvent(519,Bundle())),true)
            }
            main.post {connecting=false
                if(result.isFailure){gate.interrupted();report("AUTOMATION_TRIP_SOURCE_UNAVAILABLE ${result.exceptionOrNull()?.javaClass?.simpleName}");publish("Штатная скорость недоступна. Начало поездки не подтверждено.")}
                else publish(if(gate.tripId!=null)"Эта поездка уже отмечена; повторного старта нет."else"Подключён штатный канал скорости. Ждём остановки и устойчивого движения.")
            }
        }
    }
    companion object {const val LIVE_ENABLED="automation.live.enabled"}
}
