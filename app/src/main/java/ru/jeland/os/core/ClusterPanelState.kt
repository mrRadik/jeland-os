package ru.jeland.os.core

/** Presentation only. No transport, commands, display routing or independently owned subscriptions. */
enum class ClusterDesign(val title:String,val description:String) {
    ORIGINAL("Original","Штатная приборная панель автомобиля"),
    CLASSIC("Classic","Два больших колодца и подсказки маршрута"),
    NAVIGATION("Navigation","Карта CoMaps и компактные приборы"),
    SPORT("Sport","Центральный тахометр и крупная скорость");
    companion object {
        // Preserve the selected design when upgrading from the retired theme names.
        fun fromStored(value:String):ClusterDesign=entries.firstOrNull {it.name==value.removeSuffix("_CARBON")}?:ORIGINAL
    }
}

enum class ClusterLamp {
    POSITION, HIGH_BEAM, LOW_BEAM, FRONT_FOG, REAR_FOG, LEFT, RIGHT, DOOR, RESERVE,
    EPB, AUTO_HOLD, START_STOP_READY, START_STOP_UNAVAILABLE, BELT, ICE,
    BRAKE, OIL_PRESSURE, CHARGING, AIRBAG, COOLANT_HOT, ABS, ESP, ESP_OFF, EPS,
    CHECK_ENGINE, TPMS, GEARBOX, EPC, WASHER, HDC, LANE_STANDBY, LDW_STANDBY, BSD_ACTIVE,
    EPB_FAULT, AUTO_HOLD_READY, AUTO_HOLD_FAULT, EPS_RED, GEARBOX_RED, HDC_ACTIVE,
    BSD_WARNING, LANE_ACTIVE, LANE_WARNING, LANE_RED, LDW_ACTIVE, LDW_WARNING, GENERAL_WARNING
}

/** OEM coolant indication; never inferred from outside temperature or missing data. */
enum class ClusterCoolantCondition { UNKNOWN, COLD, NORMAL, HOT }

data class ClusterPanelState(
    val speed:Double?=null,val rpm:Double?=null,val gear:String?=null,
    val fuel:Double?=null,val fuelLiters:Double?=null,val rangeKm:Double?=null,
    val coolant:Double?=null,val outside:Double?=null,val odometer:Double?=null,
    val voltage:Double?=null,val tripKm:Double?=null,val tripMillis:Long?=null,
    val consumption:Double?=null,val lamps:Set<ClusterLamp> = emptySet(),
    val navigation:NavigationState?=null,val demo:Boolean=false,
    val coolantCondition:ClusterCoolantCondition=ClusterCoolantCondition.UNKNOWN,
    val driveMode:ClusterDriveMode?=null,
    val message:ClusterOemMessage?=null,
    val indicators:List<ClusterIndicatorSpec>?=null,
    val openings:Set<VehicleOpening> = emptySet(),
    val coolantLevel:Double?=null,val limiter:ClusterLimiter?=null,
    val tripPeriod:ClusterTripPeriod=ClusterTripPeriod.SINCE_START
) {
    companion object {
        fun from(s:UiState,trip:TripSummary?,speed:VehicleSpeedSample?,elapsed:Long,tripPeriod:ClusterTripPeriod=s.preferences.clusterTripPeriod):ClusterPanelState {
            val live=s.car.connected&&!s.car.demo
            fun raw(command:Int):List<Int>? = s.car.raw[VdsKey(VerifiedVehicleProtocol.INFO,command)]
                ?.takeIf {live&&elapsed-it.receivedMillis in 0..8_000}?.values
            val gauge=raw(119)?.takeIf {it.size==2}?.toIntArray()?.let {VehicleSpeedSample.fromCarInfo(it,elapsed)}
            val freshSpeed=speed?.takeIf {live&&elapsed-it.atMillis in 0..3_000}
            val fuel=s.vehicle.fuel.validLiters.takeIf {live||s.car.demo}
            val nativeGauge=s.car.clusterTelemetry.gauge?.takeIf {live&&elapsed-it.receivedMillis in 0..3_000}
                ?.values?.let(QnxClusterTelemetryDecoder::gauge)
            val nativeWarnings=s.car.clusterTelemetry.warnings?.takeIf {live&&elapsed-it.receivedMillis in 0..3_000}
                ?.values?.let(QnxClusterTelemetryDecoder::warnings)
            val nativeComputer=s.car.clusterTelemetry.computer?.takeIf {live&&elapsed-it.receivedMillis in 0..3_000}
                ?.values?.takeIf {it.size==42}
            val nativeTrip=s.car.clusterTelemetry.tripComputer?.takeIf {live&&elapsed-it.receivedMillis in 0..3_000}
                ?.values?.let {ClusterTripComputer.decode(it,tripPeriod)}
            // ReadOnlyID ENDURANCE_KM=72 and GRAND_TOTAL_KM=71. Big-endian bytes:
            // field log 2026-11-09 20:56: [1,186]=442 km, [0,0,6,172]=1708 km,
            // matching the factory cluster photograph at 20:55.
            val range=raw(72)?.takeIf {it.size==2&&it.all {b->b in 0..255}}
                ?.let {(it[0] shl 8) or it[1]}?.takeUnless {it==0xffff}?.toDouble()
            val odometer=raw(71)?.takeIf {it.size==4&&it.all {b->b in 0..255}}
                ?.fold(0L){n,b->(n shl 8) or b.toLong()}?.takeUnless {it==0xffffffffL}?.toDouble()
            val lamps=buildSet {
                nativeWarnings?.lamps?.let(::addAll)
                if(nativeWarnings==null&&raw(1)==listOf(1))add(ClusterLamp.POSITION)
                if(nativeWarnings==null&&raw(23)==listOf(1))add(ClusterLamp.HIGH_BEAM)
                if(nativeWarnings==null&&raw(33)==listOf(1))add(ClusterLamp.LEFT)
                if(nativeWarnings==null&&raw(32)==listOf(1))add(ClusterLamp.RIGHT)
                if((live||s.car.demo)&&s.vehicle.openings.any {it in s.vehicle.knownOpenings})add(ClusterLamp.DOOR)
                if(fuel!=null&&fuel/FuelState.CAPACITY<.1)add(ClusterLamp.RESERVE)
            }
            return ClusterPanelState(
                speed=nativeGauge?.speed?:(gauge?:freshSpeed)?.kmh,
                rpm=nativeGauge?.rpm?:s.vehicle.engineRpm?.toDouble()?.takeIf {raw(62)!=null&&it in 0.0..8000.0},
                coolant=nativeGauge?.coolant,coolantLevel=nativeGauge?.coolantLevel,
                limiter=ClusterLimiter.decode(nativeWarnings?.let {s.car.clusterTelemetry.warnings?.values},
                    s.car.clusterTelemetry.auxiliary?.takeIf {live&&elapsed-it.receivedMillis in 0..3_000}?.values),
                coolantCondition=nativeWarnings?.coolant?:ClusterCoolantCondition.UNKNOWN,
                message=nativeWarnings?.message,indicators=nativeWarnings?.indicators,
                openings=if(live||s.car.demo)s.vehicle.openings.intersect(s.vehicle.knownOpenings)else emptySet(),
                gear=if(nativeComputer!=null)ClusterGear.native(nativeComputer.first())else ClusterGear.carInfo(raw(26)?.singleOrNull()),
                fuel=fuel?.div(FuelState.CAPACITY),fuelLiters=fuel,rangeKm=range,odometer=odometer,
                outside=(if(s.weather.demo)s.weather.temperature?.toDouble()else s.weather.outsideCelsius)
                    .takeIf {live||s.car.demo},
                voltage=s.vehicle.battery.voltage.takeIf {live||s.car.demo},
                tripPeriod=tripPeriod,
                tripKm=nativeTrip?.distanceKm?:trip?.distanceMetres?.div(1000)?.takeIf {s.car.demo&&tripPeriod==ClusterTripPeriod.SINCE_START},
                tripMillis=nativeTrip?.elapsedMillis?:trip?.elapsedMillis?.takeIf {s.car.demo&&tripPeriod==ClusterTripPeriod.SINCE_START},consumption=nativeTrip?.consumption,
                driveMode=ClusterDriveMode.read(s.car,elapsed)?:nativeWarnings?.indicators?.let {icons->
                    when {icons.any {it.bit==61}->ClusterDriveMode.ECO;icons.any {it.bit==62}->ClusterDriveMode.SPORT
                        icons.any {it.bit==82}->ClusterDriveMode.COMFORT
                        icons.any {it.bit==132}->ClusterDriveMode.SNOW;icons.any {it.bit==133}->ClusterDriveMode.MUD
                        icons.any {it.bit==134}->ClusterDriveMode.SAND;icons.any {it.bit==150}->ClusterDriveMode.OFFROAD;else->null}},lamps=lamps,navigation=s.navigation.takeIf {it.active&&!it.preview&&!it.demo},demo=s.car.demo
            )
        }
    }
}
