package ru.jeland.os.ui.automation

import android.os.Bundle
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.jeland.os.core.automation.*
import ru.jeland.os.data.AppContainer
import java.util.UUID

class AutomationViewModel(private val dependencies:AppContainer):ViewModel() {
    val trip=dependencies.automationTrip.state
    fun tripDetection(enabled:Boolean){dependencies.automationTrip.setEnabled(enabled)}
    val loading=MutableStateFlow(true);val error=MutableStateFlow<String?>(null)
    val rules=dependencies.automations.rules.onEach {loading.value=false}.catch {loading.value=false;error.value="Не удалось прочитать правила. Данные не удалены."}
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val results=dependencies.automations.latestRuns.catch {error.value="Не удалось прочитать журнал сессий."}
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val temperature=dependencies.automationTemperature.state
    val link=dependencies.car.link
    private val manualBusy=MutableStateFlow(false)
    val busy=combine(manualBusy,dependencies.automationEngine.busy){a,b->a||b}
        .stateIn(viewModelScope,SharingStarted.Eagerly,false)
    val session=MutableStateFlow<AutomationSession?>(null)
    val summary=MutableStateFlow("Подключены реальные команды ГУ. Событие запуска выбирается в каждом сценарии.")
    init {viewModelScope.launch {try {val restored=dependencies.automationSessions.current();if(session.value==null)session.value=restored}
        catch(c:CancellationException){throw c}catch(_:Exception){error.value="Не удалось прочитать текущую тестовую сессию."}}}
    private fun change(block:suspend ()->Unit) {viewModelScope.launch {try {block()}catch(c:CancellationException){throw c}catch(_:Exception){error.value="Изменение не сохранено. Повторите после проверки хранилища."}}}
    fun enabled(id:UUID,value:Boolean)=change {dependencies.automations.setEnabled(id,value)}
    fun delete(id:UUID)=change {dependencies.automations.delete(id)}
    fun runManually(id:UUID) {
        if(manualBusy.value||dependencies.automationEngine.busy.value)return
        manualBusy.value=true;error.value=null
        viewModelScope.launch {
            try {
                val result=dependencies.automationEngine.runManually(id)
                summary.value=result.runs.singleOrNull()?.let {"${it.ruleName}: ${it.status.title}"}?:"Сценарий не найден."
            }catch(c:CancellationException){throw c}
            catch(_:Exception){error.value="Не удалось запустить сценарий. Автоматического повтора нет."}
            finally {manualBusy.value=false}
        }
    }
    class Factory(private val dependencies:AppContainer):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun<T:ViewModel> create(modelClass:Class<T>):T {
            require(modelClass==AutomationViewModel::class.java);return AutomationViewModel(dependencies) as T
        }
    }
}

data class AutomationDraft(
    val id:UUID=UUID.randomUUID(),val name:String="",val trigger:TriggerType=TriggerType.MANUAL,
    val threshold:String="5",val actions:List<AutomationAction> = emptyList(),val priority:Int=0,val enabled:Boolean=true,
    val launchEvent:LaunchEvent=LaunchEvent.ENGINE_START,
) {
    val seats get()=actions.filterIsInstance<SeatAction>().flatMap {it.values()}.fold(SeatsAction()) {payload,(p,v)->payload.with(p,v)}
    val shade get()=actions.filterIsInstance<SunshadeAction>().firstOrNull()?:SunshadeAction.OPEN_HALF
    fun withAction(action:AutomationAction)=copy(actions=if(actions.any {it.component==action.component})
        actions.map {if(it.component==action.component)action else it}else actions+action)
    fun rule()=AutomationRule(id,name,enabled,trigger,
        if(trigger in listOf(TriggerType.MANUAL,TriggerType.SESSION_START))null else threshold.trim().replace(',','.').toDoubleOrNull(),actions,priority,launchEvent=launchEvent)
    fun bundle()=Bundle().apply {
        putString("id",id.toString());putString("name",name);putString("trigger",trigger.name)
        putString("threshold",threshold);putString("shade",shade.name);putInt("priority",priority);putBoolean("enabled",enabled)
        putStringArrayList("devices",ArrayList(actions.map {it.component.name}))
        putString("launchEvent",launchEvent.name)
        SeatParameter.entries.zip(seats.values()).forEach {(p,v)->putString(p.name,v.name)}
    }
    companion object {
        fun from(rule:AutomationRule)=AutomationDraft(rule.id,rule.name,rule.triggerType,
            rule.temperatureThreshold?.toString()?:"5",rule.actions,rule.priority,rule.enabled,rule.launchEvent)
        fun restore(b:Bundle):AutomationDraft {
            var seats=SeatsAction()
            for(p in SeatParameter.entries)seats=seats.with(p,SeatLevel.valueOf(b.getString(p.name)?:SeatLevel.KEEP.name))
            val devices=b.getStringArrayList("devices")?:arrayListOf<String>().apply {b.getString("component")?.let(::add)}
            val trigger=TriggerType.valueOf(b.getString("trigger")?:TriggerType.MANUAL.name)
            return AutomationDraft(UUID.fromString(b.getString("id")),b.getString("name").orEmpty(),trigger,
                b.getString("threshold")?:"5",devices.flatMap {name->when(val type=ComponentType.valueOf(name)) {
                    ComponentType.SEATS->seats.split()
                    ComponentType.DRIVER_SEAT,ComponentType.PASSENGER_SEAT,ComponentType.REAR_LEFT_SEAT,ComponentType.REAR_RIGHT_SEAT->listOf(seats.forDevice(type))
                    ComponentType.SUNSHADE->listOf(SunshadeAction.valueOf(b.getString("shade")?:SunshadeAction.OPEN_HALF.name))
                    ComponentType.STEERING_WHEEL->listOf(SteeringWheelAction.HEAT_ON)
                }},b.getInt("priority"),b.getBoolean("enabled",true),LaunchEvent.valueOf(b.getString("launchEvent")?:LaunchEvent.MOVEMENT.name))
        }
    }
}

data class AutomationEditorState(val draft:AutomationDraft=AutomationDraft(),val loading:Boolean=false,val saving:Boolean=false,
    val errors:List<String> = emptyList(),val saved:Boolean=false,val unavailable:Boolean=false)
class AutomationEditorViewModel(
    private val ruleId:String,private val repository:AutomationRepository,private val handle:SavedStateHandle,
):ViewModel() {
    private val restored=handle.get<Bundle>("automation-draft")?.let {runCatching {AutomationDraft.restore(it)}.getOrNull()}
    private val flow=MutableStateFlow(AutomationEditorState(restored?:AutomationDraft(),loading=ruleId!="new"&&restored==null))
    val state=flow.asStateFlow()
    init {
        if(ruleId!="new"&&restored==null)viewModelScope.launch {
            try {
                val rule=repository.find(UUID.fromString(ruleId))
                if(rule==null)flow.value=flow.value.copy(loading=false,unavailable=true,errors=listOf("Правило уже удалено"))
                else update {AutomationDraft.from(rule)}
            }catch(c:CancellationException){throw c}
            catch(_:Exception){flow.value=flow.value.copy(loading=false,unavailable=true,errors=listOf("Не удалось загрузить правило. Данные не изменены."))}
        }else handle["automation-draft"]=flow.value.draft.bundle()
    }
    private fun update(block:(AutomationDraft)->AutomationDraft) {
        if(flow.value.saving||flow.value.unavailable)return
        val draft=block(flow.value.draft)
        handle["automation-draft"]=draft.bundle();flow.value=flow.value.copy(draft=draft,loading=false,errors=emptyList())
    }
    fun name(value:String)=update {it.copy(name=value.take(50))}
    fun addDevice(value:ComponentType)=update {draft->if(draft.actions.any {it.component==value})draft else draft.withAction(AutomationDevices.defaultAction(value))}
    fun toggleDevice(value:ComponentType)=update {draft->
        if(draft.actions.any {it.component==value})draft.copy(actions=draft.actions.filterNot {it.component==value})
        else draft.withAction(AutomationDevices.defaultAction(value))
    }
    fun removeDevice(value:ComponentType)=update {it.copy(actions=it.actions.filterNot {a->a.component==value})}
    fun trigger(value:TriggerType)=update {it.copy(trigger=value)}
    fun launchEvent(value:LaunchEvent)=update {it.copy(launchEvent=value)}
    fun threshold(value:String)=update {it.copy(threshold=value.take(8))}
    fun priority(delta:Int)=update {it.copy(priority=(it.priority+delta).coerceIn(0,10))}
    fun shade(value:SunshadeAction)=update {it.withAction(value)}
    fun seat(parameter:SeatParameter,value:SeatLevel)=update {draft->
        var next=draft.seats.with(parameter,value)
        if((value.level?:0)>0)when(parameter) {
            SeatParameter.DRIVER_HEATING->next=next.copy(driverVentilation=SeatLevel.OFF)
            SeatParameter.DRIVER_VENTILATION->next=next.copy(driverHeating=SeatLevel.OFF)
            SeatParameter.PASSENGER_HEATING->next=next.copy(passengerVentilation=SeatLevel.OFF)
            SeatParameter.PASSENGER_VENTILATION->next=next.copy(passengerHeating=SeatLevel.OFF)
            else->Unit
        }
        draft.withAction(next.forDevice(AutomationDevices.available.single {parameter in it.seatParameters()}))
    }
    fun save() {
        val s=flow.value;if(s.saving||s.loading||s.unavailable)return
        val rule=s.draft.rule();val errors=AutomationValidator.errors(rule)
        if(errors.isNotEmpty()){flow.value=s.copy(errors=errors);return}
        flow.value=s.copy(saving=true)
        viewModelScope.launch {
            try {repository.save(rule,create=ruleId=="new");flow.value=flow.value.copy(saving=false,saved=true)}
            catch(c:CancellationException){throw c}
            catch(_:Exception){flow.value=flow.value.copy(saving=false,errors=listOf("Не удалось сохранить. Проверьте, что правило не удалено, и повторите."))}
        }
    }
    class Factory(private val id:String,private val repository:AutomationRepository):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun<T:ViewModel> create(modelClass:Class<T>,extras:CreationExtras):T {
            require(modelClass==AutomationEditorViewModel::class.java)
            return AutomationEditorViewModel(id,repository,extras.createSavedStateHandle()) as T
        }
    }
}
