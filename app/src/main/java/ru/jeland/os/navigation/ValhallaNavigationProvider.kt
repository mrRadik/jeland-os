package ru.jeland.os.navigation

import android.content.Context
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import com.stadiamaps.ferrostar.core.http.IResponse
import com.valhalla.valhalla.Valhalla
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import ru.jeland.os.core.Maneuver
import ru.jeland.os.core.NavigationState
import uniffi.ferrostar.*
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ValhallaNavigationProvider(context:Context,val maps:OfflineMapManager,val gps:AndroidGpsSource):NavigationProvider {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val mutable=MutableStateFlow(NavigationState(active=false,demo=false,destination="Выберите точку",street="",minutes=0,kilometers=0.0,distanceToManeuver=0,arrivalTime="—",status="Выберите назначение · офлайн"))
    override val state=mutable.asStateFlow()
    val bridge:ClusterNavigationBridge=MockClusterNavigationBridge()
    private var actor:Valhalla?=null
    private var core:FerrostarCore?=null
    private var stateJob:Job?=null
    val voice=NavigationVoice(context)
    val voiceStatus=voice.status
    var routeRequests=0;private set
    var lastRouteMillis=0L;private set
    var lastRawResponse="";private set
    private val app=context.applicationContext
    private val nativeMutex=Mutex()
    private val initMutex=Mutex()
    private val sessionGate=NavigationSessionGate()
    @Volatile private var generation=0
    private val routeGeneration=java.util.concurrent.atomic.AtomicInteger()
    private var voiceGeometry:List<GeographicCoordinate>?=null
    private var voiceSession=0
    fun setPreview(value:Boolean){mutable.update {it.copy(preview=value)}}
    fun nameDestination(value:String){mutable.update {it.copy(destination=value)}}
    private suspend fun engine():FerrostarCore {
        core?.let{return it}
        maps.prepare()
        val config=JSONObject(app.assets.open("navigation/default.json").bufferedReader().use{it.readText()})
        config.getJSONObject("mjolnir").apply {put("tile_extract",maps.graphFile.absolutePath);put("tile_dir",maps.directory.absolutePath);remove("tile_url")}
        val configFile=File(maps.directory,"valhalla.json").apply {writeText(config.toString())}
        val localActor=Valhalla(configFile.absolutePath)
        actor=localActor
        val token=generation
        val local=CustomRouteProvider {location,waypoints->withContext(Dispatchers.IO) {nativeMutex.withLock {
            check(token==generation){"Регион маршрутизации изменён"}
            val started=android.os.SystemClock.elapsedRealtime()
            val points=listOf(location.coordinates)+waypoints.map {it.coordinate}
            val request=JSONObject().put("locations",JSONArray().apply {points.forEach {put(JSONObject().put("lat",it.lat).put("lon",it.lng))}})
                .put("costing","auto").put("format","osrm").put("language","ru-RU")
                .put("banner_instructions",true).put("voice_instructions",true).put("units","kilometers")
            val raw=localActor.routeRaw(request.toString())
            lastRawResponse=raw
            val response=JSONObject(raw)
            check(response.optString("code")=="Ok"){raw.take(500)}
            val routeJson=response.getJSONArray("routes").getJSONObject(0).toString().toByteArray()
            val allWaypoints=listOf(Waypoint(location.coordinates,WaypointKind.BREAK))+waypoints
            val route=createRouteFromOsrmRoute(routeJson,allWaypoints,6u)
            routeRequests++
            lastRouteMillis=android.os.SystemClock.elapsedRealtime()-started
            android.util.Log.i("JelandNavigation","LOCAL_ROUTE request=$routeRequests ms=$lastRouteMillis steps=${route.steps.size} meters=${route.distance}")
            listOf(route)
        }}}
        // This provider fails closed if any future change tries to perform HTTP routing.
        val noHttp=object:HttpClientProvider {override suspend fun call(request:RouteRequest):IResponse=error("HTTP routing запрещён в офлайн PoC")}
        val configNavigation=NavigationControllerConfig(
            WaypointAdvanceMode.WaypointWithinRange(25.0),stepAdvanceDistanceEntryAndSnappedExit(25u,15u,40u),
            stepAdvanceDistanceToEndOfStep(15u,40u),RouteDeviationTracking.StaticThreshold(40u,40.0),CourseFiltering.SNAP_TO_ROUTE)
        val sessionLocations=object:com.stadiamaps.ferrostar.core.location.NavigationLocationProviding {
            override suspend fun lastLocation()=gps.lastLocation()
            override fun locationUpdates(intervalMillis:Long)=sessionGate.updates(gps.locationUpdates(intervalMillis))
        }
        return FerrostarCore(local,noHttp,sessionLocations,configNavigation).also {engine->
            core=engine
            stateJob=scope.launch {engine.state.collect {s->sessionGate.access {
                // A collected Navigating value can be queued while stop resets the SDK.
                // Do not let that stale emission resurrect the stopped route or widget.
                if(core!==engine||engine.state.value!==s)return@access
                val trip=s.tripState
                if(trip is TripState.Navigating) {
                    val progress=trip.progress
                    val content=trip.visualInstruction?.primaryContent
                    val text=guidanceLabel(content?.maneuverType?.name,content?.maneuverModifier?.name,
                        content?.text?:trip.remainingSteps.firstOrNull()?.instruction.orEmpty())
                    val modifier=content?.maneuverModifier?.name.orEmpty()
                    mutable.update {it.copy(active=true,instruction=text,street=trip.remainingSteps.firstOrNull()?.roadName.orEmpty(),
                        maneuver=when {modifier.contains("LEFT")->Maneuver.LEFT;modifier.contains("RIGHT")->Maneuver.RIGHT;else->Maneuver.STRAIGHT},
                        distanceToManeuver=progress.distanceToNextManeuver.toInt(),minutes=kotlin.math.ceil(progress.durationRemaining/60).toInt(),
                        kilometers=progress.distanceRemaining/1000,arrivalTime=LocalTime.now().plusSeconds(progress.durationRemaining.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")),
                        geometry=s.routeGeometry.map {it.lat to it.lng},status=if(s.isCalculatingNewRoute)"Перестроение локально…"else"Офлайн · Ferrostar + Valhalla" )}
                    bridge.publish(NavigationGuidance(text,progress.distanceToNextManeuver,progress.durationRemaining,
                        trip.visualInstruction?.primaryContent?.maneuverType?.name,trip.visualInstruction?.primaryContent?.maneuverModifier?.name))
                    val voiceMotion=gps.voiceMotion()
                    // A real reroute is a new voice session even when the remaining step count is unchanged.
                    if(voiceGeometry!=s.routeGeometry){voiceGeometry=s.routeGeometry.toList();voiceSession++}
                    voice.announce(voiceSession,trip.remainingSteps.size,progress.distanceToNextManeuver.toInt(),text,
                        content?.maneuverType?.name,content?.maneuverModifier?.name,state.value.preview,voiceMotion.speedMps,voiceMotion.fixAgeMillis)
                } else if(trip is TripState.Complete){
                    voice.announce(voiceSession,0,0,"Вы прибыли к месту назначения","ARRIVE",null,state.value.preview)
                    mutable.update {it.copy(active=false,status="Прибыли")};bridge.publish(null)
                }
            }}}
        }
    }
    override suspend fun route(from:GeoPoint,to:GeoPoint)=withContext(Dispatchers.IO) {
        val routeToken=routeGeneration.incrementAndGet()
        voice.stop()
        voice.prepare()
        mutable.update {it.copy(calculating=true,status="Построение маршрута…")}
        try {
            val region=maps.prepare()
            require(region.contains(from)&&region.contains(to)){"Начало или назначение вне загруженного региона"}
            val engine=initMutex.withLock {engine()}
            val fix=gps.lastLocation()?.takeIf {it.provider==android.location.LocationManager.GPS_PROVIDER&&it.latitude==from.latitude&&it.longitude==from.longitude}
            val origin=UserLocation(GeographicCoordinate(from.latitude,from.longitude),fix?.accuracy?.toDouble()?:5.0,null,
                fix?.let {Instant.ofEpochMilli(it.time)}?:Instant.now(),null)
            val routes=engine.getRoutes(origin,listOf(Waypoint(GeographicCoordinate(to.latitude,to.longitude),WaypointKind.BREAK)))
            ensureActive()
            if(routeToken!=routeGeneration.get())return@withContext
            sessionGate.access {
                if(routeToken==routeGeneration.get()) {
                    engine.startNavigation(routes.first())
                    mutable.update {it.copy(calculating=false,destination="${to.latitude}, ${to.longitude}")}
                }
            }
        } catch(e:CancellationException){throw e} catch(e:Exception) {
            if(routeToken==routeGeneration.get()) {
                sessionGate.access {
                    if(routeToken==routeGeneration.get()) {
                        core?.stopNavigation();bridge.publish(null)
                        mutable.update {it.copy(calculating=false,active=false,geometry=emptyList(),instruction="",status="Не удалось построить маршрут: ${e.message}")}
                    }
                }
            };throw e
        }
    }
    override fun stop(){
        routeGeneration.incrementAndGet()
        sessionGate.blocking {
            core?.stopNavigation()
            voice.stop()
            voiceGeometry=null
            mutable.update {it.copy(calculating=false,preview=false,active=false,geometry=emptyList(),instruction="",status="Маршрут остановлен")}
            bridge.publish(null)
        }
    }
    fun clearDestination() {
        stop() // Invalidates in-flight native routing and pending speech before removing the selection.
        mutable.update {it.copy(destination="Выберите точку",street="",distanceToManeuver=0,minutes=0,kilometers=0.0,
            arrivalTime="—",status="Выберите назначение · офлайн")}
    }
    suspend fun releaseRegion()=initMutex.withLock {
        generation++;stop();stateJob?.cancelAndJoin();stateJob=null;core=null
        nativeMutex.withLock {actor?.close();actor=null}
    }
    fun enableOfflineVoice()=voice.setEnabled(true)
    override fun close(){
        // Close mmap-backed native data only after an in-flight JNI route has returned.
        runBlocking(Dispatchers.IO){releaseRegion()}
        scope.cancel();gps.close();voice.close()
    }
}
