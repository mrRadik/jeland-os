package ru.jeland.os

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.automation.*
import ru.jeland.os.data.automation.*
import java.util.UUID

/** Reference storage contract, with the production Room implementation tested on Android. */
private class AutomationMemory:AutomationRepository,AutomationSessionStore {
    private val lock=Mutex();val data=linkedMapOf<UUID,AutomationRule>()
    override val rules=MutableStateFlow<List<AutomationRule>>(emptyList())
    override val latestRuns=MutableStateFlow<List<AutomationRun>>(emptyList())
    val ledger=mutableMapOf<UUID,MutableList<AutomationRun>>();private var pointer:AutomationSession?=null
    override suspend fun find(id:UUID)=data[id]
    override suspend fun save(rule:AutomationRule,create:Boolean):AutomationRule {
        AutomationValidator.requireValid(rule);check(create==(rule.id !in data))
        data[rule.id]=rule;rules.value=data.values.toList();return rule
    }
    override suspend fun setEnabled(id:UUID,enabled:Boolean){save(data.getValue(id).copy(enabled=enabled),false)}
    override suspend fun delete(id:UUID){data.remove(id);rules.value=data.values.toList()}
    override suspend fun claimSession(session:AutomationSession):List<AutomationRule>?=lock.withLock {
        if(session.id in ledger)return@withLock null
        ledger[session.id]=mutableListOf();data.values.filter {it.enabled}.toList()
    }
    override suspend fun record(run:AutomationRun){ledger.getValue(run.sessionId).add(run);latestRuns.value=ledger.getValue(run.sessionId).toList()}
    override suspend fun runs(session:UUID)=ledger[session]?.toList().orEmpty()
    override suspend fun finish(session:UUID) {}
    override suspend fun current()=pointer
    override suspend fun setCurrent(session:AutomationSession){pointer=session}
}
class AutomationEngineTest {
    private val now=1_000_000L
    private fun seats(name:String="Зима",level:SeatLevel=SeatLevel.LEVEL_2)=AutomationRule(name=name,componentType=ComponentType.SEATS,
        triggerType=TriggerType.BELOW,temperatureThreshold=5.0,action=SeatsAction(driverHeating=level,driverVentilation=SeatLevel.OFF))
    private fun shade()=AutomationRule(name="Шторка",componentType=ComponentType.SUNSHADE,triggerType=TriggerType.SESSION_START,action=SunshadeAction.OPEN_HALF)
    private fun session()=AutomationSession(UUID.randomUUID(),now)
    private class Setup(val repo:AutomationMemory,val temp:MockAutomationTemperatureRepository,val comfort:MockAutomationComfortRepository,val engine:AutomationEngine)
    private suspend fun setup(vararg rules:AutomationRule):Setup {
        val r=AutomationMemory();rules.forEach {r.save(it,true)}
        val t=MockAutomationTemperatureRepository().apply {set(0.0,now)};val c=MockAutomationComfortRepository()
        return Setup(r,t,c,AutomationEngine(r,t,MockAutomationActionExecutor(c),{now}))
    }
    @Test fun belowAndAboveAreStrictAndUseCelsius()=runTest {
        for((type,temp,expected) in listOf(Triple(TriggerType.BELOW,4.9,true),Triple(TriggerType.BELOW,5.0,false),
            Triple(TriggerType.ABOVE,5.1,true),Triple(TriggerType.ABOVE,5.0,false))) {
            val s=setup(seats().copy(triggerType=type));s.temp.set(temp,now)
            val result=s.engine.onSessionStarted(session()).runs.single()
            assertEquals(if(expected)AutomationRunStatus.SIMULATED else AutomationRunStatus.CONDITION_NOT_MET,result.status)
        }
    }
    @Test fun missingStaleFutureAndInvalidTemperaturesNeverTriggerSeats()=runTest {
        for(sample in listOf<OutsideTemperature?>(null,OutsideTemperature(0.0,now-OutsideTemperature.MAX_AGE-1),OutsideTemperature(0.0,now+1),OutsideTemperature(Double.NaN,now))) {
            val repo=AutomationMemory().apply {save(seats(),true)}
            val t=object:AutomationTemperatureRepository {override val state=MutableStateFlow(sample)}
            val c=MockAutomationComfortRepository();val result=AutomationEngine(repo,t,MockAutomationActionExecutor(c),{now}).onSessionStarted(session())
            assertEquals(AutomationRunStatus.NO_TEMPERATURE,result.runs.single().status);assertTrue(c.state.value.seats.values.all {it==0})
        }
    }
    @Test fun shadeDoesNotDependOnTemperature()=runTest {
        val s=setup(shade());s.temp.set(null)
        assertEquals(AutomationRunStatus.SIMULATED,s.engine.onSessionStarted(session()).runs.single().status)
        assertEquals(SunshadeAction.OPEN_HALF,s.comfort.state.value.shade)
    }
    @Test fun validationRejectsEmptyNamesNoActionsAndInvalidThresholds() {
        assertTrue(AutomationValidator.errors(seats().copy(name=" ")).isNotEmpty())
        assertTrue(AutomationValidator.errors(seats().copy(name="x".repeat(51))).isNotEmpty())
        assertTrue(AutomationValidator.errors(seats().copy(action=SeatsAction())).isNotEmpty())
        for(value in listOf(null,Double.NaN,Double.POSITIVE_INFINITY,61.0))assertTrue(AutomationValidator.errors(seats().copy(temperatureThreshold=value)).isNotEmpty())
        assertTrue(AutomationValidator.errors(seats().copy(priority=11)).isNotEmpty())
        assertTrue(AutomationValidator.errors(shade().copy(temperatureThreshold=5.0)).isNotEmpty())
        assertTrue(AutomationValidator.errors(shade().copy(componentType=ComponentType.SEATS)).isNotEmpty())
    }
    @Test fun conflictingHeatAndVentilationAreRejected()=runTest {
        val action=SeatsAction(driverHeating=SeatLevel.LEVEL_1,driverVentilation=SeatLevel.LEVEL_2)
        assertTrue(AutomationValidator.errors(seats().copy(action=action)).isNotEmpty())
        val c=MockAutomationComfortRepository();assertEquals(AutomationRunStatus.STATE_CONFLICT,c.execute(action).status)
        assertTrue(c.state.value.seats.values.all {it==0})
    }
    @Test fun keepAndOffAreDifferentAndAllFourSeatsIndependent()=runTest {
        val c=MockAutomationComfortRepository()
        c.change {it.copy(seats=it.seats+(SeatParameter.DRIVER_HEATING to 2)+(SeatParameter.PASSENGER_HEATING to 3)+(SeatParameter.REAR_RIGHT_HEATING to 3))}
        val result=c.execute(SeatsAction(passengerHeating=SeatLevel.OFF,rearLeftHeating=SeatLevel.LEVEL_1))
        assertEquals(2,c.state.value.seats[SeatParameter.DRIVER_HEATING]);assertEquals(0,c.state.value.seats[SeatParameter.PASSENGER_HEATING])
        assertEquals(1,c.state.value.seats[SeatParameter.REAR_LEFT_HEATING]);assertEquals(3,c.state.value.seats[SeatParameter.REAR_RIGHT_HEATING])
        assertEquals(listOf(AutomationCommand.Seat(SeatParameter.PASSENGER_HEATING,0),AutomationCommand.Seat(SeatParameter.REAR_LEFT_HEATING,1)),result.commands)
    }
    @Test fun keepCannotSilentlyDisableAnExistingConflictingMode()=runTest {
        val c=MockAutomationComfortRepository();c.change {it.copy(seats=it.seats+(SeatParameter.DRIVER_VENTILATION to 2))}
        assertEquals(AutomationRunStatus.STATE_CONFLICT,c.execute(SeatsAction(driverHeating=SeatLevel.LEVEL_2)).status)
        assertEquals(2,c.state.value.seats[SeatParameter.DRIVER_VENTILATION])
        val commands=c.execute(SeatsAction(driverHeating=SeatLevel.LEVEL_2,driverVentilation=SeatLevel.OFF)).commands
        assertEquals(AutomationCommand.Seat(SeatParameter.DRIVER_VENTILATION,0),commands.first())
    }
    @Test fun repeatSessionCannotOverwriteManualChangesEvenWithNewEngine()=runTest {
        val s=setup(seats());val trip=session();s.engine.onSessionStarted(trip)
        s.comfort.change {it.copy(seats=it.seats+(SeatParameter.DRIVER_HEATING to 1))}
        val restarted=AutomationEngine(s.repo,s.temp,MockAutomationActionExecutor(s.comfort),{now})
        assertTrue(restarted.onSessionStarted(trip).repeated);assertEquals(1,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
        assertFalse(restarted.onSessionStarted(session()).repeated);assertEquals(2,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
    }
    @Test fun onlyEnabledRulesAreLoadedAndEditsWaitUntilNewSession()=runTest {
        val rule=seats();val disabled=shade().copy(enabled=false);val s=setup(rule,disabled);val trip=session()
        assertEquals(1,s.engine.onSessionStarted(trip).runs.size)
        s.repo.save(rule.copy(action=SeatsAction(driverHeating=SeatLevel.LEVEL_3)),false)
        s.repo.setEnabled(disabled.id,true);s.engine.onSessionStarted(trip)
        assertEquals(2,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING]);assertNull(s.comfort.state.value.shade)
        s.engine.onSessionStarted(session());assertEquals(3,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING]);assertNotNull(s.comfort.state.value.shade)
    }
    @Test fun userPriorityWinsRegardlessOfListOrderAndKeepDoesNotMerge()=runTest {
        val higher=seats("Важнее",SeatLevel.LEVEL_1).copy(priority=5,updatedAt=1)
        val lower=seats("Ниже").copy(priority=1,updatedAt=100,action=SeatsAction(rearLeftHeating=SeatLevel.LEVEL_3))
        for(rules in listOf(arrayOf(lower,higher),arrayOf(higher,lower))) {
            val s=setup(*rules);val runs=s.engine.onSessionStarted(session()).runs
            assertEquals(AutomationRunStatus.CONFLICT,runs.single {it.ruleId==lower.id}.status)
            assertEquals(1,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING]);assertEquals(0,s.comfort.state.value.seats[SeatParameter.REAR_LEFT_HEATING])
        }
    }
    @Test fun newestRuleWinsPriorityTieWithStableIdTieBreak()=runTest {
        val old=seats("Старое",SeatLevel.LEVEL_1).copy(id=UUID(0,1),updatedAt=1)
        val fresh=seats("Новое",SeatLevel.LEVEL_3).copy(id=UUID(0,2),updatedAt=2)
        val s=setup(fresh,old);s.engine.onSessionStarted(session());assertEquals(3,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
        val tied=setup(fresh.copy(updatedAt=1),old);tied.engine.onSessionStarted(session());assertEquals(1,tied.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
    }
    @Test fun simultaneousEventsClaimOnlyOnceAcrossEngines()=runTest {
        val s=setup(seats());var calls=0
        val executor=object:AutomationActionExecutor {override suspend fun execute(action:AutomationAction):AutomationExecution {calls++;yield();return MockAutomationActionExecutor(s.comfort).execute(action)}}
        val engines=listOf(AutomationEngine(s.repo,s.temp,executor,{now}),AutomationEngine(s.repo,s.temp,executor,{now}));val trip=session()
        val results=engines.map {async {it.onSessionStarted(trip)}}.awaitAll()
        assertEquals(1,calls);assertEquals(1,results.count {it.repeated})
    }
    @Test fun editingWhileExecutingDoesNotChangeClaimedRuleSnapshot()=runTest {
        val original=seats(level=SeatLevel.LEVEL_1);val s=setup(original);val entered=CompletableDeferred<Unit>();val gate=CompletableDeferred<Unit>()
        val executor=object:AutomationActionExecutor {override suspend fun execute(action:AutomationAction):AutomationExecution {entered.complete(Unit);gate.await();return s.comfort.execute(action)}}
        val engine=AutomationEngine(s.repo,s.temp,executor,{now});val running=async {engine.onSessionStarted(session())}
        entered.await();s.repo.save(original.copy(action=SeatsAction(driverHeating=SeatLevel.LEVEL_3)),false);gate.complete(Unit);running.await()
        assertEquals(1,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
        s.engine.onSessionStarted(session());assertEquals(3,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
    }
    @Test fun safetyFailuresDoNotModifyTheMockOrRetryWithinSession()=runTest {
        val checks=listOf<Pair<(MockAutomationComfort)->MockAutomationComfort,AutomationRunStatus>>(
            ({s:MockAutomationComfort->s.copy(connected=false)}) to AutomationRunStatus.NO_CONNECTION,({s:MockAutomationComfort->s.copy(known=false)}) to AutomationRunStatus.UNKNOWN_STATE,
            ({s:MockAutomationComfort->s.copy(shadeAvailable=false)}) to AutomationRunStatus.COMPONENT_UNAVAILABLE,({s:MockAutomationComfort->s.copy(parked=false)}) to AutomationRunStatus.UNSAFE,
            ({s:MockAutomationComfort->s.copy(shadeControlAllowed=false)}) to AutomationRunStatus.UNSAFE)
        for((mutate,expected)in checks) {
            val s=setup(shade());s.comfort.change(mutate);val trip=session()
            assertEquals(expected,s.engine.onSessionStarted(trip).runs.single().status);assertNull(s.comfort.state.value.shade)
            s.comfort.change {MockAutomationComfort()};assertTrue(s.engine.onSessionStarted(trip).repeated);assertNull(s.comfort.state.value.shade)
        }
    }
    @Test fun executorErrorsAreRecordedAndNeverRetried()=runTest {
        val s=setup(shade());var calls=0
        val executor=object:AutomationActionExecutor {override suspend fun execute(action:AutomationAction):AutomationExecution {calls++;error("synthetic")}}
        val engine=AutomationEngine(s.repo,s.temp,executor,{now});val trip=session()
        assertEquals(AutomationRunStatus.ERROR,engine.onSessionStarted(trip).runs.single().status)
        assertTrue(engine.onSessionStarted(trip).repeated);assertEquals(1,calls)
    }
    @Test fun updatingOrDeletingOneRuleDoesNotChangeAnother()=runTest {
        val first=seats("Первое");val second=seats("Второе");val s=setup(first,second)
        s.repo.save(first.copy(name="Изменено"),false);s.repo.setEnabled(first.id,false)
        assertEquals(second,s.repo.find(second.id));s.repo.delete(first.id);assertEquals(second,s.repo.find(second.id))
    }
    @Test fun sessionTrackerDoesNotGenerateANewSessionOnRead()=runTest {
        val store=AutomationMemory();val tracker=AutomationSessionTracker(store,{now})
        assertNull(tracker.current());val first=tracker.newSession();assertEquals(first,AutomationSessionTracker(store,{now+1}).current())
        assertNotEquals(first.id,tracker.newSession().id)
    }
}
