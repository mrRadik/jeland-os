package ru.jeland.os.core

enum class NavigationProvider(val packageName:String,val title:String) {
    DGIS("ru.dublgis.dgismobile","2ГИС"),YANDEX("ru.yandex.yandexnavi","Яндекс Навигатор");
    companion object { fun fromPackage(value:String)=entries.firstOrNull {it.packageName==value} }
}
data class NavigatorInstallation(val provider:NavigationProvider,val installed:Boolean,val version:String="")
data class NavigationPlace(val id:String,val title:String,val latitude:Double,val longitude:Double) {
    init { require(title.isNotBlank()&&title.length<=80&&validCoordinates(latitude,longitude)) }
}
fun validCoordinates(latitude:Double,longitude:Double)=latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0
fun parseNavigationCoordinates(value:String):Pair<Double,Double>? {
    val parts=value.trim().split(Regex("[,;\\s]+"))
    if(parts.size!=2)return null
    val lat=parts[0].toDoubleOrNull()?:return null
    val lon=parts[1].toDoubleOrNull()?:return null
    return if(validCoordinates(lat,lon))lat to lon else null
}
data class NavigationGuidance(val key:String,val provider:NavigationProvider,val title:String,val text:String,val detail:String,val updatedAt:Long)
data class ExternalNavigationState(
    val selected:NavigationProvider?=null,val installations:List<NavigatorInstallation> = emptyList(),
    val accessGranted:Boolean=false,val listenerConnected:Boolean=false,
    val guidance:NavigationGuidance?=null,val places:List<NavigationPlace> = emptyList(),
) {
    val selectedInstalled get()=installations.any {it.provider==selected&&it.installed}
}

/** Notification text is displayed verbatim, never converted into invented distance/ETA/turns. */
class NavigationNoticeReducer {
    private val notices=mutableMapOf<String,NavigationGuidance>()
    private fun clean(value:String)=value.replace(Regex("[\\r\\n\\t]+")," ").trim().take(240)
    fun posted(key:String,packageName:String,category:String?,ongoing:Boolean,summary:Boolean,title:String,text:String,detail:String,now:Long):Boolean {
        val provider=NavigationProvider.fromPackage(packageName)?:return false
        if(summary)return false
        val t=clean(title);val body=clean(text);val sub=clean(detail)
        // Some releases use CATEGORY_SERVICE. Accept only a route-looking ongoing notice then.
        val routeText="$t $body $sub"
        val routeLike=Regex("(?iu)(\\d+[.,]?\\d*\\s*(?:км|м|km|m)(?![\\p{L}\\p{N}])|поверн|поворот|прямо|съезд|разворот|turn|exit|roundabout)").containsMatchIn(routeText)
        if((category!="navigation"&&!(ongoing&&routeLike))||(t.isBlank()&&body.isBlank())) {
            notices.remove(key);return false
        }
        notices[key]=NavigationGuidance(key,provider,t,body,sub,now)
        return true
    }
    fun removed(key:String) {notices.remove(key)}
    fun current(provider:NavigationProvider?)=notices.values.filter {it.provider==provider}.maxByOrNull {it.updatedAt}
    fun clear() {notices.clear()}
}
