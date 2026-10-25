package ru.jeland.os.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import ru.jeland.os.core.*

/** Stable repository identities for existing UI. DEMO reducers never see live commands. */
class VehicleRouting(context: android.content.Context, store: SettingsStore) {
    val session=DesayCarSession(context,store)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val demoVehicle=MockVehicleRepository(store)
    private val demoClimate=MockClimateRepository(store)
    private val demoSeats=MockSeatRepository(store)
    private val demo get()=session.link.value.demo
    private fun <T> route(mock: StateFlow<T>,live: StateFlow<T>)=combine(mock,live,session.link){m,l,s->if(s.demo)m else l}
        .stateIn(scope,SharingStarted.Eagerly,if(demo)mock.value else live.value)
    private fun unsupported(label: String) {session.command(label,{error("Функция ещё не подтверждена и не подключена")})}
    val vehicle: VehicleRepository=object: VehicleRepository {
        override val state=route(demoVehicle.state,session.vehicle)
        override fun toggleSteeringHeat(){if(demo)demoVehicle.toggleSteeringHeat()else session.toggle(160)}
        override fun setShade(value: Shade){if(demo)demoVehicle.setShade(value)else session.shade(value)}
        override fun toggleMirrors(){if(demo)demoVehicle.toggleMirrors()else unsupported("Зеркала")}
        override fun simulateOpening(value: VehicleOpening){if(demo)demoVehicle.simulateOpening(value)}
        override fun simulateMoving(value: Boolean){if(demo)demoVehicle.simulateMoving(value)}
        override fun closeAllOpenings(){if(demo)demoVehicle.closeAllOpenings()}
        override fun simulateFuel(value: FuelState){if(demo)demoVehicle.simulateFuel(value)}
        override fun simulateTire(wheel: Wheel,reading: TireReading){if(demo)demoVehicle.simulateTire(wheel,reading)}
        override fun setTireThresholds(value: TireThresholds){if(demo)demoVehicle.setTireThresholds(value)}
        override fun toggleOption(value: VehicleOption){if(demo)demoVehicle.toggleOption(value)else unsupported(value.title)}
    }
    val climate: ClimateRepository=object: ClimateRepository {
        override val state=route(demoClimate.state,session.climate)
        override fun adjustTemperature(driver: Boolean,delta: Double){if(demo)demoClimate.adjustTemperature(driver,delta)else session.temperature(driver,delta)}
        override fun setTemperature(driver:Boolean,value:Double){if(demo)demoClimate.setTemperature(driver,value)else session.setTemperature(driver,value)}
        override fun toggleAuto(){if(demo)demoClimate.toggleAuto()else session.command("AUTO ВКЛ",{listOf(VerifiedVehicleProtocol.autoOn())})}
        override fun toggleAc(){if(demo)demoClimate.toggleAc()else session.binary(1)}
        override fun toggleRecirculation(){if(demo)demoClimate.toggleRecirculation()else session.binary(8)}
        override fun toggleDefrost(){if(demo)demoClimate.toggleDefrost()else session.binary(24)}
        override fun toggleSync(){if(demo)demoClimate.toggleSync()else session.sync()}
        override fun setFan(value: Int){if(demo)demoClimate.setFan(value)else session.command("Вентилятор $value",{listOf(VerifiedVehicleProtocol.fan(value))})}
        override fun setAirflow(value: Airflow){if(demo)demoClimate.setAirflow(value)else session.command("Обдув: ${value.title}",{listOf(VerifiedVehicleProtocol.airflow(value.commandRaw))})}
        override fun setRawAirflow(value: Int){Airflow.fromCommand(value)?.let(::setAirflow)}
        override fun togglePower(){if(demo)demoClimate.togglePower()else session.binary(18)}
        override fun toggleRearDefrost(){if(demo)demoClimate.toggleRearDefrost()else session.toggle(80)}
        override fun toggleWindshieldHeat(){if(demo)demoClimate.toggleWindshieldHeat()else session.binary(65)}
    }
    val seats: SeatRepository=object: SeatRepository {
        override val state=route(demoSeats.state,session.seats)
        override fun setLevel(side: SeatSide,mode: SeatMode,value: Int){if(demo)demoSeats.setLevel(side,mode,value)else session.seat(side,mode,value)}
        override fun setRear(value: RearHeat){if(demo)demoSeats.setRear(value)else session.rear(value)}
        override fun setRearLevel(side:RearSeatSide,value:Int){if(demo)demoSeats.setRearLevel(side,value)else session.rearSeat(side,value)}
    }
}
