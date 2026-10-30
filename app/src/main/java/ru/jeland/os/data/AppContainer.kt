package ru.jeland.os.data

import android.content.Context
import ru.jeland.os.core.*
import kotlinx.coroutines.*
import ru.jeland.os.core.automation.*

class AndroidSettingsStore(context: Context) : DurableSettingsStore {
    private val prefs=context.applicationContext.getSharedPreferences("jelandos_mock_only",Context.MODE_PRIVATE)
    override fun string(key: String, default: String)=prefs.getString(key,default) ?: default
    override fun put(key: String, value: String) { prefs.edit().putString(key,value).apply() }
    override fun putDurable(key:String,value:String){check(prefs.edit().putString(key,value).commit()){"Settings marker not persisted"}}
}
/** Constructor DI keeps mock implementations replaceable without a service locator in UI. */
class AppContainer(context: Context,automationDatabaseOverride:ru.jeland.os.data.automation.AutomationDatabase?=null,settingsStoreOverride:SettingsStore?=null) {
    private val store:SettingsStore=settingsStoreOverride?:AndroidSettingsStore(context)
    val firstRun=FirstRunSettings(store)
    private val routing=VehicleRouting(context,store)
    val car=routing.session
    init {if(ru.jeland.os.BuildConfig.LOCAL_DEMO)car.useVehicle(false)}
    val startup=SystemStartup(context,store,car::recordIntegration,wakeControl={WakeStartupService.sync(context,it)})
    val assistantAvatar=OemAssistantAvatar(context,car::recordIntegration)
    val vehicle: VehicleRepository=routing.vehicle
    val climate: ClimateRepository=routing.climate
    val seats: SeatRepository=routing.seats
    val media: MediaRepository=ru.jeland.os.data.media.VehicleMediaRepository(store,car.link,
        ru.jeland.os.data.media.OemPhoneMusicTransport(context,car::recordIntegration),car::recordIntegration)
    val nativeApps=NativeApps(context,car::recordIntegration)
    val cameras=OemCameraLauncher(context,car::recordIntegration,
        {car.link.value.let {!it.demo&&it.connected&&it.writesEnabled}},car::openCameras)
    val weather: WeatherRepository=ExistingWeatherRepository(nativeApps,MockWeatherRepository(store),car.link)
    // A native map session is shared by the full-screen tab and the home widget.
    val navigation=ru.jeland.os.navigation.OfflineNavigationRepository(context)
    val theme: ThemeRepository=PersistentThemeRepository(store)
    val dayNight=AndroidDayNightProvider(context,car.link,car::recordIntegration)
    val cluster: ClusterBridge=MockClusterBridge()
    val apps=AndroidAppsRepository(context,store)
    val connectivity:ConnectivityRepository=AndroidConnectivityRepository(context,car::recordIntegration)
    val preferences:InterfaceRepository=PersistentInterfaceRepository(store)
    val updates=ru.jeland.os.data.updates.AppUpdateManager(context,store,car::recordIntegration)
    private val automationScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val automationJobs=mutableSetOf<Job>()
    val automationDatabase=automationDatabaseOverride?:ru.jeland.os.data.automation.AutomationDatabase.open(context)
    val automations=ru.jeland.os.data.automation.RoomAutomationRepository(automationDatabase)
    val automationTemperature=ru.jeland.os.data.automation.LiveAutomationTemperatureRepository(car.link,automationScope)
    val automationSessions=ru.jeland.os.core.automation.AutomationSessionTracker(automations)
    val automationTrip=ru.jeland.os.data.automation.AndroidAutomationTripMonitor(context,store,car.link,car::recordIntegration,
        ::startAutomationTrip,{automationJobs.toList().forEach {it.cancel()}})
    val automationEngine=ru.jeland.os.core.automation.AutomationEngine(automations,automationTemperature,
        LiveAutomationActionExecutor(car::executeAutomation))
    private fun startAutomationTrip(session:AutomationSession) {
        val job=automationScope.launch(start=CoroutineStart.LAZY) {
            try {
                automations.setCurrent(session)
                val result=automationEngine.onSessionStarted(session)
                car.recordIntegration("AUTOMATION_TRIP_DONE trip=${session.id} repeated=${result.repeated} results=${result.runs.map {it.status}}")
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){car.recordIntegration("AUTOMATION_TRIP_ERROR trip=${session.id} ${e.javaClass.simpleName}; no retry")}
        }
        automationJobs+=job
        job.invokeOnCompletion {automationJobs.remove(job)}
        job.start()
    }
}
