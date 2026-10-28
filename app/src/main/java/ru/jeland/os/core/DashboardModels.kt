package ru.jeland.os.core

import kotlin.math.roundToInt

/** Litres are deliberately not presented as an exact sensor measurement when estimated. */
data class FuelState(val remainingLiters:Double?=null,val estimated:Boolean=false,val raw:Int?=null,val percent:Double?=null,val experimental:Boolean=false) {
    val validLiters:Double? get()=remainingLiters?.takeIf {it.isFinite()}?.coerceIn(0.0,CAPACITY)
    val displayLiters:Int? get()=validLiters?.roundToInt()
    val litersToFull:Int? get()=validLiters?.let {(CAPACITY-it).coerceAtLeast(0.0).roundToInt()}
    val low:Boolean get()=validLiters?.let {it/CAPACITY<.10}==true
    companion object {
        const val CAPACITY=ExperimentalVehicleConversions.TANK_LITERS
        fun fromRelative(level:Double?)=FuelState(level?.takeIf {it.isFinite() && it in 0.0..1.0}?.times(CAPACITY),estimated=true)
    }
}
/** Horizontal top view points to LEFT: vehicle LEFT side is at the BOTTOM of the image. */
enum class Wheel(val title:String,val front:Boolean,val left:Boolean) {
    FRONT_LEFT("Переднее левое",true,true), FRONT_RIGHT("Переднее правое",true,false),
    REAR_LEFT("Заднее левое",false,true), REAR_RIGHT("Заднее правое",false,false)
}
enum class TireSeverity { NORMAL, WARNING, CRITICAL, UNKNOWN }
data class TireReading(val bar:Double?=null,val temperature:Int?=null) {
    val validBar:Double? get()=bar?.takeIf {it.isFinite() && it>=0}
}
/** Configurable DEMO thresholds, never claimed to be factory recommendations. */
data class TireThresholds(val criticalLow:Double=1.7,val warningLow:Double=2.1,val warningHigh:Double=2.9,val criticalHigh:Double=3.3) {
    init {require(criticalLow>=0 && criticalLow<warningLow && warningLow<warningHigh && warningHigh<criticalHigh)}
    fun severity(reading:TireReading):TireSeverity {
        val bar=reading.validBar ?: return TireSeverity.UNKNOWN
        return when {
            bar<=criticalLow || bar>=criticalHigh->TireSeverity.CRITICAL
            bar<warningLow || bar>warningHigh->TireSeverity.WARNING
            else->TireSeverity.NORMAL
        }
    }
}
enum class VehicleSection(val title:String) {
    QUICK("Быстрые"),LIGHTS("Освещение"),LOCKS("Двери и замки"),COMFORT("Комфорт"),DRIVING("Вождение"),OTHER("Другие")
}
enum class VehicleOption(val title:String,val section:VehicleSection) {
    AUTO_LIGHT("Автоматический свет",VehicleSection.LIGHTS),FOLLOW_HOME("Свет после выхода",VehicleSection.LIGHTS),
    WELCOME_LIGHT("Приветственное освещение",VehicleSection.LIGHTS),AUTO_LOCK("Автозапирание",VehicleSection.LOCKS),
    UNLOCK_PARK("Отпирание в P",VehicleSection.LOCKS),FOLD_LOCK("Зеркала при запирании",VehicleSection.LOCKS),
    SEAT_WELCOME("Удобная посадка",VehicleSection.COMFORT),CABIN_LIGHT("Подсветка салона",VehicleSection.COMFORT),
    LANE_DEMO("Индикация полосы",VehicleSection.DRIVING),ASSIST_DEMO("Звуки ассистентов",VehicleSection.DRIVING),
    WELCOME_SOUND("Звук приветствия",VehicleSection.OTHER)
}
data class LauncherApp(val id:String,val packageName:String,val activityName:String,val label:String)
data class AppsState(val installed:List<LauncherApp> = emptyList(),val favorites:List<String> = emptyList(),val loading:Boolean=true,val error:String?=null) {
    val favoriteApps:List<LauncherApp> get()=favorites.mapNotNull {id->installed.find {it.id==id}}
}
enum class DashboardStyle(val title:String) { CLASSIC("Классический"),MINIMAL("Минималистичный") }
data class InterfacePreferences(val showFuel:Boolean=true,val showTires:Boolean=true,val showFavorites:Boolean=true,val developerPanel:Boolean=false,
    val style:DashboardStyle=DashboardStyle.CLASSIC)
data class ConnectivityState(
    val wifi:Boolean=false,val wifiLevel:Int?=null,val cellular:Boolean=false,val mobileLevel:Int?=null,
    val networkType:String?=null,val available:Boolean=false,val demo:Boolean=false,
    val wifiEnabled:Boolean?=null,val bluetoothEnabled:Boolean?=null,val bluetoothConnected:Boolean?=null,
    val bluetoothAudio:Boolean?=null,val bluetoothPhone:Boolean?=null,val internetValidated:Boolean=false,
)
