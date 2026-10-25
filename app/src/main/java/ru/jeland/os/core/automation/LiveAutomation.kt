package ru.jeland.os.core.automation

import ru.jeland.os.core.*
data class AutomationWrite(val command:AutomationCommand,val write:VdsWrite)
data class AutomationPlan(val writes:List<AutomationWrite> = emptyList(),val failure:AutomationExecution?=null)

/** All-device preflight using real telemetry only. Does not send/project a vehicle command. */
object LiveAutomationPlanner {
    private const val MAX_AGE=15_000L
    fun seatCommand(parameter:SeatParameter)=when(parameter) {
        SeatParameter.DRIVER_HEATING->95;SeatParameter.DRIVER_VENTILATION->93
        SeatParameter.PASSENGER_HEATING->96;SeatParameter.PASSENGER_VENTILATION->94
        SeatParameter.REAR_LEFT_HEATING->139;SeatParameter.REAR_RIGHT_HEATING->141
    }
    fun keys(actions:List<AutomationAction>):Set<VdsKey> = buildSet {
        actions.forEach {a->when(a) {
            is SeatAction->a.component.seatParameters().forEach {add(VdsKey(VerifiedVehicleProtocol.CAR,seatCommand(it)))}
            is SteeringWheelAction->add(VdsKey(VerifiedVehicleProtocol.CAR,160))
            is SunshadeAction->add(VdsKey(VerifiedVehicleProtocol.CAR,10))
        }}
    }
    fun plan(actions:List<AutomationAction>,link:VehicleLinkState,now:Long):AutomationPlan {
        fun fail(status:AutomationRunStatus,message:String)=AutomationPlan(failure=AutomationExecution(status,message))
        if(link.demo||!link.connected||!link.writesEnabled)return fail(AutomationRunStatus.NO_CONNECTION,"Нужно активное подключение к ГУ. Команды не отправлены.")
        if(AutomationValidator.errors(AutomationRule(name="Проверка",actions=actions)).isNotEmpty())
            return fail(AutomationRunStatus.INVALID,"Некорректный набор действий. Команды не отправлены.")
        fun scalar(cmd:Int,range:IntRange):Int?=link.raw[VdsKey(VerifiedVehicleProtocol.CAR,cmd)]
            ?.takeIf {now-it.receivedMillis in 0..MAX_AGE}?.values?.singleOrNull()?.takeIf {it in range}
        val writes=mutableListOf<AutomationWrite>()
        for(action in actions)when(action) {
            is SeatAction->{
                val values=action.values();val desired=mutableMapOf<SeatParameter,Int>()
                for((p,v) in values) {
                    if(v!=SeatLevel.KEEP||values.any {(other,level)->other!=p&&(level.level?:0)>0}) {
                        val before=scalar(seatCommand(p),0..3)?:return fail(AutomationRunStatus.UNKNOWN_STATE,"${action.component.title}: нет свежего состояния. Сценарий не отправлен.")
                        desired[p]=v.level?:before
                        if(v.level!=null&&v.level!=before)writes+=AutomationWrite(AutomationCommand.Seat(p,v.level),VerifiedVehicleProtocol.seat(seatCommand(p),v.level))
                    }
                }
                if(values.size==2&&values.all {(p,_)->(desired[p]?:0)>0})
                    return fail(AutomationRunStatus.STATE_CONFLICT,"${action.component.title}: подогрев и вентиляция несовместимы. Сценарий не отправлен.")
            }
            is SteeringWheelAction->{
                val before=scalar(160,0..1)?:return fail(AutomationRunStatus.UNKNOWN_STATE,"Нет свежего состояния подогрева руля. Сценарий не отправлен.")
                if(before==0)writes+=AutomationWrite(AutomationCommand.SteeringHeatOn,VerifiedVehicleProtocol.toggle(160,0))
            }
            is SunshadeAction->{
                val before=scalar(10,1..7)?:return fail(AutomationRunStatus.UNKNOWN_STATE,"Нет свежего состояния шторки. Сценарий не отправлен.")
                if(before !in listOf(1,3,5))return fail(AutomationRunStatus.UNSAFE,"Шторка движется или недоступна. Сценарий не отправлен.")
                val target=if(action==SunshadeAction.OPEN_FULL)Shade.OPEN else Shade.HALF
                if(!(target==Shade.OPEN&&before==5))writes+=AutomationWrite(AutomationCommand.Shade(action),VerifiedVehicleProtocol.shade(target))
            }
        }
        return AutomationPlan(writes.sortedBy {if((it.command as? AutomationCommand.Seat)?.level==0)0 else 1})
    }
}
fun interface AutomationVehiclePort {suspend fun execute(actions:List<AutomationAction>):AutomationExecution}
class LiveAutomationActionExecutor(private val vehicle:AutomationVehiclePort):AutomationActionExecutor {
    override suspend fun execute(actions:List<AutomationAction>)=vehicle.execute(actions)
}
