package ru.jeland.os.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Android GNSS, not Google fused location. Simulation is explicit and never called GPS. */
class AndroidGpsSource(context:Context):AutoCloseable {
    private val app=context.applicationContext
    private val manager=app.getSystemService(LocationManager::class.java)
    private val flow=MutableSharedFlow<Location>(replay=1,extraBufferCapacity=32)
    @Volatile private var last:Location?=null
    private var listening=false
    private var demo=false
    private val courseTracker=NavigationCourseTracker()
    val position=kotlinx.coroutines.flow.MutableStateFlow<GeoPoint?>(null)
    val bearing=kotlinx.coroutines.flow.MutableStateFlow<Double?>(null)
    val status=kotlinx.coroutines.flow.MutableStateFlow("GPS не запрошен")
    private val listener=LocationListener {location->
        last=location;position.value=GeoPoint(location.latitude,location.longitude);flow.tryEmit(location)
        bearing.value=courseTracker.update(GeoPoint(location.latitude,location.longitude),location.elapsedRealtimeNanos/1_000_000,
            location.bearing.toDouble().takeIf {location.hasBearing()},location.speed.toDouble().takeIf {location.hasSpeed()},
            location.accuracy.toDouble().takeIf {location.hasAccuracy()})
        @Suppress("DEPRECATION") val mocked=location.isFromMockProvider
        status.value="${if(mocked)"ТЕСТОВЫЙ GPS-провайдер"else"GNSS"} · точность ±${location.accuracy.toInt()} м"
    }
    fun start():Boolean {
        if(demo)return true
        if(app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){status.value="Разрешите доступ к GPS";return false}
        if(listening)return true
        return try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,listener,Looper.getMainLooper())
            listening=true
            status.value=if(manager.isProviderEnabled(LocationManager.GPS_PROVIDER))"Ожидаем спутниковый фикс…"else"Спутниковое позиционирование выключено в настройках"
            true
        }catch(e:IllegalArgumentException){false}catch(e:SecurityException){false}
    }
    fun routeOrigin():GeoPoint? {
        val location=last?:return null
        val age=(android.os.SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos)/1_000_000
        @Suppress("DEPRECATION") val mocked=location.isFromMockProvider
        val demoFix=ru.jeland.os.BuildConfig.DEMO_FEATURES&&demo&&location.provider=="jeland-demo"
        return if((demoFix||!mocked&&location.provider==LocationManager.GPS_PROVIDER)&&age in 0..30000&&location.hasAccuracy()&&location.accuracy<=100)
            GeoPoint(location.latitude,location.longitude)else null
    }
    fun voiceMotion():NavigationVoiceMotion {
        val fix=last?:return NavigationVoiceMotion()
        val age=(android.os.SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)/1_000_000
        if((fix.provider!=LocationManager.GPS_PROVIDER&&!(ru.jeland.os.BuildConfig.DEMO_FEATURES&&demo))||age !in 0..3000)return NavigationVoiceMotion()
        return NavigationVoiceMotion(fix.speed.toDouble().takeIf {fix.hasSpeed()&&it.isFinite()&&it in 0.0..70.0},age)
    }
    fun locationUpdates(intervalMillis:Long)=flow.asSharedFlow()
    internal fun demoLocation(point:GeoPoint,heading:Double,speedMps:Float) {
        check(ru.jeland.os.BuildConfig.DEMO_FEATURES)
        if(!demo){pause();demo=true}
        listener.onLocationChanged(Location("jeland-demo").apply {
            latitude=point.latitude;longitude=point.longitude;accuracy=3f;bearing=heading.toFloat();speed=speedMps
            time=System.currentTimeMillis();elapsedRealtimeNanos=android.os.SystemClock.elapsedRealtimeNanos()
        })
        status.value="DEMO · имитация движения"
    }
    internal fun endDemo() {
        if(!demo)return
        demo=false;last=null;position.value=null;bearing.value=null;courseTracker.reset()
    }
    fun pause(){manager.removeUpdates(listener);listening=false}
    override fun close(){pause()}
}
