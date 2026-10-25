package ru.jeland.os.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.jeland.os.core.*
import kotlin.math.round

private fun SettingsStore.int(key: String, default: Int) = string(key, "$default").toIntOrNull() ?: default
private fun SettingsStore.bool(key: String, default: Boolean) = string(key, "$default").toBooleanStrictOrNull() ?: default
private inline fun <reified T : Enum<T>> SettingsStore.enum(key: String, default: T): T = enumValues<T>().firstOrNull { it.name == string(key, default.name) } ?: default

class MockClimateRepository(private val store: SettingsStore) : ClimateRepository {
    private fun temp(key: String) = (store.string(key,"22.0").toDoubleOrNull()?.takeIf { it.isFinite() } ?: 22.0).coerceIn(16.0,32.0)
    private val flow = MutableStateFlow(ClimateState(driver=temp("climate.driver"), passenger=temp("climate.passenger"),
        fan=store.int("climate.fan",3).coerceIn(0,VerifiedVehicleProtocol.FAN_MAX), auto=store.bool("climate.auto",true), ac=store.bool("climate.ac",true),
        recirculation=store.bool("climate.recirculation",false), defrost=store.bool("climate.defrost",false),
        sync=store.bool("climate.sync",false), airflow=store.enum("climate.airflow",Airflow.MIXED),
        power=store.bool("climate.power",true),rearDefrost=store.bool("climate.rearDefrost",false),windshieldHeat=store.bool("climate.windshieldHeat",false)))
    override val state = flow.asStateFlow()
    private fun change(requiresPower:Boolean=true,block: (ClimateState)->ClimateState) {
        if(requiresPower && !flow.value.controlsEnabled)return
        flow.update(block); val s=flow.value
        mapOf("driver" to s.driver,"passenger" to s.passenger,"fan" to s.fan,"auto" to s.auto,"ac" to s.ac,
            "recirculation" to s.recirculation,"defrost" to s.defrost,"sync" to s.sync,"airflow" to s.airflow.name,
            "power" to s.power,"rearDefrost" to s.rearDefrost,"windshieldHeat" to s.windshieldHeat)
            .forEach { (k,v)->store.put("climate.$k",v.toString()) }
    }
    override fun adjustTemperature(driver: Boolean, delta: Double) = change { s ->
        val current=if(driver) s.driver else s.passenger
        val next=(round((current+delta)*2)/2).coerceIn(16.0,32.0)
        if(s.sync) s.copy(driver=next,passenger=next) else if(driver) s.copy(driver=next) else s.copy(passenger=next)
    }
    override fun setTemperature(driver:Boolean,value:Double) {
        if(!value.isFinite()||value !in 16.0..32.0)return
        val next=round(value*2)/2
        change {s->if(s.sync)s.copy(driver=next,passenger=next)else if(driver)s.copy(driver=next)else s.copy(passenger=next)}
    }
    override fun toggleAuto()=change { it.copy(auto=!it.auto) }
    override fun toggleAc()=change { it.copy(ac=!it.ac) }
    override fun toggleRecirculation()=change { it.copy(recirculation=!it.recirculation) }
    override fun toggleDefrost()=change { it.copy(defrost=!it.defrost) }
    override fun toggleSync()=change { it.copy(sync=!it.sync,passenger=if(!it.sync) it.driver else it.passenger) }
    override fun setFan(value: Int)=change { it.copy(fan=value.coerceIn(0,VerifiedVehicleProtocol.FAN_MAX),auto=false) }
    override fun setAirflow(value: Airflow)=change { it.copy(airflow=value) }
    override fun setRawAirflow(value:Int){Airflow.fromCommand(value)?.let(::setAirflow)}
    override fun togglePower()=change(requiresPower=false) {it.copy(power=!it.power)}
    override fun toggleRearDefrost()=change {it.copy(rearDefrost=!it.rearDefrost)}
    override fun toggleWindshieldHeat()=change {it.copy(windshieldHeat=!it.windshieldHeat)}
}
class MockSeatRepository(private val store: SettingsStore) : SeatRepository {
    private fun channel(name: String)=SeatChannel(store.int("seat.$name.heat",0).coerceIn(0,3),store.int("seat.$name.vent",0).coerceIn(0,3))
    private val legacyRear=store.enum("seat.rear",RearHeat.OFF)
    private val initialLeft=store.int("seat.rear.left.heat",if(legacyRear in listOf(RearHeat.LEFT,RearHeat.BOTH))1 else 0).coerceIn(0,3)
    private val initialRight=store.int("seat.rear.right.heat",if(legacyRear in listOf(RearHeat.RIGHT,RearHeat.BOTH))1 else 0).coerceIn(0,3)
    private val flow=MutableStateFlow(SeatState(channel("driver"),channel("passenger"),RearHeat.fromLevels(initialLeft,initialRight),rearLeft=initialLeft,rearRight=initialRight))
    override val state=flow.asStateFlow()
    override fun setLevel(side: SeatSide, mode: SeatMode, value: Int) {
        val level=value.coerceIn(0,3)
        flow.update { s ->
            val old=s.channel(side)
            // Demo policy only: heat/vent are mutually exclusive. Not an OEM claim.
            val new=if(mode==SeatMode.HEAT) old.copy(heat=level,ventilation=if(level>0) 0 else old.ventilation)
                else old.copy(ventilation=level,heat=if(level>0) 0 else old.heat)
            if(side==SeatSide.DRIVER) s.copy(driver=new) else s.copy(passenger=new)
        }
        persist()
    }
    override fun setRear(value: RearHeat) {
        flow.update {
            val left=if(value in listOf(RearHeat.LEFT,RearHeat.BOTH))(it.rearLeft?:0).coerceAtLeast(1)else 0
            val right=if(value in listOf(RearHeat.RIGHT,RearHeat.BOTH))(it.rearRight?:0).coerceAtLeast(1)else 0
            it.copy(rear=value,rearLeft=left,rearRight=right)
        };persist()
    }
    override fun setRearLevel(side:RearSeatSide,value:Int) {
        flow.update {
            val left=if(side==RearSeatSide.LEFT)value.coerceIn(0,3)else it.rearLeft?:0
            val right=if(side==RearSeatSide.RIGHT)value.coerceIn(0,3)else it.rearRight?:0
            it.copy(rear=RearHeat.fromLevels(left,right),rearLeft=left,rearRight=right)
        };persist()
    }
    private fun persist() {
        val s=flow.value
        store.put("seat.driver.heat","${s.driver.heat}"); store.put("seat.driver.vent","${s.driver.ventilation}")
        store.put("seat.passenger.heat","${s.passenger.heat}"); store.put("seat.passenger.vent","${s.passenger.ventilation}")
        store.put("seat.rear",s.rear.name)
        store.put("seat.rear.left.heat","${s.rearLeft?:0}");store.put("seat.rear.right.heat","${s.rearRight?:0}")
    }
}
class MockVehicleRepository(private val store: SettingsStore) : VehicleRepository {
    private val flow=MutableStateFlow(VehicleState(battery=BatteryReading.DEMO,steeringHeat=store.bool("vehicle.steering",false),shade=store.enum("vehicle.shade",Shade.CLOSED),mirrorsFolded=store.bool("vehicle.mirrors",false),
        tireThresholds=runCatching {TireThresholds(store.string("tires.criticalLow","1.7").toDouble(),store.string("tires.warningLow","2.1").toDouble(),store.string("tires.warningHigh","2.9").toDouble(),store.string("tires.criticalHigh","3.3").toDouble())}.getOrDefault(TireThresholds()),
        options=VehicleOption.entries.associateWith {store.bool("option.${it.name}",false)}))
    override val state=flow.asStateFlow()
    override fun toggleSteeringHeat() { flow.update { it.copy(steeringHeat=!it.steeringHeat) }; store.put("vehicle.steering","${flow.value.steeringHeat}") }
    override fun setShade(value: Shade) { flow.update { it.copy(shade=value) }; store.put("vehicle.shade",value.name) }
    override fun toggleMirrors() { flow.update { it.copy(mirrorsFolded=!it.mirrorsFolded) }; store.put("vehicle.mirrors","${flow.value.mirrorsFolded}") }
    override fun simulateOpening(value: VehicleOpening) { flow.update { it.copy(openings=if(value in it.openings) it.openings-value else it.openings+value) } }
    override fun simulateMoving(value: Boolean) { flow.update { it.copy(mockMoving=value) } }
    override fun closeAllOpenings() {flow.update {it.copy(openings=emptySet())}}
    override fun simulateFuel(value:FuelState) {flow.update {it.copy(fuel=value)}}
    override fun simulateTire(wheel:Wheel,reading:TireReading) {flow.update {it.copy(tires=it.tires+(wheel to reading))}}
    override fun setTireThresholds(value:TireThresholds) {
        flow.update {it.copy(tireThresholds=value)}
        mapOf("criticalLow" to value.criticalLow,"warningLow" to value.warningLow,"warningHigh" to value.warningHigh,"criticalHigh" to value.criticalHigh).forEach {(k,v)->store.put("tires.$k",v.toString())}
    }
    override fun toggleOption(value:VehicleOption) {flow.update {it.copy(options=it.options+(value to !(it.options[value]?:false)))};store.put("option.${value.name}","${flow.value.options[value]}")}
}
class MockMediaRepository(private val store: SettingsStore) : MediaRepository {
    private val flow=MutableStateFlow(MediaState(index=store.int("media.track",0).coerceIn(demoTracks.indices),volume=store.int("media.volume",45).coerceIn(0,100),source=store.string("media.source","Bluetooth"),liked=store.bool("media.liked",false)))
    override val state=flow.asStateFlow()
    override fun togglePlay() { flow.update { it.copy(playing=!it.playing) } }
    override fun next()=selectTrack((flow.value.index+1)%demoTracks.size)
    override fun previous()=selectTrack((flow.value.index+demoTracks.size-1)%demoTracks.size)
    override fun selectTrack(index: Int) { flow.update { it.copy(index=index.coerceIn(demoTracks.indices),position=0) }; store.put("media.track","${flow.value.index}") }
    override fun tick() { if(flow.value.playing) { if(flow.value.position>=flow.value.track.duration) next() else flow.update { it.copy(position=it.position+1) } } }
    override fun setVolume(value: Int) { flow.update { it.copy(volume=value.coerceIn(0,100)) }; store.put("media.volume","${flow.value.volume}") }
    override fun setSource(value: String) { flow.update { it.copy(source=value) }; store.put("media.source",value) }
    override fun toggleLike() { flow.update { it.copy(liked=!it.liked) };store.put("media.liked","${flow.value.liked}") }
    override fun seek(seconds: Int) { flow.update { it.copy(position=seconds.coerceIn(0,it.track.duration)) } }
}
class MockWeatherRepository(private val store: SettingsStore) : WeatherRepository {
    private val flow=MutableStateFlow(WeatherState(city=store.string("weather.city","Челябинск")))
    override val state=flow.asStateFlow()
    override fun useCity(city: String) { flow.update { it.copy(city=city) }; store.put("weather.city",city) }
}
class MockNavigationRepository(private val store: SettingsStore) : NavigationRepository {
    private val flow=MutableStateFlow(NavigationState(destination=store.string("navigation.destination","На работу")))
    override val state=flow.asStateFlow()
    override fun toggleRoute() { flow.update { it.copy(active=!it.active) } }
    override fun setDestination(value: String) { flow.update { it.copy(destination=value,active=true) }; store.put("navigation.destination",value) }
    override fun zoom(delta: Float) { flow.update { it.copy(zoom=(it.zoom+delta).coerceIn(0.7f,2.5f)) } }
    override fun nextManeuver() { flow.update { it.copy(maneuver=Maneuver.entries[(it.maneuver.ordinal+1)%Maneuver.entries.size]) } }
}
class MockThemeRepository(private val store: SettingsStore) : ThemeRepository {
    private val flow=MutableStateFlow(store.enum("theme.mode",ThemeMode.DARK)); override val mode=flow.asStateFlow()
    override fun setMode(value: ThemeMode) { flow.value=value; store.put("theme.mode",value.name) }
}
class MockDayNightProvider(private val store: SettingsStore) : DayNightProvider {
    private val flow=MutableStateFlow(store.bool("theme.mockNight",true)); override val isNight=flow.asStateFlow()
    fun toggle() { flow.value=!flow.value; store.put("theme.mockNight","${flow.value}") }
}
class MockClusterBridge : ClusterBridge {
    private val flow=MutableStateFlow(NavigationState()); override val lastNavigation=flow.asStateFlow()
    override fun publish(state: NavigationState) { flow.value=state } // In-memory only. No Binder/broadcast/display calls.
}
