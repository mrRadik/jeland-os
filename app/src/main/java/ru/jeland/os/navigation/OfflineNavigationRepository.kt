package ru.jeland.os.navigation

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import android.net.Uri
import ru.jeland.os.core.NavigationRepository

data class NavigationPreview(val bitmap:android.graphics.Bitmap,val dark:Boolean,val regionId:String?,val routeKey:Int,
    val focus:RoutePreviewWindow?=null,val bearing:Double=0.0)

/** One application-scoped session shared by the screen and the home widget. */
class OfflineNavigationRepository(context:Context):NavigationRepository,AutoCloseable {
    val places=NavigationPlacesRepository(ru.jeland.os.data.AndroidSettingsStore(context))
    val maps=OfflineMapManager(context)
    val gps=AndroidGpsSource(context)
    val provider=ValhallaNavigationProvider(context,maps,gps)
    val downloads=NavigationDownloads(context)
    override val state=provider.state
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val course=combine(state,gps.position,gps.bearing){s,p,b->navigationCourse(s,p,b)}
        .stateIn(scope,SharingStarted.Eagerly,null)
    private var request:Job?=null
    val selected=MutableStateFlow<NavigationPlace?>(null)
    val message=MutableStateFlow("")
    val importing=MutableStateFlow(false)
    val preparing=MutableStateFlow(true)
    // A separate bounded local snapshot; never a screenshot of the full navigation tab.
    val previewImage=MutableStateFlow<NavigationPreview?>(null)
    var camera:NavigationCamera?=null
    init {scope.launch {try {maps.prepare()}catch(e:CancellationException){throw e}catch(e:Exception){message.value="Карта недоступна: ${e.message}"}finally {preparing.value=false}}}
    fun resume(){if(!state.value.preview)gps.start()}
    fun pause(){gps.pause()}
    fun build(from:GeoPoint?=null,to:GeoPoint?=selected.value?.point) {
        val start=from?:gps.routeOrigin()
        if(start==null){message.value="Нет свежего GNSS-фикса. Разрешите GPS или явно выберите просмотр маршрута.";return}
        if(to==null){message.value="Нажмите на дом на карте или выберите адрес поиском";return}
        if(importing.value)return
        message.value=if(from!=null)"ПРОСМОТР маршрута · старт выбран вручную, не GNSS"else"Маршрут от текущей GNSS-позиции"
        if(from!=null)gps.replay(start)
        else gps.start()
        provider.setPreview(from!=null)
        val title=selected.value?.takeIf {it.point==to}?.fullName?:"Точка на карте"
        request?.cancel();request=scope.launch {try {provider.route(start,to);provider.nameDestination(title);message.value=""}catch(e:CancellationException){throw e}catch(e:Exception){message.value=e.message?:"Ошибка маршрута";android.util.Log.e("JelandNavigation","Route failed",e)}}
    }
    fun select(place:NavigationPlace){selected.value=place;message.value=""} // The destination card already shows the address.
    fun clearSelection() {
        if(state.value.active)return // Clearing a draft destination is not an implicit stop-navigation button.
        request?.cancel();request=null
        provider.clearDestination()
        selected.value=null;previewImage.value=null;message.value=""
        gps.start()
    }
    fun importRegion(uri:Uri) {
        if(importing.value)return
        importing.value=true
        scope.launch {
            try {
                request?.cancelAndJoin();provider.releaseRegion()
                val info=maps.importRegion(uri);selected.value=null;camera=null;previewImage.value=null
                message.value="Загружен регион: ${info.title}"
            }catch(e:CancellationException){throw e}catch(e:Exception){message.value="Импорт не выполнен: ${e.message}"}
            finally {importing.value=false}
        }
    }
    fun installDownloaded() {
        val downloaded=downloads.state.value
        val uri=downloaded.ready?:return
        if(downloaded.kind=="region")importRegion(uri)
        else scope.launch {
            importing.value=true
            try {maps.installOverview(uri);previewImage.value=null;message.value="Обзорная карта установлена"}
            catch(e:CancellationException){throw e}
            catch(e:Exception){message.value="Карта не установлена: ${e.message}"}
            finally {importing.value=false}
        }
    }
    override fun toggleRoute(){if(state.value.active)provider.stop()else build()}
    override fun setDestination(value:String){scope.launch {
        val place=maps.search(value).firstOrNull()
        if(place!=null)select(place)else message.value="В загруженном регионе ничего не найдено"
    }}
    override fun zoom(delta:Float){} // Map gestures own camera, not navigation state.
    override fun nextManeuver(){} // Never synthesize real maneuver advancement from a button.
    fun stop(){request?.cancel();provider.stop();previewImage.value=null;gps.start()}
    override fun close(){request?.cancel();scope.cancel();provider.close();downloads.close()}
}
