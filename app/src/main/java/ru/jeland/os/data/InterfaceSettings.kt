package ru.jeland.os.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.core.*

class PersistentInterfaceRepository(private val store:SettingsStore):InterfaceRepository {
    private fun enabled(key:String,default:Boolean)=store.string("ui.$key","$default").toBooleanStrictOrNull()?:default
    private val flow=MutableStateFlow(InterfacePreferences(enabled("fuel",true),enabled("tires",true),enabled("favorites",true),enabled("developer",false),
        runCatching {DashboardStyle.valueOf(store.string("ui.style",DashboardStyle.CLASSIC.name))}.getOrDefault(DashboardStyle.CLASSIC),
        FreeDashboardLayout.decode(store.string("ui.free-layout",FreeDashboardLayout.default().encode()))))
    override val state=flow.asStateFlow()
    override fun change(value:InterfacePreferences) {
        val validated=value.copy(freeLayout=FreeDashboardLayout.decode(value.freeLayout.encode()))
        flow.value=validated
        mapOf("fuel" to value.showFuel,"tires" to value.showTires,"favorites" to value.showFavorites,"developer" to value.developerPanel).forEach {(k,v)->store.put("ui.$k","$v")}
        store.put("ui.style",value.style.name)
        store.put("ui.free-layout",validated.freeLayout.encode())
    }
}
