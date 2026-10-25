package ru.jeland.os.navigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import ru.jeland.os.core.NavigationState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One reusable offscreen snapshotter while the home widget is visible. No second MapView,
 * no HTTP, and no changes to the full-screen map's saved camera or overview.
 * All calls (including cancellation) are made from the main dispatcher.
 */
internal class WidgetMapSnapshot(private val context:Context,private val width:Int,private val height:Int) {
    private var snapshotter:MapSnapshotter?=null
    private var lastBearing=0.0
    private val arrow by lazy {vehicleArrow()}
    suspend fun render(maps:OfflineMapManager,state:NavigationState,position:GeoPoint?,heading:Double?,dark:Boolean):NavigationPreview {
        val region=checkNotNull(maps.region.value)
        val fix=position?.takeIf {region.contains(it)}
        val bearing=navigationBearing(navigationCourse(state,fix,heading),lastBearing).also {lastBearing=it}
        val focus=if(state.active)routePreviewWindow(state.geometry,fix,state.distanceToManeuver,width,height,bearing)else null
        val center=focus?.center?:fix?:region.center
        val style=JSONObject(localStyle(maps,dark))
        // Attribution is displayed once by the widget, not baked behind the instruction panel.
        style.getJSONObject("sources").getJSONObject("region").remove("attribution")
        style.getJSONObject("sources").optJSONObject("overview")?.remove("attribution")
        focus?.let {
            val line=JSONObject().put("type","Feature").put("properties",JSONObject()).put("geometry",
                JSONObject().put("type","LineString").put("coordinates",JSONArray().apply {
                    it.points.forEach {p->put(JSONArray().put(p.longitude).put(p.latitude))}
                }))
            style.getJSONObject("sources").put("widget-route",JSONObject().put("type","geojson").put("data",line))
            style.getJSONArray("layers").put(JSONObject("""{"id":"widget-route-shadow","type":"line","source":"widget-route","layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"#00557a","line-width":13}}"""))
            style.getJSONArray("layers").put(JSONObject("""{"id":"widget-route-line","type":"line","source":"widget-route","layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"#20caff","line-width":8}}"""))
        }
        val camera=CameraPosition.Builder().target(LatLng(center.latitude,center.longitude)).zoom(focus?.zoom?:15.5)
            .bearing(bearing)
            .padding(0.0,0.0,0.0,height*.27).build()
        MapLibre.getInstance(context)
        val renderer=snapshotter?.also {it.setStyleJson(style.toString());it.setCameraPosition(camera)}?:MapSnapshotter(context,
            MapSnapshotter.Options(width,height).withPixelRatio(1f).withLogo(false)
                .withStyleBuilder(Style.Builder().fromJson(style.toString())).withCameraPosition(camera)).also {snapshotter=it}
        return suspendCancellableCoroutine {continuation->
            continuation.invokeOnCancellation {renderer.cancel()}
            renderer.start({snapshot->
                if(continuation.isActive) {
                    val bitmap=snapshot.bitmap.copy(Bitmap.Config.ARGB_8888,true)
                    fix?.let {
                        val pixel=snapshot.pixelForLatLng(LatLng(it.latitude,it.longitude))
                        val canvas=Canvas(bitmap);val markerSize=(width*.065f).coerceIn(24f,40f)
                        canvas.save();canvas.translate(pixel.x,pixel.y);canvas.rotate(navigationMarkerRotation(bearing,camera.bearing))
                        canvas.drawBitmap(arrow,null,android.graphics.RectF(-markerSize/2,-markerSize/2,markerSize/2,markerSize/2),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG));canvas.restore()
                    }
                    continuation.resume(NavigationPreview(bitmap,dark,region.id,if(state.active)state.geometry.hashCode()else 0,focus,bearing))
                }
            },{error->if(continuation.isActive)continuation.resumeWithException(IllegalStateException(error))})
        }
    }
    fun cancel(){snapshotter?.cancel()}
}
