package ru.jeland.os.ui.automation

import android.os.Bundle
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.jeland.os.BuildConfig
import ru.jeland.os.core.automation.*
import ru.jeland.os.data.AppContainer
import java.util.UUID

class AutomationViewModel(private val dependencies:AppContainer):ViewModel() {
    val loading=MutableStateFlow(true);val error=MutableStateFlow<String?>(null)
    val rules=dependencies.automations.rules.onEach {loading.value=false}.catch {loading.value=false;error.value="Не удалось прочитать правила. Данные не удалены."}
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val results=dependencies.automations.latestRuns.catch {error.value="Не удалось прочитать журнал сессий."}
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val temperature=dependencies.automationTemperature.state;val comfort=dependencies.automationComfort.state
    val busy=MutableStateFlow(false);val session=MutableStateFlow<AutomationSession?>(null)
    val summary=MutableStateFlow("Запуск только вручную в MOCK. Автомобильные команды не отправляются.")
    init {viewModelScope.launch {try {val restored=dependencies.automationSessions.current();if(session.value==null)session.value=restored}
        catch(c:CancellationException){throw c}catch(_:Exception){error.value="Не удалось прочитать текущую тестовую сессию."}}}
    private fun change(block:suspend ()->Unit) {viewModelScope.launch {try {block()}catch(c:CancellationException){throw c}catch(_:Exception){error.value="Изменение не сохранено. Повторите после проверки хранилища."}}}
    fun enabled(id:UUID,value:Boolean)=change {dependencies.automations.setEnabled(id,value)}
    fun delete(id:UUID)=change {dependencies.automations.delete(id)}
    fun setTemperature(text:String) {
        if(!BuildConfig.DEBUG||busy.value)return
        val value=text.trim().replace(',','.').toDoubleOrNull()
        if(value==null||!value.isFinite()||value !in -60.0..60.0){error.value="Тестовая температура: от −60 до +60 °C";return}
        dependencies.automationTemperature.set(value);error.value=null
    }
    fun noTemperature(){if(BuildConfig.DEBUG&&!busy.value)dependencies.automationTemperature.set(null)}
    fun staleTemperature(){if(BuildConfig.DEBUG&&!busy.value)dependencies.automationTemperature.stale()}
    fun toggleConnection(){if(BuildConfig.DEBUG&&!busy.value)dependencies.automationComfort.change {it.copy(connected=!it.connected)}}
    fun toggleKnown(){if(BuildConfig.DEBUG&&!busy.value)dependencies.automationComfort.change {it.copy(known=!it.known)}}
    fun toggleParked(){if(BuildConfig.DEBUG&&!busy.value)dependencies.automationComfort.change {it.copy(parked=!it.parked)}}
    fun manualDriverHeat(){if(BuildConfig.DEBUG&&!busy.value)dependencies.automationComfort.change {it.copy(seats=it.seats+
        (SeatParameter.DRIVER_HEATING to ((it.seats.getValue(SeatParameter.DRIVER_HEATING)+1)%4))+(SeatParameter.DRIVER_VENTILATION to 0))}}
    fun simulate(new:Boolean) {
        if(!BuildConfig.DEBUG||busy.value)return
        busy.value=true;error.value=null
        viewModelScope.launch {
            try {
                val next=if(new)dependencies.automationSessions.newSession()else dependencies.automationSessions.current()
                if(next==null){summary.value="Сначала начните новую тестовую сессию.";return@launch}
                session.value=next
                val result=dependencies.automationEngine.onSessionStarted(next)
                summary.value=if(result.repeated)"Эта сессия уже проверена. Повторных команд нет."
                    else if(result.runs.isEmpty())"Сессия начата. Активных правил нет."
                    else "Сессия проверена · ${result.runs.size} правил. Результат только в MOCK."
            }catch(c:CancellationException){throw c}
            catch(_:Exception){error.value="Проверка сессии прервана. Автоматического повтора нет; сохранённый журнал оставлен."}
            finally {busy.value=false}
        }
    }
    class Factory(private val dependencies:AppContainer):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun<T:ViewModel> create(modelClass:Class<T>):T {
            require(modelClass==AutomationViewModel::class.java);return AutomationViewModel(dependencies) as T
        }
    }
}

data class AutomationDraft(
    val id:UUID=UUID.randomUUID(),val name:String="",val component:ComponentType=ComponentType.SEATS,
    val trigger:TriggerType=TriggerType.BELOW,val threshold:String="5",val seats:SeatsAction=SeatsAction(),
    val shade:SunshadeAction=SunshadeAction.OPEN_HALF,val priority:Int=0,val enabled:Boolean=true,
) {
    fun rule()=AutomationRule(id,name,enabled,component,if(component==ComponentType.SEATS)trigger else TriggerType.SESSION_START,
        if(component==ComponentType.SEATS)threshold.trim().replace(',','.').toDoubleOrNull()else null,
        if(component==ComponentType.SEATS)seats else shade,priority)
    fun bundle()=Bundle().apply {
        putString("id",id.toString());putString("name",name);putString("component",component.name);putString("trigger",trigger.name)
        putString("threshold",threshold);putString("shade",shade.name);putInt("priority",priority);putBoolean("enabled",enabled)
        SeatParameter.entries.zip(seats.values()).forEach {(p,v)->putString(p.name,v.name)}
    }
    companion object {
        fun from(rule:AutomationRule)=AutomationDraft(rule.id,rule.name,rule.componentType,
            if(rule.triggerType==TriggerType.SESSION_START)TriggerType.BELOW else rule.triggerType,
            rule.temperatureThreshold?.toString()?:"5",rule.action as? SeatsAction?:SeatsAction(),
            rule.action as? SunshadeAction?:SunshadeAction.OPEN_HALF,rule.priority,rule.enabled)
        fun restore(b:Bundle):AutomationDraft {
            var seats=SeatsAction();for(p in SeatParameter.entries)seats=seats.with(p,SeatLevel.valueOf(checkNotNull(b.getString(p.name))))
            return AutomationDraft(UUID.fromString(b.getString("id")),b.getString("name").orEmpty(),ComponentType.valueOf(checkNotNull(b.getString("component"))),
                TriggerType.valueOf(checkNotNull(b.getString("trigger"))),b.getString("threshold")?:"5",seats,
                SunshadeAction.valueOf(checkNotNull(b.getString("shade"))),b.getInt("priority"),b.getBoolean("enabled"))
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
    fun component(value:ComponentType)=update {it.copy(component=value)}
    fun trigger(value:TriggerType)=update {it.copy(trigger=value)}
    fun threshold(value:String)=update {it.copy(threshold=value.take(8))}
    fun priority(delta:Int)=update {it.copy(priority=(it.priority+delta).coerceIn(0,10))}
    fun shade(value:SunshadeAction)=update {it.copy(shade=value)}
    fun seat(parameter:SeatParameter,value:SeatLevel)=update {draft->
        var next=draft.seats.with(parameter,value)
        if((value.level?:0)>0)when(parameter) {
            SeatParameter.DRIVER_HEATING->next=next.copy(driverVentilation=SeatLevel.OFF)
            SeatParameter.DRIVER_VENTILATION->next=next.copy(driverHeating=SeatLevel.OFF)
            SeatParameter.PASSENGER_HEATING->next=next.copy(passengerVentilation=SeatLevel.OFF)
            SeatParameter.PASSENGER_VENTILATION->next=next.copy(passengerHeating=SeatLevel.OFF)
            else->Unit
        }
        draft.copy(seats=next)
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
