package ru.jeland.os.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.BuildConfig
import ru.jeland.os.core.SettingsStore

enum class AppLanguage(val tag:String,val title:String) { RUSSIAN("ru","Русский"),ENGLISH("en","English") }
data class FirstRunState(val language:AppLanguage=AppLanguage.RUSSIAN,val complete:Boolean=false)
class FirstRunSettings(private val store:SettingsStore) {
    private val mutable=MutableStateFlow(FirstRunState(
        AppLanguage.entries.firstOrNull {it.tag==store.string("app.language","ru")}?:AppLanguage.RUSSIAN,
        store.string("setup.complete",BuildConfig.DEMO_FEATURES.toString())=="true"))
    val state=mutable.asStateFlow()
    fun language(value:AppLanguage){store.put("app.language",value.tag);mutable.value=mutable.value.copy(language=value)}
    fun finish(){store.put("setup.complete","true");mutable.value=mutable.value.copy(complete=true)}
    fun reopen(){mutable.value=mutable.value.copy(complete=false)}
}
