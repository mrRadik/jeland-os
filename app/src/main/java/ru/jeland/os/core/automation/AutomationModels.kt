package ru.jeland.os.core.automation

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ComponentType(val title:String) {
    SEATS("Сиденья · старый формат"), SUNSHADE("Шторка"), STEERING_WHEEL("Руль"),
    DRIVER_SEAT("Сиденье водителя"),PASSENGER_SEAT("Переднее пассажирское"),
    REAR_LEFT_SEAT("Заднее левое сиденье"),REAR_RIGHT_SEAT("Заднее правое сиденье");
    val isSeat get()=this in listOf(DRIVER_SEAT,PASSENGER_SEAT,REAR_LEFT_SEAT,REAR_RIGHT_SEAT)
    fun seatParameters():List<SeatParameter> = when(this) {
        DRIVER_SEAT->listOf(SeatParameter.DRIVER_HEATING,SeatParameter.DRIVER_VENTILATION)
        PASSENGER_SEAT->listOf(SeatParameter.PASSENGER_HEATING,SeatParameter.PASSENGER_VENTILATION)
        REAR_LEFT_SEAT->listOf(SeatParameter.REAR_LEFT_HEATING)
        REAR_RIGHT_SEAT->listOf(SeatParameter.REAR_RIGHT_HEATING)
        else->emptyList()
    }
}
enum class TriggerType { BELOW, ABOVE, SESSION_START, MANUAL }
enum class LaunchEvent(val title:String) {ENGINE_START("При запуске двигателя"),MOVEMENT("После начала движения")}
enum class SeatLevel(val title:String,val level:Int?) {
    KEEP("Не изменять",null), OFF("0",0), LEVEL_1("1",1), LEVEL_2("2",2), LEVEL_3("3",3)
}
enum class SeatParameter(val title:String,val heating:Boolean) {
    DRIVER_HEATING("Водитель · подогрев",true), DRIVER_VENTILATION("Водитель · вентиляция",false),
    PASSENGER_HEATING("Пассажир · подогрев",true), PASSENGER_VENTILATION("Пассажир · вентиляция",false),
    REAR_LEFT_HEATING("Заднее левое · подогрев",true), REAR_RIGHT_HEATING("Заднее правое · подогрев",true)
}
sealed interface AutomationAction { val component:ComponentType }
/** Legacy six-channel payload, used only for old storage/draft conversion. Not a scenario device. */
data class SeatsAction(
    val driverHeating:SeatLevel=SeatLevel.KEEP, val driverVentilation:SeatLevel=SeatLevel.KEEP,
    val passengerHeating:SeatLevel=SeatLevel.KEEP, val passengerVentilation:SeatLevel=SeatLevel.KEEP,
    val rearLeftHeating:SeatLevel=SeatLevel.KEEP, val rearRightHeating:SeatLevel=SeatLevel.KEEP,
) {
    fun values()=listOf(driverHeating,driverVentilation,passengerHeating,passengerVentilation,rearLeftHeating,rearRightHeating)
    fun with(parameter:SeatParameter,value:SeatLevel)=when(parameter) {
        SeatParameter.DRIVER_HEATING->copy(driverHeating=value)
        SeatParameter.DRIVER_VENTILATION->copy(driverVentilation=value)
        SeatParameter.PASSENGER_HEATING->copy(passengerHeating=value)
        SeatParameter.PASSENGER_VENTILATION->copy(passengerVentilation=value)
        SeatParameter.REAR_LEFT_HEATING->copy(rearLeftHeating=value)
        SeatParameter.REAR_RIGHT_HEATING->copy(rearRightHeating=value)
    }
    fun forDevice(type:ComponentType)=SeatAction(type,
        values()[type.seatParameters().first {it.heating}.ordinal],
        type.seatParameters().firstOrNull {!it.heating}?.let {values()[it.ordinal]}?:SeatLevel.KEEP)
    fun split():List<SeatAction> = listOf(ComponentType.DRIVER_SEAT,ComponentType.PASSENGER_SEAT,ComponentType.REAR_LEFT_SEAT,ComponentType.REAR_RIGHT_SEAT)
        .map(::forDevice).filter {a->a.values().any {it.second!=SeatLevel.KEEP}}
}
data class SeatAction(
    override val component:ComponentType,val heating:SeatLevel=SeatLevel.KEEP,val ventilation:SeatLevel=SeatLevel.KEEP,
):AutomationAction {
    init {require(component.isSeat)}
    fun values()=component.seatParameters().map {it to if(it.heating)heating else ventilation}
    fun legacy()=values().fold(SeatsAction()) {payload,(p,v)->payload.with(p,v)}
}
enum class SunshadeAction(val title:String):AutomationAction {
    OPEN_FULL("Открыть полностью"), OPEN_HALF("Открыть наполовину");
    override val component get()=ComponentType.SUNSHADE
}
enum class SteeringWheelAction(val title:String):AutomationAction {
    HEAT_ON("Включить подогрев");
    override val component get()=ComponentType.STEERING_WHEEL
}
/** The device catalog is extended in code, never by entering arbitrary vehicle commands. */
object AutomationDevices {
    val available:List<ComponentType> = listOf(ComponentType.DRIVER_SEAT,ComponentType.PASSENGER_SEAT,
        ComponentType.REAR_LEFT_SEAT,ComponentType.REAR_RIGHT_SEAT,ComponentType.SUNSHADE,ComponentType.STEERING_WHEEL)
    fun defaultAction(type:ComponentType):AutomationAction=when(type) {
        ComponentType.SEATS->error("Группа сидений заменена отдельными устройствами")
        ComponentType.DRIVER_SEAT,ComponentType.PASSENGER_SEAT,ComponentType.REAR_LEFT_SEAT,ComponentType.REAR_RIGHT_SEAT->SeatAction(type)
        ComponentType.SUNSHADE->SunshadeAction.OPEN_HALF
        ComponentType.STEERING_WHEEL->SteeringWheelAction.HEAT_ON
    }
}
data class AutomationRule(
    val id:UUID=UUID.randomUUID(),val name:String,val enabled:Boolean=true,
    val triggerType:TriggerType=TriggerType.MANUAL,val temperatureThreshold:Double?=null,
    val actions:List<AutomationAction>,val priority:Int=0,val createdAt:Long=0,val updatedAt:Long=0,
    val launchEvent:LaunchEvent=LaunchEvent.MOVEMENT,
)
object AutomationValidator {
    fun errors(rule:AutomationRule):List<String> = buildList {
        if(rule.name.isBlank())add("Введите название")
        if(rule.name.length>50)add("Не больше 50 символов в названии")
        if(rule.priority !in 0..10)add("Приоритет должен быть от 0 до 10")
        if(rule.actions.isEmpty())add("Добавьте хотя бы одно устройство")
        if(rule.actions.map {it.component}.distinct().size!=rule.actions.size)add("Устройство можно добавить только один раз")
        when(rule.triggerType) {
            TriggerType.BELOW,TriggerType.ABOVE->if(rule.temperatureThreshold?.let {it.isFinite()&&it in -60.0..60.0}!=true)add("Температура должна быть от −60 до +60 °C")
            TriggerType.MANUAL,TriggerType.SESSION_START->if(rule.temperatureThreshold!=null)add("Этому режиму температурный порог не нужен")
        }
        for(a in rule.actions)when(a) {
            is SeatAction->{
                if(a.values().all {it.second==SeatLevel.KEEP})add("${a.component.title}: выберите действие")
                if((a.heating.level?:0)>0&&(a.ventilation.level?:0)>0)add("${a.component.title}: нагрев и вентиляция несовместимы")
                if(a.component.seatParameters().size==1&&a.ventilation!=SeatLevel.KEEP)add("У заднего сиденья нет вентиляции")
            }
            is SunshadeAction,is SteeringWheelAction->Unit
        }
    }
    fun requireValid(rule:AutomationRule) {require(errors(rule).isEmpty()){errors(rule).joinToString(". ")}}
}
data class AutomationSession(val id:UUID,val startedAt:Long,val launchEvent:LaunchEvent=LaunchEvent.MOVEMENT)
enum class TemperatureSource { MOCK, DESAY_VDS }
data class OutsideTemperature(val celsius:Double,val measuredAt:Long,val source:TemperatureSource=TemperatureSource.MOCK) {
    fun fresh(now:Long)=celsius.isFinite()&&celsius in -60.0..60.0&&now-measuredAt in 0..(if(source==TemperatureSource.DESAY_VDS)15_000L else MAX_AGE)
    companion object {const val MAX_AGE=300_000L}
}
interface AutomationTemperatureRepository {val state:StateFlow<OutsideTemperature?>}
sealed interface AutomationCommand {
    data class Seat(val parameter:SeatParameter,val level:Int):AutomationCommand {init {require(level in 0..3)}}
    data class Shade(val position:SunshadeAction):AutomationCommand
    data object SteeringHeatOn:AutomationCommand
}
enum class AutomationRunStatus(val title:String) {
    CONFIRMED("Подтверждено автомобилем"), SENT("Команды отправлены"), UNCONFIRMED("Нет подтверждения"),
    PARTIAL("Отправка прервана"), INTERRUPTED("Выполнение прервано"),
    PENDING("Ожидает"), SIMULATED("Выполнено в MOCK"), NO_CHANGES("Изменения не нужны"),
    CONDITION_NOT_MET("Условие не выполнено"), NO_TEMPERATURE("Нет свежей температуры"),
    CONFLICT("Уступила приоритет"), INVALID("Ошибка правила"), NO_CONNECTION("Нет связи"),
    COMPONENT_UNAVAILABLE("Компонент недоступен"), UNKNOWN_STATE("Состояние неизвестно"),
    UNSAFE("Небезопасные условия"), STATE_CONFLICT("Конфликт режимов"), ERROR("Ошибка выполнения")
}
data class AutomationExecution(val status:AutomationRunStatus,val message:String,val commands:List<AutomationCommand> = emptyList())
data class AutomationRun(
    val sessionId:UUID,val ruleId:UUID,val ruleName:String,val status:AutomationRunStatus,
    val message:String,val commandSummary:String="",val at:Long,
)
data class AutomationSessionResult(val session:AutomationSession,val repeated:Boolean,val runs:List<AutomationRun>)
interface AutomationRepository {
    val rules:Flow<List<AutomationRule>>
    val latestRuns:Flow<List<AutomationRun>>
    suspend fun find(id:UUID):AutomationRule?
    suspend fun save(rule:AutomationRule,create:Boolean):AutomationRule
    suspend fun setEnabled(id:UUID,enabled:Boolean)
    suspend fun delete(id:UUID)
    /** Atomic durable claim + immutable active-rule snapshot + pending ledger, before execution. */
    suspend fun claimSession(session:AutomationSession,manualRuleId:UUID?=null):List<AutomationRule>?
    suspend fun record(run:AutomationRun)
    suspend fun runs(session:UUID):List<AutomationRun>
    suspend fun finish(session:UUID)
}
interface AutomationSessionStore {
    suspend fun current():AutomationSession?
    suspend fun setCurrent(session:AutomationSession)
}
class AutomationSessionTracker(private val store:AutomationSessionStore,private val clock:()->Long=System::currentTimeMillis) {
    suspend fun newSession()=AutomationSession(UUID.randomUUID(),clock()).also {store.setCurrent(it)}
    suspend fun current()=store.current()
}
interface AutomationActionExecutor {suspend fun execute(actions:List<AutomationAction>):AutomationExecution}
fun formatCelsius(value:Double):String {
    val number=if(value==value.toInt().toDouble())value.toInt().toString()else java.lang.String.format(java.util.Locale.US,"%.1f",value)
    return "${if(value>0)"+"else""}$number °C"
}
fun AutomationRule.conditionSummary()=when(triggerType) {
    TriggerType.BELOW->"Автоматически · ниже ${temperatureThreshold?.let(::formatCelsius)?:"—"} · ${launchEvent.title.lowercase()}"
    TriggerType.ABOVE->"Автоматически · выше ${temperatureThreshold?.let(::formatCelsius)?:"—"} · ${launchEvent.title.lowercase()}"
    TriggerType.MANUAL->"Вручную · без температурного условия"
    TriggerType.SESSION_START->"Автоматически · ${launchEvent.title.lowercase()} · без температурного условия"
}
fun AutomationAction.summary():String=when(this) {
    is SunshadeAction->title
    is SteeringWheelAction->title
    is SeatAction->values().filter {it.second!=SeatLevel.KEEP}.joinToString("\n") {(p,v)->"${if(p.heating)"Подогрев"else"Вентиляция"}: ${if(v==SeatLevel.OFF)"выключить"else v.title}"}
}
fun AutomationCommand.summary()=when(this) {
    is AutomationCommand.Seat->"${parameter.title} → $level"
    is AutomationCommand.Shade->"Шторка → ${position.title}"
    AutomationCommand.SteeringHeatOn->"Руль → включить подогрев"
}
