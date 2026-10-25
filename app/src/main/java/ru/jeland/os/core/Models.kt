package ru.jeland.os.core

enum class ThemeMode(val title: String) { DARK("Тёмная"), LIGHT("Светлая"), AUTO("Авто") }
enum class SeatSide(val title: String) { DRIVER("Сиденье водителя"), PASSENGER("Сиденье пассажира") }
enum class SeatMode { HEAT, VENTILATE }
enum class RearSeatSide(val title:String,val commandId:Int) { LEFT("Заднее левое",139),RIGHT("Заднее правое",141) }
enum class RearHeat(val title: String) { OFF("Выкл."), LEFT("Левый"), RIGHT("Правый"), BOTH("Оба");
    companion object {fun fromLevels(left:Int?,right:Int?)=when {left==null||right==null->OFF;left>0&&right>0->BOTH;left>0->LEFT;right>0->RIGHT;else->OFF}}
}
enum class Shade(val title: String) { CLOSED("Закрыта"), HALF("Наполовину"), OPEN("Открыта") }
enum class AirOutlet(val title:String) { FACE("В лицо"), FEET("В ноги"), WINDSHIELD("На лобовое") }
/** Owner-confirmed SET enum, not a bit mask. OEM reports the last two as GET 6/7. */
enum class Airflow(val title:String,val commandRaw:Int,val outlets:Set<AirOutlet>) {
    FACE("В лицо",0,setOf(AirOutlet.FACE)),
    FEET("В ноги",2,setOf(AirOutlet.FEET)),
    MIXED("Лицо + ноги",1,setOf(AirOutlet.FACE,AirOutlet.FEET)),
    FEET_GLASS("Ноги + стекло",3,setOf(AirOutlet.FEET,AirOutlet.WINDSHIELD)),
    WINDSHIELD("На лобовое",4,setOf(AirOutlet.WINDSHIELD)),
    FACE_GLASS("Лицо + стекло",5,setOf(AirOutlet.FACE,AirOutlet.WINDSHIELD)),
    ALL("Лицо + ноги + стекло",6,AirOutlet.entries.toSet());
    val feedbackRaw get()=if(commandRaw>=5)commandRaw+1 else commandRaw
    fun toggle(outlet:AirOutlet):Airflow = fromOutlets(if(outlet in outlets)outlets-outlet else outlets+outlet)?:this
    companion object {
        fun fromCommand(raw:Int?)=entries.firstOrNull {it.commandRaw==raw}
        fun fromFeedback(raw:Int?)=entries.firstOrNull {it.feedbackRaw==raw}
        fun fromOutlets(outlets:Set<AirOutlet>)=entries.firstOrNull {it.outlets==outlets}
    }
}
enum class Maneuver { RIGHT, LEFT, STRAIGHT }
enum class VehicleOpening(val title: String) {
    FRONT_LEFT("Водительская"), FRONT_RIGHT("Передняя правая"), REAR_LEFT("Задняя левая"), REAR_RIGHT("Задняя правая"), TRUNK("Багажник"), HOOD("Капот")
}
data class ClimateState(
    val driver: Double = 22.0, val passenger: Double = 22.0, val fan: Int = 3,
    val auto: Boolean = true, val ac: Boolean = true, val recirculation: Boolean = false,
    val defrost: Boolean = false, val sync: Boolean = false, val airflow: Airflow = Airflow.MIXED,
    val available: Set<Int>? = null, val driverRaw: Int? = null, val passengerRaw: Int? = null,
    val power: Boolean = false, val rearDefrost: Boolean = false, val windshieldHeat: Boolean = false, val airflowRaw: Int? = null,
) {
    fun known(cmd: Int) = available == null || cmd in available
    val controlsEnabled get()=known(18)&&power
    val reportedAirflow get()=if(available==null)airflow else Airflow.fromFeedback(airflowRaw).takeIf {known(6)}
}
data class SeatChannel(val heat: Int = 0, val ventilation: Int = 0)
data class SeatState(val driver: SeatChannel = SeatChannel(), val passenger: SeatChannel = SeatChannel(), val rear: RearHeat = RearHeat.OFF,
    val available: Set<Int>? = null, val rearLeft: Int? = null, val rearRight: Int? = null) {
    fun channel(side: SeatSide) = if (side == SeatSide.DRIVER) driver else passenger
    fun known(cmd: Int) = available == null || cmd in available
    fun rearLevel(side:RearSeatSide):Int?=(if(side==RearSeatSide.LEFT)rearLeft else rearRight).takeIf {known(side.commandId)}
}
data class VehicleState(
    val steeringHeat: Boolean = false, val shade: Shade = Shade.CLOSED,
    val mirrorsFolded: Boolean = false, val openings: Set<VehicleOpening> = emptySet(),
    val mockMoving: Boolean = false,
    val fuel: FuelState = FuelState(31.0),
    val tires: Map<Wheel,TireReading> = Wheel.entries.associateWith { TireReading(2.4,18) },
    val tireThresholds: TireThresholds = TireThresholds(),
    val options: Map<VehicleOption,Boolean> = emptyMap(),
    val live: Boolean = false, val knownOpenings: Set<VehicleOpening> = VehicleOpening.entries.toSet(),
    val steeringKnown: Boolean = true, val shadeRaw: List<Int>? = null, val requestedShade: Shade? = null,
    val engineRpm: Int? = null, val tireChannels: List<Double?> = emptyList(),
    val battery:BatteryReading=BatteryReading(),
)
data class Track(val title: String, val artist: String, val duration: Int)
val demoTracks = listOf(Track("Blinding Lights", "The Weeknd", 200), Track("Midnight City", "M83", 244), Track("Nightcall", "Kavinsky", 258))
data class MediaState(val index: Int = 0, val playing: Boolean = false, val position: Int = 84, val volume: Int = 45, val source: String = "Bluetooth", val liked: Boolean = false,
    val live:Boolean=false,val liveTrack:Track?=null,val controlsEnabled:Boolean=true,val status:String="DEMO · без аудио",val positionKnown:Boolean=true,val artwork:MediaArtwork?=null,val playlist:List<MediaQueueItem> = emptyList(),val currentId:String?=null,val bluetoothConnected:Boolean?=null) {
    val track: Track get() = if(live)liveTrack?:Track("Музыка","",0) else demoTracks[index.coerceIn(demoTracks.indices)]
}
data class NavigationState(
    val external:ExternalNavigationState?=null,
    val demo:Boolean=true, val preview:Boolean=false, val calculating:Boolean=false, val instruction:String="", val status:String="",
    val geometry:List<Pair<Double,Double>> = emptyList(),
    val active: Boolean = true, val destination: String = "На работу", val maneuver: Maneuver = Maneuver.RIGHT,
    val distanceToManeuver: Int = 800, val street: String = "ул. Ленина", val minutes: Int = 12,
    val kilometers: Double = 8.4, val zoom: Float = 1f,
    val arrivalTime: String = java.time.LocalTime.now().plusMinutes(12).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
)
data class WeatherState(val temperature: Int? = 8, val city: String = "Челябинск", val condition: String = "Облачно",val demo:Boolean=true,val outsideCelsius:Double?=null) {
    val temperatureLabel get()=if(!demo)OutsideTemperature.label(outsideCelsius)else temperature?.let {"${if(it>0)"+"else""}$it°"}?:"—°"
}
data class UiState(
    val climate: ClimateState = ClimateState(), val seats: SeatState = SeatState(),
    val vehicle: VehicleState = VehicleState(), val media: MediaState = MediaState(),
    val navigation: NavigationState = NavigationState(), val weather: WeatherState = WeatherState(),
    val theme: ThemeMode = ThemeMode.DARK, val night: Boolean = true,
    val apps: AppsState = AppsState(), val connectivity: ConnectivityState = ConnectivityState(),
    val preferences: InterfacePreferences = InterfacePreferences(),
    val car: VehicleLinkState = VehicleLinkState(),
) { val dark: Boolean get() = theme == ThemeMode.DARK || (theme == ThemeMode.AUTO && night) }
