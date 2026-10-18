package ru.jeland.os

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.core.automation.*
import ru.jeland.os.data.automation.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AutomationRoomInstrumentation {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun rule(name:String="Зимний комфорт")=AutomationRule(name=name,componentType=ComponentType.SEATS,
        triggerType=TriggerType.BELOW,temperatureThreshold=5.0,
        action=SeatsAction(SeatLevel.LEVEL_2,SeatLevel.OFF,SeatLevel.LEVEL_1,SeatLevel.KEEP,SeatLevel.LEVEL_3,SeatLevel.OFF))
    @Test fun typedRoundtripAndChangesAreIsolated()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AutomationDatabase::class.java).build()
        try {
            val repo=RoomAutomationRepository(db,{1000})
            val first=repo.save(rule(),true);val second=repo.save(rule("Другое"),true)
            assertEquals(first,repo.find(first.id));assertEquals(second,repo.find(second.id))
            val changed=repo.save(first.copy(name="Изменено",action=SeatsAction(rearRightHeating=SeatLevel.LEVEL_1)),false)
            assertEquals(1000L,changed.createdAt);assertTrue(changed.updatedAt>second.updatedAt);assertEquals(second,repo.find(second.id))
            repo.setEnabled(first.id,false);assertFalse(repo.find(first.id)!!.enabled);assertEquals(second,repo.find(second.id))
            val shade=repo.save(AutomationRule(name="Шторка",componentType=ComponentType.SUNSHADE,triggerType=TriggerType.SESSION_START,action=SunshadeAction.OPEN_HALF),true)
            assertNull(db.automationDao().find(shade.id.toString())!!.seats)
            assertNull(db.automationDao().find(second.id.toString())!!.sunshade)
            assertEquals(SunshadeAction.OPEN_HALF,repo.find(shade.id)!!.action)
            repo.delete(first.id);assertEquals(setOf(second.id,shade.id),repo.rules.first().map {it.id}.toSet())
        } finally {db.close()}
    }
    @Test fun validationFailureRollsBackAndDeletedRuleCannotBeResurrected()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AutomationDatabase::class.java).build()
        try {
            val repo=RoomAutomationRepository(db);val original=repo.save(rule(),true)
            try {repo.save(original.copy(name=" "),false);fail("Invalid update must fail")}catch(_:IllegalArgumentException) {}
            assertEquals(original,repo.find(original.id))
            repo.delete(original.id)
            try {repo.save(original,false);fail("Deleted rule must not reappear")}catch(_:IllegalStateException) {}
            assertTrue(repo.rules.first().isEmpty())
        } finally {db.close()}
    }
    @Test fun diskReopenPreservesRulesSessionPointerAndNoReplay()=runBlocking {
        val filename="qa-automations-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,AutomationDatabase::class.java,filename).build()
        try {
            var repo=RoomAutomationRepository(db);val rule=repo.save(rule(),true)
            val tracker=AutomationSessionTracker(repo);val session=tracker.newSession()
            val temperature=MockAutomationTemperatureRepository().apply {set(0.0)}
            val comfort=MockAutomationComfortRepository()
            AutomationEngine(repo,temperature,MockAutomationActionExecutor(comfort)).onSessionStarted(session)
            db.close();db=Room.databaseBuilder(context,AutomationDatabase::class.java,filename).build();repo=RoomAutomationRepository(db)
            assertEquals(rule,repo.find(rule.id));assertEquals(session,AutomationSessionTracker(repo).current())
            comfort.change {it.copy(seats=it.seats+(SeatParameter.DRIVER_HEATING to 1))}
            assertTrue(AutomationEngine(repo,temperature,MockAutomationActionExecutor(comfort)).onSessionStarted(session).repeated)
            assertEquals(1,comfort.state.value.seats[SeatParameter.DRIVER_HEATING]);assertEquals(1,repo.runs(session.id).size)
            assertEquals(AutomationRunStatus.SIMULATED,repo.runs(session.id).single().status)
        } finally {db.close();check(filename.startsWith("qa-automations-"));context.deleteDatabase(filename)}
    }
    @Test fun interruptedPendingClaimIsNotReplayedAfterReopen()=runBlocking {
        val filename="qa-automations-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,AutomationDatabase::class.java,filename).build()
        try {
            var repo=RoomAutomationRepository(db);repo.save(rule(),true)
            val session=AutomationSession(UUID.randomUUID(),System.currentTimeMillis())
            assertEquals(1,repo.claimSession(session)!!.size);db.close()
            db=Room.databaseBuilder(context,AutomationDatabase::class.java,filename).build();repo=RoomAutomationRepository(db)
            val comfort=MockAutomationComfortRepository();val temperature=MockAutomationTemperatureRepository().apply {set(0.0)}
            val result=AutomationEngine(repo,temperature,MockAutomationActionExecutor(comfort)).onSessionStarted(session)
            assertTrue(result.repeated);assertEquals(AutomationRunStatus.PENDING,result.runs.single().status)
            assertTrue(comfort.state.value.seats.values.all {it==0})
        } finally {db.close();check(filename.startsWith("qa-automations-"));context.deleteDatabase(filename)}
    }
    @Test fun concurrentClaimsAcrossRepositoriesAreAtomic()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AutomationDatabase::class.java).build()
        try {
            val repo=RoomAutomationRepository(db);repo.save(rule(),true)
            val temperature=MockAutomationTemperatureRepository().apply {set(0.0)};val count=AtomicInteger()
            val executor=object:AutomationActionExecutor {override suspend fun execute(action:AutomationAction):AutomationExecution {
                count.incrementAndGet();delay(30);return AutomationExecution(AutomationRunStatus.SIMULATED,"synthetic")}}
            val trip=AutomationSession(UUID.randomUUID(),System.currentTimeMillis())
            val results=(0..1).map {async(Dispatchers.IO){AutomationEngine(RoomAutomationRepository(db),temperature,executor).onSessionStarted(trip)}}.awaitAll()
            assertEquals(1,count.get());assertEquals(1,results.count {it.repeated});assertEquals(1,repo.runs(trip.id).size)
        } finally {db.close()}
    }
    @Test fun claimSnapshotsRulesBeforeSubsequentEdits()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AutomationDatabase::class.java).build()
        try {
            val repo=RoomAutomationRepository(db);val original=repo.save(rule(),true)
            val trip=AutomationSession(UUID.randomUUID(),System.currentTimeMillis());val snapshot=repo.claimSession(trip)!!.single()
            repo.save(original.copy(action=SeatsAction(driverHeating=SeatLevel.LEVEL_3)),false)
            assertEquals(original,snapshot);assertEquals(SeatLevel.LEVEL_3,(repo.find(original.id)!!.action as SeatsAction).driverHeating)
            assertNull(repo.claimSession(trip))
        } finally {db.close()}
    }
}
