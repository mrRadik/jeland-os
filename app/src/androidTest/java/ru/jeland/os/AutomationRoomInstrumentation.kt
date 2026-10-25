package ru.jeland.os

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
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
    private fun rule(name:String="Зимний комфорт")=AutomationRule(name=name,triggerType=TriggerType.BELOW,temperatureThreshold=5.0,
        actions=SeatsAction(SeatLevel.LEVEL_2,SeatLevel.OFF,SeatLevel.LEVEL_1,SeatLevel.KEEP,SeatLevel.LEVEL_3,SeatLevel.OFF).split())
    @Test fun v1MigrationPreservesDevicesModesHistoryAndSessionPointer()=runBlocking {
        val filename="qa-automations-migrate-${UUID.randomUUID()}.db"
        val seatsId=UUID.randomUUID();val shadeId=UUID.randomUUID();val sessionId=UUID.randomUUID()
        val path=context.getDatabasePath(filename);path.parentFile!!.mkdirs()
        val fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("ru.jeland.os.data.automation.AutomationDatabase/1.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path,null).use {old->
            val entities=fixture.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.optJSONArray("indices")
                if(indices!=null)for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            val setup=fixture.getJSONArray("setupQueries")
            for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO automation_rules (id,name,enabled,componentType,triggerType,temperatureThreshold,priority,createdAt,updatedAt,seats_driverHeating,seats_driverVentilation,seats_passengerHeating,seats_passengerVentilation,seats_rearLeftHeating,seats_rearRightHeating) VALUES (?,?,1,'SEATS','BELOW',5,3,41,42,'LEVEL_2','OFF','KEEP','KEEP','LEVEL_1','KEEP')",arrayOf(seatsId.toString(),"Зима"))
            old.execSQL("INSERT INTO automation_rules (id,name,enabled,componentType,triggerType,priority,createdAt,updatedAt,sunshade) VALUES (?,?,1,'SUNSHADE','SESSION_START',0,43,44,'OPEN_HALF')",arrayOf(shadeId.toString(),"Шторка"))
            old.execSQL("INSERT INTO automation_sessions VALUES (?,40,1)",arrayOf(sessionId.toString()))
            old.execSQL("INSERT INTO automation_runs VALUES (?,?,?,'SIMULATED',?,'legacy command',45)",arrayOf(sessionId.toString(),seatsId.toString(),"Зима","Старый журнал"))
            old.execSQL("INSERT INTO automation_session_pointer VALUES (1,?,40)",arrayOf(sessionId.toString()))
            old.version=1
        }
        val db=Room.databaseBuilder(context,AutomationDatabase::class.java,filename).addMigrations(AutomationDatabase.MIGRATION_1_2,AutomationDatabase.MIGRATION_2_3,AutomationDatabase.MIGRATION_3_4).build()
        try {
            val repo=RoomAutomationRepository(db)
            val migrated=repo.find(seatsId)!!
            assertEquals("Зима",migrated.name);assertEquals(41L,migrated.createdAt);assertEquals(42L,migrated.updatedAt)
            assertEquals(3,migrated.priority);assertEquals(TriggerType.BELOW,migrated.triggerType);assertEquals(5.0,migrated.temperatureThreshold!!,0.0)
            assertEquals(SeatLevel.LEVEL_2,migrated.actions.filterIsInstance<SeatAction>().single {it.component==ComponentType.DRIVER_SEAT}.heating)
            assertEquals(SeatLevel.LEVEL_1,migrated.actions.filterIsInstance<SeatAction>().single {it.component==ComponentType.REAR_LEFT_SEAT}.heating)
            assertEquals(TriggerType.SESSION_START,repo.find(shadeId)!!.triggerType)
            assertEquals(listOf(SunshadeAction.OPEN_HALF),repo.find(shadeId)!!.actions)
            assertEquals("Старый журнал",repo.runs(sessionId).single().message)
            assertEquals(AutomationSession(sessionId,40),repo.current())
            assertNull(repo.claimSession(AutomationSession(sessionId,40)))
            val multi=repo.save(migrated.copy(actions=migrated.actions+SteeringWheelAction.HEAT_ON+SunshadeAction.OPEN_FULL),false)
            assertEquals(multi,repo.find(seatsId));assertEquals(4,db.automationDao().find(seatsId.toString())!!.devices.size)
            repo.delete(seatsId)
            assertEquals(0,db.openHelper.readableDatabase.query("SELECT * FROM automation_devices WHERE ruleId='$seatsId'").use {it.count})
            assertEquals("legacy command",repo.runs(sessionId).single().commandSummary)
            assertNotNull(repo.find(shadeId))
        }finally {db.close();check(filename.startsWith("qa-automations-"));context.deleteDatabase(filename)}
    }
    @Test fun multiDeviceRoundtripAndManualClaimSelectsDisabledRuleOnly()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AutomationDatabase::class.java).build()
        try {
            val repo=RoomAutomationRepository(db)
            val selected=repo.save(rule().copy(enabled=false,actions=rule().actions+SunshadeAction.OPEN_FULL+SteeringWheelAction.HEAT_ON),true)
            repo.save(rule("Другое"),true)
            assertEquals(selected,repo.find(selected.id))
            val manual=AutomationSession(UUID.randomUUID(),1)
            assertEquals(listOf(selected),repo.claimSession(manual,selected.id))
            assertEquals(selected.id,repo.runs(manual.id).single().ruleId)
            assertNull(repo.claimSession(manual,selected.id))
        }finally {db.close()}
    }
    @Test fun launchEventRoundtripAndClaimsAreIndependentAndManualIsNotFiltered()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AutomationDatabase::class.java).build()
        try {
            val repo=RoomAutomationRepository(db)
            val engine=repo.save(rule("Прогрев").copy(launchEvent=LaunchEvent.ENGINE_START),true)
            val motion=repo.save(rule("Движение").copy(launchEvent=LaunchEvent.MOVEMENT),true)
            assertEquals(engine,repo.find(engine.id));assertEquals(motion,repo.find(motion.id))
            val engineSession=AutomationSession(UUID.randomUUID(),1,LaunchEvent.ENGINE_START)
            assertEquals(listOf(engine),repo.claimSession(engineSession))
            assertEquals(listOf(motion),repo.claimSession(AutomationSession(UUID.randomUUID(),2,LaunchEvent.MOVEMENT)))
            repo.setCurrent(engineSession);assertEquals(engineSession,repo.current())
            assertEquals(listOf(engine),repo.claimSession(AutomationSession(UUID.randomUUID(),3),engine.id))
        }finally {db.close()}
    }
    @Test fun v3MigrationDefaultsOldRulesToMovementWithoutChangingHistory()=runBlocking {
        val filename="qa-automations-v3-${UUID.randomUUID()}.db";val id=UUID.randomUUID();val trip=UUID.randomUUID()
        val path=context.getDatabasePath(filename);path.parentFile!!.mkdirs()
        val fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("ru.jeland.os.data.automation.AutomationDatabase/3.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path,null).use {old->
            val entities=fixture.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.optJSONArray("indices")
                if(indices!=null)for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            val setup=fixture.getJSONArray("setupQueries");for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO automation_rules VALUES (?,'Старый сценарий',1,'SESSION_START',NULL,2,41,42)",arrayOf(id.toString()))
            old.execSQL("INSERT INTO automation_devices (ruleId,componentType,position,steering) VALUES (?,'STEERING_WHEEL',0,'HEAT_ON')",arrayOf(id.toString()))
            old.execSQL("INSERT INTO automation_sessions VALUES (?,40,1)",arrayOf(trip.toString()))
            old.execSQL("INSERT INTO automation_runs VALUES (?,?,'Старый сценарий','SIMULATED','История','commands',45)",arrayOf(trip.toString(),id.toString()))
            old.execSQL("INSERT INTO automation_session_pointer VALUES (1,?,40)",arrayOf(trip.toString()));old.version=3
        }
        val db=Room.databaseBuilder(context,AutomationDatabase::class.java,filename).addMigrations(AutomationDatabase.MIGRATION_3_4).build()
        try {
            val repo=RoomAutomationRepository(db);val rule=repo.find(id)!!
            assertEquals(LaunchEvent.MOVEMENT,rule.launchEvent);assertEquals(41L,rule.createdAt);assertEquals(42L,rule.updatedAt)
            assertEquals(listOf(SteeringWheelAction.HEAT_ON),rule.actions);assertEquals("История",repo.runs(trip).single().message)
            assertEquals(AutomationSession(trip,40),repo.current());assertNull(repo.claimSession(AutomationSession(trip,40)))
            assertTrue(repo.claimSession(AutomationSession(UUID.randomUUID(),50,LaunchEvent.ENGINE_START))!!.isEmpty())
            assertEquals(listOf(rule),repo.claimSession(AutomationSession(UUID.randomUUID(),60,LaunchEvent.MOVEMENT)))
        }finally {db.close();check(filename.startsWith("qa-automations-"));context.deleteDatabase(filename)}
    }
    @Test fun v2MigrationSplitsSeatsPreservesOffOrderAndAllRuleMetadata()=runBlocking {
        val filename="qa-automations-v2-${UUID.randomUUID()}.db"
        val id=UUID.randomUUID();val session=UUID.randomUUID()
        val path=context.getDatabasePath(filename);path.parentFile!!.mkdirs()
        val fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("ru.jeland.os.data.automation.AutomationDatabase/2.json").bufferedReader().use {it.readText()}).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path,null).use {old->
            val entities=fixture.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.optJSONArray("indices")
                if(indices!=null)for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            val setup=fixture.getJSONArray("setupQueries")
            for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO automation_rules VALUES (?,'Комфорт',0,'BELOW',5,3,41,42)",arrayOf(id.toString()))
            val target="(ruleId,componentType,position,seats_driverHeating,seats_driverVentilation,seats_passengerHeating,seats_passengerVentilation,seats_rearLeftHeating,seats_rearRightHeating,sunshade,steering)"
            old.execSQL("INSERT INTO automation_devices $target VALUES (?,'SUNSHADE',0,NULL,NULL,NULL,NULL,NULL,NULL,'OPEN_HALF',NULL)",arrayOf(id.toString()))
            old.execSQL("INSERT INTO automation_devices $target VALUES (?,'SEATS',1,'LEVEL_2','OFF','OFF','LEVEL_1','KEEP','OFF',NULL,NULL)",arrayOf(id.toString()))
            old.execSQL("INSERT INTO automation_devices $target VALUES (?,'STEERING_WHEEL',2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'HEAT_ON')",arrayOf(id.toString()))
            old.execSQL("INSERT INTO automation_sessions VALUES (?,40,1)",arrayOf(session.toString()))
            old.execSQL("INSERT INTO automation_runs VALUES (?,?,'Комфорт','SIMULATED','История','legacy',45)",arrayOf(session.toString(),id.toString()))
            old.execSQL("INSERT INTO automation_session_pointer VALUES (1,?,40)",arrayOf(session.toString()))
            old.version=2
        }
        var db=Room.databaseBuilder(context,AutomationDatabase::class.java,filename).addMigrations(AutomationDatabase.MIGRATION_2_3,AutomationDatabase.MIGRATION_3_4).build()
        try {
            var repo=RoomAutomationRepository(db);val migrated=repo.find(id)!!
            assertEquals(listOf(SunshadeAction.OPEN_HALF,SeatAction(ComponentType.DRIVER_SEAT,SeatLevel.LEVEL_2,SeatLevel.OFF),
                SeatAction(ComponentType.PASSENGER_SEAT,SeatLevel.OFF,SeatLevel.LEVEL_1),SeatAction(ComponentType.REAR_RIGHT_SEAT,SeatLevel.OFF),
                SteeringWheelAction.HEAT_ON),migrated.actions)
            assertEquals("Комфорт",migrated.name);assertFalse(migrated.enabled);assertEquals(TriggerType.BELOW,migrated.triggerType)
            assertEquals(5.0,migrated.temperatureThreshold!!,0.0);assertEquals(3,migrated.priority)
            assertEquals(41L,migrated.createdAt);assertEquals(42L,migrated.updatedAt)
            assertTrue(AutomationValidator.errors(migrated).isEmpty());assertEquals("История",repo.runs(session).single().message)
            assertEquals(AutomationSession(session,40),repo.current());assertNull(repo.claimSession(AutomationSession(session,40)))
            db.close();db=Room.databaseBuilder(context,AutomationDatabase::class.java,filename).build();repo=RoomAutomationRepository(db)
            assertEquals(migrated,repo.find(id));assertEquals("legacy",repo.runs(session).single().commandSummary)
        }finally {db.close();check(filename.startsWith("qa-automations-"));context.deleteDatabase(filename)}
    }
    @Test fun typedRoundtripAndChangesAreIsolated()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AutomationDatabase::class.java).build()
        try {
            val repo=RoomAutomationRepository(db,{1000})
            val first=repo.save(rule(),true);val second=repo.save(rule("Другое"),true)
            assertEquals(first,repo.find(first.id));assertEquals(second,repo.find(second.id))
            val changed=repo.save(first.copy(name="Изменено",actions=SeatsAction(rearRightHeating=SeatLevel.LEVEL_1).split()),false)
            assertEquals(1000L,changed.createdAt);assertTrue(changed.updatedAt>second.updatedAt);assertEquals(second,repo.find(second.id))
            repo.setEnabled(first.id,false);assertFalse(repo.find(first.id)!!.enabled);assertEquals(second,repo.find(second.id))
            val shade=repo.save(AutomationRule(name="Шторка",triggerType=TriggerType.SESSION_START,actions=listOf(SunshadeAction.OPEN_HALF)),true)
            assertNull(db.automationDao().find(shade.id.toString())!!.devices.single().seats)
            assertNull(db.automationDao().find(second.id.toString())!!.devices.first().sunshade)
            assertEquals(SunshadeAction.OPEN_HALF,repo.find(shade.id)!!.actions.single())
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
            val executor=object:AutomationActionExecutor {override suspend fun execute(action:List<AutomationAction>):AutomationExecution {
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
            repo.save(original.copy(actions=SeatsAction(driverHeating=SeatLevel.LEVEL_3).split()),false)
            assertEquals(original,snapshot);assertEquals(SeatLevel.LEVEL_3,(repo.find(original.id)!!.actions.single() as SeatAction).heating)
            assertNull(repo.claimSession(trip))
        } finally {db.close()}
    }
}
