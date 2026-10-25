package ru.jeland.os.core.automation

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Explicit trip events or a selected manual rule only; never triggered by an Activity or wake-up. */
class AutomationEngine(
    private val repository:AutomationRepository,private val temperature:AutomationTemperatureRepository,
    private val executor:AutomationActionExecutor,private val clock:()->Long=System::currentTimeMillis,
) {
    private val mutex=Mutex()
    private val running=MutableStateFlow(false)
    val busy=running.asStateFlow()
    suspend fun onSessionStarted(session:AutomationSession)=run(session,null)
    suspend fun runManually(ruleId:UUID)=run(AutomationSession(UUID.randomUUID(),clock()),ruleId)
    private suspend fun run(session:AutomationSession,manualRuleId:UUID?):AutomationSessionResult=mutex.withLock {
        running.value=true
        try {runClaimed(session,manualRuleId)}finally {running.value=false}
    }
    private suspend fun runClaimed(session:AutomationSession,manualRuleId:UUID?):AutomationSessionResult {
        val snapshot=repository.claimSession(session,manualRuleId)
            ?:return AutomationSessionResult(session,true,repository.runs(session.id))
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
                manualRuleId!=null->eligible+=rule
                rule.launchEvent!=session.launchEvent->Unit
                rule.triggerType==TriggerType.MANUAL->Unit
                rule.triggerType==TriggerType.SESSION_START->eligible+=rule
                reading==null||!reading.fresh(now)->result(rule,AutomationRunStatus.NO_TEMPERATURE,"Нет актуальной наружной температуры. Действий нет.")
                rule.triggerType==TriggerType.BELOW&&!(reading.celsius<rule.temperatureThreshold!!)->result(rule,AutomationRunStatus.CONDITION_NOT_MET,rule.conditionSummary())
                rule.triggerType==TriggerType.ABOVE&&!(reading.celsius>rule.temperatureThreshold!!)->result(rule,AutomationRunStatus.CONDITION_NOT_MET,rule.conditionSummary())
                else->eligible+=rule
            }
        }
        // Scenarios are indivisible: a device conflict must never silently execute half a scenario.
        val claimed=mutableMapOf<ComponentType,AutomationRule>()
        val order=compareByDescending<AutomationRule>{it.priority}.thenByDescending {it.updatedAt}.thenBy {it.id.toString()}
        for(rule in eligible.sortedWith(order)) {
            val winner=rule.actions.firstNotNullOfOrNull {claimed[it.component]}
            if(winner!=null) {
                result(rule,AutomationRunStatus.CONFLICT,"Общее устройство со сценарием «${winner.name}» (приоритет ${winner.priority}). Сценарий пропущен целиком.")
                continue
            }
            rule.actions.forEach {claimed[it.component]=rule}
            val execution=try {executor.execute(rule.actions)}catch(cancelled:CancellationException){
                withContext(NonCancellable) {result(rule,AutomationRunStatus.INTERRUPTED,"Выполнение прервано. Часть команд могла быть отправлена; автоматического повтора нет.")}
                throw cancelled
            }
                catch(_:Exception){AutomationExecution(AutomationRunStatus.ERROR,"Ошибка исполнителя. Повтор автоматически не выполняется.")}
            result(rule,execution.status,(if(manualRuleId!=null)"Ручной запуск. "else"")+execution.message,execution.commands)
        }
        repository.finish(session.id)
        return AutomationSessionResult(session,false,results)
    }
}
