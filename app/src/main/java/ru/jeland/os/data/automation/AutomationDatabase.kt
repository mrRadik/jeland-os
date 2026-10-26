package ru.jeland.os.data.automation

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.map
import ru.jeland.os.core.automation.*
import java.util.UUID

data class SeatsActionRecord(
    val driverHeating:SeatLevel,val driverVentilation:SeatLevel,
    val passengerHeating:SeatLevel,val passengerVentilation:SeatLevel,
    val rearLeftHeating:SeatLevel,val rearRightHeating:SeatLevel,
) {
    fun domain()=SeatsAction(driverHeating,driverVentilation,passengerHeating,passengerVentilation,rearLeftHeating,rearRightHeating)
    companion object {fun from(a:SeatsAction)=SeatsActionRecord(a.driverHeating,a.driverVentilation,a.passengerHeating,a.passengerVentilation,a.rearLeftHeating,a.rearRightHeating)}
}
@Entity(tableName="automation_rules")
data class AutomationRuleRecord(
    @PrimaryKey val id:String,val name:String,val enabled:Boolean,val triggerType:TriggerType,
    val temperatureThreshold:Double?,val priority:Int,val createdAt:Long,val updatedAt:Long,
    @ColumnInfo(defaultValue="'MOVEMENT'") val launchEvent:LaunchEvent=LaunchEvent.MOVEMENT,
) {
    companion object {
        fun from(r:AutomationRule)=AutomationRuleRecord(r.id.toString(),r.name,r.enabled,r.triggerType,
            r.temperatureThreshold,r.priority,r.createdAt,r.updatedAt,r.launchEvent)
    }
}
@Entity(tableName="automation_devices",primaryKeys=["ruleId","componentType"],indices=[Index("ruleId")],
    foreignKeys=[ForeignKey(entity=AutomationRuleRecord::class,parentColumns=["id"],childColumns=["ruleId"],onDelete=ForeignKey.CASCADE)])
data class AutomationDeviceRecord(
    val ruleId:String,val componentType:ComponentType,val position:Int,
    @Embedded(prefix="seats_") val seats:SeatsActionRecord?,val sunshade:SunshadeAction?,val steering:SteeringWheelAction?,
) {
    fun domain():AutomationAction=when(componentType) {
        ComponentType.SEATS->error("Требуется миграция группы сидений")
        ComponentType.DRIVER_SEAT,ComponentType.PASSENGER_SEAT,ComponentType.REAR_LEFT_SEAT,ComponentType.REAR_RIGHT_SEAT->{
            check(sunshade==null&&steering==null)
            val payload=checkNotNull(seats).domain()
            check(SeatParameter.entries.filterNot {it in componentType.seatParameters()}.all {payload.values()[it.ordinal]==SeatLevel.KEEP})
            payload.forDevice(componentType)
        }
        ComponentType.SUNSHADE->{check(seats==null&&steering==null);checkNotNull(sunshade)}
        ComponentType.STEERING_WHEEL->{check(seats==null&&sunshade==null);checkNotNull(steering)}
    }
    companion object {
        fun from(id:UUID,position:Int,a:AutomationAction)=AutomationDeviceRecord(id.toString(),a.component,position,
            (a as? SeatAction)?.legacy()?.let(SeatsActionRecord::from),a as? SunshadeAction,a as? SteeringWheelAction)
    }
}
data class AutomationRuleWithDevices(
    @Embedded val rule:AutomationRuleRecord,
    @Relation(parentColumn="id",entityColumn="ruleId") val devices:List<AutomationDeviceRecord>,
) {
    fun domain()=AutomationRule(UUID.fromString(rule.id),rule.name,rule.enabled,rule.triggerType,rule.temperatureThreshold,
        devices.sortedBy {it.position}.map {it.domain()},rule.priority,rule.createdAt,rule.updatedAt,rule.launchEvent)
}
@Entity(tableName="automation_sessions")
data class AutomationSessionRecord(@PrimaryKey val id:String,val startedAt:Long,val finished:Boolean=false)
@Entity(tableName="automation_runs",primaryKeys=["sessionId","ruleId"],indices=[Index("sessionId")])
data class AutomationRunRecord(
    val sessionId:String,val ruleId:String,val ruleName:String,val status:AutomationRunStatus,
    val message:String,val commandSummary:String,val at:Long,
) {
    fun domain()=AutomationRun(UUID.fromString(sessionId),UUID.fromString(ruleId),ruleName,status,message,commandSummary,at)
    companion object {fun from(r:AutomationRun)=AutomationRunRecord(r.sessionId.toString(),r.ruleId.toString(),r.ruleName,r.status,r.message,r.commandSummary,r.at)}
}
@Entity(tableName="automation_session_pointer")
data class AutomationSessionPointer(@PrimaryKey val singleton:Int=1,val sessionId:String,val startedAt:Long,
    @ColumnInfo(defaultValue="'MOVEMENT'") val launchEvent:LaunchEvent=LaunchEvent.MOVEMENT)
class AutomationConverters {
    @TypeConverter fun launch(value:String):LaunchEvent=LaunchEvent.valueOf(value)
    @TypeConverter fun launch(value:LaunchEvent):String=value.name
    @TypeConverter fun component(value:String):ComponentType=ComponentType.valueOf(value)
    @TypeConverter fun component(value:ComponentType):String=value.name
    @TypeConverter fun trigger(value:String):TriggerType=TriggerType.valueOf(value)
    @TypeConverter fun trigger(value:TriggerType):String=value.name
    @TypeConverter fun level(value:String):SeatLevel=SeatLevel.valueOf(value)
    @TypeConverter fun level(value:SeatLevel):String=value.name
    @TypeConverter fun shade(value:String?):SunshadeAction?=value?.let(SunshadeAction::valueOf)
    @TypeConverter fun shade(value:SunshadeAction?):String?=value?.name
    @TypeConverter fun steering(value:String?):SteeringWheelAction?=value?.let(SteeringWheelAction::valueOf)
    @TypeConverter fun steering(value:SteeringWheelAction?):String?=value?.name
    @TypeConverter fun status(value:String):AutomationRunStatus=AutomationRunStatus.valueOf(value)
    @TypeConverter fun status(value:AutomationRunStatus):String=value.name
}
@Dao
interface AutomationDao {
    @Transaction @Query("SELECT * FROM automation_rules ORDER BY priority DESC, createdAt DESC, id") fun observeRules():kotlinx.coroutines.flow.Flow<List<AutomationRuleWithDevices>>
    @Transaction @Query("SELECT * FROM automation_rules WHERE id=:id") suspend fun find(id:String):AutomationRuleWithDevices?
    @Query("SELECT MAX(updatedAt) FROM automation_rules") suspend fun lastUpdated():Long?
    @Transaction @Query("SELECT * FROM automation_rules WHERE enabled=1 AND triggerType IN ('BELOW','ABOVE','SESSION_START') AND launchEvent=:event ORDER BY id") suspend fun active(event:LaunchEvent):List<AutomationRuleWithDevices>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(rule:AutomationRuleRecord)
    @Update suspend fun update(rule:AutomationRuleRecord):Int
    @Query("DELETE FROM automation_rules WHERE id=:id") suspend fun delete(id:String)
    @Query("DELETE FROM automation_devices WHERE ruleId=:id") suspend fun deleteDevices(id:String)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertDevices(devices:List<AutomationDeviceRecord>)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun claim(session:AutomationSessionRecord):Long
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertRuns(runs:List<AutomationRunRecord>)
    @Update suspend fun updateRun(run:AutomationRunRecord):Int
    @Query("SELECT * FROM automation_runs WHERE sessionId=:id ORDER BY at,ruleId") suspend fun runs(id:String):List<AutomationRunRecord>
    @Query("SELECT * FROM automation_runs WHERE sessionId=(SELECT id FROM automation_sessions ORDER BY rowid DESC LIMIT 1) ORDER BY at,ruleId") fun observeLatestRuns():kotlinx.coroutines.flow.Flow<List<AutomationRunRecord>>
    @Query("UPDATE automation_sessions SET finished=1 WHERE id=:id") suspend fun finish(id:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun setPointer(pointer:AutomationSessionPointer)
    @Query("SELECT * FROM automation_session_pointer WHERE singleton=1") suspend fun pointer():AutomationSessionPointer?
}
@Database(entities=[AutomationRuleRecord::class,AutomationDeviceRecord::class,AutomationSessionRecord::class,AutomationRunRecord::class,AutomationSessionPointer::class],version=4,exportSchema=true)
@TypeConverters(AutomationConverters::class)
abstract class AutomationDatabase:RoomDatabase() {
    abstract fun automationDao():AutomationDao
    companion object {
        /** Preserve IDs, launch modes, timestamps, journal and trip pointer. */
        val MIGRATION_1_2=object:Migration(1,2) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE automation_rules RENAME TO automation_rules_v1")
                db.execSQL("CREATE TABLE automation_rules (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, enabled INTEGER NOT NULL, triggerType TEXT NOT NULL, temperatureThreshold REAL, priority INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("INSERT INTO automation_rules SELECT id,name,enabled,triggerType,temperatureThreshold,priority,createdAt,updatedAt FROM automation_rules_v1")
                db.execSQL("CREATE TABLE automation_devices (ruleId TEXT NOT NULL,componentType TEXT NOT NULL,position INTEGER NOT NULL,seats_driverHeating TEXT,seats_driverVentilation TEXT,seats_passengerHeating TEXT,seats_passengerVentilation TEXT,seats_rearLeftHeating TEXT,seats_rearRightHeating TEXT,sunshade TEXT,steering TEXT,PRIMARY KEY(ruleId,componentType),FOREIGN KEY(ruleId) REFERENCES automation_rules(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX index_automation_devices_ruleId ON automation_devices(ruleId)")
                db.execSQL("INSERT INTO automation_devices SELECT id,componentType,0,seats_driverHeating,seats_driverVentilation,seats_passengerHeating,seats_passengerVentilation,seats_rearLeftHeating,seats_rearRightHeating,sunshade,NULL FROM automation_rules_v1")
                db.execSQL("DROP TABLE automation_rules_v1")
            }
        }
        val MIGRATION_2_3=object:Migration(2,3) {
            override fun migrate(db:SupportSQLiteDatabase) { SeatDeviceMigration.statements.forEach(db::execSQL) }
        }
        val MIGRATION_3_4=object:Migration(3,4) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE automation_rules ADD COLUMN launchEvent TEXT NOT NULL DEFAULT 'MOVEMENT'")
                db.execSQL("ALTER TABLE automation_session_pointer ADD COLUMN launchEvent TEXT NOT NULL DEFAULT 'MOVEMENT'")
            }
        }
        fun open(context:Context)=Room.databaseBuilder(context.applicationContext,AutomationDatabase::class.java,"jeland_automations.db")
            .addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
    }
}
class RoomAutomationRepository(private val db:AutomationDatabase,private val clock:()->Long=System::currentTimeMillis):AutomationRepository,AutomationSessionStore {
    private val dao=db.automationDao()
    override val rules=dao.observeRules().map {rows->rows.map {it.domain()}}
    override val latestRuns=dao.observeLatestRuns().map {rows->rows.map {it.domain()}}
    override suspend fun find(id:UUID)=dao.find(id.toString())?.domain()
    override suspend fun save(rule:AutomationRule,create:Boolean):AutomationRule=db.withTransaction {
        val previous=dao.find(rule.id.toString())?.rule
        if(create)check(previous==null){"Правило с таким ID уже существует"}else check(previous!=null){"Правило удалено. Сохранение отменено"}
        val saved=rule.copy(name=rule.name.trim(),createdAt=previous?.createdAt?:clock(),updatedAt=maxOf(clock(),(dao.lastUpdated()?:-1)+1))
        AutomationValidator.requireValid(saved)
        if(create)dao.insert(AutomationRuleRecord.from(saved))else check(dao.update(AutomationRuleRecord.from(saved))==1)
        dao.deleteDevices(saved.id.toString())
        dao.insertDevices(saved.actions.mapIndexed {i,a->AutomationDeviceRecord.from(saved.id,i,a)})
        saved
    }
    override suspend fun setEnabled(id:UUID,enabled:Boolean) {db.withTransaction {
        val old=checkNotNull(dao.find(id.toString())){"Правило удалено"}.domain()
        save(old.copy(enabled=enabled),create=false)
    }}
    override suspend fun delete(id:UUID)=dao.delete(id.toString())
    override suspend fun claimSession(session:AutomationSession,manualRuleId:UUID?):List<AutomationRule>?=db.withTransaction {
        // Deletion racing a manual click is an explicit error, never an empty successful run.
        val selected=manualRuleId?.let {checkNotNull(dao.find(it.toString())){"Сценарий удалён"}.domain()}
        if(dao.claim(AutomationSessionRecord(session.id.toString(),session.startedAt))==-1L)return@withTransaction null
        val rules=if(selected!=null)listOf(selected)else dao.active(session.launchEvent).map {it.domain()}
        dao.insertRuns(rules.map {AutomationRunRecord(session.id.toString(),it.id.toString(),it.name,AutomationRunStatus.PENDING,
            "Записано до выполнения. При прерывании не переигрывается автоматически.","",session.startedAt)})
        rules
    }
    override suspend fun record(run:AutomationRun){check(dao.updateRun(AutomationRunRecord.from(run))==1){"Session must be claimed before execution"}}
    override suspend fun runs(session:UUID)=dao.runs(session.toString()).map {it.domain()}
    override suspend fun finish(session:UUID)=dao.finish(session.toString())
    override suspend fun current()=dao.pointer()?.let {AutomationSession(UUID.fromString(it.sessionId),it.startedAt,it.launchEvent)}
    override suspend fun setCurrent(session:AutomationSession)=dao.setPointer(AutomationSessionPointer(sessionId=session.id.toString(),startedAt=session.startedAt,launchEvent=session.launchEvent))
}
