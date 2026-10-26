package ru.jeland.os.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.core.*

class PersistentInterfaceRepository(private val store:SettingsStore):InterfaceRepository {
    private fun enabled(key:String,default:Boolean)=store.string("ui.$key","$default").toBooleanStrictOrNull()?:default
    private val flow=MutableStateFlow(InterfacePreferences(enabled("fuel",true),enabled("tires",true),enabled("favorites",true),enabled("developer",false),
        runCatching {DashboardStyle.valueOf(store.string("ui.style",DashboardStyle.CLASSIC.name))}.getOrDefault(DashboardStyle.CLASSIC)))
    override val state=flow.asStateFlow()
    override fun change(value:InterfacePreferences) {
        flow.value=value
        mapOf("fuel" to value.showFuel,"tires" to value.showTires,"favorites" to value.showFavorites,"developer" to value.developerPanel).forEach {(k,v)->store.put("ui.$k","$v")}
        store.put("ui.style",value.style.name)
    }
}
