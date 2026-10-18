package ru.jeland.os.data

import android.content.*
import android.os.*
import android.util.Log
import com.desaysv.ivi.vdb.IVDBus
import com.desaysv.ivi.vdb.IVDBusNotify
import com.desaysv.ivi.vdb.event.VDEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.jeland.os.core.*
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
    @Volatile private var vehicleMode=settings.string("car.live","false")=="true"
    @Volatile private var writes=false
    @Volatile private var bus: IVDBus?=null
    private var binding: ServiceConnection?=null
    private var notify: IVDBusNotify?=null
    private var poll: ScheduledFuture<*>?=null
    private val busy=AtomicBoolean(false)
    private val panelCloseWindow=HvacPanelCloseWindow()
    private val panelCallbacks=Any()
    private var panelReceiver:BroadcastReceiver?=null
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
        stop(); vehicleMode=value; settings.put("car.live",value.toString())
        linkFlow.value=VehicleLinkState(demo=!value,detail=if(value)"ГУ: ожидаем подключение"else"DEMO · без подключения к авто")
        report("MODE_CHANGE ${if(value)"VEHICLE"else"DEMO"}; writes locked")
        if(foreground && value)connect()
    }
    fun resume() {foreground=true;report("APP_RESUME mode=${if(vehicleMode)"VEHICLE"else"DEMO"}"); if(vehicleMode && binding==null)connect()}
    fun pause() {foreground=false;report("APP_PAUSE — writes locked");stop()}
    fun saveDiagnostics():SavedDiagnostics {
        report("EXPORT_REQUEST — cached data only; no vehicle command")
        return DiagnosticsExporter.save(context,link.value).also {report("EXPORT_SAVED ${it.location} journals=${it.logCount}")}
    }
    fun allowWrites(value: Boolean) {
        writes=value && foreground && vehicleMode && bus!=null
        linkFlow.update {it.copy(writesEnabled=writes,detail=if(it.connected){if(writes)"ГУ подключено · управление разрешено"else"ГУ подключено · только чтение"}else it.detail,lastCommand=if(writes)"Запись разрешена вручную до ухода с экрана"else"Запись заблокирована")}
        report(if(writes)"WRITE_UNLOCK manual parked assertion"else"WRITE_LOCK")
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
                linkFlow.update {it.copy(connected=true,detail="ГУ подключено · только чтение")}
                report("CONNECTED ${name.flattenToShortString()} SDK=${Build.VERSION.SDK_INT}")
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
        main.post {cancelPanelCollapse()}
        synchronized(stateLock){epoch++;reducer.clear()};writes=false;poll?.cancel(false);poll=null
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
    private fun requireScalar(module: Int,cmd: Int,range: IntRange): Int = reducer.scalar(module,cmd,now())?.takeIf {it in range}
        ?:error("Нет свежего состояния 0x${module.toString(16)}/$cmd. Обновите показания.")
    // Runs only on main, during a bounded window after an explicit command from this app.
    private fun cancelPanelCollapse() {
        panelCloseWindow.cancel();main.removeCallbacksAndMessages(panelCallbacks)
        panelReceiver?.let {runCatching {context.unregisterReceiver(it)}};panelReceiver=null
    }
    private fun closeOemClimatePanel(token:Int,reason:String) {
        if(token!=epoch || !foreground || !vehicleMode || !writes)return
        if(!panelCloseWindow.claim(now()))return
        try {OemHvacPanel.close(context);report("HVAC_UI_CLOSE_REQUEST reason=$reason — UI only; visual result not confirmed")}
        catch(error:Exception){report("HVAC_UI_CLOSE_UNAVAILABLE ${error.javaClass.simpleName}: ${error.message}")}
    }
    private fun collapsePanelAfterCommand(token:Int) {
        main.post {
            if(token!=epoch || !foreground || !vehicleMode || !writes)return@post
            cancelPanelCollapse();panelCloseWindow.arm(now())
            fun schedule(delay:Long,reason:String) {
                main.postDelayed({closeOemClimatePanel(token,reason)},panelCallbacks,delay)
            }
            val receiver=object:BroadcastReceiver(){
                override fun onReceive(context:Context,intent:Intent) {
                    if(intent.action!=OemHvacPanel.OPEN || !panelCloseWindow.active(now()))return
                    report("HVAC_UI_OPEN_OBSERVED during own command window")
                    // OEM ignores close while its 300 ms opening animation is active.
                    schedule(380,"OEM_OPEN+380ms")
                }
            }
            try {
                val filter=IntentFilter(OemHvacPanel.OPEN)
                if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED)
                else @Suppress("DEPRECATION") context.registerReceiver(receiver,filter)
                panelReceiver=receiver
            }catch(error:Exception){report("HVAC_UI_WATCH_UNAVAILABLE ${error.message}")}
            // Fallbacks also cover OPEN emitted before receiver registration. Never repeat a VDS SET.
            for(delay in listOf(450L,1000L,1700L,2500L))schedule(delay,"SET+${delay}ms")
            main.postDelayed({cancelPanelCollapse()},panelCallbacks,3250)
        }
    }
    fun command(label: String, build: ()->List<VdsWrite>, shade: Shade?=null) {
        if(!writes || !foreground || !vehicleMode || bus==null){linkFlow.update {it.copy(lastCommand="Запись заблокирована: подключите ГУ и разрешите команды на стоянке",userMessage="Сначала подключите ГУ и разрешите управление на стоянке.")};return}
        if(!busy.compareAndSet(false,true)){linkFlow.update {it.copy(lastCommand="Дождитесь обратного чтения предыдущей команды",userMessage="Подождите завершения команды.")};return}
        val token=epoch
        val previousShade=requestedShade
        linkFlow.update {it.copy(busy=true,userMessage=null)}
        if(shade!=null){requestedShade=shade;publish()}
        worker.execute {
            try {
                val commands=build();require(commands.isNotEmpty() && commands.all(VerifiedVehicleProtocol::allowed))
                if(commands.any(VerifiedVehicleProtocol::requiresClimatePower))check(requireScalar(VerifiedVehicleProtocol.HVAC,18,0..1)==1){"Климат выключен"}
                val climateUi=commands.any {it.key.module==VerifiedVehicleProtocol.HVAC || (it.key.module==VerifiedVehicleProtocol.CAR && it.key.cmd in listOf(93,94,95,96,139,141,160))}
                val before=commands.associateWith {reducer.values(it.feedback,now())}
                for(write in commands){
                    check(token==epoch && foreground && writes && vehicleMode){"Команда отменена до отправки"}
                    val current=bus?:error("Нет соединения")
                    report("SET_REQUEST $label ${write.key} VALUE=[${write.value}] epoch=$token")
                    current.set(event(write.key,intArrayOf(write.value))) // exactly once; never retry
                    report("SET_RETURNED $label — not actuator confirmation")
                }
                if(token==epoch){requestedShade=shade?:requestedShade;publish();linkFlow.update {it.copy(lastCommand="$label: отправлено, ожидаем обратное состояние")}}
                if(climateUi)collapsePanelAfterCommand(token)
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
                for(wait in listOf(400L,800L,1200L)){
                    Thread.sleep(wait)
                    if(token!=epoch)return@execute
                    commands.map {it.feedback}.distinct().forEach {read(it,token)};publish()
                }
                if(token==epoch){
                    val summary=commands.joinToString {write->
                        val actual=reducer.values(write.feedback,now())
                        when {actual==null->"нет обратных данных"
                            write.expected==null->if(actual!=before[write])"feedback изменился; положение не расшифровано"else"конечное положение не подтверждено"
                            actual==listOf(write.expected)->if(actual==before[write])"GET совпадает, значение уже было установлено"else"GET подтвердил новое значение"
                            else->"не подтверждено, GET=$actual"}
                    }
                    linkFlow.update {it.copy(lastCommand="$label: $summary")};report("READBACK $label $summary")
                }
            }catch(error: Exception){
                report("COMMAND_ERROR $label $error")
                if(token==epoch){
                    if(shade!=null){requestedShade=previousShade;publish()}
                    linkFlow.update {it.copy(lastCommand="$label: ${error.message}. Повтор SET не выполнялся.",userMessage="Ошибка команды. Проверьте состояние автомобиля.")}
                }
            }
            finally {busy.set(false);if(token==epoch)linkFlow.update {it.copy(busy=false)}}
        }
    }
    fun binary(cmd: Int)=command("Климат / $cmd",{val current=requireScalar(VerifiedVehicleProtocol.HVAC,cmd,0..1);listOf(VerifiedVehicleProtocol.binary(cmd,if(cmd==8)current==1 else current==0))})
    fun toggle(cmd: Int)=command("Переключение / $cmd",{listOf(VerifiedVehicleProtocol.toggle(cmd,requireScalar(if(cmd==160)VerifiedVehicleProtocol.CAR else VerifiedVehicleProtocol.HVAC,cmd,0..1)))})
    fun temperature(driver: Boolean,delta: Double)=command("Температура",{val current=requireScalar(VerifiedVehicleProtocol.HVAC,if(driver)10 else 13,0..16)
        listOf(VerifiedVehicleProtocol.temperature(driver,(current+if(delta>0)1 else -1).coerceIn(0,16)))})
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
