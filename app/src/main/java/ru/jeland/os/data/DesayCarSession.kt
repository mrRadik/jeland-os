package ru.jeland.os.data

import android.content.*
import android.os.*
import android.util.Log
import androidx.core.content.ContextCompat
import com.desaysv.ivi.vdb.IVDBus
import com.desaysv.ivi.vdb.IVDBusNotify
import com.desaysv.ivi.vdb.event.VDEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.jeland.os.core.*
import ru.jeland.os.core.automation.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Same normal-app Binder contract as Probe. Never replays persisted settings into the car. */
class DesayCarSession(context: Context, private val settings: SettingsStore) {
    private val context=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadScheduledExecutor()
    private val reducer=LiveVehicleReducer()
    private val stateLock=Any()
    @Volatile private var epoch=0
    @Volatile private var foreground=false
    @Volatile private var vehicleMode=!ru.jeland.os.BuildConfig.DEMO_FEATURES||settings.string("car.live","false")=="true"
    @Volatile private var writes=false
    @Volatile private var bus: IVDBus?=null
    private var binding: ServiceConnection?=null
    private var notify: IVDBusNotify?=null
    private var poll: ScheduledFuture<*>?=null
    private val busy=AtomicBoolean(false)
    private val projected=CommandProjection()
    private val commandSequence=java.util.concurrent.atomic.AtomicLong()
    private val climatePanelDismissal=ClimatePanelDismissal()
    private var climatePanelReceiver:BroadcastReceiver?=null
    @Volatile private var requestedShade: Shade?=null
    private val linkFlow=MutableStateFlow(VehicleLinkState(demo=!vehicleMode))
    val link=linkFlow.asStateFlow()
    private val climateFlow=MutableStateFlow(reducer.climate(now()))
    val climate=climateFlow.asStateFlow()
    private val seatsFlow=MutableStateFlow(reducer.seats(now()))
    val seats=seatsFlow.asStateFlow()
    private val vehicleFlow=MutableStateFlow(reducer.vehicle(now()))
    val vehicle=vehicleFlow.asStateFlow()
    private val journal=File(context.getExternalFilesDir(null)?:context.filesDir,"JelandOS_vehicle_${System.currentTimeMillis()}.log")
    private var qnxTyres:QnxTyreTemperatureReader?=null
    private val tyreDiagnosticsFlow=MutableStateFlow(TyreTemperatureDiagnostics())
    val tyreDiagnostics=tyreDiagnosticsFlow.asStateFlow()
    private val freshness=object: Runnable {
        override fun run() {if(foreground && vehicleMode){publish();main.postDelayed(this,1000)}}
    }
    init {report("SESSION_BEGIN app=${ru.jeland.os.BuildConfig.VERSION_NAME} SDK=${Build.VERSION.SDK_INT} mode=${if(vehicleMode)"VEHICLE"else"DEMO"}")}
    private fun now()=SystemClock.elapsedRealtime()
    fun recordIntegration(text:String){report("INTEGRATION ${text.take(2000)}")}
    @Synchronized private fun report(text: String) {
        Log.i("JelandOSVehicle",text)
        runCatching {journal.appendText("${System.currentTimeMillis()} $text\n")}
    }
    fun useVehicle(value: Boolean) {
        if(!ru.jeland.os.BuildConfig.DEMO_FEATURES&&!value)return
        stop(); vehicleMode=value; settings.put("car.live",value.toString())
        linkFlow.value=VehicleLinkState(demo=!value,detail=if(value)"ГУ: ожидаем подключение"else"DEMO · без подключения к авто")
        report("MODE_CHANGE ${if(value)"VEHICLE"else"DEMO"}; writes locked")
        if(foreground && value)connect()
    }
    fun resume() {foreground=true;report("APP_RESUME mode=${if(vehicleMode)"VEHICLE"else"DEMO"}"); if(vehicleMode && binding==null)connect()}
    fun pause() {foreground=false;report("APP_PAUSE — writes locked");stop()}
    fun saveDiagnostics():SavedDiagnostics {
        report("EXPORT_REQUEST — cached data only; no vehicle command")
        return DiagnosticsExporter.save(context,link.value,tyreDiagnostics.value).also {report("EXPORT_SAVED ${it.location} journals=${it.logCount}")}
    }
    @Synchronized fun clearDiagnostics():ClearedDiagnostics {
        val header="${System.currentTimeMillis()} SESSION_BEGIN app=${ru.jeland.os.BuildConfig.VERSION_NAME} mode=${if(vehicleMode)"VEHICLE"else"DEMO"} reason=USER_LOG_CLEAR\n"
        val result=DiagnosticsCleaner.clear(context,journal,header)
        report("LOGS_CLEARED journals=${result.journals} archives=${result.archives} activeReset=${result.activeReset} failed=${result.failed}; only own files")
        return result
    }
    fun allowWrites(value: Boolean) {
        writes=foreground && vehicleMode && bus!=null
        linkFlow.update {it.copy(writesEnabled=writes,detail=if(it.connected)"ГУ подключено · управление доступно"else it.detail)}
        report("WRITE_POLICY automatic while foreground+connected; enabled=$writes")
    }
    fun refresh() {val token=epoch;worker.execute {if(token==epoch)readAll(token)}}
    private fun publish() {
        val time=now()
        climateFlow.value=reducer.climate(time);seatsFlow.value=reducer.seats(time)
        vehicleFlow.value=reducer.vehicle(time,requestedShade)
        linkFlow.update {it.copy(raw=reducer.snapshot())}
    }
    private fun event(key: VdsKey, values: IntArray?=null, subscription: Boolean=false): VDEvent {
        val payload=Bundle()
        if(subscription)payload.putIntArray("CMD_ID_ARRAY",values)else payload.putInt("CMD_ID",key.cmd)
        if(values!=null && !subscription)payload.putIntArray("VALUE",values)
        return VDEvent(key.module,payload).also {it.createTimeMillis()}
    }
    private fun decode(event: VDEvent?): Pair<VdsKey,List<Int>?>? {
        if(event==null)return null
        val payload=event.payload?:return null
        val cmd=payload.get("CMD_ID") as? Int?:return null
        val values=if(payload.getBoolean("IS_NULL",false))null else (payload.get("VALUE") as? IntArray)?.takeIf {it.isNotEmpty()}?.toList()
        return VdsKey(event.id,cmd) to values
    }
    private fun read(key: VdsKey, token: Int) {
        if(token!=epoch)return
        val response=bus?.get(event(key))
        val decoded=decode(response)
        synchronized(stateLock){
            if(token!=epoch)return
            if(decoded!=null && decoded.first!=key){report("GET MISMATCH $key -> ${decoded.first}"); reducer.accept(key,null,now())}
            else reducer.accept(key,decoded?.second,now())
        }
        report("GET $key VALUE=${decoded?.second}")
    }
    private fun readAll(token: Int) {
        if(bus==null || token!=epoch)return
        for((module,commands) in VerifiedVehicleProtocol.subscriptions)for(cmd in commands){
            if(token!=epoch)return
            val key=VdsKey(module,cmd)
            try {read(key,token)}catch(error: Exception){synchronized(stateLock){if(token==epoch)reducer.accept(key,null,now())};report("GET ERROR $key $error")}
        }
        if(token==epoch)publish()
    }
    private fun connect() {
        if(!foreground || !vehicleMode || binding!=null)return
        val token=++epoch
        writes=false;requestedShade=null;synchronized(stateLock){reducer.clear()};publish()
        main.removeCallbacks(freshness);main.post(freshness)
        linkFlow.value=VehicleLinkState(demo=false,detail="Подключение CarInfoService…")
        val callback=object: IVDBusNotify.Stub(){
            override fun onVDBusNotify(value: VDEvent?) {
                if(token!=epoch)return
                try {val decoded=decode(value)?:return
                    worker.execute {synchronized(stateLock){if(token==epoch){reducer.accept(decoded.first,decoded.second,now());publish();report("EVENT ${decoded.first} VALUE=${decoded.second}")}}}
                }catch(error: Exception){report("EVENT ERROR $error")}
            }
        }
        val connection=object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if(token!=epoch || !foreground)return
                bus=IVDBus.Stub.asInterface(binder)
                registerClimatePanelReceiver()
                writes=foreground&&vehicleMode
                linkFlow.update {it.copy(connected=true,writesEnabled=writes,detail="ГУ подключено · управление доступно")}
                report("CONNECTED ${name.flattenToShortString()} SDK=${Build.VERSION.SDK_INT}")
                qnxTyres?.close()
                qnxTyres=QnxTyreTemperatureReader(::report,sample={frame->
                    synchronized(stateLock){if(token==epoch&&foreground&&vehicleMode){reducer.acceptTyreTemperature(frame,now());publish()}}
                },diagnostics={debug->if(token==epoch&&foreground&&vehicleMode)tyreDiagnosticsFlow.value=debug}).also {it.start()}
                worker.execute {
                    for((module,commands) in VerifiedVehicleProtocol.subscriptions){
                        if(token!=epoch)return@execute
                        try {bus?.subscribeCustomizedEvent(event(VdsKey(module,0),commands.toIntArray(),true),callback)}catch(error: Exception){report("SUBSCRIBE ERROR $module $error")}
                    }
                    readAll(token)
                }
                poll=worker.scheduleWithFixedDelay({if(token==epoch)readAll(token)},5,5,TimeUnit.SECONDS)
            }
            override fun onServiceDisconnected(name: ComponentName) {if(token==epoch)failed("Связь с ГУ потеряна")}
            override fun onBindingDied(name: ComponentName) {if(token==epoch)failed("Сервис перезапущен — подключитесь снова")}
            override fun onNullBinding(name: ComponentName) {if(token==epoch)failed("Сервис вернул пустой Binder")}
        }
        binding=connection;notify=callback
        try {
            val intent=Intent("action.desaysv.ivi.vds.carinfo.SERVICE").setComponent(ComponentName("com.desaysv.ivi.vds.carinfo","com.desaysv.ivi.vds.carinfo.service.CarInfoService"))
            if(!context.bindService(intent,connection,Context.BIND_AUTO_CREATE))failed("CarInfoService не найден. DEMO можно включить вручную.")
        }catch(error: Exception){failed("Подключение запрещено/недоступно: ${error.message}")}
        main.postDelayed({if(token==epoch && bus==null)failed("Время подключения к ГУ истекло")},10_000)
    }
    private fun failed(reason: String) {stop();linkFlow.update {it.copy(demo=!vehicleMode,detail=reason)};report(reason)}
    private fun stop() {
        climatePanelDismissal.cancel()
        climatePanelReceiver?.let {runCatching {context.unregisterReceiver(it)}};climatePanelReceiver=null
        qnxTyres?.close();qnxTyres=null
        tyreDiagnosticsFlow.update {it.copy(active=false,phase=TyreReadPhase.STOPPED,tcpConnected=false,retryAtMillis=null)}
        synchronized(stateLock){epoch++;reducer.clear()};projected.clear();commandSequence.incrementAndGet();writes=false;poll?.cancel(false);poll=null
        main.removeCallbacks(freshness)
        val previous=bus;val callback=notify;val connection=binding
        bus=null;notify=null;binding=null;requestedShade=null
        // No cleanup SET, no switching HVAC/seat/shade off on lifecycle transitions.
        if(previous!=null && callback!=null)worker.execute {
            for((module,commands) in VerifiedVehicleProtocol.subscriptions)runCatching {
                previous.unsubscribeCustomizedEvent(event(VdsKey(module,0),commands.toIntArray(),true),callback)
            }
        }
        if(connection!=null)runCatching {context.unbindService(connection)}
        publish()
        linkFlow.update {it.copy(connected=false,writesEnabled=false,busy=false,raw=emptyMap(),userMessage=null,detail=if(vehicleMode)"ГУ отключено · нет свежих данных"else"DEMO · без подключения к авто")}
    }
    private fun requireScalar(module: Int,cmd: Int,range: IntRange): Int = (projected.value(VdsKey(module,cmd),now())?:reducer.scalar(module,cmd,now()))?.takeIf {it in range}
        ?:error("Нет свежего состояния 0x${module.toString(16)}/$cmd. Обновите показания.")
    private fun registerClimatePanelReceiver() {
        if(climatePanelReceiver!=null)return
        val receiver=object:BroadcastReceiver() {
            override fun onReceive(context:Context?,intent:Intent?) {
                if(intent?.action=="com.desaysv.svhvac.OPEN_PANEL")onOemClimatePanelOpened()
            }
        }
        try {
            // Exported only to receive the OEM app's broadcast; inert outside our armed window.
            ContextCompat.registerReceiver(context,receiver,IntentFilter("com.desaysv.svhvac.OPEN_PANEL"),ContextCompat.RECEIVER_EXPORTED)
            climatePanelReceiver=receiver
        }catch(e:Exception){report("HVAC_UI_RECEIVER_UNAVAILABLE ${e.javaClass.simpleName}")}
    }
    internal fun onOemClimatePanelOpened() {
        val ticket=climatePanelDismissal.opened(now())?:return
        val token=epoch
        worker.execute {
            if(token!=epoch||!foreground||!vehicleMode||!writes||!climatePanelDismissal.canDismiss(ticket,now()))return@execute
            val current=bus?:return@execute
            try {
                // HvacService CLOSE_HVAC_BT uses /9=2 for the HVAC display bit only.
                // Do NOT call that broad service action: it also changes /51 and /64.
                current.set(event(VdsKey(VerifiedVehicleProtocol.HVAC,9),intArrayOf(2)))
                report("HVAC_UI_DISMISS_SENT after OEM_OPEN; /9=2 once; no actuator repeat")
            }catch(e:Exception){report("HVAC_UI_DISMISS_ERROR ${e.javaClass.simpleName}; no retry")}
        }
    }
    // Each action sends its selected actuator request exactly once.
    fun command(label: String, build: ()->List<VdsWrite>, shade: Shade?=null) {
        if(!writes || !foreground || !vehicleMode || bus==null){linkFlow.update {it.copy(lastCommand="Нет активного подключения ГУ",userMessage="Подключите ГУ для управления.")};return}
        val token=epoch
        val sequence=commandSequence.incrementAndGet()
        val previousShade=requestedShade
        linkFlow.update {it.copy(busy=true,userMessage=null)}
        if(shade!=null){requestedShade=shade;publish()}
        worker.execute {
            var panelTicket:Long?=null
            try {
                val commands=build();require(commands.isNotEmpty() && commands.all(VerifiedVehicleProtocol::allowed))
                if(commands.any(VerifiedVehicleProtocol::requiresClimatePower))check(requireScalar(VerifiedVehicleProtocol.HVAC,18,0..1)==1){"Климат выключен"}
                val climateUi=commands.any {it.key.module==VerifiedVehicleProtocol.HVAC || (it.key.module==VerifiedVehicleProtocol.CAR && it.key.cmd in listOf(93,94,95,96,139,141,160))}
                val before=commands.associateWith {reducer.values(it.feedback,now())}
                val feedbackKeys=(commands.map {it.feedback}+if(commands.any {it.feedback.module==VerifiedVehicleProtocol.HVAC&&it.feedback.cmd in listOf(3,10,13)})
                    listOf(3,10,13).map {VdsKey(VerifiedVehicleProtocol.HVAC,it)}else emptyList()).distinct()
                panelTicket=climatePanelDismissal.arm(commands,now())
                for(write in commands){
                    check(token==epoch && foreground && writes && vehicleMode){"Команда отменена до отправки"}
                    val current=bus?:error("Нет соединения")
                    report("SET_REQUEST $label ${write.key} VALUE=[${write.value}] epoch=$token")
                    current.set(event(write.key,intArrayOf(write.value))) // exactly once; never retry
                    if(token!=epoch)return@execute
                    write.expected?.let {projected.record(write.feedback,it,now())}
                    report("SET_RETURNED $label — not actuator confirmation")
                }
                if(token==epoch){requestedShade=shade?:requestedShade;publish();linkFlow.update {it.copy(lastCommand="$label: отправлено, ожидаем обратное состояние")}}
                if(commands==listOf(VerifiedVehicleProtocol.openCameras())) {
                    // Native AVM owns its window and safety restrictions. Never retry/toggle,
                    // poll an actuator or immediately close the native UI after this request.
                    report("AVM_OPEN_REQUEST_RETURNED — native window visibility is not confirmed")
                    if(token==epoch)linkFlow.update {it.copy(lastCommand="Камеры: запрос открытия штатного интерфейса отправлен")}
                    return@execute
                }
                if(shade!=null) {
                    // Read-only telemetry in the background. A second shade button need not wait for readback.
                    for(delay in listOf(400L,1200L,2400L))worker.schedule({
                        if(token==epoch)runCatching {
                            commands.map {it.feedback}.distinct().forEach {read(it,token)};publish()
                            report("SHADE_READBACK requested=$shade actual=${commands.map {reducer.values(it.feedback,now())}}; visual follows local command")
                        }.onFailure {report("SHADE_READBACK_ERROR $it")}
                    },delay,TimeUnit.MILLISECONDS)
                    return@execute
                }
                for((index,wait) in listOf(400L,1200L,2400L).withIndex())worker.schedule({
                    if(token!=epoch||sequence!=commandSequence.get())return@schedule
                    try {
                    feedbackKeys.forEach {read(it,token)};publish()
                    if(climateUi)runCatching {read(VdsKey(VerifiedVehicleProtocol.HVAC,9),token)} // read-only diagnostics
                if(index==2&&token==epoch){
                    val summary=commands.joinToString {write->
                        val actual=reducer.values(write.feedback,now())
                        when {actual==null->"нет обратных данных"
                            write.expected==null->if(actual!=before[write])"feedback изменился; положение не расшифровано"else"конечное положение не подтверждено"
                            actual==listOf(write.expected)->if(actual==before[write])"GET совпадает, значение уже было установлено"else"GET подтвердил новое значение"
                            else->"не подтверждено, GET=$actual"}
                    }
                    linkFlow.update {it.copy(lastCommand="$label: $summary")};report("READBACK $label $summary")
                }
                    }catch(error:Exception){report("READBACK_ERROR $label $error")}
                },wait,TimeUnit.MILLISECONDS)
            }catch(error: Exception){
                panelTicket?.let {climatePanelDismissal.cancel(it)}
                report("COMMAND_ERROR $label $error")
                if(token==epoch){
                    if(shade!=null){requestedShade=previousShade;publish()}
                    linkFlow.update {it.copy(lastCommand="$label: ${error.message}. Повтор SET не выполнялся.",userMessage="Ошибка команды. Проверьте состояние автомобиля.")}
                }
            }
            finally {busy.set(false);if(token==epoch)linkFlow.update {it.copy(busy=false)}}
        }
    }
    /** Acknowledged automation path on the SAME worker/epoch as ordinary controls; never retries SET. */
    suspend fun executeAutomation(actions:List<AutomationAction>):AutomationExecution = suspendCancellableCoroutine {pending->
        val token=epoch
        val issued=mutableListOf<AutomationWrite>()
        fun complete(result:AutomationExecution) {
            report("AUTOMATION_RESULT ${result.status} ${result.message} issued=${result.commands.joinToString {it.summary()}}")
            if(pending.isActive)pending.resume(result)
        }
        fun connected()=token==epoch&&foreground&&writes&&vehicleMode&&bus!=null&&pending.isActive
        pending.invokeOnCancellation {report("AUTOMATION_CANCELLED epoch=$token; no retry; some requests may already have been sent")}
        worker.execute {
            if(!connected()){complete(AutomationExecution(AutomationRunStatus.NO_CONNECTION,"Нет активного подключения ГУ. Команды не отправлены."));return@execute}
            try {
                // Read every needed device BEFORE the first SET; no projected/DEMO values are used.
                LiveAutomationPlanner.keys(actions).forEach {key->check(connected()){ "Подключение изменилось" };read(key,token)}
                publish()
                val plan=LiveAutomationPlanner.plan(actions,linkFlow.value.copy(raw=reducer.snapshot()),now())
                plan.failure?.let {complete(it);return@execute}
                if(plan.writes.isEmpty()){complete(AutomationExecution(AutomationRunStatus.NO_CHANGES,"По свежим данным нужные значения уже установлены."));return@execute}
                check(plan.writes.all {VerifiedVehicleProtocol.allowed(it.write)})
                climatePanelDismissal.arm(plan.writes.map {it.write},now())
                for(item in plan.writes) {
                    check(connected()){ "Выполнение отменено: связь или активность изменилась" }
                    if(item.command==AutomationCommand.SteeringHeatOn) {
                        // /160 is a toggle: never toggle an already-on heater off, and never retry.
                        read(item.write.feedback,token)
                        val actual=reducer.scalar(VerifiedVehicleProtocol.CAR,160,now())
                        if(actual==1)continue
                        check(actual==0){"Состояние подогрева руля стало неизвестно"}
                    }
                    check(connected()){ "Подключение изменилось до отправки" }
                    val current=bus?:error("Нет соединения")
                    // Record BEFORE Binder: an exception does not prove the actuator received nothing.
                    issued+=item
                    report("AUTOMATION_SET_REQUEST ${item.command.summary()} ${item.write.key} VALUE=[${item.write.value}] epoch=$token")
                    current.set(event(item.write.key,intArrayOf(item.write.value)))
                    report("AUTOMATION_SET_RETURNED — request only, not actuator confirmation")
                    (item.command as? AutomationCommand.Shade)?.let {
                        requestedShade=if(it.position==SunshadeAction.OPEN_FULL)Shade.OPEN else Shade.HALF
                    }
                }
                if(issued.isEmpty()){complete(AutomationExecution(AutomationRunStatus.NO_CHANGES,"Устройства уже в нужном состоянии."));return@execute}
                publish()
                // SETs above have no imposed delay. A single later GET is confirmation only, never a retry.
                worker.schedule({
                    if(!pending.isActive)return@schedule
                    try {
                        check(connected()){ "Связь потеряна после отправки" }
                        issued.map {it.write.feedback}.distinct().forEach {read(it,token)}
                        publish()
                        val missing=issued.filter {item->item.write.expected?.let {reducer.values(item.write.feedback,now())!=listOf(it)}==true}
                        val unknown=issued.any {it.write.expected==null}
                        val status=when {missing.isNotEmpty()->AutomationRunStatus.UNCONFIRMED;unknown->AutomationRunStatus.SENT;else->AutomationRunStatus.CONFIRMED}
                        val message=when(status) {
                            AutomationRunStatus.CONFIRMED->"Обратные показания ГУ совпали с запрошенными значениями."
                            AutomationRunStatus.SENT->"Запросы отправлены. Точное конечное положение шторки не подтверждается этим каналом."
                            else->"Запросы отправлены, но не все обратные показания совпали. Повторных команд нет."
                        }
                        complete(AutomationExecution(status,message,issued.map {it.command}))
                    }catch(e:Exception){complete(AutomationExecution(AutomationRunStatus.UNCONFIRMED,"После отправки нет достоверного подтверждения: ${e.message}. Повторов нет.",issued.map {it.command}))}
                },1200,TimeUnit.MILLISECONDS)
            }catch(e:Exception) {
                complete(AutomationExecution(if(issued.isEmpty())AutomationRunStatus.ERROR else AutomationRunStatus.PARTIAL,
                    "${e.message}. ${if(issued.isEmpty())"Команды не отправлены"else"Часть запросов могла выполниться"}. Автоматического повтора нет.",issued.map {it.command}))
            }
        }
    }
    fun openCameras()=command("Штатные камеры 360°",{listOf(VerifiedVehicleProtocol.openCameras())})
    fun binary(cmd: Int)=command("Климат / $cmd",{val current=requireScalar(VerifiedVehicleProtocol.HVAC,cmd,0..1);listOf(VerifiedVehicleProtocol.binary(cmd,if(cmd==8)current==1 else current==0))})
    fun toggle(cmd: Int)=command("Переключение / $cmd",{listOf(VerifiedVehicleProtocol.toggle(cmd,requireScalar(if(cmd==160)VerifiedVehicleProtocol.CAR else VerifiedVehicleProtocol.HVAC,cmd,0..1)))})
    fun temperature(driver: Boolean,delta: Double)=command("Температура",{val current=requireScalar(VerifiedVehicleProtocol.HVAC,if(driver)10 else 13,0..16)
        listOf(VerifiedVehicleProtocol.temperature(driver,(current+if(delta>0)1 else -1).coerceIn(0,16)))})
    fun setTemperature(driver:Boolean,celsius:Double)=command("Температура: выбранное значение",{
        requireScalar(VerifiedVehicleProtocol.HVAC,if(driver)10 else 13,0..16)
        listOf(VerifiedVehicleProtocol.absoluteTemperature(driver,celsius))
    })
    fun sync()=command("SYNC",{listOf(VerifiedVehicleProtocol.sync(requireScalar(VerifiedVehicleProtocol.HVAC,3,0..1)==1))})
    fun seat(side: SeatSide,mode: SeatMode,level: Int) {
        val cmd=if(mode==SeatMode.HEAT){if(side==SeatSide.DRIVER)95 else 96}else if(side==SeatSide.DRIVER)93 else 94
        command("${side.title} / ${mode.name} $level",{requireScalar(VerifiedVehicleProtocol.CAR,cmd,0..3);listOf(VerifiedVehicleProtocol.seat(cmd,level))})
    }
    fun rearSeat(side:RearSeatSide,level:Int)=command("${side.title}: подогрев $level",{
        requireScalar(VerifiedVehicleProtocol.CAR,side.commandId,0..3)
        listOf(VerifiedVehicleProtocol.rearSeat(side,level))
    })
    fun rear(value: RearHeat)=command("Задний подогрев ${value.title}",{
        val l=requireScalar(VerifiedVehicleProtocol.CAR,139,0..3);val r=requireScalar(VerifiedVehicleProtocol.CAR,141,0..3)
        listOf(VerifiedVehicleProtocol.seat(139,if(value in listOf(RearHeat.LEFT,RearHeat.BOTH))l.coerceAtLeast(1)else 0),
            VerifiedVehicleProtocol.seat(141,if(value in listOf(RearHeat.RIGHT,RearHeat.BOTH))r.coerceAtLeast(1)else 0))
    })
    fun shade(value: Shade)=command("Шторка ${value.title}",{listOf(VerifiedVehicleProtocol.shade(value))},value)
}
