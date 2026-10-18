package ru.jeland.os.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.jeland.os.core.*
import ru.jeland.os.data.AppContainer

class MainViewModel(val dependencies: AppContainer) : ViewModel() {
    private val noticeReducer=VehicleNoticeReducer()
    private val noticeFlow=MutableStateFlow(VehicleNoticeState())
    val vehicleNotices=noticeFlow.asStateFlow()
    private val vehicleBundle=combine(dependencies.climate.state,dependencies.seats.state,dependencies.vehicle.state) { c,s,v-> Triple(c,s,v) }
    private val aggregate=combine(vehicleBundle,dependencies.media.state,dependencies.navigation.state,dependencies.weather.state,dependencies.theme.mode) { car,m,n,w,t->
        UiState(climate=car.first,seats=car.second,vehicle=car.third,media=m,navigation=n,weather=w,theme=t)
    }
    val state=combine(aggregate,dependencies.dayNight.isNight,dependencies.apps.state,dependencies.connectivity.state,dependencies.preferences.state) { s,night,apps,connection,prefs->s.copy(night=night,apps=apps,connectivity=connection,preferences=prefs) }
        .combine(dependencies.car.link) {s,link->if(link.demo)s.copy(car=link)else s.copy(car=link,
            weather=if(s.weather.demo)WeatherState(null,"","Штатная погода",demo=false)else s.weather,
            climate=dependencies.car.climate.value,seats=dependencies.car.seats.value,vehicle=dependencies.car.vehicle.value)}
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),UiState(
            climate=dependencies.climate.state.value,seats=dependencies.seats.state.value,vehicle=dependencies.vehicle.state.value,
            media=dependencies.media.state.value,navigation=dependencies.navigation.state.value,weather=dependencies.weather.state.value,
            theme=dependencies.theme.mode.value,night=dependencies.dayNight.isNight.value,
            apps=dependencies.apps.state.value,connectivity=dependencies.connectivity.state.value,preferences=dependencies.preferences.state.value,car=dependencies.car.link.value))
    val appIcons=dependencies.apps.icons
    val message=MutableStateFlow<String?>(null)
    val savingLogs=MutableStateFlow(false)
    val savedLogs=MutableStateFlow<ru.jeland.os.data.SavedDiagnostics?>(null)
    val logsMessage=MutableStateFlow<String?>(null)
    private val foreground=MutableStateFlow(false)
    init {
        viewModelScope.launch {
            dependencies.vehicle.state.map {it.openings to it.mockMoving}.distinctUntilChanged().collectLatest {(openings,moving)->
                // A new event cancels only the wait, not other openings' original deadlines.
                while(true) {
                    val now=android.os.SystemClock.elapsedRealtime()
                    val notices=noticeReducer.update(openings,moving,now)
                    noticeFlow.value=notices
                    val deadline=notices.parked.minOfOrNull {it.expiresAtMillis}?:break
                    delay((deadline-now).coerceAtLeast(1L))
                }
            }
        }
        dependencies.navigation.state.onEach(dependencies.cluster::publish).launchIn(viewModelScope)
        // Detailed SET/GET/readback stays in diagnostics, not in an overlay over the dashboard.
        dependencies.car.link.map {it.userMessage}.distinctUntilChanged().onEach {if(it!=null)message.value=it}.launchIn(viewModelScope)
        viewModelScope.launch { foreground.collectLatest { visible->if(visible)while(true) { delay(1000); dependencies.media.tick() } } }
    }
    fun resume() {if(foreground.value)return;foreground.value=true;dependencies.car.resume();dependencies.connectivity.start();refreshApps()}
    fun pause() {foreground.value=false;dependencies.car.pause();dependencies.connectivity.stop()}
    fun refreshApps() {viewModelScope.launch {dependencies.apps.refresh()}}
    fun launchApp(id:String) {dependencies.apps.launch(id)?.let {message.value=it}}
    fun openVehicleSettings() {if(!dependencies.nativeApps.openSettings())message.value="Штатные настройки недоступны."}
    fun openWeather() {if(!dependencies.nativeApps.openWeather())message.value="Штатная погода недоступна."}
    fun notify(text:String) {message.value=text}
    fun clearMessage() {message.value=null}
    fun saveLogs() {
        if(savingLogs.value)return
        savingLogs.value=true
        viewModelScope.launch {
            try {
                val saved=withContext(Dispatchers.IO){dependencies.car.saveDiagnostics()}
                savedLogs.value=saved;logsMessage.value="Сохранено: ${saved.location} · журналов: ${saved.logCount}"
            }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(error:Exception){logsMessage.value="Не удалось сохранить логи: ${error.message}. Исходные журналы не удалены; можно забрать через START → L."}
            finally {savingLogs.value=false}
        }
    }
    override fun onCleared() {dependencies.car.pause();dependencies.connectivity.stop();super.onCleared()}
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java)); return MainViewModel(container) as T
        }
    }
}
