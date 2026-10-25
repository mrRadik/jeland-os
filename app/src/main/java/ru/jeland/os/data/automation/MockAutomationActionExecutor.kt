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
    val steeringHeating:Boolean=false,val steeringAvailable:Boolean=true,
)
/** Deliberately isolated from VehicleRouting/SeatsRepository/VehicleRepository: cannot reach VDS. */
class MockAutomationComfortRepository {
    private val flow=MutableStateFlow(MockAutomationComfort());val state=flow.asStateFlow()
    @Synchronized fun change(block:(MockAutomationComfort)->MockAutomationComfort){flow.value=block(flow.value)}
    fun execute(action:AutomationAction)=execute(listOf(action))
    /** Preflight the entire scenario on a private copy. Publish once, or change nothing. MOCK only. */
    @Synchronized fun execute(actions:List<AutomationAction>):AutomationExecution {
        require(actions.isNotEmpty()&&actions.map {it.component}.distinct().size==actions.size)
        var next=flow.value;val commands=mutableListOf<AutomationCommand>()
        for(action in actions) {
            val (result,updated)=preview(next,action)
            if(result.status !in listOf(AutomationRunStatus.SIMULATED,AutomationRunStatus.NO_CHANGES))return result
            next=updated;commands+=result.commands
        }
        flow.value=next
        return AutomationExecution(if(commands.isEmpty())AutomationRunStatus.NO_CHANGES else AutomationRunStatus.SIMULATED,
            "Только локальная модель MOCK. Команды автомобилю не отправлялись.",commands)
    }
    private fun preview(current:MockAutomationComfort,action:AutomationAction):Pair<AutomationExecution,MockAutomationComfort> {
        fun fail(status:AutomationRunStatus,text:String)=AutomationExecution(status,text) to current
        if(!current.connected)return fail(AutomationRunStatus.NO_CONNECTION,"Тестовое соединение отсутствует. Изменений нет.")
        if(!current.known)return fail(AutomationRunStatus.UNKNOWN_STATE,"Текущее состояние неизвестно. Изменений нет.")
        val commands:List<AutomationCommand>;val next:MockAutomationComfort
        when(action) {
            is SeatAction->{
                if(!current.seatsAvailable)return fail(AutomationRunStatus.COMPONENT_UNAVAILABLE,"Сиденья недоступны в MOCK.")
                val desired=current.seats.toMutableMap()
                if(action.component.seatParameters().size==1&&action.ventilation!=SeatLevel.KEEP)
                    return fail(AutomationRunStatus.COMPONENT_UNAVAILABLE,"У заднего сиденья нет вентиляции.")
                action.values().forEach {(p,v)->if(v.level!=null)desired[p]=v.level}
                val selected=action.component.seatParameters()
                if(selected.size==2&&selected.all {desired.getValue(it)>0})
                    return fail(AutomationRunStatus.STATE_CONFLICT,"Подогрев и вентиляция несовместимы. KEEP сохраняет текущий режим; укажите 0 для его выключения.")
                // Off commands precede enabling commands; KEEP is never emitted, OFF is explicit zero.
                commands=action.values().mapNotNull {(p,v)->v.level?.takeIf {it!=current.seats.getValue(p)}?.let {AutomationCommand.Seat(p,it)}}
                    .sortedBy {if(it.level==0)0 else 1}
                next=current.copy(seats=desired.toMap())
            }
            is SunshadeAction->{
                if(!current.shadeAvailable)return fail(AutomationRunStatus.COMPONENT_UNAVAILABLE,"Шторка недоступна в MOCK.")
                if(!current.parked||!current.shadeControlAllowed)return fail(AutomationRunStatus.UNSAFE,"Для шторки нужна стоянка и доступность управления. Действий нет.")
                commands=if(current.shade==action)emptyList()else listOf(AutomationCommand.Shade(action))
                next=current.copy(shade=action)
            }
            is SteeringWheelAction->{
                if(!current.steeringAvailable)return fail(AutomationRunStatus.COMPONENT_UNAVAILABLE,"Руль недоступен в MOCK.")
                commands=if(current.steeringHeating)emptyList()else listOf(AutomationCommand.SteeringHeatOn)
                next=current.copy(steeringHeating=true)
            }
        }
        return AutomationExecution(if(commands.isEmpty())AutomationRunStatus.NO_CHANGES else AutomationRunStatus.SIMULATED,
            "Только локальная модель MOCK. Физическое выполнение не проверялось; OEM-команд нет.",commands) to next
    }
}
class MockAutomationActionExecutor(private val comfort:MockAutomationComfortRepository):AutomationActionExecutor {
    override suspend fun execute(actions:List<AutomationAction>)=comfort.execute(actions)
}
