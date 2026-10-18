package ru.jeland.os.data

import android.content.Context
import ru.jeland.os.core.*

class AndroidSettingsStore(context: Context) : SettingsStore {
    private val prefs=context.applicationContext.getSharedPreferences("jelandos_mock_only",Context.MODE_PRIVATE)
    override fun string(key: String, default: String)=prefs.getString(key,default) ?: default
    override fun put(key: String, value: String) { prefs.edit().putString(key,value).apply() }
}
/** Constructor DI keeps mock implementations replaceable without a service locator in UI. */
class AppContainer(context: Context,automationDatabaseOverride:ru.jeland.os.data.automation.AutomationDatabase?=null,settingsStoreOverride:SettingsStore?=null) {
    private val store:SettingsStore=settingsStoreOverride?:AndroidSettingsStore(context)
    private val routing=VehicleRouting(context,store)
    val car=routing.session
    val vehicle: VehicleRepository=routing.vehicle
    val climate: ClimateRepository=routing.climate
    val seats: SeatRepository=routing.seats
    val media: MediaRepository=MockMediaRepository(store)
    val nativeApps=NativeApps(context,car::recordIntegration)
    val weather: WeatherRepository=ExistingWeatherRepository(nativeApps,MockWeatherRepository(store),car.link)
    val navigation: NavigationRepository=MockNavigationRepository(store)
    val theme: ThemeRepository=MockThemeRepository(store)
    val dayNight=MockDayNightProvider(store)
    val cluster: ClusterBridge=MockClusterBridge()
    val apps=AndroidAppsRepository(context,store)
    val connectivity:ConnectivityRepository=AndroidConnectivityRepository(context,car::recordIntegration)
    val preferences:InterfaceRepository=PersistentInterfaceRepository(store)
    // Independent MOCK graph: no VehicleRouting/DesayCarSession executor can enter automations.
    val automationDatabase=automationDatabaseOverride?:ru.jeland.os.data.automation.AutomationDatabase.open(context)
    val automations=ru.jeland.os.data.automation.RoomAutomationRepository(automationDatabase)
    val automationTemperature=ru.jeland.os.data.automation.MockAutomationTemperatureRepository()
    val automationComfort=ru.jeland.os.data.automation.MockAutomationComfortRepository()
    val automationSessions=ru.jeland.os.core.automation.AutomationSessionTracker(automations)
    val automationEngine=ru.jeland.os.core.automation.AutomationEngine(automations,automationTemperature,
        ru.jeland.os.data.automation.MockAutomationActionExecutor(automationComfort))
}
