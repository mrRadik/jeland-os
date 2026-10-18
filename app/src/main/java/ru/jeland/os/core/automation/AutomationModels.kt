package ru.jeland.os.core.automation

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ComponentType(val title:String) { SEATS("Сиденья"), SUNSHADE("Шторка") }
enum class TriggerType { BELOW, ABOVE, SESSION_START }
enum class SeatLevel(val title:String,val level:Int?) {
    KEEP("Не изменять",null), OFF("0",0), LEVEL_1("1",1), LEVEL_2("2",2), LEVEL_3("3",3)
}
enum class SeatParameter(val title:String,val heating:Boolean) {
    DRIVER_HEATING("Водитель · подогрев",true), DRIVER_VENTILATION("Водитель · вентиляция",false),
    PASSENGER_HEATING("Пассажир · подогрев",true), PASSENGER_VENTILATION("Пассажир · вентиляция",false),
    REAR_LEFT_HEATING("Заднее левое · подогрев",true), REAR_RIGHT_HEATING("Заднее правое · подогрев",true)
}
sealed interface AutomationAction { val component:ComponentType }
data class SeatsAction(
    val driverHeating:SeatLevel=SeatLevel.KEEP, val driverVentilation:SeatLevel=SeatLevel.KEEP,
    val passengerHeating:SeatLevel=SeatLevel.KEEP, val passengerVentilation:SeatLevel=SeatLevel.KEEP,
    val rearLeftHeating:SeatLevel=SeatLevel.KEEP, val rearRightHeating:SeatLevel=SeatLevel.KEEP,
):AutomationAction {
    override val component get()=ComponentType.SEATS
    fun values()=listOf(driverHeating,driverVentilation,passengerHeating,passengerVentilation,rearLeftHeating,rearRightHeating)
    fun with(parameter:SeatParameter,value:SeatLevel)=when(parameter) {
        SeatParameter.DRIVER_HEATING->copy(driverHeating=value)
        SeatParameter.DRIVER_VENTILATION->copy(driverVentilation=value)
        SeatParameter.PASSENGER_HEATING->copy(passengerHeating=value)
        SeatParameter.PASSENGER_VENTILATION->copy(passengerVentilation=value)
        SeatParameter.REAR_LEFT_HEATING->copy(rearLeftHeating=value)
        SeatParameter.REAR_RIGHT_HEATING->copy(rearRightHeating=value)
    }
}
enum class SunshadeAction(val title:String):AutomationAction {
    OPEN_FULL("Открыть полностью"), OPEN_HALF("Открыть наполовину");
    override val component get()=ComponentType.SUNSHADE
}
data class AutomationRule(
    val id:UUID=UUID.randomUUID(), val name:String, val enabled:Boolean=true,
    val componentType:ComponentType, val triggerType:TriggerType, val temperatureThreshold:Double?=null,
    val action:AutomationAction, val priority:Int=0, val createdAt:Long=0, val updatedAt:Long=0,
)
object AutomationValidator {
    fun errors(rule:AutomationRule):List<String> = buildList {
        if(rule.name.isBlank())add("Введите название")
        if(rule.name.length>50)add("Не больше 50 символов в названии")
        if(rule.priority !in 0..10)add("Приоритет должен быть от 0 до 10")
        if(rule.componentType!=rule.action.component)add("Тип действий не совпадает с компонентом")
        when(val a=rule.action) {
            is SeatsAction->{
                if(rule.triggerType !in listOf(TriggerType.BELOW,TriggerType.ABOVE))add("Для сидений нужно температурное условие")
                if(rule.temperatureThreshold?.let {it.isFinite()&&it in -60.0..60.0}!=true)add("Температура должна быть от −60 до +60 °C")
                if(a.values().all {it==SeatLevel.KEEP})add("Выберите хотя бы одно действие для сидений")
                if((a.driverHeating.level?:0)>0&&(a.driverVentilation.level?:0)>0)add("Нагрев и вентиляция водителя несовместимы")
                if((a.passengerHeating.level?:0)>0&&(a.passengerVentilation.level?:0)>0)add("Нагрев и вентиляция пассажира несовместимы")
            }
            is SunshadeAction->if(rule.triggerType!=TriggerType.SESSION_START||rule.temperatureThreshold!=null)add("Шторка запускается без температурного условия")
        }
    }
    fun requireValid(rule:AutomationRule) {require(errors(rule).isEmpty()){errors(rule).joinToString(". ")}}
}
data class AutomationSession(val id:UUID,val startedAt:Long)
enum class TemperatureSource { MOCK, DESAY_VDS }
data class OutsideTemperature(val celsius:Double,val measuredAt:Long,val source:TemperatureSource=TemperatureSource.MOCK) {
    fun fresh(now:Long)=celsius.isFinite()&&celsius in -60.0..60.0&&now-measuredAt in 0..MAX_AGE
    companion object {const val MAX_AGE=300_000L}
}
interface AutomationTemperatureRepository {val state:StateFlow<OutsideTemperature?>}
sealed interface AutomationCommand {
    data class Seat(val parameter:SeatParameter,val level:Int):AutomationCommand {init {require(level in 0..3)}}
    data class Shade(val position:SunshadeAction):AutomationCommand
}
enum class AutomationRunStatus(val title:String) {
    PENDING("Ожидает"), SIMULATED("Выполнено в MOCK"), NO_CHANGES("Изменения не нужны"),
    CONDITION_NOT_MET("Условие не выполнено"), NO_TEMPERATURE("Нет свежей температуры"),
    CONFLICT("Уступила приоритет"), INVALID("Ошибка правила"), NO_CONNECTION("Нет связи · MOCK"),
    COMPONENT_UNAVAILABLE("Компонент недоступен · MOCK"), UNKNOWN_STATE("Состояние неизвестно · MOCK"),
    UNSAFE("Небезопасные условия · MOCK"), STATE_CONFLICT("Конфликт режимов · MOCK"), ERROR("Ошибка выполнения")
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
    suspend fun claimSession(session:AutomationSession):List<AutomationRule>?
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
interface AutomationActionExecutor {suspend fun execute(action:AutomationAction):AutomationExecution}
fun formatCelsius(value:Double):String {
    val number=if(value==value.toInt().toDouble())value.toInt().toString()else java.lang.String.format(java.util.Locale.US,"%.1f",value)
    return "${if(value>0)"+"else""}$number °C"
}
fun AutomationRule.conditionSummary()=when(triggerType) {
    TriggerType.BELOW->"Ниже ${temperatureThreshold?.let(::formatCelsius)?:"—"}"
    TriggerType.ABOVE->"Выше ${temperatureThreshold?.let(::formatCelsius)?:"—"}"
    TriggerType.SESSION_START->"При новой сессии · без температурного условия"
}
fun AutomationAction.summary():String=when(this) {
    is SunshadeAction->title
    is SeatsAction->SeatParameter.entries.zip(values()).joinToString("\n") {(p,v)->"${p.title}: ${v.title}"}
}
fun AutomationCommand.summary()=when(this) {
    is AutomationCommand.Seat->"${parameter.title} → $level"
    is AutomationCommand.Shade->"Шторка → ${position.title}"
}
