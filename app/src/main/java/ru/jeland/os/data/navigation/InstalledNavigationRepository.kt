package ru.jeland.os.data.navigation

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.jeland.os.core.*

object NavigatorIntents {
    fun route(provider:NavigationProvider,place:NavigationPlace):Intent {
        val uri=when(provider) {
            NavigationProvider.DGIS->Uri.parse("dgis://2gis.ru/routeSearch/rsType/car/to/${place.longitude},${place.latitude}")
            NavigationProvider.YANDEX->Uri.parse("yandexnavi://build_route_on_map").buildUpon()
                .appendQueryParameter("lat_to",place.latitude.toString()).appendQueryParameter("lon_to",place.longitude.toString()).build()
        }
        return Intent(Intent.ACTION_VIEW,uri).setPackage(provider.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

class InstalledNavigationRepository(context:Context,private val store:SettingsStore,private val report:(String)->Unit={}):NavigationRepository {
    private val context=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var selected=NavigationProvider.entries.firstOrNull {it.name==store.string("navigation.provider","")}
    private var places=readPlaces()
    private val flow=MutableStateFlow(NavigationState(active=false,destination="",distanceToManeuver=0,street="",minutes=0,kilometers=0.0,arrivalTime="",external=ExternalNavigationState(selected=selected,places=places)))
    override val state=flow.asStateFlow()
    init { refresh();NavigationNotices.changes.onEach {updateGuidance()}.launchIn(scope) }
    private fun readPlaces():List<NavigationPlace> = runCatching {
        val array=JSONArray(store.string("navigation.places.v1","[]"))
        (0 until array.length().coerceAtMost(12)).mapNotNull {index->runCatching {
            val o=array.getJSONObject(index);NavigationPlace(o.getString("id"),o.getString("title"),o.getDouble("lat"),o.getDouble("lon"))
        }.getOrNull()}.distinctBy {it.id}
    }.getOrDefault(emptyList())
    private fun persistPlaces() {store.put("navigation.places.v1",JSONArray().apply {places.forEach {p->put(JSONObject().put("id",p.id).put("title",p.title).put("lat",p.latitude).put("lon",p.longitude))}}.toString());refresh()}
    @Suppress("DEPRECATION") private fun installation(provider:NavigationProvider)=runCatching {
        val p=context.packageManager.getPackageInfo(provider.packageName,0)
        NavigatorInstallation(provider,p.applicationInfo?.enabled==true&&context.packageManager.getLaunchIntentForPackage(provider.packageName)!=null,p.versionName.orEmpty())
    }.getOrDefault(NavigatorInstallation(provider,false))
    private fun access()=runCatching {context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(context,NavigationNotificationListener::class.java))}.getOrDefault(false)
    override fun refresh() {
        val installed=NavigationProvider.entries.map(::installation)
        // First use requires an explicit choice; never switch behind the user's back.
        flow.value=flow.value.copy(external=ExternalNavigationState(selected,installed,access(),NavigationNotices.connected,null,places))
        updateGuidance()
    }
    private fun updateGuidance() {flow.update {n->val e=n.external?:return@update n
        n.copy(external=e.copy(listenerConnected=NavigationNotices.connected,guidance=if(e.accessGranted&&e.selectedInstalled&&NavigationNotices.connected)NavigationNotices.current(selected)else null))}}
    override fun selectProvider(value:NavigationProvider) {selected=value;store.put("navigation.provider",value.name);refresh();report("NAV_PROVIDER selected=${value.name}")}
    override fun openNavigator():String? {
        refresh();val provider=selected?:return "Выберите 2ГИС или Яндекс Навигатор."
        val intent=context.packageManager.getLaunchIntentForPackage(provider.packageName)?:return "${provider.title} не установлен или отключён."
        return launch(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),provider,"open")
    }
    override fun openPlace(id:String):String? {
        refresh();val provider=selected?:return "Сначала выберите навигатор."
        val place=places.firstOrNull {it.id==id}?:return "Сначала задайте координаты места."
        if(flow.value.external?.selectedInstalled!=true)return "${provider.title} не установлен или отключён."
        return launch(NavigatorIntents.route(provider,place),provider,"route")
    }
    private fun launch(intent:Intent,provider:NavigationProvider,operation:String):String? = runCatching {
        context.startActivity(intent);report("NAV_LAUNCH provider=${provider.name} operation=$operation");null
    }.getOrElse {report("NAV_LAUNCH failed provider=${provider.name} error=${it.javaClass.simpleName}");"Не удалось открыть ${provider.title}. Проверьте установленное приложение."}
    override fun savePlace(id:String,title:String,coordinates:String):String? {
        val point=parseNavigationCoordinates(coordinates)?:return "Введите широту, долготу: 55.16, 61.40."
        if(title.isBlank()||title.trim().length>80)return "Название: от 1 до 80 символов."
        if(places.size>=12&&places.none {it.id==id})return "Можно сохранить до 12 мест."
        places=places.filterNot {it.id==id}+NavigationPlace(id,title.trim(),point.first,point.second);persistPlaces();return null
    }
    override fun removePlace(id:String) {places=places.filterNot {it.id==id};persistPlaces()}
    override fun openNotificationAccess():Boolean=runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true
    }.getOrElse {report("NAV_NOTICE settings_failed ${it.javaClass.simpleName}");false}
    // The route belongs to the selected navigator, not to our illustration controls.
    override fun toggleRoute() {}
    override fun setDestination(value:String) {}
    override fun zoom(delta:Float) {}
    override fun nextManeuver() {}
}
