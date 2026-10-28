package ru.jeland.os.navigation

import ru.jeland.os.localization.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons
import java.util.Locale

@Composable fun NavigationMapSettings(repository:OfflineNavigationRepository) {
    var showMaps by rememberSaveable {mutableStateOf(false)}
    val c=Jeland.colors
    MapCatalogSourceField(repository,"map-catalog-source")
    Control("Скачать карты",Modifier.fillMaxWidth().testTag("map-catalog-open"),selected=true){showMaps=true}
    if(showMaps)Dialog(onDismissRequest={showMaps=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(20.dp),contentAlignment=Alignment.Center) {
            Panel(Modifier.widthIn(max=680.dp).fillMaxWidth().heightIn(max=maxHeight*.85f).testTag("map-catalog-dialog")) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Скачать карты",Modifier.weight(1f),color=c.text,fontSize=Jeland.type.title)
                    RoundControl(JIcons.Close,"Закрыть список карт",Modifier.testTag("map-catalog-close")){showMaps=false}
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=18.dp).padding(bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    NavigationDownloadPanel(repository)
                }
            }
        }
    }
}

@Composable private fun MapCatalogSourceField(repository:OfflineNavigationRepository,tag:String) {
    val catalog by repository.mapCatalog.state.collectAsStateWithLifecycle()
    val keyboard=LocalSoftwareKeyboardController.current
    OutlinedTextField(catalog.source,repository.mapCatalog::setSource,label={Text("Путь до карт")},
        placeholder={Text(DEFAULT_MAP_SOURCE)},singleLine=true,colors=navigationFieldColors(),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Done),
        keyboardActions=KeyboardActions(onDone={keyboard?.hide();repository.mapCatalog.refresh()}),
        modifier=Modifier.fillMaxWidth().testTag(tag))
}

@Composable internal fun NavigationDownloadPanel(repository:OfflineNavigationRepository,onOverview:(()->Unit)?=null) {
    val catalog by repository.mapCatalog.state.collectAsStateWithLifecycle()
    val download by repository.downloads.state.collectAsStateWithLifecycle()
    val installing by repository.importing.collectAsStateWithLifecycle()
    val deleting by repository.deletingMap.collectAsStateWithLifecycle()
    val region by repository.maps.region.collectAsStateWithLifecycle()
    val overviewSource by repository.maps.overviewSourceUrl.collectAsStateWithLifecycle()
    val overviewAvailable by repository.maps.overviewAvailable.collectAsStateWithLifecycle()
    val operationMessage by repository.message.collectAsStateWithLifecycle()
    val navigation by repository.state.collectAsStateWithLifecycle()
    var mobileMap by remember {mutableStateOf<DownloadableMap?>(null)}
    var confirmInstall by remember {mutableStateOf(false)}
    var deleteMap by remember {mutableStateOf<DownloadableMap?>(null)}
    var error by remember {mutableStateOf("")}
    val c=Jeland.colors
    LaunchedEffect(Unit){repository.mapCatalog.refresh(force=false)}
    fun startDownload(map:DownloadableMap,allowMobile:Boolean=false) {
        error=""
        try {repository.downloadMap(map,allowMobile)}catch(e:Exception){error=e.message?:"Не удалось начать загрузку"}
    }
    MapCatalogSourceField(repository,"map-catalog-source-in-list")
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Label(if(catalog.loading)"Загрузка списка…"else"Доступные карты",modifier=Modifier.weight(1f))
        RoundControl(JIcons.Refresh,"Обновить список карт",Modifier.testTag("map-catalog-refresh"),enabled=!catalog.loading){repository.mapCatalog.refresh()}
    }
    if(catalog.loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=c.accent)
    if(catalog.error.isNotBlank())Label(catalog.error,small=true)
    if(catalog.loaded&&catalog.maps.isEmpty())Label("В этом источнике пока нет карт.",small=true,muted=true)
    for(map in catalog.maps) {
        val current=download.sourceUrl==map.url
        val installed=region?.id==map.id||(map.kind=="overview"&&overviewAvailable&&(overviewSource==map.url||map.id=="russia-overview"))
        Column(Modifier.fillMaxWidth().testTag("map-entry-${map.id}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(map.title,color=c.text,fontSize=Jeland.type.body)
                    Text(mapSize(map.bytes)+(if(map.kind=="overview")" · обзорная"else""),color=c.muted,fontSize=Jeland.type.footnote)
                }
                when {
                    current&&installing->Label(if(deleting)"Удаление…"else"Установка…",small=true)
                    current&&download.running->Control("Отменить",onClick=repository.downloads::cancel)
                    current&&download.ready!=null->Control("Установить",selected=true,enabled=!installing) {
                        if(navigation.active||navigation.calculating)confirmInstall=true else repository.installDownloaded()
                    }
                    installed->InstalledMapAction(map,!installing&&!download.running){deleteMap=map}
                    else->Control("Скачать",Modifier.testTag("map-download-${map.id}"),selected=true,enabled=!download.running&&!installing) {
                        if(repository.needsMobileMapConfirmation())mobileMap=map else startDownload(map)
                    }
                }
            }
            if(current&&download.status.isNotBlank()&&!download.installed) {
                Label(if(deleting)"Удаление…"else if(installing)"Установка…"else download.status,small=true,muted=true)
                if(download.running&&download.total>0) {
                    LinearProgressIndicator(progress={(download.received.toFloat()/download.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=c.accent)
                    Label("${mapSize(download.received)} / ${mapSize(download.total)}",small=true,muted=true)
                }
            }
            HorizontalDivider(color=c.outline)
        }
    }
    // Removing local maps must also work offline or with an unavailable custom catalog.
    val localOnly=buildList {
        region?.takeIf {local->catalog.maps.none {it.id==local.id}}?.let {
            add(DownloadableMap(it.id,it.title,"",it.bytes))
        }
        if(overviewAvailable&&catalog.maps.none {it.kind=="overview"&&(it.id=="russia-overview"||it.url==overviewSource)})
            add(DownloadableMap("russia-overview","Россия · обзорная карта дорог",overviewSource,0,kind="overview"))
    }
    for(map in localOnly) {
        Row(Modifier.fillMaxWidth().testTag("map-local-${map.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(map.title,Modifier.weight(1f),color=c.text,fontSize=Jeland.type.body)
            InstalledMapAction(map,!installing&&!download.running){deleteMap=map}
        }
        HorizontalDivider(color=c.outline)
    }
    // A resumed download stays accessible even if its source or release disappeared.
    if(download.sourceUrl.isNotBlank()&&catalog.maps.none {it.url==download.sourceUrl}) {
        Label(download.title,small=true)
        if(download.status.isNotBlank())Label(download.status,small=true,muted=true)
        if(download.running)Control("Отменить загрузку",onClick=repository.downloads::cancel)
        else if(download.ready!=null)Control("Установить",enabled=!installing) {
            if(navigation.active||navigation.calculating)confirmInstall=true else repository.installDownloaded()
        }
    }
    if(error.isNotBlank())Label(error,small=true)
    if(operationMessage.startsWith("Не удалось удалить"))Label(operationMessage,small=true)
    onOverview?.let {Control("Обзор России",Modifier.fillMaxWidth().testTag("navigation-russia-overview"),enabled=overviewAvailable&&!installing,onClick=it)}
    deleteMap?.let {map->AlertDialog(onDismissRequest={deleteMap=null},containerColor=c.panel,
        title={Text("Удалить карту?",color=c.text)},
        text={Text(map.title+if(map.kind=="region"&&(navigation.active||navigation.calculating))"\nТекущий маршрут будет завершён."else"",color=c.muted)},
        confirmButton={TextButton(onClick={deleteMap=null;repository.removeMap(map)},modifier=Modifier.testTag("map-delete-confirm")){Text("Удалить",color=c.critical)}},
        dismissButton={TextButton(onClick={deleteMap=null}){Text("Отмена",color=c.muted)}})}
    mobileMap?.let {map->AlertDialog(onDismissRequest={mobileMap=null},containerColor=c.panel,
        title={Text("Скачать через мобильную сеть?",color=c.text)},
        text={Text("${map.title} · ${mapSize(map.bytes)}",color=c.muted)},
        confirmButton={TextButton(onClick={mobileMap=null;startDownload(map,true)},modifier=Modifier.testTag("map-download-mobile-confirm")){Text("Скачать",color=c.accent)}},
        dismissButton={TextButton(onClick={mobileMap=null}){Text("Отмена",color=c.muted)}})}
    if(confirmInstall)AlertDialog(onDismissRequest={confirmInstall=false},containerColor=c.panel,
        title={Text("Установить карту?",color=c.text)},text={Text("Текущий маршрут будет завершён.",color=c.muted)},
        confirmButton={TextButton(onClick={confirmInstall=false;repository.installDownloaded()}){Text("Установить",color=c.accent)}},
        dismissButton={TextButton(onClick={confirmInstall=false}){Text("Отмена",color=c.muted)}})
}

@Composable private fun InstalledMapAction(map:DownloadableMap,enabled:Boolean,onRemove:()->Unit) {
    Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Label("Установлена",small=true,muted=true)
        Control("Удалить",Modifier.testTag("map-delete-${map.id}"),enabled=enabled,onClick=onRemove)
    }
}

internal fun mapSize(bytes:Long):String=if(bytes<1048576)"${(bytes/1024).coerceAtLeast(1)} КБ"
    else String.format(ru.jeland.os.localization.AppLocale.current,"%.1f МБ",bytes/1048576.0)
