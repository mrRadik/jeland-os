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
    override suspend fun claimSession(session:AutomationSession,manualRuleId:UUID?):List<AutomationRule>?=lock.withLock {
        if(session.id in ledger)return@withLock null
        ledger[session.id]=mutableListOf();data.values.filter {if(manualRuleId!=null)it.id==manualRuleId else it.enabled&&it.triggerType!=TriggerType.MANUAL&&it.launchEvent==session.launchEvent}.toList()
    }
    override suspend fun record(run:AutomationRun){ledger.getValue(run.sessionId).add(run);latestRuns.value=ledger.getValue(run.sessionId).toList()}
    override suspend fun runs(session:UUID)=ledger[session]?.toList().orEmpty()
    override suspend fun finish(session:UUID) {}
    override suspend fun current()=pointer
    override suspend fun setCurrent(session:AutomationSession){pointer=session}
}
class AutomationEngineTest {
    private val now=1_000_000L
    private fun seats(name:String="Зима",level:SeatLevel=SeatLevel.LEVEL_2)=AutomationRule(name=name,triggerType=TriggerType.BELOW,temperatureThreshold=5.0,actions=SeatsAction(driverHeating=level,driverVentilation=SeatLevel.OFF).split())
    private fun shade()=AutomationRule(name="Шторка",triggerType=TriggerType.SESSION_START,actions=listOf(SunshadeAction.OPEN_HALF))
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
        assertTrue(AutomationValidator.errors(seats().copy(actions=SeatsAction().split())).isNotEmpty())
        for(value in listOf(null,Double.NaN,Double.POSITIVE_INFINITY,61.0))assertTrue(AutomationValidator.errors(seats().copy(temperatureThreshold=value)).isNotEmpty())
        assertTrue(AutomationValidator.errors(seats().copy(priority=11)).isNotEmpty())
        assertTrue(AutomationValidator.errors(shade().copy(temperatureThreshold=5.0)).isNotEmpty())
        assertTrue(AutomationValidator.errors(shade().copy(actions=listOf(SunshadeAction.OPEN_HALF,SunshadeAction.OPEN_FULL))).isNotEmpty())
    }
    @Test fun conflictingHeatAndVentilationAreRejected()=runTest {
        val action=SeatsAction(driverHeating=SeatLevel.LEVEL_1,driverVentilation=SeatLevel.LEVEL_2)
        assertTrue(AutomationValidator.errors(seats().copy(actions=action.split())).isNotEmpty())
        val c=MockAutomationComfortRepository();assertEquals(AutomationRunStatus.STATE_CONFLICT,c.execute(action.split()).status)
        assertTrue(c.state.value.seats.values.all {it==0})
    }
    @Test fun keepAndOffAreDifferentAndAllFourSeatsIndependent()=runTest {
        val c=MockAutomationComfortRepository()
        c.change {it.copy(seats=it.seats+(SeatParameter.DRIVER_HEATING to 2)+(SeatParameter.PASSENGER_HEATING to 3)+(SeatParameter.REAR_RIGHT_HEATING to 3))}
        val result=c.execute(SeatsAction(passengerHeating=SeatLevel.OFF,rearLeftHeating=SeatLevel.LEVEL_1).split())
        assertEquals(2,c.state.value.seats[SeatParameter.DRIVER_HEATING]);assertEquals(0,c.state.value.seats[SeatParameter.PASSENGER_HEATING])
        assertEquals(1,c.state.value.seats[SeatParameter.REAR_LEFT_HEATING]);assertEquals(3,c.state.value.seats[SeatParameter.REAR_RIGHT_HEATING])
        assertEquals(listOf(AutomationCommand.Seat(SeatParameter.PASSENGER_HEATING,0),AutomationCommand.Seat(SeatParameter.REAR_LEFT_HEATING,1)),result.commands)
    }
    @Test fun keepCannotSilentlyDisableAnExistingConflictingMode()=runTest {
        val c=MockAutomationComfortRepository();c.change {it.copy(seats=it.seats+(SeatParameter.DRIVER_VENTILATION to 2))}
        assertEquals(AutomationRunStatus.STATE_CONFLICT,c.execute(SeatsAction(driverHeating=SeatLevel.LEVEL_2).split()).status)
        assertEquals(2,c.state.value.seats[SeatParameter.DRIVER_VENTILATION])
        val commands=c.execute(SeatsAction(driverHeating=SeatLevel.LEVEL_2,driverVentilation=SeatLevel.OFF).split()).commands
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
        s.repo.save(rule.copy(actions=SeatsAction(driverHeating=SeatLevel.LEVEL_3).split()),false)
        s.repo.setEnabled(disabled.id,true);s.engine.onSessionStarted(trip)
        assertEquals(2,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING]);assertNull(s.comfort.state.value.shade)
        s.engine.onSessionStarted(session());assertEquals(3,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING]);assertNotNull(s.comfort.state.value.shade)
    }
    @Test fun differentSeatsDoNotConflictRegardlessOfPriorityOrListOrder()=runTest {
        val higher=seats("Важнее",SeatLevel.LEVEL_1).copy(priority=5,updatedAt=1)
        val lower=seats("Ниже").copy(priority=1,updatedAt=100,actions=SeatsAction(rearLeftHeating=SeatLevel.LEVEL_3).split())
        for(rules in listOf(arrayOf(lower,higher),arrayOf(higher,lower))) {
            val s=setup(*rules);val runs=s.engine.onSessionStarted(session()).runs
            assertEquals(AutomationRunStatus.SIMULATED,runs.single {it.ruleId==lower.id}.status)
            assertEquals(1,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING]);assertEquals(3,s.comfort.state.value.seats[SeatParameter.REAR_LEFT_HEATING])
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
        val executor=object:AutomationActionExecutor {override suspend fun execute(action:List<AutomationAction>):AutomationExecution {calls++;yield();return MockAutomationActionExecutor(s.comfort).execute(action)}}
        val engines=listOf(AutomationEngine(s.repo,s.temp,executor,{now}),AutomationEngine(s.repo,s.temp,executor,{now}));val trip=session()
        val results=engines.map {async {it.onSessionStarted(trip)}}.awaitAll()
        assertEquals(1,calls);assertEquals(1,results.count {it.repeated})
    }
    @Test fun editingWhileExecutingDoesNotChangeClaimedRuleSnapshot()=runTest {
        val original=seats(level=SeatLevel.LEVEL_1);val s=setup(original);val entered=CompletableDeferred<Unit>();val gate=CompletableDeferred<Unit>()
        val executor=object:AutomationActionExecutor {override suspend fun execute(action:List<AutomationAction>):AutomationExecution {entered.complete(Unit);gate.await();return s.comfort.execute(action)}}
        val engine=AutomationEngine(s.repo,s.temp,executor,{now});val running=async {engine.onSessionStarted(session())}
        entered.await();s.repo.save(original.copy(actions=SeatsAction(driverHeating=SeatLevel.LEVEL_3).split()),false);gate.complete(Unit);running.await()
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
        val executor=object:AutomationActionExecutor {override suspend fun execute(action:List<AutomationAction>):AutomationExecution {calls++;error("synthetic")}}
        val engine=AutomationEngine(s.repo,s.temp,executor,{now});val trip=session()
        assertEquals(AutomationRunStatus.ERROR,engine.onSessionStarted(trip).runs.single().status)
        assertTrue(engine.onSessionStarted(trip).repeated);assertEquals(1,calls)
    }
    @Test fun updatingOrDeletingOneRuleDoesNotChangeAnother()=runTest {
        val first=seats("Первое");val second=seats("Второе");val s=setup(first,second)
        s.repo.save(first.copy(name="Изменено"),false);s.repo.setEnabled(first.id,false)
        assertEquals(second,s.repo.find(second.id));s.repo.delete(first.id);assertEquals(second,s.repo.find(second.id))
    }
    @Test fun catalogIncludesSteeringWithAnExplicitOnAction() {
        assertEquals(ComponentType.entries.filterNot {it==ComponentType.SEATS}.toSet(),AutomationDevices.available.toSet())
        assertEquals(SteeringWheelAction.HEAT_ON,AutomationDevices.defaultAction(ComponentType.STEERING_WHEEL))
    }
    @Test fun fourSeparateSeatsHaveOnlyTheirOwnSupportedParameters() {
        val seats=AutomationDevices.available.filter {it.isSeat}
        assertEquals(4,seats.size)
        assertEquals(SeatParameter.entries.toSet(),seats.flatMap {it.seatParameters()}.toSet())
        assertEquals(listOf(2,2,1,1),seats.map {it.seatParameters().size})
        assertFalse(ComponentType.SEATS in AutomationDevices.available)
        val two=seats.take(2).map {SeatAction(it,SeatLevel.LEVEL_1,SeatLevel.OFF)}
        assertTrue(AutomationValidator.errors(seats().copy(actions=two)).isEmpty())
        assertTrue(AutomationValidator.errors(seats().copy(actions=two+two.first())).isNotEmpty())
        assertTrue(AutomationValidator.errors(seats().copy(actions=listOf(SeatAction(ComponentType.DRIVER_SEAT)))).isNotEmpty())
    }
    @Test fun oldPayloadSplitsWithoutLosingOffAndWithoutAddingUntouchedSeats() {
        val old=SeatsAction(driverHeating=SeatLevel.OFF,passengerVentilation=SeatLevel.LEVEL_2,rearRightHeating=SeatLevel.LEVEL_1)
        val split=old.split()
        assertEquals(listOf(ComponentType.DRIVER_SEAT,ComponentType.PASSENGER_SEAT,ComponentType.REAR_RIGHT_SEAT),split.map {it.component})
        assertEquals(old,split.flatMap {it.values()}.fold(SeatsAction()) {a,(p,v)->a.with(p,v)})
        split.forEach {a->assertEquals(a,AutomationDeviceRecord.from(UUID.randomUUID(),0,a).domain())}
    }
    @Test fun rearVentilationIsRejectedWithoutChangingAnySeat()=runTest {
        val invalid=SeatAction(ComponentType.REAR_LEFT_SEAT,SeatLevel.LEVEL_1,SeatLevel.LEVEL_2)
        assertTrue(AutomationValidator.errors(seats().copy(actions=listOf(invalid))).isNotEmpty())
        val c=MockAutomationComfortRepository();val before=c.state.value
        assertEquals(AutomationRunStatus.COMPONENT_UNAVAILABLE,c.execute(listOf(SteeringWheelAction.HEAT_ON,invalid)).status)
        assertEquals(before,c.state.value)
    }
    @Test fun changingPassengerDoesNotInspectOrModifyAnotherSeatsModes()=runTest {
        val c=MockAutomationComfortRepository()
        c.change {it.copy(seats=it.seats+(SeatParameter.DRIVER_HEATING to 2)+(SeatParameter.DRIVER_VENTILATION to 2))}
        assertEquals(AutomationRunStatus.SIMULATED,c.execute(SeatAction(ComponentType.PASSENGER_SEAT,SeatLevel.LEVEL_1,SeatLevel.OFF)).status)
        assertEquals(2,c.state.value.seats[SeatParameter.DRIVER_HEATING]);assertEquals(2,c.state.value.seats[SeatParameter.DRIVER_VENTILATION])
        assertEquals(1,c.state.value.seats[SeatParameter.PASSENGER_HEATING])
    }
    @Test fun emptyAndDuplicateDeviceListsAreRejected() {
        assertTrue(AutomationValidator.errors(seats().copy(actions=emptyList())).isNotEmpty())
        assertTrue(AutomationValidator.errors(seats().copy(actions=listOf(SteeringWheelAction.HEAT_ON,SteeringWheelAction.HEAT_ON))).isNotEmpty())
    }
    @Test fun oneScenarioExecutesAllThreeDevicesAndSteeringOnIsIdempotent()=runTest {
        val rule=seats().copy(actions=seats().actions+SunshadeAction.OPEN_HALF+SteeringWheelAction.HEAT_ON)
        val s=setup(rule)
        val first=s.engine.onSessionStarted(session()).runs.single()
        assertEquals(AutomationRunStatus.SIMULATED,first.status)
        assertEquals(2,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
        assertEquals(SunshadeAction.OPEN_HALF,s.comfort.state.value.shade)
        assertTrue(s.comfort.state.value.steeringHeating)
        assertEquals(AutomationRunStatus.NO_CHANGES,s.engine.onSessionStarted(session()).runs.single().status)
        assertTrue(s.comfort.state.value.steeringHeating)
    }
    @Test fun manualRuleNeverRunsOnAutomaticSessionAndCanRunWithoutTemperature()=runTest {
        val manual=AutomationRule(name="Руль по кнопке",actions=listOf(SteeringWheelAction.HEAT_ON))
        val s=setup(manual);s.temp.set(null)
        assertTrue(s.engine.onSessionStarted(session()).runs.isEmpty())
        assertFalse(s.comfort.state.value.steeringHeating)
        assertEquals(AutomationRunStatus.SIMULATED,s.engine.runManually(manual.id).runs.single().status)
        assertTrue(s.comfort.state.value.steeringHeating)
    }
    @Test fun manualLaunchSelectsOnlyTheClickedRuleEvenWhenAutoDisabledOrTemperatureDoesNotMatch()=runTest {
        val selected=seats().copy(enabled=false);val other=shade()
        val s=setup(selected,other);s.temp.set(30.0,now)
        val result=s.engine.runManually(selected.id)
        assertEquals(selected.id,result.runs.single().ruleId)
        assertEquals(AutomationRunStatus.SIMULATED,result.runs.single().status)
        assertEquals(2,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING]);assertNull(s.comfort.state.value.shade)
        assertTrue(s.engine.onSessionStarted(result.session).repeated)
    }
    @Test fun automaticWithoutTemperatureWorksForEveryDeviceAndOnlyOncePerTrip()=runTest {
        val rule=seats().copy(triggerType=TriggerType.SESSION_START,temperatureThreshold=null,
            actions=seats().actions+SteeringWheelAction.HEAT_ON+SunshadeAction.OPEN_FULL)
        val s=setup(rule);s.temp.set(null);val trip=session()
        assertEquals(AutomationRunStatus.SIMULATED,s.engine.onSessionStarted(trip).runs.single().status)
        s.comfort.change {it.copy(steeringHeating=false)}
        assertTrue(s.engine.onSessionStarted(trip).repeated);assertFalse(s.comfort.state.value.steeringHeating)
    }
    @Test fun temperatureConditionAppliesToEntireScenarioNotOnlySeats()=runTest {
        val rule=seats().copy(actions=listOf(SteeringWheelAction.HEAT_ON,SunshadeAction.OPEN_HALF))
        val s=setup(rule);s.temp.set(20.0,now)
        assertEquals(AutomationRunStatus.CONDITION_NOT_MET,s.engine.onSessionStarted(session()).runs.single().status)
        assertFalse(s.comfort.state.value.steeringHeating);assertNull(s.comfort.state.value.shade)
        s.temp.set(null)
        assertEquals(AutomationRunStatus.NO_TEMPERATURE,s.engine.onSessionStarted(session()).runs.single().status)
    }
    @Test fun mockBatchPreflightPreventsPartialChangesAndManualDoesNotBypassSafety()=runTest {
        val rule=AutomationRule(name="Всё вместе",actions=listOf(SteeringWheelAction.HEAT_ON,SunshadeAction.OPEN_HALF))
        val s=setup(rule);s.comfort.change {it.copy(parked=false)}
        val before=s.comfort.state.value
        assertEquals(AutomationRunStatus.UNSAFE,s.engine.runManually(rule.id).runs.single().status)
        assertEquals(before,s.comfort.state.value)
        s.comfort.change {it.copy(parked=true,steeringAvailable=false)}
        assertEquals(AutomationRunStatus.COMPONENT_UNAVAILABLE,s.engine.runManually(rule.id).runs.single().status)
        assertNull(s.comfort.state.value.shade)
    }
    @Test fun overlapRejectsTheWholeLowerPriorityScenarioAndDoesNotBlockUnrelatedDevices()=runTest {
        val winner=seats().copy(priority=5)
        val loser=seats("Проигравший").copy(actions=seats().actions+SunshadeAction.OPEN_HALF,priority=2)
        val independent=AutomationRule(name="Руль",triggerType=TriggerType.SESSION_START,actions=listOf(SteeringWheelAction.HEAT_ON))
        val s=setup(loser,independent,winner);val runs=s.engine.onSessionStarted(session()).runs
        assertEquals(AutomationRunStatus.CONFLICT,runs.single {it.ruleId==loser.id}.status)
        assertNull(s.comfort.state.value.shade);assertTrue(s.comfort.state.value.steeringHeating)
    }
    @Test fun sessionTrackerDoesNotGenerateANewSessionOnRead()=runTest {
        val store=AutomationMemory();val tracker=AutomationSessionTracker(store,{now})
        assertNull(tracker.current());val first=tracker.newSession();assertEquals(first,AutomationSessionTracker(store,{now+1}).current())
        assertNotEquals(first.id,tracker.newSession().id)
    }
    @Test fun engineAndMovementEventsSelectOnlyTheirOwnRulesAndCannotConflictAcrossEvents()=runTest {
        val warm=seats("Прогрев",SeatLevel.LEVEL_3).copy(launchEvent=LaunchEvent.ENGINE_START)
        val moving=seats("В пути",SeatLevel.LEVEL_1).copy(launchEvent=LaunchEvent.MOVEMENT)
        val s=setup(warm,moving)
        val engineSession=session().copy(launchEvent=LaunchEvent.ENGINE_START)
        assertEquals(warm.id,s.engine.onSessionStarted(engineSession).runs.single().ruleId)
        assertEquals(3,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
        assertTrue(s.engine.onSessionStarted(engineSession).repeated)
        assertEquals(moving.id,s.engine.onSessionStarted(session()).runs.single().ruleId)
        assertEquals(1,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
    }
    @Test fun manualLaunchIgnoresEventButStillHonorsExecutorSafety()=runTest {
        val rule=seats().copy(launchEvent=LaunchEvent.ENGINE_START,enabled=false)
        val s=setup(rule)
        assertEquals(rule.id,s.engine.runManually(rule.id).runs.single().ruleId)
        assertEquals(2,s.comfort.state.value.seats[SeatParameter.DRIVER_HEATING])
    }
    @Test fun liveShadePlanWorksForBothEngineAndMovementEventsAndIsNeverReplayed()=runTest {
        for(event in LaunchEvent.entries) {
            val rule=AutomationRule(name="Шторка и руль",triggerType=TriggerType.SESSION_START,launchEvent=event,
                actions=listOf(SunshadeAction.OPEN_HALF,SteeringWheelAction.HEAT_ON))
            val repo=AutomationMemory().apply {save(rule,true)}
            val temperature=MockAutomationTemperatureRepository();var calls=0
            // Pure protocol mapping test, not a claim of physical Binder/actuator verification.
            val executor=LiveAutomationActionExecutor {actions->
                calls++
                val state=ru.jeland.os.core.VehicleLinkState(demo=false,connected=true,writesEnabled=true,raw=mapOf(
                    ru.jeland.os.core.VdsKey(ru.jeland.os.core.VerifiedVehicleProtocol.CAR,10) to ru.jeland.os.core.RawVehicleSample(listOf(1),now),
                    ru.jeland.os.core.VdsKey(ru.jeland.os.core.VerifiedVehicleProtocol.CAR,160) to ru.jeland.os.core.RawVehicleSample(listOf(0),now)))
                val plan=LiveAutomationPlanner.plan(actions,state,now)
                assertNull(plan.failure);assertEquals(2,plan.writes.size)
                AutomationExecution(AutomationRunStatus.SENT,"Protocol plan only",plan.writes.map {it.command})
            }
            val engine=AutomationEngine(repo,temperature,executor,{now});val trip=session().copy(launchEvent=event)
            assertEquals(AutomationRunStatus.SENT,engine.onSessionStarted(trip).runs.single().status)
            assertTrue(engine.onSessionStarted(trip).repeated);assertEquals(1,calls)
        }
    }
    @Test fun cancellationClearsBusyAndRecordsUncertainOutcomeWithoutRetry()=runTest {
        val s=setup(seats());val entered=CompletableDeferred<Unit>()
        val executor=object:AutomationActionExecutor {
            override suspend fun execute(actions:List<AutomationAction>):AutomationExecution {entered.complete(Unit);awaitCancellation()}
        }
        val engine=AutomationEngine(s.repo,s.temp,executor,{now});val trip=session()
        val running=launch {engine.onSessionStarted(trip)};entered.await();assertTrue(engine.busy.value)
        running.cancelAndJoin();assertFalse(engine.busy.value)
        assertEquals(AutomationRunStatus.INTERRUPTED,s.repo.runs(trip.id).single().status)
        assertTrue(engine.onSessionStarted(trip).repeated)
    }
}
