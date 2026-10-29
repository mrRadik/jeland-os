package ru.jeland.os.navigation

data class GeoPoint(val latitude:Double,val longitude:Double)

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
data class NavigationVoiceMotion(val speedMps:Double?=null,val fixAgeMillis:Long=0)

internal fun navigationBearing(heading:Double?,previous:Double=0.0):Double {
    val value=heading?.takeIf {it.isFinite()}?:previous.takeIf {it.isFinite()}?:0.0
    return ((value%360.0)+360.0)%360.0
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
    else->"%.1f км".format(ru.jeland.os.localization.AppLocale.current,meters/1000)
}

fun distanceGuidance(meters:Int,instruction:String):String {
    val clean=instruction.trim().trimEnd('.', ' ')
    if(clean.isBlank())return ""
    if(meters<30||clean.startsWith("Через ",ignoreCase=true)||clean.startsWith("Вы прибыли",ignoreCase=true))return clean
    val rounded=if(meters in 50..999)kotlin.math.round(meters/10.0)*10 else meters.toDouble()
    return "Через ${distanceLabel(rounded)} ${clean.replaceFirstChar {it.lowercase(ru.jeland.os.localization.AppLocale.current)}}"
}
