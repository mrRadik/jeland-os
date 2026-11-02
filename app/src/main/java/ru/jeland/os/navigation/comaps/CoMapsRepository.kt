package ru.jeland.os.navigation.comaps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import app.organicmaps.sdk.*
import app.organicmaps.sdk.Map as CoMap
import app.organicmaps.sdk.bookmarks.data.FeatureId
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.location.*
import app.organicmaps.sdk.routing.*
import app.organicmaps.sdk.search.*
import app.organicmaps.sdk.util.Config
import app.organicmaps.sdk.util.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.jeland.os.BuildConfig
import ru.jeland.os.core.Maneuver
import ru.jeland.os.core.NavigationState
import ru.jeland.os.navigation.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class CoMapsRegion(val id:String,val name:String,val parent:String,val bytes:Long,val status:Int,val progress:Float,val children:Boolean,val error:Int)
data class CoMapsResult(val index:Int,val place:NavigationPlace,val suggestion:String?=null)

/** Application-scoped local engine; shared route/GPS for full map and home widget. */
class CoMapsRepository(context:Context,private val gps:AndroidGpsSource,val voice:NavigationVoice) {
    private val app=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val ready=MutableStateFlow(false)
    val error=MutableStateFlow<String?>(null)
    val message=MutableStateFlow("")
    val state=MutableStateFlow(NavigationState(demo=false,active=false,destination="",geometry=emptyList(),instruction=""))
    val selected=MutableStateFlow<NavigationPlace?>(null)
    val results=MutableStateFlow<List<CoMapsResult>>(emptyList())
    val searching=MutableStateFlow(false)
    val regions=MutableStateFlow<List<CoMapsRegion>>(emptyList())
    val folder=MutableStateFlow("Countries")
    val missingMaps=MutableStateFlow<List<String>>(emptyList())
    val revision=MutableStateFlow(0)
    val following=MutableStateFlow(true)
    val demoDriving=MutableStateFlow(false)
    private var demoJob:Job?=null
    private var demoPendingDownload=false
    private var startAttempts=0
    private var core:OrganicMaps?=null
    private var initializing=false
    private var timestamp=0L
    private var searchJob:Job?=null
    private var routeSession=0
    private val maneuverIdentity=CoMapsManeuverIdentity()
    private var allowFollow=false
    private var gpsListener:BaseLocationProvider.Listener?=null
    private var storageSubscription:Int?=null
    private var pendingFollow=true
    private var changingFollow=false
    private var surfaceVisible=false
    private var followJob:Job?=null
    private var centeredForFollow=false
    private var followNeedsZoom=true
    private val journey=CoMapsGuidanceSession()
    private val routeUpdate=CoMapsRouteUpdate()
    private var routeRequested=false
    private var routeTransition=false
    private var routeBuildJob:Job?=null
    private val positionModeListener=LocationState.ModeChangeListener {mode->
        if(surfaceVisible&&!routeTransition&&!changingFollow&&(!pendingFollow||centeredForFollow)&&mode in listOf(LocationState.NOT_FOLLOW,LocationState.NOT_FOLLOW_NO_POSITION)) {
            following.value=false;pendingFollow=false;centeredForFollow=false
        }
    }
    val locationHelper:LocationHelper get()=checkNotNull(core).locationHelper
    private val placeListener=object:PlacePageActivationListener {
        override fun onPlacePageActivated(data:app.organicmaps.sdk.widget.placepage.PlacePageData) {
            if(state.value.calculating||routeTransition)return
            if(data is MapObject&&!data.isMyPosition)select(NavigationPlace(data.title.ifBlank {"Точка на карте"},GeoPoint(data.lat,data.lon),address=data.address.orEmpty()),false)
        }
        override fun onPlacePageDeactivated() {}
        override fun onSwitchFullScreenMode() {}
    }
    private val searchListener=object:SearchListener {
        override fun onResultsUpdate(items:Array<SearchResult>,time:Long) {
            if(time!=timestamp)return
            results.value=items.mapIndexed {index,result->CoMapsResult(index,NavigationPlace(result.name,GeoPoint(result.lat,result.lon),locality=result.description?.region.orEmpty()),result.suggestion)}
        }
        override fun onResultsEnd(time:Long){if(time==timestamp)searching.value=false}
    }
    private val storageListener=object:MapManager.StorageCallback {
        override fun onStatusChanged(data:List<MapManager.StorageCallbackData>) {
            refreshMaps();revision.value++
            if(data.any {it.newStatus==CountryItem.STATUS_DONE})message.value="Карта загружена"
            if(BuildConfig.DEMO_FEATURES&&demoPendingDownload&&MapManager.nativeGetStatus("Andorra")==CountryItem.STATUS_DONE){demoPendingDownload=false;startDemoTrip()}
        }
        override fun onProgress(id:String,local:Long,remote:Long){refreshMaps()}
    }
    private val routing=object:RoutingController.Container {
        override fun showRoutePlan(show:Boolean,completion:Runnable?){completion?.run()}
        override fun onStartRouteBuilding(){state.value=state.value.copy(calculating=!journey.started,geometry=emptyList());message.value=if(journey.started)"Перестраиваем маршрут…"else"Строим маршрут…"}
        override fun onBuiltRoute() {
            if(!routeRequested)return
            missingMaps.value=emptyList();message.value=""
            // NEED_MORE_MAPS also emits onBuiltRoute before the error callback.
            val session=routeSession
            routeBuildJob?.cancel()
            routeBuildJob=scope.launch {
                repeat(20) {
                    delay(100)
                    if(!routeRequested||session!=routeSession||missingMaps.value.isNotEmpty())return@launch
                    val nativeState=Framework.nativeGetRouteFollowingInfo()?.routingSessionState?.name
                    if(nativeState!=null&&CoMapsRouteUpdate.ready(nativeState)&&RoutingController.get().routeGeometry.pointCount>1) {
                        updateGuidance()
                        if(allowFollow&&!journey.started)startGuidance()
                        else when(journey.built(state.value.geometry.hashCode(),allowFollow)) {
                            NavigationJourneyEvent.REBUILT->{maneuverIdentity.reset();voice.journey(NavigationJourneyEvent.REBUILT)}
                            else->Unit
                        }
                        if(!allowFollow)routeTransition=false
                        return@launch
                    }
                }
            }
        }
        override fun onCommonBuildError(code:Int,maps:Array<String>) {
            routeTransition=false
            state.value=state.value.copy(calculating=false,active=journey.started);missingMaps.value=maps.toList()
            message.value=if(maps.isNotEmpty())"Для маршрута скачайте недостающие регионы"else"Не удалось построить маршрут. Проверьте точки и загруженные карты."
        }
        override fun onNavigationCancelled(){state.value=state.value.copy(active=false,calculating=false)}
        override fun onPlanningCancelled(){state.value=state.value.copy(active=false,calculating=false)}
        override fun updateBuildProgress(progress:Int,router:Router){if(ready.value&&routeRequested)updateGuidance()}
        override fun refreshNavigationController(){if(ready.value&&routeRequested)updateGuidance()}
    }
    fun initialize() {
        if(initializing||ready.value)return
        initializing=true;error.value=null
        try {
            ConnectionState.INSTANCE.initialize(app)
            val factory=object:LocationProviderFactory {
                override fun isGoogleLocationAvailable(context:Context)=false
                override fun getProvider(context:Context,listener:BaseLocationProvider.Listener):BaseLocationProvider {
                    gpsListener=listener
                    return object:BaseLocationProvider(listener) {
                        override fun start(interval:Long){}
                        override fun stop(){}
                    }
                }
            }
            val storage=CoMapsContext(app).apply {prepareRenderer()}
            val engine=OrganicMaps(storage,"web","ru.jeland.os",BuildConfig.VERSION_CODE,BuildConfig.VERSION_NAME,"ru.jeland.os.updates",factory)
            core=engine
            engine.init {
                scope.launch {
                    ready.value=true;initializing=false
                    Config.TTS.setEnabled(false)
                    Config.setAutodownloadEnabled(false)
                    RoutingController.get().attach(routing)
                    SearchEngine.INSTANCE.addListener(searchListener)
                    Framework.nativePlacePageActivationListener(placeListener)
                    LocationState.nativeSetListener(positionModeListener)
                    storageSubscription=MapManager.nativeSubscribe(storageListener)
                    // The published binary bundles default/vehicle style assets.
                    MapMode.set(MapMode.Default);Framework.nativeSwitchToUsingVehicleStyle(true)
                    Framework.nativeSetAutoZoomEnabled(true)
                    folder.value=MapManager.nativeGetRoot();refreshMaps();resume()
                }
            }
            scope.launch {gps.locationUpdates(1000).collect {location->
                if(ready.value&&locationHelper.isActive){
                    val engineLocation=if(BuildConfig.DEMO_FEATURES&&demoDriving.value)Location(location).apply {
                        provider=locationHelper.savedLocation?.provider?:"gps"
                    }else location
                    gpsListener?.onLocationChanged(engineLocation);delay(40)
                    applyPendingFollow()
                    if(state.value.active)updateGuidance(location)
                }
            }}
        }catch(e:Exception){fail(e)}catch(e:LinkageError){fail(e)}
    }
    private fun fail(value:Throwable){initializing=false;error.value="Не удалось запустить карту CoMaps";android.util.Log.e("JelandCoMaps","Core initialization",value)}
    fun resume() {
        if(ready.value&&app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){locationHelper.start();gps.start()}
    }
    fun pause(){if(ready.value&&!state.value.active)locationHelper.stop()}
    fun theme(dark:Boolean){if(ready.value)MapAppearance.set(if(dark)MapAppearance.Dark else MapAppearance.Light)}
    fun follow() {
        following.value=true;pendingFollow=true;centeredForFollow=false;followNeedsZoom=true
        if(!ready.value)return
        Framework.nativeDeactivateMapSelectionCircle(false)
        Framework.nativeSetAutoZoomEnabled(true)
        resume();applyPendingFollow()
    }
    fun setFollowing(enabled:Boolean) {
        if(enabled){follow();return}
        following.value=false;pendingFollow=false;centeredForFollow=false
        followJob?.cancel()
        if(ready.value&&CoMap.isEngineCreated())Framework.nativeStopLocationFollow()
    }
    fun surfaceReady() {
        surfaceVisible=true
        if(following.value){pendingFollow=true;applyPendingFollow()}
    }
    fun surfacePaused(){surfaceVisible=false;followJob?.cancel()}
    private fun applyPendingFollow() {
        if(!following.value||!pendingFollow||!ready.value||!surfaceVisible||!CoMap.isEngineCreated()||followJob?.isActive==true)return
        val fix=locationHelper.savedLocation?:return
        val age=(SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1_000_000
        if(age !in 0..30_000||!fix.hasAccuracy()||fix.accuracy>100)return
        val hasCourse=fix.hasBearing()&&fix.hasSpeed()&&fix.speed>=1f
        followJob=scope.launch {
            changingFollow=true
            try {
                if(followNeedsZoom) {
                    // Recenter also restores a useful driving scale after
                    // browsing a country/world overview. Do this once, never
                    // on each GPS update or surface transfer.
                    followNeedsZoom=false
                    Framework.nativeZoomToPoint(fix.latitude,fix.longitude,if(journey.started)17 else 16,false)
                    delay(100)
                }
                repeat(5) {
                    if(!following.value||!surfaceVisible)return@launch
                    val previous=LocationState.getMode()
                    if(previous==LocationState.FOLLOW_AND_ROTATE){centeredForFollow=true;pendingFollow=false;routeTransition=false;return@launch}
                    if(previous==LocationState.FOLLOW) {
                        centeredForFollow=true
                        routeTransition=false
                        // At rest there may be no trustworthy course. Stay
                        // centered; a later moving fix completes rotation.
                        if(!hasCourse)return@launch
                    }
                    LocationState.nativeSwitchToNextMode()
                    // Camera state is updated by the renderer asynchronously.
                    // Never queue several "next mode" commands against one state.
                    var ticks=0
                    while(LocationState.getMode()==previous&&ticks++<20)delay(50)
                    if(LocationState.getMode()==previous)return@launch
                }
                pendingFollow=LocationState.getMode()!=LocationState.FOLLOW_AND_ROTATE
            }finally {changingFollow=false}
        }
    }
    fun zoom(delta:Int) {
        if(!ready.value||!CoMap.isEngineCreated())return
        // Manual scale stays chosen while position following continues. The
        // recenter button explicitly restores the speed-dependent auto zoom.
        Framework.nativeSetAutoZoomEnabled(false)
        followNeedsZoom=false
        if(delta>0)CoMap.zoomIn()else CoMap.zoomOut()
    }
    fun select(place:NavigationPlace,zoom:Boolean=true) {
        selected.value=place;message.value=""
        setFollowing(false)
        if(ready.value&&zoom)Framework.nativeSetViewportCenter(place.point.latitude,place.point.longitude,17)
    }
    fun choose(result:CoMapsResult) {
        if(!result.suggestion.isNullOrBlank()){search(result.suggestion);return}
        select(result.place);SearchEngine.INSTANCE.showResult(result.index)
    }
    fun clearSelection(){if(!state.value.active){selected.value=null;state.value=state.value.copy(arrived=false);SearchEngine.INSTANCE.cancel();Framework.nativeDeactivatePopup()}}
    fun search(query:String) {
        searchJob?.cancel();timestamp=SystemClock.elapsedRealtimeNanos();results.value=emptyList();searching.value=false
        if(!ready.value||query.trim().length<2)return
        searchJob=scope.launch {
            delay(250);searching.value=true
            val position=gps.position.value
            if(!SearchEngine.INSTANCE.search(app,query.trim(),false,timestamp,position!=null,position?.latitude?:0.0,position?.longitude?:0.0))searching.value=false
        }
    }
    fun build(from:GeoPoint?=null) {
        if(!ready.value||state.value.calculating)return
        val target=selected.value?:return
        val origin=from?:gps.routeOrigin()
        if(origin==null){message.value="Ожидаем точное местоположение GPS";resume();return}
        routeBuildJob?.cancel();journey.reset();routeUpdate.reset();routeRequested=true;routeTransition=true;startAttempts=0
        searchJob?.cancel();timestamp++;SearchEngine.INSTANCE.cancel()
        Framework.nativeDeactivatePopup();Framework.nativeDeactivateMapSelectionCircle(false)
        allowFollow=from==null;routeSession++;maneuverIdentity.reset()
        missingMaps.value=emptyList();voice.stop()
        state.value=state.value.copy(demo=demoDriving.value,active=false,preview=!allowFollow,calculating=true,arrived=false,destination=target.fullName,geometry=emptyList())
        val start=MapObject.createMapObject(FeatureId.EMPTY,if(allowFollow)MapObject.MY_POSITION else MapObject.API_POINT,"Старт","",origin.latitude,origin.longitude)
        val end=MapObject.createMapObject(FeatureId.EMPTY,MapObject.API_POINT,target.name,"",target.point.latitude,target.point.longitude)
        RoutingController.get().prepare(start,end,Router.Vehicle)
    }
    /** Called once after a successful build; native reroutes retain their camera and navigation session. */
    internal fun startGuidance() {
        if(!routeRequested)return
        if(RoutingController.get().isNavigating)return
        val nativeState=Framework.nativeGetRouteFollowingInfo()?.routingSessionState?.name
        if(nativeState !in setOf("RouteNotStarted","OnRoute","OffRoute")) {
            if(nativeState in setOf("RouteBuilding","RouteRebuilding")&&startAttempts++<40) {
                val session=routeSession
                routeBuildJob=scope.launch {delay(50);if(routeRequested&&session==routeSession)startGuidance()}
            }else {routeTransition=false;message.value="Не удалось начать ведение. Постройте маршрут заново."}
            return
        }
        if(!journey.started)journey.built(state.value.geometry.hashCode(),true)
        allowFollow=true;routeTransition=true;following.value=true;pendingFollow=true;centeredForFollow=false
        followJob?.cancel();changingFollow=true
        searchJob?.cancel();SearchEngine.INSTANCE.cancel()
        Framework.nativeDeactivatePopup();Framework.nativeDeactivateMapSelectionCircle(false)
        val fix=locationHelper.savedLocation
        Framework.nativeSet3dMode(true,false)
        Framework.nativeSetAutoZoomEnabled(true)
        if(fix!=null){Framework.nativeZoomToPoint(fix.latitude,fix.longitude,17,false);followNeedsZoom=false}
        else followNeedsZoom=true
        state.value=state.value.copy(preview=false)
        RoutingController.get().start()
        voice.prepare();voice.journey(NavigationJourneyEvent.STARTED)
        if(BuildConfig.DEMO_FEATURES&&demoDriving.value)playDemoRoute()
        followJob=scope.launch {
            delay(250)
            changingFollow=false;followJob=null;applyPendingFollow()
        }
    }
    internal fun updateGuidance(location:Location?=null) {
        if(!routeRequested)return
        val controller=RoutingController.get()
        val info=Framework.nativeGetRouteFollowingInfo()?:return
        val nativeState=info.routingSessionState.name
        if(journey.update(nativeState,location!=null)==NavigationJourneyEvent.ARRIVED){finishArrival();return}
        if(routeUpdate.clearDrawing(nativeState)) {
            // This API removes only drape subroutes. It retains the routing
            // session, destination and camera; the next native InsertRoute adds
            // the replacement drawing. Never close/restart guidance here.
            Framework.nativeRemoveRoute()
        }
        if(CoMapsRouteUpdate.isBuilding(nativeState)) {
            state.value=state.value.copy(geometry=emptyList())
            return
        }
        if(!CoMapsRouteUpdate.ready(nativeState))return
        val geometry=controller.routeGeometry.mJunctions.map {it.mLon to it.mLat}
        val direction=info.carDirection.name
        val distance=coMapsMeters(info.distToTurn.mDistance,info.distToTurn.mUnits.ordinal).toInt()
        val street=info.nextStreet.orEmpty()
        val step=maneuverIdentity.update(direction,street,distance)
        val modifier=when {direction.contains("U_TURN")->"UTURN";direction.contains("LEFT")->"LEFT";direction.contains("RIGHT")->"RIGHT";else->"STRAIGHT"}
        val roundabout=direction.contains("ROUND_ABOUT")
        val instruction=coMapsTurnInstruction(direction,info.exitNum)+if(street.isNotBlank()&&direction!="REACHED_YOUR_DESTINATION")" · $street"else""
        state.value=state.value.copy(calculating=false,active=true,geometry=geometry,instruction=instruction,
            roundaboutExit=info.exitNum.takeIf {roundabout&&it>0},
            maneuver=when(modifier){"UTURN","LEFT"->Maneuver.LEFT;"RIGHT"->Maneuver.RIGHT;else->Maneuver.STRAIGHT},
            street=street,distanceToManeuver=distance,minutes=(info.totalTimeInSeconds/60).coerceAtLeast(1),kilometers=coMapsMeters(info.distToTarget.mDistance,info.distToTarget.mUnits.ordinal)/1000,
            arrivalTime=LocalTime.now().plusSeconds(info.totalTimeInSeconds.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")))
        if(location!=null&&allowFollow&&journey.started&&nativeState=="OnRoute") {
            val motion=gps.voiceMotion()
            voice.announce(routeSession,step,distance,instruction,if(roundabout)"ROUNDABOUT"else"TURN",modifier,false,motion.speedMps,motion.fixAgeMillis,if(direction=="REACHED_YOUR_DESTINATION")""else street)
        }
    }
    private fun finishArrival() {
        endDemo()
        routeRequested=false;allowFollow=false;routeTransition=false;routeBuildJob?.cancel();routeUpdate.reset()
        RoutingController.get().cancel()
        state.value=state.value.copy(active=false,calculating=false,arrived=true,instruction="Вы прибыли",distanceToManeuver=0,minutes=0,kilometers=0.0)
        message.value="";voice.journey(NavigationJourneyEvent.ARRIVED)
    }
    fun stop(announce:Boolean=true) {
        if(!ready.value)return
        endDemo()
        val event=journey.stop()
        routeRequested=false;routeTransition=false;routeBuildJob?.cancel();followJob?.cancel();routeSession++;routeUpdate.reset()
        allowFollow=false;RoutingController.get().cancel();voice.stop()
        state.value=NavigationState(demo=false,active=false,destination="",geometry=emptyList(),instruction="");missingMaps.value=emptyList()
        message.value=""
        if(announce&&event!=null)voice.journey(event)
    }
    fun startDemoTrip() {
        if(!BuildConfig.DEMO_FEATURES)return
        if(!ready.value){initialize();return}
        if(MapManager.nativeGetStatus("Andorra")!=CountryItem.STATUS_DONE) {
            demoPendingDownload=true
            message.value="Для демонстрации скачайте карту Андорры";openFolder(MapManager.nativeGetRoot());download("Andorra")
            return
        }
        stop(false);demoDriving.value=true
        selected.value=NavigationPlace("Encamp · DEMO",GeoPoint(42.5367,1.5824))
        gps.demoLocation(GeoPoint(42.5078,1.5220),0.0,0f)
        demoJob=scope.launch {delay(200);if(demoDriving.value)build()}
    }
    private fun playDemoRoute() {
        demoJob?.cancel()
        val path=CoMapsDemoRoute(state.value.geometry)
        demoJob=scope.launch {
            var traveled=0.0
            while(demoDriving.value&&routeRequested) {
                path.sample(traveled)?.let {(point,heading)->gps.demoLocation(point,heading,12f)}?:break
                if(traveled>=path.length)break
                traveled+=12.0;delay(1000)
            }
        }
    }
    private fun endDemo(){demoPendingDownload=false;demoJob?.cancel();if(demoDriving.value){demoDriving.value=false;gps.endDemo();gps.start()}}
    fun refreshMaps() {
        if(!ready.value)return
        val items=mutableListOf<CountryItem>();MapManager.nativeListItems(folder.value,0.0,0.0,false,false,items)
        regions.value=items.distinctBy {it.id}.map {CoMapsRegion(it.id,it.name.orEmpty(),it.directParentId.orEmpty(),it.totalSize,it.status,it.progress,it.isExpandable,it.errorCode)}
    }
    fun openFolder(id:String){folder.value=id;refreshMaps()}
    fun parentFolder(){val item=CountryItem.fill(folder.value);openFolder(item.directParentId.takeIf {it.isNotBlank()}?:MapManager.nativeGetRoot())}
    fun download(id:String,allowMobile:Boolean=false) {
        if(!ready.value)return
        val connectivity=app.getSystemService(android.net.ConnectivityManager::class.java)
        val network=connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        if(network==null){message.value="Нет подключения к интернету";return}
        if(!allowMobile&&!network.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)&&!network.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            message.value="Разрешите мобильную сеть для скачивания карты";return
        }
        if(allowMobile)MapManager.nativeEnableDownloadOn3g()
        if(!MapManager.nativeHasSpaceToDownloadCountry(id)){message.value="Недостаточно места для карты";return}
        if(MapManager.nativeGetStatus(id)==CountryItem.STATUS_UPDATABLE)MapManager.startUpdate(id)else MapManager.startDownload(id)
    }
    fun cancelDownload(id:String){MapManager.nativeCancel(id)}
    fun deleteMap(id:String) {
        if(state.value.active||state.value.calculating){message.value="Сначала завершите маршрут";return}
        MapManager.nativeDelete(id);refreshMaps();revision.value++
    }
    fun countryAt(point:GeoPoint)=if(ready.value)MapManager.nativeFindCountry(point.latitude,point.longitude)else null
    fun close() {
        if(ready.value) {
            stop(false);locationHelper.stop();RoutingController.get().detach()
            storageSubscription?.let(MapManager::nativeUnsubscribe)
            SearchEngine.INSTANCE.removeListener(searchListener)
            Framework.nativeRemovePlacePageActivationListener(placeListener)
            LocationState.nativeRemoveListener()
        }
        scope.cancel()
    }
}
