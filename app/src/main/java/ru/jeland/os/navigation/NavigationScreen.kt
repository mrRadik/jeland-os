package ru.jeland.os.navigation

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import ru.jeland.os.core.Maneuver
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.util.Locale

/** Native map fills the tab; tools are overlays, never a scrolling page around a small map. */
@Composable fun FullNavigationScreen(repository:OfflineNavigationRepository,dark:Boolean) {
    val state by repository.state.collectAsStateWithLifecycle()
    val region by repository.maps.region.collectAsStateWithLifecycle()
    val selected by repository.selected.collectAsStateWithLifecycle()
    val favorites by repository.places.favorites.collectAsStateWithLifecycle()
    var favoritePlace by remember {mutableStateOf<NavigationPlace?>(null)}
    val position by repository.gps.position.collectAsStateWithLifecycle()
    val bearing by repository.course.collectAsStateWithLifecycle()
    val gpsStatus by repository.gps.status.collectAsStateWithLifecycle()
    val message by repository.message.collectAsStateWithLifecycle()
    val preparing by repository.preparing.collectAsStateWithLifecycle()
    val importing by repository.importing.collectAsStateWithLifecycle()
    val mapRevision by repository.maps.revision.collectAsStateWithLifecycle()
    val c=Jeland.colors
    val keyboard=LocalSoftwareKeyboardController.current
    var sheet by rememberSaveable {mutableStateOf<String?>(null)}
    var searchText by rememberSaveable(stateSaver=TextFieldValue.Saver) {mutableStateOf(TextFieldValue())}
    val query=searchText.text
    var previewOrigin by rememberSaveable {mutableStateOf("")}
    var results by remember {mutableStateOf<List<NavigationPlace>>(emptyList())}
    var searching by remember {mutableStateOf(false)}
    var searchError by remember {mutableStateOf<String?>(null)}
    var mapError by remember(region?.id) {mutableStateOf<String?>(null)}
    var rendered by remember(region?.id) {mutableStateOf(false)}
    var follow by rememberSaveable {mutableStateOf(true)}
    var northUp by rememberSaveable {mutableStateOf(false)}
    var viewport by remember {mutableStateOf<MapViewportRequest?>(null)}
    var sequence by remember {mutableIntStateOf(0)}
    fun camera(action:MapViewportAction){viewport=MapViewportRequest(++sequence,action)}
    fun dismiss(){keyboard?.hide();sheet=null}
    fun choose(place:NavigationPlace) {
        repository.select(place);searchText=TextFieldValue();dismiss();follow=false;camera(MapViewportAction.DESTINATION)
    }
    var wasActive by remember {mutableStateOf(state.active)}
    LaunchedEffect(state.active) {
        if(state.active&&!wasActive){follow=true;northUp=false}
        wasActive=state.active
    }
    BackHandler(sheet!=null){dismiss()}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(!repository.gps.start())repository.message.value="Включите точное местоположение и GPS в настройках устройства."
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)repository.importRegion(uri)}
    LaunchedEffect(query,region?.id,importing) {
        results=emptyList();searchError=null
        if(!importing&&query.trim().length>=2) {
            searching=true
            try {delay(300);results=repository.maps.search(query)}
            catch(e:CancellationException){throw e}
            catch(e:Exception){searchError="Поиск недоступен: ${e.message}"}
            finally {searching=false}
        }else searching=false
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.map).testTag("offline-navigation-screen")) {
        val narrow=maxWidth<560.dp
        val overlayWidth=if(narrow)maxWidth-24.dp else (maxWidth*.70f).coerceAtMost(620.dp)
        if(region!=null&&!importing)key(region!!.id,mapRevision) {
            OfflineMap(repository.maps,state,dark,Modifier.fillMaxSize().testTag("offline-map"),position,selected?.point,
                repository.camera,{repository.camera=it},follow,
                onDestination=::choose,request=viewport,
                onGesture={follow=false},onReady={rendered=true},onError={mapError=it},heading=bearing,northUp=northUp)
        }
        if(preparing||importing)Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color=c.accent)
            Label(if(importing)"Установка офлайн-карты…"else"Подготовка офлайн-карты…")
        }
        // Contrast only at the edges, leaving the map itself unobscured.
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(c.background.copy(alpha=.65f),androidx.compose.ui.graphics.Color.Transparent))))
        Column(Modifier.align(Alignment.TopStart).padding(14.dp).width(overlayWidth),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Panel(Modifier.fillMaxWidth().testTag("navigation-search-open").clickable {sheet="search"}) {
                Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Icon(JIcons.Search,null,Modifier.size(28.dp),tint=c.accent)
                    Text("Куда поедем?",color=c.text,fontSize=Jeland.type.body,modifier=Modifier.weight(1f))
                    Text("ОФЛАЙН",color=c.muted,fontSize=Jeland.type.footnote)
                }
            }
            if(state.active)Panel(Modifier.fillMaxWidth().testTag("navigation-maneuver")) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Icon(navigationManeuverIcon(state),null,Modifier.size(48.dp),tint=c.accent)
                    Column(Modifier.weight(1f)) {
                        Text(distanceLabel(state.distanceToManeuver.toDouble()),color=c.text,fontSize=Jeland.type.heading,fontWeight=FontWeight.SemiBold)
                        Text(state.instruction.ifBlank {state.street},color=c.text,fontSize=Jeland.type.secondary,maxLines=3,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            RoundControl(JIcons.Add,"Приблизить карту",Modifier.testTag("navigation-zoom-in")){camera(MapViewportAction.ZOOM_IN)}
            RoundControl(JIcons.Remove,"Отдалить карту",Modifier.testTag("navigation-zoom-out")){camera(MapViewportAction.ZOOM_OUT)}
            RoundControl(JIcons.Navigation,"По ходу движения",Modifier.testTag("navigation-recenter"),active=follow&&!northUp) {follow=true;northUp=false;camera(MapViewportAction.POSITION)}
            if(state.active)RoundControl(JIcons.Expand,"Весь маршрут",Modifier.testTag("navigation-overview")){follow=false;camera(MapViewportAction.OVERVIEW)}
            RoundControl(JIcons.Settings,"Настройки карты",Modifier.testTag("navigation-options")){sheet="options"}
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start=14.dp,end=14.dp,bottom=32.dp).width(overlayWidth),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(mapError!=null||(!preparing&&region==null))Panel(Modifier.fillMaxWidth()) {
                Label(mapError?:message.ifBlank {"Карта недоступна"},Modifier.padding(14.dp),small=true)
                Control("Офлайн-карты",Modifier.padding(8.dp)){sheet="maps"}
            }
            if(message.isNotBlank()&&sheet==null)Panel(Modifier.fillMaxWidth().testTag("navigation-message").clickable {repository.message.value=""}) {
                Text(message,color=c.muted,fontSize=Jeland.type.footnote,maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(12.dp))
            }
            Panel(Modifier.fillMaxWidth().testTag("navigation-trip-card")) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(state.active) {
                        if(state.preview)Text("ПРОСМОТР · НЕ GPS-ВЕДЕНИЕ",color=c.warm,fontSize=Jeland.type.footnote,modifier=Modifier.testTag("navigation-preview-indicator"))
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${state.minutes} мин",color=c.accent,fontSize=Jeland.type.heading,fontWeight=FontWeight.SemiBold)
                                Text("${distanceLabel(state.kilometers*1000)} · прибытие ${state.arrivalTime}",color=c.text,fontSize=Jeland.type.secondary)
                            }
                            RoundControl(JIcons.Close,"Завершить маршрут",Modifier.testTag("navigation-stop"),onClick=repository::stop)
                        }
                        Text(state.destination,color=c.muted,fontSize=Jeland.type.footnote,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }else if(selected!=null) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text(selected!!.fullName,color=c.text,fontSize=Jeland.type.body,maxLines=2,overflow=TextOverflow.Ellipsis,
                                modifier=Modifier.weight(1f).testTag("navigation-selected"))
                            RoundControl(JIcons.Star,"Сохранить точку в избранное",Modifier.testTag("navigation-selected-favorite"),
                                active=favorites.any {it.id==navigationPlaceKey(selected!!)}) {favoritePlace=selected}
                            RoundControl(JIcons.Close,"Сбросить выбранную точку",Modifier.testTag("navigation-clear-selection"),enabled=!importing) {
                                searchText=TextFieldValue();results=emptyList();previewOrigin="";repository.clearSelection()
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Control(if(state.calculating)"Строим…"else"Поехали",Modifier.weight(1f).testTag("navigation-build"),selected=true,enabled=!state.calculating&&!importing) {
                                follow=true;repository.build()
                            }
                            Control("Просмотр",Modifier.testTag("navigation-preview-toggle"),enabled=!state.calculating){sheet="preview"}
                        }
                    }else {
                        Text("Выберите место на карте",color=c.text,fontSize=Jeland.type.body)
                        Text("Нажмите на дом или найдите адрес",color=c.muted,fontSize=Jeland.type.footnote)
                    }
                    if(!state.active)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(JIcons.Navigation,null,Modifier.size(16.dp),tint=if(repository.gps.routeOrigin()!=null)c.accent else c.muted)
                        Text(gpsStatus,color=c.muted,fontSize=Jeland.type.footnote,maxLines=2,modifier=Modifier.weight(1f))
                        if(repository.gps.routeOrigin()==null)Text("GPS",color=c.accent,fontSize=Jeland.type.secondary,modifier=Modifier.clickable {permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}.padding(10.dp).testTag("navigation-gps"))
                    }
                }
            }
        }
        Text("© OpenStreetMap contributors · ODbL · Geofabrik",color=c.artMuted,fontSize=Jeland.type.micro,
            modifier=Modifier.align(Alignment.BottomStart).background(c.map.copy(alpha=.85f)).padding(horizontal=12.dp,vertical=6.dp))
        if(rendered)Box(Modifier.size(1.dp).testTag("navigation-map-rendered"))
        if(sheet!=null) {
            Box(Modifier.matchParentSize().background(c.scrim).clickable {dismiss()})
            Panel(Modifier.align(Alignment.TopCenter).padding(14.dp).widthIn(max=680.dp).fillMaxWidth()
                .then(if(sheet=="search")Modifier.height(maxHeight-28.dp)else Modifier.heightIn(max=maxHeight-28.dp)).testTag("navigation-sheet")) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(when(sheet){"search"->"Поиск места";"maps"->"Офлайн-карты";"preview"->"Просмотр маршрута";"voice-license"->"Условия голосовой модели";else->"Настройки карты"},color=c.text,fontSize=Jeland.type.title,modifier=Modifier.weight(1f))
                    RoundControl(JIcons.Close,"Закрыть настройки карты",Modifier.testTag("navigation-sheet-close")){dismiss()}
                }
                if(sheet=="search")NavigationSearchPanel(searchText,{searchText=it},results,searching,searchError,::choose,Modifier.weight(1f),
                    repository.places,{favoritePlace=it})
                else Column(Modifier.verticalScroll(rememberScrollState()).padding(start=18.dp,end=18.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    when(sheet) {
                        "voice-license"->NavigationVoiceLicensePanel(repository.provider.voice){sheet="options";repository.provider.voice.test()}
                        "preview"->{
                            Label("Ручной старт — только просмотр, не определение вашего местоположения.",small=true)
                            OutlinedTextField(previewOrigin,{previewOrigin=it.take(100)},singleLine=true,label={Text("Широта, долгота старта")},colors=navigationFieldColors(),modifier=Modifier.fillMaxWidth().testTag("navigation-origin"))
                            Control("Старт в центре региона",Modifier.fillMaxWidth().testTag("navigation-origin-center")){previewOrigin=region?.center?.let {"${it.latitude}, ${it.longitude}"}.orEmpty()}
                            Control("Просмотреть маршрут",Modifier.fillMaxWidth().testTag("navigation-preview-build"),selected=true,enabled=selected!=null&&!state.calculating) {
                                val origin=parseCoordinates(previewOrigin)
                                if(origin==null)repository.message.value="Укажите корректные координаты старта"
                                else {follow=false;repository.build(from=origin);dismiss()}
                            }
                            if(message.isNotBlank())Label(message,small=true)
                        }
                        "maps"->{
                            NavigationDownloadPanel(repository){follow=false;camera(MapViewportAction.RUSSIA);dismiss()}
                            HorizontalDivider(color=c.outline)
                            Label(region?.title?:"Карта не загружена")
                            Label("В комплекте тестовый регион: Челябинск, Миасс, Златоуст и окрестности. Это не вся область. За границей пакета маршруты не гарантированы.",small=true,muted=true)
                            Label("Карта, поиск и дорожный граф работают локально. Пробки не подключены.",small=true,muted=true)
                            Label(if((region?.houseNumberCount?:0)>0)"Номера домов появляются при приближении (масштаб 16+), где адрес указан в OpenStreetMap."else"В этом пакете нет слоя номеров домов. Встроенная карта новой версии содержит адресные подписи.",small=true,muted=true)
                            Control(if(importing)"Импорт…"else"Импорт региона (.jelandnav.zip)",Modifier.fillMaxWidth().testTag("navigation-import"),enabled=!importing) {importer.launch(arrayOf("application/zip","application/octet-stream"))}
                            Label("Импорт останавливает маршрут. Используйте доверенные пакеты: контрольная сумма проверяет целостность, а не автора.",small=true,muted=true)
                            region?.let {Label(it.attribution,small=true,muted=true)}
                        }
                        else->{
                            SettingsToggle("Следовать за автомобилем","При перемещении карты вручную слежение отключается",follow){follow=!follow;if(follow)camera(MapViewportAction.POSITION)}
                            Control("Карта на север",Modifier.fillMaxWidth()){northUp=true;camera(MapViewportAction.NORTH);dismiss()}
                            Control("Разрешить GPS",Modifier.fillMaxWidth()){permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
                            Label(gpsStatus,small=true,muted=true)
                            NavigationVoicePanel(repository.provider.voice){sheet="voice-license"}
                            Control("Офлайн-карты",Modifier.fillMaxWidth().testTag("navigation-maps")){sheet="maps"}
                            Label("Навигационная версия для проверки. Реальное ведение на ГУ ещё требует испытаний. Оформление карты следует теме Jeland OS.",small=true,muted=true)
                        }
                    }
                }
            }
        }
    }
    favoritePlace?.let {place->NavigationFavoriteDialog(place,repository.places){favoritePlace=null}}
}

@Composable internal fun navigationFieldColors()=OutlinedTextFieldDefaults.colors(
    focusedTextColor=Jeland.colors.text,unfocusedTextColor=Jeland.colors.text,cursorColor=Jeland.colors.accent,
    focusedBorderColor=Jeland.colors.accent,unfocusedBorderColor=Jeland.colors.outline,
    focusedLabelColor=Jeland.colors.accent,unfocusedLabelColor=Jeland.colors.muted)

@Composable fun OfflineNavigationWidget(repository:OfflineNavigationRepository,dark:Boolean,modifier:Modifier,onClick:()->Unit) {
    val state by repository.state.collectAsStateWithLifecycle()
    val region by repository.maps.region.collectAsStateWithLifecycle()
    val mapRevision by repository.maps.revision.collectAsStateWithLifecycle()
    val preview by repository.previewImage.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var size by remember {mutableStateOf(IntSize.Zero)}
    val renderWidth=size.width.coerceAtMost(720)
    val renderHeight=if(size.width>0)(size.height.toDouble()*renderWidth/size.width).toInt().coerceAtLeast(120)else 120
    val renderer=remember(context,renderWidth,renderHeight) {WidgetMapSnapshot(context.applicationContext,maxOf(120,renderWidth),renderHeight)}
    DisposableEffect(renderer){onDispose {renderer.cancel()}}
    LaunchedEffect(renderer,dark,region?.id,mapRevision) {
        if(region==null||size.width<=0)return@LaunchedEffect
        // One request at a time; consume only the most recent fix while rendering.
        combine(repository.state,repository.gps.position,repository.course){s,p,b->Triple(s,p,b)}.conflate().collect {(s,p,b)->
            try {
                val image=renderer.render(repository.maps,s,p,b,dark)
                if(image.routeKey==(if(repository.state.value.active)repository.state.value.geometry.hashCode()else 0))repository.previewImage.value=image
            }catch(e:CancellationException){throw e}
            catch(e:Exception){android.util.Log.w("JelandNavigation","Widget map snapshot unavailable",e)}
            delay(250)
        }
    }
    val matchingPreview=preview?.takeIf {it.dark==dark&&it.regionId==region?.id&&it.routeKey==(if(state.active)state.geometry.hashCode()else 0)}
    val c=Jeland.colors
    Box(modifier.fillMaxHeight().onSizeChanged {size=it}.clip(Jeland.card).border(1.dp,c.outline,Jeland.card).testTag("navigation-widget")) {
        if(matchingPreview!=null)Image(matchingPreview.bitmap.asImageBitmap(),"Ближайший участок маршрута",Modifier.fillMaxSize().testTag("navigation-widget-map"),contentScale=ContentScale.FillBounds)
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.raised,c.background))),contentAlignment=Alignment.Center) {
            Icon(JIcons.Navigation,null,Modifier.size(72.dp),tint=c.accent.copy(alpha=.32f))
        }
        Box(Modifier.matchParentSize().clickable(onClick=onClick))
        if(state.active&&state.preview)Text("ПРОСМОТР",color=c.warm,fontSize=Jeland.type.micro,
            modifier=Modifier.align(Alignment.TopStart).padding(10.dp).background(c.panel.copy(alpha=.85f),Jeland.control).padding(6.dp))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(c.panel.copy(alpha=.55f),c.panel.copy(alpha=.98f)))).padding(start=12.dp,end=12.dp,top=10.dp,bottom=18.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(if(state.active)navigationManeuverIcon(state)else JIcons.Navigation,if(state.active)"Следующий манёвр"else null,
                Modifier.size(if(state.active)64.dp else 40.dp).testTag("navigation-widget-maneuver"),tint=c.accent)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(if(state.active)distanceGuidance(state.distanceToManeuver,state.instruction.ifBlank {"Продолжайте движение"})else"Куда поедем?",
                    color=c.text,fontSize=Jeland.type.body,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("navigation-widget-guidance"))
                Text(if(state.active)"${state.minutes} мин · ${distanceLabel(state.kilometers*1000)}"else"Нажмите, чтобы выбрать дом",color=c.muted,fontSize=Jeland.type.footnote,maxLines=1)
            }
        }
        Text("© OpenStreetMap · ODbL",color=c.muted,fontSize=Jeland.type.micro,modifier=Modifier.align(Alignment.BottomEnd).padding(horizontal=8.dp,vertical=3.dp))
    }
}

@Composable private fun navigationManeuverIcon(state:ru.jeland.os.core.NavigationState)=when {
    state.instruction.startsWith("Вы прибыли")->JIcons.Destination
    state.instruction.contains("разверн",ignoreCase=true)->JIcons.UTurn
    state.instruction.contains("кругов",ignoreCase=true)||state.instruction.contains("кольц",ignoreCase=true)->JIcons.Roundabout
    state.maneuver==Maneuver.LEFT->JIcons.TurnLeft
    state.maneuver==Maneuver.RIGHT->JIcons.TurnRight
    else->JIcons.Straight
}
