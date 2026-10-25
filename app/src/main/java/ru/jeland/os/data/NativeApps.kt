package ru.jeland.os.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import ru.jeland.os.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class NativeApps(context:Context,private val report:(String)->Unit={}) {
    private val context=context.applicationContext
    companion object {
        const val SETTINGS_PACKAGE="com.desaysv.setting"
        const val WEATHER_PACKAGE="com.liontech.weather"
        const val ENGINEERING_PACKAGE="com.desaysv.engmode"
        fun engineeringIntent()=Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(ENGINEERING_PACKAGE,"com.desaysv.engmode.MainActivity"))
        fun settingsIntent()=Intent("com.desaysv.vehiclesetting.ACTION_VEHICLE_SETTING")
            .setComponent(ComponentName(SETTINGS_PACKAGE,"com.desaysv.setting.ui.activity.SettingActivity"))
        fun weatherIntent()=Intent("com.liontech.weather.ACTION_TO_OPEN_ACTIVITY")
            .setComponent(ComponentName(WEATHER_PACKAGE,"com.liontech.weather.SplashActivity"))
    }
    @Suppress("DEPRECATION") private fun available(intent:Intent)=runCatching {
        context.packageManager.getActivityInfo(intent.component!!,0).let {it.exported&&it.enabled&&it.applicationInfo.enabled}
    }.getOrDefault(false)
    val settingsAvailable get()=available(settingsIntent())
    val weatherAvailable get()=available(weatherIntent())
    val engineeringAvailable get()=available(engineeringIntent())
    fun openEngineering()=open(engineeringIntent())
    fun openSettings():Boolean {
        if(settingsAvailable)return open(settingsIntent())
        return runCatching {context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true}
            .getOrElse {report("NATIVE_SETTINGS unavailable ${it.javaClass.simpleName}");false}
    }
    fun openWeather()=open(weatherIntent())
    private fun open(intent:Intent):Boolean {
        if(!available(intent)){report("NATIVE_APP unavailable ${intent.component?.packageName}");return false}
        return runCatching {context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));report("NATIVE_APP user_open ${intent.component?.packageName}");true}
            .getOrElse {report("NATIVE_APP failed ${it.javaClass.simpleName}");false}
    }
}

/** No network, bind, refresh request, private-file access or foreign code loading. */
class ExistingWeatherRepository(private val native:NativeApps,private val mock:WeatherRepository,car:StateFlow<VehicleLinkState>):WeatherRepository {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val unavailable=WeatherState(null,"","Штатная погода",demo=false)
    private val clock=flow {while(true){emit(android.os.SystemClock.elapsedRealtime());delay(1000)}}
    private val outsideCache=OutsideReadingCache()
    override val state=combine(mock.state,car,clock){weather,link,now->
        if(link.demo){outsideCache.clear();if(!native.weatherAvailable)weather else unavailable}
        else unavailable.copy(outsideCelsius=outsideCache.update(OutsideTemperature.celsius(link,now),now))}
        .stateIn(scope,SharingStarted.WhileSubscribed(5000),if(car.value.demo&&!native.weatherAvailable)mock.state.value else unavailable)
    override fun useCity(city:String) {if(state.value.demo)mock.useCity(city)}
}
