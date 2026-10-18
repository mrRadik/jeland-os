package ru.jeland.os.data.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.jeland.os.core.automation.*

class MockAutomationTemperatureRepository:AutomationTemperatureRepository {
    private val flow=MutableStateFlow<OutsideTemperature?>(null)
    override val state=flow.asStateFlow()
    fun set(value:Double?,at:Long=System.currentTimeMillis()) {
        require(value==null||value.isFinite()&&value in -60.0..60.0)
        flow.value=value?.let {OutsideTemperature(it,at)}
    }
    fun stale(){flow.update {it?.copy(measuredAt=System.currentTimeMillis()-OutsideTemperature.MAX_AGE-1)}}
}
data class MockAutomationComfort(
    val seats:Map<SeatParameter,Int> = SeatParameter.entries.associateWith {0},val shade:SunshadeAction?=null,
    val connected:Boolean=true,val seatsAvailable:Boolean=true,val shadeAvailable:Boolean=true,
    val known:Boolean=true,val parked:Boolean=true,val shadeControlAllowed:Boolean=true,
)
/** Deliberately isolated from VehicleRouting/SeatsRepository/VehicleRepository: cannot reach VDS. */
class MockAutomationComfortRepository {
    private val flow=MutableStateFlow(MockAutomationComfort());val state=flow.asStateFlow()
    @Synchronized fun change(block:(MockAutomationComfort)->MockAutomationComfort){flow.value=block(flow.value)}
    @Synchronized fun execute(action:AutomationAction):AutomationExecution {
        val current=flow.value
        fun fail(status:AutomationRunStatus,text:String)=AutomationExecution(status,text)
        if(!current.connected)return fail(AutomationRunStatus.NO_CONNECTION,"Тестовое соединение отсутствует. Изменений нет.")
        if(!current.known)return fail(AutomationRunStatus.UNKNOWN_STATE,"Текущее состояние неизвестно. Изменений нет.")
        val commands:List<AutomationCommand>;val next:MockAutomationComfort
        when(action) {
            is SeatsAction->{
                if(!current.seatsAvailable)return fail(AutomationRunStatus.COMPONENT_UNAVAILABLE,"Сиденья недоступны в MOCK.")
                val desired=current.seats.toMutableMap()
                SeatParameter.entries.zip(action.values()).forEach {(p,v)->if(v.level!=null)desired[p]=v.level}
                for(pair in listOf(SeatParameter.DRIVER_HEATING to SeatParameter.DRIVER_VENTILATION,SeatParameter.PASSENGER_HEATING to SeatParameter.PASSENGER_VENTILATION)) {
                    if(desired.getValue(pair.first)>0&&desired.getValue(pair.second)>0)
                        return fail(AutomationRunStatus.STATE_CONFLICT,"Подогрев и вентиляция несовместимы. KEEP сохраняет текущий режим; укажите 0 для его выключения.")
                }
                // Off commands precede enabling commands; KEEP is never emitted, OFF is explicit zero.
                commands=SeatParameter.entries.zip(action.values()).mapNotNull {(p,v)->v.level?.takeIf {it!=current.seats.getValue(p)}?.let {AutomationCommand.Seat(p,it)}}
                    .sortedBy {if(it.level==0)0 else 1}
                next=current.copy(seats=desired.toMap())
            }
            is SunshadeAction->{
                if(!current.shadeAvailable)return fail(AutomationRunStatus.COMPONENT_UNAVAILABLE,"Шторка недоступна в MOCK.")
                if(!current.parked||!current.shadeControlAllowed)return fail(AutomationRunStatus.UNSAFE,"Для шторки нужна стоянка и доступность управления. Действий нет.")
                commands=if(current.shade==action)emptyList()else listOf(AutomationCommand.Shade(action))
                next=current.copy(shade=action)
            }
        }
        flow.value=next
        return AutomationExecution(if(commands.isEmpty())AutomationRunStatus.NO_CHANGES else AutomationRunStatus.SIMULATED,
            "Только локальная модель MOCK. Физическое выполнение не проверялось; OEM-команд нет.",commands)
    }
}
class MockAutomationActionExecutor(private val comfort:MockAutomationComfortRepository):AutomationActionExecutor {
    override suspend fun execute(action:AutomationAction)=comfort.execute(action)
}
