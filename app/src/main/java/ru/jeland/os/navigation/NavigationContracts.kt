package ru.jeland.os.navigation

import kotlinx.coroutines.flow.StateFlow
import ru.jeland.os.core.NavigationState

data class GeoPoint(val latitude:Double,val longitude:Double)
data class NavigationGuidance(val instruction:String,val distanceMeters:Double,val remainingSeconds:Double,
    val maneuverType:String?=null,val maneuverModifier:String?=null)
data class RegionData(val id:String,val title:String,val bytes:Long,val attribution:String,
    val center:GeoPoint=GeoPoint(42.507,1.53),val bounds:List<Double> = listOf(1.40,42.40,1.80,42.70),val houseNumberCount:Int=0) {
    fun contains(point:GeoPoint)=point.longitude in bounds[0]..bounds[2]&&point.latitude in bounds[1]..bounds[3]
}
data class NavigationPlace(val name:String,val point:GeoPoint,val kind:String="point",val locality:String="",val address:String="") {
    private fun distinctParts(vararg parts:String)=parts.map {it.trim()}.filter {it.isNotEmpty()}.distinctBy {it.lowercase(java.util.Locale.ROOT)}
    val fullName:String get()=distinctParts(name,address,locality).joinToString(", ")
    val searchSubtitle:String get()=when {
        kind=="settlement"&&name.equals(locality,true)->"Населённый пункт"
        address.isNotBlank()->distinctParts(locality,address.takeUnless {it.equals(name,true)}.orEmpty()).joinToString(" · ").ifBlank {"Населённый пункт не указан"}
        kind=="place"->distinctParts(locality,"Адрес не указан").joinToString(" · ")
        locality.isNotBlank()->locality
        else->"Населённый пункт не указан"
    }
}
data class NavigationCamera(val target:GeoPoint,val zoom:Double,val bearing:Double=0.0,val tilt:Double=0.0)
data class NavigationVoiceMotion(val speedMps:Double?=null,val fixAgeMillis:Long=0)

/** Readable street-level guidance, preserving a closer manual zoom and the last valid course. */
internal fun navigationFollowCamera(point:GeoPoint,previous:NavigationCamera,active:Boolean,heading:Double?):NavigationCamera =
    NavigationCamera(point,maxOf(if(active)16.5 else 14.5,previous.zoom),navigationBearing(heading,previous.bearing),if(active)35.0 else 0.0)

/** GPS and camera angles share one convention: clockwise from geographic north. */
internal fun navigationBearing(heading:Double?,previous:Double=0.0):Double {
    val value=heading?.takeIf {it.isFinite()}?:previous.takeIf {it.isFinite()}?:0.0
    return ((value%360.0)+360.0)%360.0
}

/** Canvas is screen-aligned, unlike MapLibre's map-aligned vehicle symbol. */
internal fun navigationMarkerRotation(heading:Double,cameraBearing:Double):Float=
    (((navigationBearing(heading)-navigationBearing(cameraBearing)+540.0)%360.0)-180.0).toFloat()

fun parseCoordinates(value:String):GeoPoint? {
    val parts=value.split(',').map {it.trim().toDoubleOrNull()}
    if(parts.size!=2)return null
    val lat=parts[0]?:return null;val lon=parts[1]?:return null
    return if(lat.isFinite()&&lon.isFinite()&&lat in -90.0..90.0&&lon in -180.0..180.0)GeoPoint(lat,lon)else null
}

/** Presentation of engine-provided maneuver metadata; never computes a maneuver. */
fun guidanceLabel(type:String?,modifier:String?,text:String):String {
    val kind=type.orEmpty().uppercase();val direction=modifier.orEmpty().uppercase().replace("_","")
    val action=when {
        kind.contains("ARRIVE")->"Вы прибыли"
        kind.contains("ROUNDABOUT")||kind.contains("ROTARY")->"Круговое движение"
        kind.contains("DEPART")->"Начните движение"
        direction.contains("UTURN")->"Развернитесь"
        direction.contains("RIGHT")->"Поверните направо"
        direction.contains("LEFT")->"Поверните налево"
        direction=="STRAIGHT"->"Продолжайте движение прямо"
        else->""
    }
    val cleaned=text.trim().replace(Regex("(?iu)на лево"),"налево").replace(Regex("(?iu)на право"),"направо")
        .replace(Regex("(?iu)^(налево|направо|прямо)\\s*[·.]\\s*"),"").trimEnd('.', ' ')
    if(kind.contains("ARRIVE"))return action
    // The engine often already supplies a complete Russian instruction. Never prepend
    // another direction ("Налево · Поверните налево") to that sentence.
    if(Regex("(?iu)^(поверните|сверните|двигайтесь|продолжайте|держитесь|развернитесь|направляйтесь|выезжайте|въезжайте|съезжайте|на |через |вы |начните)").containsMatchIn(cleaned))return cleaned
    return when {cleaned.isBlank()->action;action.isBlank()->cleaned;else->"$action · $cleaned"}
}

fun distanceLabel(meters:Double):String=when {
    !meters.isFinite()||meters<0->"—"
    meters<1000->"${meters.toInt()} м"
    else->"%.1f км".format(java.util.Locale.forLanguageTag("ru"),meters/1000)
}

fun distanceGuidance(meters:Int,instruction:String):String {
    val clean=instruction.trim().trimEnd('.', ' ')
    if(clean.isBlank())return ""
    if(meters<30||clean.startsWith("Через ",ignoreCase=true)||clean.startsWith("Вы прибыли",ignoreCase=true))return clean
    val rounded=if(meters in 50..999)kotlin.math.round(meters/10.0)*10 else meters.toDouble()
    return "Через ${distanceLabel(rounded)} ${clean.replaceFirstChar {it.lowercase(java.util.Locale.forLanguageTag("ru"))}}"
}

/** Engine-independent boundary. No vehicle, display, Binder or VDS references. */
interface NavigationProvider : AutoCloseable {
    val state:StateFlow<NavigationState>
    suspend fun route(from:GeoPoint,to:GeoPoint)
    fun stop()
}
interface MapDataRepository {
    suspend fun prepare():RegionData
    fun mapFile():java.io.File
}
interface ClusterNavigationBridge {
    val guidance:StateFlow<NavigationGuidance?>
    fun publish(guidance:NavigationGuidance?)
}
class MockClusterNavigationBridge:ClusterNavigationBridge {
    private val mutable=kotlinx.coroutines.flow.MutableStateFlow<NavigationGuidance?>(null)
    override val guidance=mutable as StateFlow<NavigationGuidance?>
    override fun publish(guidance:NavigationGuidance?) { mutable.value=guidance }
}
