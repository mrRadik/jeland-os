package ru.jeland.os.core

import kotlinx.coroutines.flow.StateFlow

interface VehicleRepository {
    val state: StateFlow<VehicleState>
    fun toggleSteeringHeat()
    fun setShade(value: Shade)
    fun toggleMirrors()
    fun simulateOpening(value: VehicleOpening)
    fun simulateMoving(value: Boolean)
    fun closeAllOpenings()
    fun simulateFuel(value: FuelState)
    fun simulateTire(wheel: Wheel, reading: TireReading)
    fun setTireThresholds(value: TireThresholds)
    fun toggleOption(value: VehicleOption)
}
interface ClimateRepository {
    val state: StateFlow<ClimateState>
    fun adjustTemperature(driver: Boolean, delta: Double)
    fun setTemperature(driver:Boolean,value:Double)
    fun toggleAuto(); fun toggleAc(); fun toggleRecirculation(); fun toggleDefrost(); fun toggleSync()
    fun setFan(value: Int)
    fun setAirflow(value: Airflow)
    fun setRawAirflow(value: Int) {}
    fun togglePower() {}
    fun toggleRearDefrost() {}
    fun toggleWindshieldHeat() {}
}
interface SeatRepository {
    val state: StateFlow<SeatState>
    fun setLevel(side: SeatSide, mode: SeatMode, value: Int)
    fun setRear(value: RearHeat)
    fun setRearLevel(side:RearSeatSide,value:Int)
}
interface MediaRepository {
    val state: StateFlow<MediaState>
    fun togglePlay(); fun next(); fun previous(); fun tick(); fun toggleLike()
    fun setVolume(value: Int); fun selectTrack(index: Int); fun setSource(value: String); fun seek(seconds: Int)
    fun start() {}
    fun stop() {}
}
interface WeatherRepository { val state: StateFlow<WeatherState>; fun useCity(city: String) }
interface NavigationRepository {
    val state: StateFlow<NavigationState>
    fun toggleRoute(); fun setDestination(value: String); fun zoom(delta: Float); fun nextManeuver()
    fun refresh() {}
    fun selectProvider(value:NavigationProvider) {}
    fun openNavigator():String?="Навигатор не подключён."
    fun openPlace(id:String):String?="Место не задано."
    fun savePlace(id:String,title:String,coordinates:String):String?="Место не сохранено."
    fun removePlace(id:String) {}
    fun openNotificationAccess():Boolean=false
}
interface ThemeRepository { val mode: StateFlow<ThemeMode>; fun setMode(value: ThemeMode) }
interface DayNightProvider { val isNight: StateFlow<Boolean> }
interface ClusterBridge { val lastNavigation: StateFlow<NavigationState>; fun publish(state: NavigationState) }
interface AppsRepository {
    val state:StateFlow<AppsState>
    suspend fun refresh()
    fun toggleFavorite(id:String)
    fun moveFavorite(id:String,delta:Int)
    fun launch(id:String):String?
}
interface ConnectivityRepository { val state:StateFlow<ConnectivityState>; fun start();fun stop() }
interface InterfaceRepository {
    val state:StateFlow<InterfacePreferences>
    fun change(value:InterfacePreferences)
}

/** Small persistence port: no Android dependency in models or mock reducers. */
interface SettingsStore {
    fun string(key: String, default: String): String
    fun put(key: String, value: String)
}
/** Small durable safety markers; implementations must throw if persistence fails. */
interface DurableSettingsStore:SettingsStore {fun putDurable(key:String,value:String)}
class MemorySettingsStore : SettingsStore {
    private val values = mutableMapOf<String,String>()
    override fun string(key: String, default: String) = values[key] ?: default
    override fun put(key: String, value: String) { values[key] = value }
}
