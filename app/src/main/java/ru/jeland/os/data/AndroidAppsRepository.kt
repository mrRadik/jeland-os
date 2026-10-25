package ru.jeland.os.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.jeland.os.core.*

/** Visible launcher apps only; no fabricated installation status or external installs. */
class AndroidAppsRepository(context:Context,private val store:SettingsStore):AppsRepository {
    private val context=context.applicationContext
    private val flow=MutableStateFlow(AppsState(favorites=store.string("apps.favorites","").split('|').filter {it.isNotBlank()}.distinct()))
    override val state=flow.asStateFlow()
    private val iconFlow=MutableStateFlow<Map<String,Bitmap>>(emptyMap())
    val icons=iconFlow.asStateFlow()
    private val refreshLock=Mutex()
    override suspend fun refresh()=withContext(Dispatchers.IO) {
        refreshLock.withLock {
        flow.update {it.copy(loading=true,error=null)}
        runCatching {
            val pm=context.packageManager
            @Suppress("DEPRECATION")
            val matches=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                .filter {it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.packageName!=context.packageName}
                .distinctBy {it.activityInfo.packageName+"/"+it.activityInfo.name}
            val apps=matches.map {r->val a=r.activityInfo;LauncherApp("${a.packageName}/${a.name}",a.packageName,a.name,r.loadLabel(pm).toString())}.sortedBy {it.label.lowercase()}
            val icons=matches.mapNotNull {r->runCatching {"${r.activityInfo.packageName}/${r.activityInfo.name}" to LauncherIconRenderer.render(r.loadIcon(pm))}.getOrNull()}.toMap()
            iconFlow.value=icons
            flow.update {it.copy(installed=apps,loading=false,error=null)}
        }.onFailure {flow.update {it.copy(loading=false,error="Не удалось прочитать приложения. Попробуйте обновить.")}}
        Unit
        }
    }
    override fun toggleFavorite(id:String) {
        if(flow.value.installed.none {it.id==id})return
        flow.update {it.copy(favorites=if(id in it.favorites)it.favorites-id else it.favorites+id)};persist()
    }
    override fun moveFavorite(id:String,delta:Int) {
        flow.update {s->
            val list=s.favorites.toMutableList();val from=list.indexOf(id)
            if(from<0)s else {val to=(from+delta).coerceIn(list.indices);list.removeAt(from);list.add(to,id);s.copy(favorites=list)}
        };persist()
    }
    private fun persist()=store.put("apps.favorites",flow.value.favorites.joinToString("|"))
    override fun launch(id:String):String? {
        val app=flow.value.installed.find {it.id==id}?:return "Приложение недоступно. Обновите список."
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.packageName,app.activityName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        }.getOrElse {"Не удалось открыть ${app.label}. Возможно, приложение удалено или запуск запрещён системой."}
    }
}
