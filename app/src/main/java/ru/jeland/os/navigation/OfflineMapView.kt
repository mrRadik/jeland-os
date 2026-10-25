package ru.jeland.os.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.json.JSONObject
import ru.jeland.os.core.NavigationState
import ru.jeland.os.ui.Control
import ru.jeland.os.ui.Label
import ru.jeland.os.ui.Panel
import ru.jeland.os.ui.SectionTitle
import kotlinx.coroutines.delay

enum class MapViewportAction { ZOOM_IN, ZOOM_OUT, NORTH, POSITION, OVERVIEW, DESTINATION, RUSSIA }
data class MapViewportRequest(val sequence:Int,val action:MapViewportAction)

@Composable fun OfflineMap(maps:OfflineMapManager,state:NavigationState,dark:Boolean,modifier:Modifier=Modifier,
    position:GeoPoint?=null,destination:GeoPoint?=null,camera:NavigationCamera?=null,onCamera:(NavigationCamera)->Unit={},follow:Boolean=true,onDestination:(NavigationPlace)->Unit={},request:MapViewportRequest?=null,onGesture:()->Unit={},onReady:()->Unit={},onError:(String)->Unit={},heading:Double?=null,northUp:Boolean=false) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val context=LocalContext.current
    val view=remember(context) {
        MapLibre.getInstance(context)
        MapView(context,MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            // AndroidView's hierarchy saver must not query native camera after onRelease.
            // Route/session state belongs to the repository, not a destroyed native View.
            isSaveEnabled=false;isSaveFromParentEnabled=false;onCreate(null)
        }
    }
    val released=remember(view) {booleanArrayOf(false)}
    var map by remember {mutableStateOf<MapLibreMap?>(null)}
    val readyCallback by rememberUpdatedState(onReady)
    val errorCallback by rememberUpdatedState(onError)
    val gestureCallback by rememberUpdatedState(onGesture)
    val geometry by rememberUpdatedState(state.geometry)
    val choose by rememberUpdatedState(onDestination)
    val saveCamera by rememberUpdatedState(onCamera)
    val latestPosition by rememberUpdatedState(position)
    val latestDestination by rememberUpdatedState(destination)
    val resolvedCourse=navigationCourse(state,position,heading)
    var heldCourse by remember {mutableStateOf(navigationBearing(camera?.bearing))}
    val vehicleCourse=resolvedCourse?:heldCourse
    val latestCourse by rememberUpdatedState(vehicleCourse)
    LaunchedEffect(resolvedCourse) {resolvedCourse?.let {heldCourse=it}}
    fun updateRoute(style:Style) {
        if(released[0])return
        val source=style.getSourceAs<GeoJsonSource>("route")?:return
        val points=geometry.map {Point.fromLngLat(it.second,it.first)}
        if(points.size>=2)source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(points)))
        else source.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[]}")
    }
    fun updatePoints(style:Style) {
        if(released[0])return
        for((name,point) in listOf("position" to latestPosition,"destination" to latestDestination)) {
            style.getSourceAs<GeoJsonSource>(name)?.let {source->
                if(point==null)source.setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[]}")
                else source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(point.longitude,point.latitude)))
            }
        }
    }
    DisposableEffect(view,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(!released[0])when(event){
            Lifecycle.Event.ON_START->view.onStart();Lifecycle.Event.ON_RESUME->view.onResume()
            Lifecycle.Event.ON_PAUSE->view.onPause();Lifecycle.Event.ON_STOP->view.onStop();else->Unit}}
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))view.onStart()
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))view.onResume()
        onDispose {lifecycle.removeObserver(observer);if(!released[0]){view.onPause();view.onStop()}}
    }
    AndroidView(modifier=modifier,onRelease={
        released[0]=true;map=null
        it.onPause();it.onStop();it.onDestroy()
    },factory={
        view.also {v->
            v.addOnDidFailLoadingMapListener {message->if(!released[0])errorCallback(message)}
            v.addOnDidFinishRenderingFrameListener {fully,_,_->if(!released[0]&&fully&&map?.style?.isFullyLoaded==true) {
                readyCallback()
            }}
            v.getMapAsync {m->
                if(released[0])return@getMapAsync
                map=m
                val center=maps.region.value?.center?:GeoPoint(42.507,1.53)
                val target=camera?.target?:center
                m.uiSettings.apply {isLogoEnabled=false;isAttributionEnabled=false;isCompassEnabled=false}
                m.cameraPosition=CameraPosition.Builder().target(LatLng(target.latitude,target.longitude)).zoom(camera?.zoom?:13.5).bearing(camera?.bearing?:0.0).tilt(camera?.tilt?:0.0).build()
                m.addOnCameraMoveStartedListener {reason->if(reason==1)gestureCallback()}
                m.addOnCameraIdleListener {
                    if(!released[0])m.cameraPosition.let {p->p.target?.let {saveCamera(NavigationCamera(GeoPoint(it.latitude,it.longitude),p.zoom,p.bearing,p.tilt))}}
                }
                m.addOnMapClickListener {point->
                    choose(tappedPlace(m,point));true
                }
                m.addOnMapLongClickListener {point->choose(tappedPlace(m,point));true}
            }
        }
    })
    LaunchedEffect(map,dark) {
        if(!released[0])map?.setStyle(Style.Builder().fromJson(localStyle(maps,dark))) {style->
            if(released[0])return@setStyle
            style.addImage("vehicle-arrow",vehicleArrow())
            style.addSource(GeoJsonSource("route","{\"type\":\"FeatureCollection\",\"features\":[]}"))
            style.addLayer(LineLayer("route-shadow","route").withProperties(lineColor("#00557a"),lineWidth(12f),lineJoin("round"),lineCap("round")))
            style.addLayer(LineLayer("route-line","route").withProperties(lineColor("#20caff"),lineWidth(7f),lineJoin("round"),lineCap("round")))
            for((name,color) in listOf("position" to "#ffffff","destination" to "#ffb455")) {
                style.addSource(GeoJsonSource(name,"{\"type\":\"FeatureCollection\",\"features\":[]}"))
                // Map-aligned symbol uses the world course. The camera applies the inverse
                // rotation; subtracting its bearing here would rotate the vehicle twice.
                if(name=="position")style.addLayer(SymbolLayer("position-arrow",name).withProperties(iconImage("vehicle-arrow"),iconSize(1f),iconAllowOverlap(true),iconIgnorePlacement(true),iconRotationAlignment("map"),iconRotate(latestCourse.toFloat())))
                else style.addLayer(CircleLayer("$name-dot",name).withProperties(circleColor(color),circleRadius(8f),circleStrokeColor("#00caff"),circleStrokeWidth(3f)))
            }
            updateRoute(style)
            updatePoints(style)
            android.util.Log.i("JelandNavigation","LOCAL_MAP_STYLE_READY dark=$dark")
            // MapView's default loading foreground can cover a static offline map until
            // three frames occur. We already show our import status; clear that overlay.
            view.foreground=null

        }
    }
    LaunchedEffect(state.geometry,map) {map?.style?.let {updateRoute(it)}}
    LaunchedEffect(position,destination,map) {map?.style?.let {updatePoints(it)}}
    LaunchedEffect(vehicleCourse,map) {if(!released[0])map?.style?.getLayerAs<SymbolLayer>("position-arrow")?.setProperties(iconRotate(vehicleCourse.toFloat()))}
    LaunchedEffect(position,vehicleCourse,follow,northUp,map,state.active) {
        val point=position
        val m=map
        if(!released[0]&&follow&&point!=null&&m!=null&&maps.region.value?.contains(point)==true) {
            val previous=m.cameraPosition
            val camera=navigationFollowCamera(point,NavigationCamera(point,previous.zoom,previous.bearing,previous.tilt),state.active,if(northUp)0.0 else vehicleCourse)
            m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(previous).target(LatLng(point.latitude,point.longitude))
                .zoom(camera.zoom).tilt(camera.tilt).bearing(camera.bearing).build()),700)
        }
    }
    LaunchedEffect(request,map) {
        val m=map
        if(!released[0]&&m!=null)when(request?.action) {
            MapViewportAction.ZOOM_IN->m.animateCamera(CameraUpdateFactory.zoomIn())
            MapViewportAction.ZOOM_OUT->m.animateCamera(CameraUpdateFactory.zoomOut())
            MapViewportAction.NORTH->m.animateCamera(CameraUpdateFactory.bearingTo(0.0))
            MapViewportAction.POSITION->{val p=position?:maps.region.value?.center;if(p!=null) {
                val previous=m.cameraPosition
                val target=navigationFollowCamera(p,NavigationCamera(p,maxOf(15.5,previous.zoom),previous.bearing,previous.tilt),state.active,if(northUp)0.0 else vehicleCourse)
                m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(previous)
                    .target(LatLng(p.latitude,p.longitude)).zoom(target.zoom).tilt(target.tilt).bearing(target.bearing).build()),700)
            }}
            MapViewportAction.OVERVIEW->if(state.geometry.size>1)m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(state.geometry.map {LatLng(it.first,it.second)}).build(),72))
            MapViewportAction.DESTINATION->destination?.let {m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude,it.longitude),maxOf(15.5,m.cameraPosition.zoom)))}
            // A whole-country fit on a tall HU forces the native camera towards the
            // equator (world-height constraint). Start at a readable Russian overview;
            // all of Russia is available by panning/zooming, including the dateline.
            MapViewportAction.RUSSIA->m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(62.0,80.0),4.0))
            null->Unit
        }
    }
    LaunchedEffect(state.geometry,map) {
        val m=map
        if(!released[0]&&!follow&&request?.action!=MapViewportAction.RUSSIA&&state.geometry.size>2&&m!=null) {
            val bounds=LatLngBounds.Builder().includes(state.geometry.map {LatLng(it.first,it.second)}).build()
            m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds,32))
        }
    }
}

/** A crisp navigation cursor, not an invented vehicle position. Source exists only for a real/explicit preview fix. */
internal fun vehicleArrow():android.graphics.Bitmap {
    val bitmap=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888)
    val canvas=android.graphics.Canvas(bitmap)
    val path=android.graphics.Path().apply {moveTo(32f,6f);lineTo(54f,55f);lineTo(32f,45f);lineTo(10f,55f);close()}
    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.rgb(21,184,255);style=android.graphics.Paint.Style.FILL}
    canvas.drawPath(path,paint)
    paint.style=android.graphics.Paint.Style.STROKE;paint.strokeWidth=3f;paint.color=android.graphics.Color.WHITE;paint.strokeJoin=android.graphics.Paint.Join.ROUND
    canvas.drawPath(path,paint)
    return bitmap
}

/** Hit test the visible building, then find an actual address inside its geometry.
 * Query source points, not just rendered labels: collisions may hide a valid house number.
 * Never borrow a nearby address from the building across the road.
 */
internal fun tappedPlace(map:MapLibreMap,point:LatLng):NavigationPlace {
    val clicked=GeoPoint(point.latitude,point.longitude)
    val building=map.queryRenderedFeatures(map.projection.toScreenLocation(point),"buildings").firstOrNull()
        ?:return NavigationPlace("Точка на карте",clicked)
    fun rings(coordinates:List<List<Point>>)=coordinates.map {ring->ring.map {GeoPoint(it.latitude(),it.longitude())}}
    val footprints=when(val shape=building.geometry()) {
        is org.maplibre.geojson.Polygon->listOf(rings(shape.coordinates()))
        is org.maplibre.geojson.MultiPolygon->shape.coordinates().map(::rings)
        else->emptyList()
    }
    val address=map.style?.getSourceAs<VectorSource>("region")?.querySourceFeatures(arrayOf("addresses"),null)
        ?.filter {feature->val p=feature.geometry() as? Point
            p!=null&&feature.hasProperty("house_number")&&footprints.any {insideFootprint(GeoPoint(p.latitude(),p.longitude()),it)}}
        ?.minByOrNull {feature->val p=feature.geometry() as Point
            (p.latitude()-clicked.latitude)*(p.latitude()-clicked.latitude)+(p.longitude()-clicked.longitude)*(p.longitude()-clicked.longitude)}
    val number=address?.getStringProperty("house_number").orEmpty()
    val street=address?.takeIf {it.hasProperty("street")}?.getStringProperty("street").orEmpty()
    // Keep the chosen point inside the tapped footprint. The router snaps to the
    // drivable network; a geocoded building centre is not a verified car entrance.
    return NavigationPlace(if(number.isNotBlank())listOf(street,number).filter {it.isNotBlank()}.joinToString(", ")else
        building.takeIf {it.hasProperty("name")}?.getStringProperty("name")?.takeIf {it.isNotBlank()}?:"Здание на карте",clicked,"building")
}

internal fun localStyle(maps:OfflineMapManager,dark:Boolean):String {
    val tiled=maps.mapFile().extension=="pmtiles"
    val uri=android.net.Uri.fromFile(maps.mapFile()).toString()
    val source=if(tiled)"""{"type":"vector","url":${JSONObject.quote("pmtiles://$uri")},"attribution":"© OpenStreetMap contributors · ODbL · Geofabrik"}"""
        else """{"type":"geojson","data":${JSONObject.quote(uri)},"attribution":"© OpenStreetMap contributors · ODbL · Geofabrik"}"""
    val background=if(dark)"#071b29"else"#e6e5de"
    val road=if(dark)"#61879d"else"#ffffff"
    val building=if(dark)"#16344a"else"#d3d0c7"
    val land=if(dark)"#16372c"else"#b3caa0"
    val text=if(dark)"#d6e9f4"else"#34424c"
    val style=JSONObject("""{"version":8,"name":"Jeland local region","glyphs":"asset://navigation/fonts/{fontstack}/{range}.pbf","sources":{"region":$source},"layers":[
        {"id":"background","type":"background","paint":{"background-color":"$background"}},
        {"id":"land","type":"fill","source":"region","filter":["all",["==","kind","land"],["==","${'$'}type","Polygon"]],"paint":{"fill-color":"$land"}},
        {"id":"buildings","type":"fill","source":"region","filter":["all",["==","kind","building"],["==","${'$'}type","Polygon"]],"paint":{"fill-color":"$building"}},
        {"id":"water","type":"line","source":"region","filter":["==","kind","water"],"paint":{"line-color":"#247eac","line-width":2}},
        {"id":"minor-roads","type":"line","source":"region","filter":["all",["==","kind","road"],["in","highway","service","footway","path","track","steps","pedestrian","cycleway"]],"paint":{"line-color":"$road","line-opacity":0.45,"line-width":["interpolate",["linear"],["zoom"],8,0.3,13,0.8,17,2]}},
        {"id":"roads","type":"line","source":"region","filter":["all",["==","kind","road"],["!in","highway","service","footway","path","track","steps","pedestrian","cycleway"]],"paint":{"line-color":"$road","line-width":["interpolate",["linear"],["zoom"],8,0.7,13,1.8,17,4]}},
        {"id":"major-roads","type":"line","source":"region","filter":["all",["==","kind","road"],["in","highway","motorway","trunk","primary","secondary"]],"paint":{"line-color":"${if(dark)"#adc5cb"else"#e5ba75"}","line-width":["interpolate",["linear"],["zoom"],8,1,13,3,17,7]}},
        {"id":"road-labels","type":"symbol","source":"region","minzoom":12,"filter":["all",["==","kind","road"],["!=","name",""]],"layout":{"symbol-placement":"line","text-field":["get","name"],"text-font":["Noto Sans Regular"],"text-size":15,"text-max-angle":30,"symbol-spacing":300},"paint":{"text-color":"$text","text-halo-color":"$background","text-halo-width":2}}
    ]}""")
    val overview=maps.overviewFile()
    if(overview.exists()) {
        style.getJSONObject("sources").put("overview",JSONObject().put("type","vector").put("url","pmtiles://${android.net.Uri.fromFile(overview)}")
            .put("attribution","© OpenStreetMap contributors · Protomaps · Natural Earth · ODbL"))
        val extra=org.json.JSONArray("""[
            {"id":"overview-earth","type":"fill","source":"overview","source-layer":"earth","paint":{"fill-color":"${if(dark)"#102b3a"else"#e6e5de"}"}},
            {"id":"overview-water","type":"fill","source":"overview","source-layer":"water","paint":{"fill-color":"${if(dark)"#092137"else"#b8d9e8"}"}},
            {"id":"overview-boundaries","type":"line","source":"overview","source-layer":"boundaries","maxzoom":11,"filter":["==","kind","country"],"paint":{"line-color":"${if(dark)"#557185"else"#b4a3a0"}","line-width":1,"line-dasharray":[4,3]}},
            {"id":"overview-roads","type":"line","source":"overview","source-layer":"roads","maxzoom":11,"paint":{"line-color":"${if(dark)"#7b9faf"else"#d4ad66"}","line-width":["interpolate",["linear"],["zoom"],2,0.5,4,1.2,6,2,11,3.5]}},
            {"id":"overview-places","type":"symbol","source":"overview","source-layer":"places","maxzoom":11,
             "layout":{"text-field":["coalesce",["get","name:ru"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":13,"text-padding":5},
             "paint":{"text-color":"$text","text-halo-color":"$background","text-halo-width":1.5}}
        ]""")
        val merged=org.json.JSONArray().put(style.getJSONArray("layers").getJSONObject(0))
        for(i in 0 until extra.length())merged.put(extra.getJSONObject(i))
        val existing=style.getJSONArray("layers")
        for(i in 1 until existing.length())merged.put(existing.getJSONObject(i))
        style.put("layers",merged)
    }
    if(tiled) {
        val layers=style.getJSONArray("layers")
        for(i in 0 until layers.length())layers.getJSONObject(i).let {if(it.optString("source")=="region"&&!it.has("source-layer"))it.put("source-layer","region")}
        if((maps.region.value?.houseNumberCount?:0)>0)layers.put(JSONObject("""{
            "id":"house-numbers","type":"symbol","source":"region","source-layer":"addresses","minzoom":16,
            "filter":["!=","house_number",""],
            "layout":{"symbol-placement":"point","text-field":["get","house_number"],"text-font":["Noto Sans Regular"],
                "text-size":["interpolate",["linear"],["zoom"],16,11,18,14,20,16],
                "text-padding":3,"text-allow-overlap":false,"text-ignore-placement":false},
            "paint":{"text-color":"${if(dark)"#dceaf1"else"#344750"}","text-halo-color":"${if(dark)"#102a3d"else"#f5f5ef"}","text-halo-width":1.2}
        }"""))
    }
    return style.toString()
}
