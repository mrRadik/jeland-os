package ru.jeland.os.data.automation

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.jeland.os.core.automation.*
import java.util.UUID

/** Typed columns, not an executable/unvalidated JSON blob. Add payloads with explicit migrations. */
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
    @PrimaryKey val id:String,val name:String,val enabled:Boolean,val componentType:ComponentType,
    val triggerType:TriggerType,val temperatureThreshold:Double?,val priority:Int,val createdAt:Long,val updatedAt:Long,
    @Embedded(prefix="seats_") val seats:SeatsActionRecord?,val sunshade:SunshadeAction?,
) {
    fun domain():AutomationRule {
        check((componentType==ComponentType.SEATS&&seats!=null&&sunshade==null)||
            (componentType==ComponentType.SUNSHADE&&seats==null&&sunshade!=null)){"Invalid typed action payload"}
        return AutomationRule(UUID.fromString(id),name,enabled,componentType,triggerType,temperatureThreshold,
            seats?.domain()?:checkNotNull(sunshade),priority,createdAt,updatedAt)
    }
    companion object {
        fun from(r:AutomationRule)=AutomationRuleRecord(r.id.toString(),r.name,r.enabled,r.componentType,r.triggerType,r.temperatureThreshold,
            r.priority,r.createdAt,r.updatedAt,(r.action as? SeatsAction)?.let(SeatsActionRecord::from),r.action as? SunshadeAction)
    }
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
data class AutomationSessionPointer(@PrimaryKey val singleton:Int=1,val sessionId:String,val startedAt:Long)
class AutomationConverters {
    @TypeConverter fun component(value:String):ComponentType=ComponentType.valueOf(value)
    @TypeConverter fun component(value:ComponentType):String=value.name
    @TypeConverter fun trigger(value:String):TriggerType=TriggerType.valueOf(value)
    @TypeConverter fun trigger(value:TriggerType):String=value.name
    @TypeConverter fun level(value:String):SeatLevel=SeatLevel.valueOf(value)
    @TypeConverter fun level(value:SeatLevel):String=value.name
    @TypeConverter fun shade(value:String?):SunshadeAction?=value?.let(SunshadeAction::valueOf)
    @TypeConverter fun shade(value:SunshadeAction?):String?=value?.name
    @TypeConverter fun status(value:String):AutomationRunStatus=AutomationRunStatus.valueOf(value)
    @TypeConverter fun status(value:AutomationRunStatus):String=value.name
}
@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation_rules ORDER BY priority DESC, updatedAt DESC, id") fun observeRules():Flow<List<AutomationRuleRecord>>
    @Query("SELECT * FROM automation_rules WHERE id=:id") suspend fun find(id:String):AutomationRuleRecord?
    @Query("SELECT MAX(updatedAt) FROM automation_rules") suspend fun lastUpdated():Long?
    @Query("SELECT * FROM automation_rules WHERE enabled=1 ORDER BY id") suspend fun active():List<AutomationRuleRecord>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(rule:AutomationRuleRecord)
    @Update suspend fun update(rule:AutomationRuleRecord):Int
    @Query("DELETE FROM automation_rules WHERE id=:id") suspend fun delete(id:String)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun claim(session:AutomationSessionRecord):Long
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertRuns(runs:List<AutomationRunRecord>)
    @Update suspend fun updateRun(run:AutomationRunRecord):Int
    @Query("SELECT * FROM automation_runs WHERE sessionId=:id ORDER BY at,ruleId") suspend fun runs(id:String):List<AutomationRunRecord>
    @Query("SELECT * FROM automation_runs WHERE sessionId=(SELECT id FROM automation_sessions ORDER BY rowid DESC LIMIT 1) ORDER BY at,ruleId") fun observeLatestRuns():Flow<List<AutomationRunRecord>>
    @Query("UPDATE automation_sessions SET finished=1 WHERE id=:id") suspend fun finish(id:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun setPointer(pointer:AutomationSessionPointer)
    @Query("SELECT * FROM automation_session_pointer WHERE singleton=1") suspend fun pointer():AutomationSessionPointer?
}
@Database(entities=[AutomationRuleRecord::class,AutomationSessionRecord::class,AutomationRunRecord::class,AutomationSessionPointer::class],version=1,exportSchema=true)
@TypeConverters(AutomationConverters::class)
abstract class AutomationDatabase:RoomDatabase() {
    abstract fun automationDao():AutomationDao
    companion object {
        fun open(context:Context)=Room.databaseBuilder(context.applicationContext,AutomationDatabase::class.java,"jeland_automations.db").build()
    }
}
class RoomAutomationRepository(private val db:AutomationDatabase,private val clock:()->Long=System::currentTimeMillis):AutomationRepository,AutomationSessionStore {
    private val dao=db.automationDao()
    override val rules=dao.observeRules().map {rows->rows.map {it.domain()}}
    override val latestRuns=dao.observeLatestRuns().map {rows->rows.map {it.domain()}}
    override suspend fun find(id:UUID)=dao.find(id.toString())?.domain()
    override suspend fun save(rule:AutomationRule,create:Boolean):AutomationRule=db.withTransaction {
        val previous=dao.find(rule.id.toString())
        if(create)check(previous==null){"Правило с таким ID уже существует"}else check(previous!=null){"Правило удалено. Сохранение отменено"}
        val saved=rule.copy(name=rule.name.trim(),createdAt=previous?.createdAt?:clock(),updatedAt=maxOf(clock(),(dao.lastUpdated()?:-1)+1))
        AutomationValidator.requireValid(saved)
        if(create)dao.insert(AutomationRuleRecord.from(saved))else check(dao.update(AutomationRuleRecord.from(saved))==1)
        saved
    }
    override suspend fun setEnabled(id:UUID,enabled:Boolean) {db.withTransaction {
        val old=checkNotNull(dao.find(id.toString())){"Правило удалено"}.domain()
        save(old.copy(enabled=enabled),create=false)
    }}
    override suspend fun delete(id:UUID)=dao.delete(id.toString())
    override suspend fun claimSession(session:AutomationSession):List<AutomationRule>?=db.withTransaction {
        if(dao.claim(AutomationSessionRecord(session.id.toString(),session.startedAt))==-1L)return@withTransaction null
        val rules=dao.active().map {it.domain()}
        dao.insertRuns(rules.map {AutomationRunRecord(session.id.toString(),it.id.toString(),it.name,AutomationRunStatus.PENDING,
            "Записано до выполнения. При прерывании не переигрывается автоматически.","",session.startedAt)})
        rules
    }
    override suspend fun record(run:AutomationRun){check(dao.updateRun(AutomationRunRecord.from(run))==1){"Session must be claimed before execution"}}
    override suspend fun runs(session:UUID)=dao.runs(session.toString()).map {it.domain()}
    override suspend fun finish(session:UUID)=dao.finish(session.toString())
    override suspend fun current()=dao.pointer()?.let {AutomationSession(UUID.fromString(it.sessionId),it.startedAt)}
    override suspend fun setCurrent(session:AutomationSession)=dao.setPointer(AutomationSessionPointer(sessionId=session.id.toString(),startedAt=session.startedAt))
}
