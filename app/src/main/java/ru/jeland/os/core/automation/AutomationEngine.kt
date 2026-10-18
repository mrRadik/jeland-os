package ru.jeland.os.core.automation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Explicit session events only; no Activity, navigation, timer, radio or Desay dependency. */
class AutomationEngine(
    private val repository:AutomationRepository,private val temperature:AutomationTemperatureRepository,
    private val executor:AutomationActionExecutor,private val clock:()->Long=System::currentTimeMillis,
) {
    private val mutex=Mutex()
    suspend fun onSessionStarted(session:AutomationSession):AutomationSessionResult=mutex.withLock {
        val snapshot=repository.claimSession(session)
            ?:return@withLock AutomationSessionResult(session,true,repository.runs(session.id))
        val reading=temperature.state.value;val now=clock()
        val eligible=mutableListOf<AutomationRule>();val results=mutableListOf<AutomationRun>()
        suspend fun result(rule:AutomationRule,status:AutomationRunStatus,message:String,commands:List<AutomationCommand> = emptyList()) {
            val run=AutomationRun(session.id,rule.id,rule.name,status,message,commands.joinToString("\n"){it.summary()},clock())
            repository.record(run);results+=run
        }
        for(rule in snapshot) {
            val errors=AutomationValidator.errors(rule)
            when {
                errors.isNotEmpty()->result(rule,AutomationRunStatus.INVALID,errors.joinToString(". "))
                rule.triggerType!=TriggerType.SESSION_START&&(reading==null||!reading.fresh(now))->result(rule,AutomationRunStatus.NO_TEMPERATURE,"Нет актуальной наружной температуры. Действий нет.")
                rule.triggerType==TriggerType.BELOW&&!(reading!!.celsius<rule.temperatureThreshold!!)->result(rule,AutomationRunStatus.CONDITION_NOT_MET,rule.conditionSummary())
                rule.triggerType==TriggerType.ABOVE&&!(reading!!.celsius>rule.temperatureThreshold!!)->result(rule,AutomationRunStatus.CONDITION_NOT_MET,rule.conditionSummary())
                else->eligible+=rule
            }
        }
        // Whole-component arbitration, including KEEP. Never merge incidental list order.
        val order=compareByDescending<AutomationRule>{it.priority}.thenByDescending {it.updatedAt}.thenBy {it.id.toString()}
        for((_,rules)in eligible.groupBy {it.componentType}.toSortedMap(compareBy {it.ordinal})) {
            val sorted=rules.sortedWith(order);val winner=sorted.first()
            for(loser in sorted.drop(1))result(loser,AutomationRunStatus.CONFLICT,"Выбран сценарий «${winner.name}» (приоритет ${winner.priority}). KEEP не заполняется другими правилами.")
            val execution=try {executor.execute(winner.action)}catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){AutomationExecution(AutomationRunStatus.ERROR,"Ошибка исполнителя. Повтор автоматически не выполняется.")}
            result(winner,execution.status,execution.message,execution.commands)
        }
        repository.finish(session.id)
        AutomationSessionResult(session,false,results)
    }
}
