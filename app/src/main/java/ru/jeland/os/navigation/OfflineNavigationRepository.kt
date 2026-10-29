package ru.jeland.os.navigation

import android.content.Context
import ru.jeland.os.core.NavigationRepository
import ru.jeland.os.navigation.comaps.CoMapsRepository

/** One CoMaps session and voice shared by the navigation tab and home map. */
class OfflineNavigationRepository(context:Context):NavigationRepository,AutoCloseable {
    val places=NavigationPlacesRepository(ru.jeland.os.data.AndroidSettingsStore(context))
    val gps=AndroidGpsSource(context)
    val voice=NavigationVoice(context)
    val comaps=CoMapsRepository(context,gps,voice)
    override val state=comaps.state
    fun resume()=comaps.resume()
    fun pause(){comaps.pause();if(!state.value.active)gps.pause()}
    override fun toggleRoute(){if(state.value.active)comaps.stop()else comaps.build()}
    override fun setDestination(value:String){comaps.initialize();comaps.search(value)}
    override fun zoom(delta:Float)=comaps.zoom(if(delta>0)1 else -1)
    override fun nextManeuver(){} // Maneuvers advance only from actual position.
    fun stop()=comaps.stop()
    override fun close(){comaps.close();voice.close();gps.close()}
}
