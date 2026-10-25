package ru.jeland.os.data

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import ru.jeland.os.core.*

/** Reads the same resolved uiMode as Desay's launcher. Never changes the HU's mode. */
class AndroidDayNightProvider(context:Context,private val link:StateFlow<VehicleLinkState>,private val log:(String)->Unit={}) : DayNightProvider,ComponentCallbacks {
    private val app=context.applicationContext
    private val systemNight=MutableStateFlow(DayNightSignal.read(app.resources.configuration.uiMode)?:true)
    private val demoOverride=MutableStateFlow<Boolean?>(null)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var started=false
    override val isNight=combine(systemNight,demoOverride,link){system,demo,car->
        if(car.demo)demo?:system else system
    }.stateIn(scope,SharingStarted.WhileSubscribed(5000),systemNight.value)

    fun start() {
        if(!started){app.registerComponentCallbacks(this);started=true}
        update(app.resources.configuration)
    }
    fun stop() {
        if(started){app.unregisterComponentCallbacks(this);started=false}
    }
    private fun update(configuration:Configuration) {
        val value=DayNightSignal.read(configuration.uiMode)
        if(value==null){log("THEME_SYSTEM uiMode=${configuration.uiMode} unknown; retaining last mode");return}
        if(systemNight.value!=value)log("THEME_SYSTEM uiMode=${configuration.uiMode} night=$value")
        systemNight.value=value
    }
    override fun onConfigurationChanged(newConfig:Configuration)=update(newConfig)
    override fun onLowMemory()=Unit
    /** Test-only override is never applied while using the head unit. */
    fun toggle() {
        if(!link.value.demo)return
        demoOverride.value=!(demoOverride.value?:systemNight.value)
    }
}

class PersistentThemeRepository(private val store:SettingsStore):ThemeRepository {
    // Enable requested automatic behaviour once on upgrade, then respect manual choices.
    private val initial=if(store.string("theme.systemAuto.v1","false")!="true") {
        store.put("theme.systemAuto.v1","true");store.put("theme.mode",ThemeMode.AUTO.name);ThemeMode.AUTO
    }else runCatching {ThemeMode.valueOf(store.string("theme.mode",ThemeMode.AUTO.name))}.getOrDefault(ThemeMode.AUTO)
    private val flow=MutableStateFlow(initial)
    override val mode=flow.asStateFlow()
    override fun setMode(value:ThemeMode){flow.value=value;store.put("theme.mode",value.name)}
}
