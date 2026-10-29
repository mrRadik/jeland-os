package ru.jeland.os.navigation.comaps

import android.Manifest
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.organicmaps.sdk.JelandMapView
import app.organicmaps.sdk.Map as CoMap
import app.organicmaps.sdk.MapRenderingListener
import app.organicmaps.sdk.OrganicMaps
import app.organicmaps.sdk.downloader.CountryItem
import ru.jeland.os.localization.LocalizedIcon as Icon
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.navigation.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*

// The CoMaps native renderer has one surface. Transitioning pages explicitly
// transfer ownership before attaching the next surface, including the home map.
private var currentSurface:JelandMapView?=null

@Composable private fun CoMapsSurface(repository:CoMapsRepository,dark:Boolean,modifier:Modifier=Modifier) {
    val ready by repository.ready.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val lifecycle=LocalLifecycleOwner.current
    val context=LocalContext.current
    var view by remember {mutableStateOf<JelandMapView?>(null)}
    LaunchedEffect(repository){repository.initialize()}
    LaunchedEffect(ready,dark){if(ready)repository.theme(dark)}
    DisposableEffect(lifecycle,ready) {
        val observer=LifecycleEventObserver {_,event->
            if(ready)when(event) {
                Lifecycle.Event.ON_RESUME->{OrganicMaps.nativeSetContext(context);repository.resume();view?.takeIf {it===currentSurface}?.let {it.map().onResume();repository.surfaceReady()}}
                Lifecycle.Event.ON_PAUSE->{view?.takeIf {it===currentSurface}?.let {it.map().onPause();repository.surfacePaused()};OrganicMaps.nativeSetContext(context.applicationContext)}
                else->Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {lifecycle.lifecycle.removeObserver(observer)}
    }
    if(ready)AndroidView(factory={host->
        currentSurface?.let {old->repository.surfacePaused();old.map().onSurfaceDestroyed(false);old.visibility=View.INVISIBLE}
        JelandMapView(host,repository.locationHelper,object:MapRenderingListener {
            override fun onRenderingCreated(){repository.surfaceReady()}
            override fun onRenderingRestored(){repository.surfaceReady()}
            override fun onRenderingInitializationFinished(){repository.surfaceReady()}
        }).also {mapView->
            mapView.map().setCallbackUnsupported {repository.error.value="Устройство не поддерживает отрисовку карты"}
            mapView.map().onStart();mapView.map().onResume();currentSurface=mapView;view=mapView
        }
    },modifier=modifier.testTag("comaps-map"),onRelease={old->
        if(currentSurface===old){repository.surfacePaused();old.map().onPause();old.map().onStop();old.map().onSurfaceDestroyed(false);currentSurface=null}
        view=null
    })else Box(modifier.background(Jeland.colors.map),contentAlignment=Alignment.Center) {
        if(error!=null)Label(error!!,Modifier.padding(20.dp))else CircularProgressIndicator(color=Jeland.colors.accent)
    }
}

@Composable fun CoMapsScreen(host:OfflineNavigationRepository,dark:Boolean) {
    val repository=host.comaps
    val state by repository.state.collectAsStateWithLifecycle()
    val selected by repository.selected.collectAsStateWithLifecycle()
    val message by repository.message.collectAsStateWithLifecycle()
    val ready by repository.ready.collectAsStateWithLifecycle()
    val results by repository.results.collectAsStateWithLifecycle()
    val searching by repository.searching.collectAsStateWithLifecycle()
    val missing by repository.missingMaps.collectAsStateWithLifecycle()
    val following by repository.following.collectAsStateWithLifecycle()
    var sheet by rememberSaveable {mutableStateOf<String?>(null)}
    var query by rememberSaveable(stateSaver=TextFieldValue.Saver){mutableStateOf(TextFieldValue())}
    var favorite by remember {mutableStateOf<NavigationPlace?>(null)}
    val c=Jeland.colors
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){repository.resume();if(following)repository.follow()}
    LaunchedEffect(query.text,ready){repository.search(query.text)}
    BackHandler(sheet!=null){sheet=null}
    BoxWithConstraints(Modifier.fillMaxSize().testTag("comaps-navigation-screen")) {
        CoMapsSurface(repository,dark,Modifier.fillMaxSize())
        val width=(maxWidth-28.dp).coerceAtMost(620.dp)
        Column(Modifier.align(Alignment.TopStart).padding(14.dp).width(width),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.fillMaxWidth().clickable {sheet="search"}.testTag("navigation-search-open")) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Icon(JIcons.Search,null,Modifier.size(28.dp),tint=c.accent)
                    Text("Куда поедем?",Modifier.weight(1f),color=c.text,fontSize=Jeland.type.body)
                    Text("CoMaps",color=c.muted,fontSize=Jeland.type.footnote)
                }
            }
            if(state.active&&!state.preview)Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Icon(navigationManeuverIcon(state),null,Modifier.size(48.dp),tint=c.accent)
                    Column {Text(distanceLabel(state.distanceToManeuver.toDouble()),color=c.accent,fontSize=Jeland.type.heading);Text(state.instruction,color=c.text,fontSize=Jeland.type.body)}
                }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            RoundControl(JIcons.Add,"Приблизить карту",Modifier.testTag("navigation-zoom-in"),enabled=ready){repository.zoom(1)}
            RoundControl(JIcons.Remove,"Отдалить карту",Modifier.testTag("navigation-zoom-out"),enabled=ready){repository.zoom(-1)}
            RoundControl(JIcons.Navigation,"Следовать за автомобилем",Modifier.testTag("navigation-recenter"),active=following,enabled=ready){repository.follow();permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
            RoundControl(JIcons.Settings,"Настройки карты",Modifier.testTag("navigation-options")){sheet="options"}
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp).width(width),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(message.isNotBlank())Panel(Modifier.fillMaxWidth()) {Label(message,Modifier.padding(14.dp),small=true)}
            if(missing.isNotEmpty())Control("Скачать недостающие карты",Modifier.fillMaxWidth()){sheet="maps"}
            if(state.active||selected!=null)Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            if(state.active)Text(if(state.preview)"Просмотр маршрута"else"${state.minutes} мин · ${distanceLabel(state.kilometers*1000)}",color=c.accent,fontSize=Jeland.type.title)
                            Text(selected?.fullName?:state.destination,color=c.text,fontSize=Jeland.type.body,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        RoundControl(JIcons.Close,if(state.active)"Завершить маршрут"else"Сбросить выбранную точку") {if(state.active)repository.stop()else repository.clearSelection()}
                    }
                    if(!state.active)Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Control(if(state.calculating)"Строим…"else"Поехали",Modifier.weight(1f).testTag("navigation-build"),selected=true,enabled=!state.calculating) {
                            selected?.let {host.places.rememberSelection(query.text,it)};repository.build()
                        }
                        RoundControl(JIcons.Star,"Сохранить точку в избранное"){favorite=selected}
                    }
                }
            }else Control("Скачать карты",Modifier.testTag("navigation-maps")){sheet="maps"}
        }
        Text("© OpenStreetMap · CoMaps",color=c.muted,fontSize=Jeland.type.micro,
            modifier=Modifier.align(Alignment.BottomEnd).background(c.panel.copy(alpha=.9f)).padding(4.dp))
        if(sheet!=null) {
            Box(Modifier.matchParentSize().background(c.scrim).clickable {sheet=null})
            Panel(Modifier.align(Alignment.TopCenter).padding(14.dp).widthIn(max=740.dp).fillMaxWidth().heightIn(max=maxHeight-28.dp)
                .then(if(sheet=="search")Modifier.fillMaxHeight()else Modifier).testTag("comaps-sheet")) {
                Row(Modifier.padding(16.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(when(sheet){"search"->"Поиск места";"maps"->"Скачать карты";"voice"->"Условия голосовой модели";else->"Настройки карты"},Modifier.weight(1f),color=c.text,fontSize=Jeland.type.title)
                    RoundControl(JIcons.Close,"Закрыть настройки карты"){sheet=null}
                }
                if(sheet=="search")NavigationSearchPanel(query,{query=it},results.map {it.place},searching,null,{place->
                    val result=results.firstOrNull {it.place==place}
                    if(result?.suggestion?.isNotBlank()==true)query=TextFieldValue(result.suggestion.orEmpty())
                    else {if(result!=null)repository.choose(result)else repository.select(place);host.places.rememberSelection(query.text,place);sheet=null}
                },Modifier.weight(1f),host.places,{favorite=it})
                else if(sheet=="maps")CoMapsDownloadPanel(repository,Modifier.weight(1f,false))
                else Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=18.dp).padding(bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(sheet=="voice")NavigationVoiceLicensePanel(repository.voice){sheet="options";repository.voice.test()}
                    else {
                        SettingsToggle("Следовать за автомобилем","Автомобиль остаётся в центре, карта поворачивается по ходу движения",following){repository.setFollowing(!following)}
                        Control("Скачать карты",Modifier.fillMaxWidth()){sheet="maps"}
                        Control("Разрешить GPS",Modifier.fillMaxWidth()){permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
                        NavigationVoicePanel(repository.voice){sheet="voice"}
                    }
                }
            }
        }
    }
    favorite?.let {NavigationFavoriteDialog(it,host.places){favorite=null}}
}

@Composable fun CoMapsWidget(host:OfflineNavigationRepository,dark:Boolean,modifier:Modifier,framed:Boolean,onClick:()->Unit) {
    val state by host.comaps.state.collectAsStateWithLifecycle()
    val c=Jeland.colors
    val content:@Composable ()->Unit={
        Box(Modifier.fillMaxSize()) {
            CoMapsSurface(host.comaps,dark,Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().clickable(onClick=onClick))
            Text("© OpenStreetMap · CoMaps",color=c.muted,fontSize=Jeland.type.micro,
                modifier=Modifier.align(Alignment.TopEnd).background(c.panel.copy(alpha=.8f)).padding(4.dp))
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(c.panel.copy(alpha=.92f)).clickable(onClick=onClick).padding(14.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(if(state.active)navigationManeuverIcon(state)else JIcons.Navigation,null,Modifier.size(38.dp),tint=c.accent)
                Column(Modifier.weight(1f)) {
                    Text(if(state.active)distanceLabel(state.distanceToManeuver.toDouble())else"Куда поедем?",color=c.text,fontSize=Jeland.type.body,maxLines=1)
                    Text(if(state.active)state.instruction else"Нажмите, чтобы выбрать адрес",color=c.muted,fontSize=Jeland.type.footnote,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
    if(framed)Panel(modifier){content()}else Box(modifier){content()}
}

@Composable fun CoMapsInitialSettings(host:OfflineNavigationRepository) {
    var open by remember {mutableStateOf(false)}
    Control("Скачать карты",Modifier.fillMaxWidth().testTag("settings-download-maps")){open=true}
    if(open)androidx.compose.ui.window.Dialog(onDismissRequest={open=false}) {
        Panel(Modifier.fillMaxWidth().heightIn(max=680.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Label("Скачать карты",Modifier.weight(1f));RoundControl(JIcons.Close,"Закрыть список карт"){open=false}
            }
            CoMapsDownloadPanel(host.comaps,Modifier.weight(1f,false))
        }
    }
}

@Composable fun CoMapsDownloadPanel(repository:CoMapsRepository,modifier:Modifier=Modifier) {
    val ready by repository.ready.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val maps by repository.regions.collectAsStateWithLifecycle()
    val folder by repository.folder.collectAsStateWithLifecycle()
    val missing by repository.missingMaps.collectAsStateWithLifecycle()
    var allowMobile by rememberSaveable {mutableStateOf(false)}
    var filter by rememberSaveable(folder) {mutableStateOf("")}
    var deleting by remember {mutableStateOf<CoMapsRegion?>(null)}
    val c=Jeland.colors
    LaunchedEffect(Unit){repository.initialize();repository.refreshMaps()}
    Column(modifier.padding(horizontal=16.dp).padding(bottom=14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(!ready){if(error!=null)Label(error!!)else CircularProgressIndicator(color=c.accent);return@Column}
        Text("Карты CoMaps · OpenStreetMap",color=c.muted,fontSize=Jeland.type.footnote)
        SettingsToggle("Разрешить мобильную сеть","Скачанные карты работают без интернета",allowMobile){allowMobile=!allowMobile}
        if(folder!="Countries")Control("Назад к списку регионов",Modifier.fillMaxWidth()){repository.parentFolder()}
        OutlinedTextField(filter,{filter=it},singleLine=true,label={Text("Найти регион")},colors=navigationFieldColors(),modifier=Modifier.fillMaxWidth())
        if(missing.isNotEmpty())Control("Скачать карты для маршрута",Modifier.fillMaxWidth(),selected=true){missing.forEach {repository.download(it,allowMobile)}}
        LazyColumn(Modifier.weight(1f,false).heightIn(min=120.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(maps.filter {it.name.contains(filter,true)||it.id.contains(filter,true)},key={it.id}) {map->
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f).clickable {if(map.children)repository.openFolder(map.id)}) {
                        Text(map.name,color=c.text,fontSize=Jeland.type.body)
                        Text(when(map.status){CountryItem.STATUS_DONE->"Установлена";CountryItem.STATUS_PROGRESS,CountryItem.STATUS_ENQUEUED,CountryItem.STATUS_APPLYING->"${map.progress.toInt()}%";CountryItem.STATUS_FAILED->"Не удалось скачать. Проверьте подключение";else->"%.1f МБ".format(map.bytes/1048576.0)},color=c.muted,fontSize=Jeland.type.footnote)
                    }
                    when {
                        map.children->Control("Открыть"){repository.openFolder(map.id)}
                        map.status in listOf(CountryItem.STATUS_PROGRESS,CountryItem.STATUS_ENQUEUED,CountryItem.STATUS_APPLYING)->Control("Отмена"){repository.cancelDownload(map.id)}
                        map.status==CountryItem.STATUS_DONE->Control("Удалить"){deleting=map}
                        else->Control(if(map.status==CountryItem.STATUS_UPDATABLE)"Обновить"else"Скачать",selected=true){repository.download(map.id,allowMobile)}
                    }
                }
                HorizontalDivider(color=c.outline)
            }
        }
    }
    deleting?.let {map->AlertDialog(onDismissRequest={deleting=null},title={Text("Удалить карту?")},text={Text(map.name)},
        confirmButton={TextButton(onClick={repository.deleteMap(map.id);deleting=null}){Text("Удалить")}},dismissButton={TextButton(onClick={deleting=null}){Text("Отмена")}})}
}
