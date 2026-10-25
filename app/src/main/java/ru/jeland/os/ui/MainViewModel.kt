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
    val state=combine(aggregate,dependencies.dayNight.isNight,dependencies.apps.state,dependencies.connectivity.state,dependencies.preferences.state) { s,night,apps,connection,prefs->s.copy(night=night,apps=apps,connectivity=RadioReadings.withMedia(connection,s.media),preferences=prefs) }
        .combine(dependencies.car.link) {s,link->if(link.demo)s.copy(car=link)else s.copy(car=link,
            weather=if(s.weather.demo)WeatherState(null,"","Штатная погода",demo=false)else s.weather,
            climate=dependencies.car.climate.value,seats=dependencies.car.seats.value,vehicle=dependencies.car.vehicle.value)}
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),UiState(
            climate=dependencies.climate.state.value,seats=dependencies.seats.state.value,vehicle=dependencies.vehicle.state.value,
            media=dependencies.media.state.value,navigation=dependencies.navigation.state.value,weather=dependencies.weather.state.value,
            theme=dependencies.theme.mode.value,night=dependencies.dayNight.isNight.value,
            apps=dependencies.apps.state.value,connectivity=dependencies.connectivity.state.value,preferences=dependencies.preferences.state.value,car=dependencies.car.link.value))
    val appIcons=dependencies.apps.icons
    val startup=dependencies.startup.state
    val tyreDiagnostics=dependencies.car.tyreDiagnostics
    val message=MutableStateFlow<String?>(null)
    val savingLogs=MutableStateFlow(false)
    val clearingLogs=MutableStateFlow(false)
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
        combine(foreground,dependencies.car.link){visible,link->visible&&!link.demo}.distinctUntilChanged()
            .onEach {if(it)dependencies.assistantAvatar.start()else dependencies.assistantAvatar.stop()}.launchIn(viewModelScope)
    }
    fun resume() {dependencies.startup.refresh();dependencies.updates.resume();if(foreground.value)return;foreground.value=true;dependencies.dayNight.start();dependencies.car.resume();dependencies.automationTrip.resume();dependencies.media.start();dependencies.connectivity.start();dependencies.navigation.resume();refreshApps()}
    fun pause() {foreground.value=false;dependencies.updates.pause();dependencies.automationTrip.pause();dependencies.cameras.cancel();dependencies.assistantAvatar.stop();dependencies.dayNight.stop();dependencies.media.stop();dependencies.car.pause();dependencies.connectivity.stop();dependencies.navigation.pause()}
    fun refreshApps() {viewModelScope.launch {dependencies.apps.refresh()}}
    fun launchApp(id:String) {dependencies.apps.launch(id)?.let {message.value=it}}
    fun openVehicleSettings() {if(!dependencies.nativeApps.openSettings())message.value="Штатные настройки недоступны."}
    fun setSystemStartup(enabled:Boolean) {
        dependencies.startup.setEnabled(enabled)
        if(enabled&&!startup.value.permissionGranted)openStartupPermission()
    }
    fun openStartupPermission() {
        if(startup.value.permissionScreenUnavailable) {
            dependencies.startup.refresh()
            if(!startup.value.permissionGranted)message.value="На компьютере: START.cmd → A. Затем вернитесь в Jeland OS."
        }else if(!dependencies.startup.openPermissionSettings())message.value="На этом ГУ разрешение выдаётся с компьютера: START.cmd → A."
    }
    fun openEngineeringMenu() {if(!dependencies.nativeApps.openEngineering())message.value="Инженерное меню недоступно на этом устройстве."}
    fun openCameras() {
        if(dependencies.car.link.value.demo){message.value="Штатные камеры доступны на ГУ в режиме автомобиля.";return}
        dependencies.cameras.open {message.value=it}
    }
    fun openNavigator() {dependencies.navigation.openNavigator()?.let {message.value=it}}
    fun openNavigationPlace(id:String) {dependencies.navigation.openPlace(id)?.let {message.value=it}}
    fun navigationAccess() {if(!dependencies.navigation.openNotificationAccess())message.value="Настройки доступа к уведомлениям недоступны."}
    fun openWeather() {if(!dependencies.nativeApps.openWeather())message.value="Штатная погода недоступна."}
    fun notify(text:String) {message.value=text}
    fun clearMessage() {message.value=null}
    fun saveLogs() {
        if(savingLogs.value||clearingLogs.value)return
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
    fun clearLogs() {
        if(savingLogs.value||clearingLogs.value)return
        clearingLogs.value=true
        viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO){dependencies.car.clearDiagnostics()}
                savedLogs.value=null
                logsMessage.value="Удалено журналов: ${result.journals}, ZIP: ${result.archives}. ${if(result.activeReset>0)"Текущий журнал начат заново."else"Новые события продолжают записываться."}${if(result.failed>0)" Не удалось очистить: ${result.failed}; часть файлов осталась."else""}"
            }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
            catch(error:Exception){logsMessage.value="Очистка не завершена: ${error.message}. Некоторые журналы могли быть удалены."}
            finally {clearingLogs.value=false}
        }
    }
    override fun onCleared() {dependencies.automationTrip.pause();dependencies.cameras.cancel();dependencies.assistantAvatar.stop();dependencies.media.stop();dependencies.car.pause();dependencies.connectivity.stop();super.onCleared()}
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java)); return MainViewModel(container) as T
        }
    }
}
