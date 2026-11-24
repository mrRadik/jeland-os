package ru.jeland.os.ui.cluster

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import ru.jeland.os.core.*
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.localization.localized
import ru.jeland.os.navigation.rememberStrelkaRoadAlert
import ru.jeland.os.navigation.comaps.CoMapsClusterSurface
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.util.Date

@Composable internal fun ClusterDesignSettings(state:UiState,model:MainViewModel,onPreview:()->Unit) {
    Panel(Modifier.fillMaxWidth().testTag("cluster-settings")) {
        Row(Modifier.fillMaxWidth().padding(22.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)){SectionTitle("Приборная панель",state.preferences.clusterDesign.title)}
            Control("Настроить",Modifier.testTag("open-cluster-preview"),onClick=onPreview)
        }
    }
}

@Composable internal fun ClusterDesignChoices(selectedDesign:ClusterDesign,dark:Boolean,accent:ClusterAccent,media:MediaState,navigationMode:ClusterNavigationMode=ClusterNavigationMode.MAP,onSelect:(ClusterDesign)->Unit) {
    val c=Jeland.colors
    ClusterDesign.entries.chunked(2).forEach {row->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            row.forEach {design->
                val selected=selectedDesign==design
                val shape=RoundedCornerShape(14.dp)
                Column(Modifier.weight(1f).clip(shape).background(c.background)
                    .border(if(selected)1.5.dp else 1.dp,if(selected)c.accent else c.outline.copy(alpha=.5f),shape)
                    .clickable(role=Role.RadioButton){onSelect(design)}.semantics {this.selected=selected}
                    .testTag("cluster-design-${design.name.lowercase()}")) {
                    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1920f/720f)) {
                        ClusterArtwork(ClusterPanelState(),design,dark,"—:—",Modifier.fillMaxSize(),miniature=true,accent=accent,navigationMode=navigationMode)
                        ClusterMusicOverlay(media,design,dark,accent,miniature=true)
                    }
                    Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected,onClick=null,colors=RadioButtonDefaults.colors(selectedColor=c.accent,unselectedColor=c.muted))
                        Column(Modifier.weight(1f).padding(start=8.dp)) {
                            Text(design.title,color=c.text,fontSize=Jeland.type.body,maxLines=1)
                            Text(design.description,color=c.muted,fontSize=Jeland.type.footnote,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Live readings on the GU. Display output is applied by the explicit preview action only. */
@Composable fun ClusterPreviewScreen(state:UiState,model:MainViewModel,onBack:()->Unit) {
    androidx.activity.compose.BackHandler(onBack=onBack)
    val trips by model.dependencies.trips.state.collectAsStateWithLifecycle()
    var elapsed by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
    var clock by remember {mutableStateOf("")}
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle,context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true){elapsed=SystemClock.elapsedRealtime();clock=android.text.format.DateFormat.getTimeFormat(context).format(Date());delay(1000)}
        }
    }
    val panel=rememberClusterPanelSnapshot(state,trips.current,trips.speed,elapsed)
    val design=state.preferences.clusterDesign
    var expanded by rememberSaveable {mutableStateOf(false)}
    val c=Jeland.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=20.dp).testTag("cluster-preview-page"),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)){SectionTitle("Приборная панель")}
            Control("Назад в настройки",Modifier.testTag("leave-cluster-preview"),onClick=onBack)
        }
        Panel(Modifier.fillMaxWidth().testTag("cluster-preview-card")) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("Предпросмотр",color=c.muted,fontSize=Jeland.type.footnote)
                        Text(design.title,color=c.text,fontSize=Jeland.type.title)
                    }
                    Control("На весь экран",Modifier.testTag("expand-cluster-preview")){expanded=true}
                }
                if(!expanded)ClusterStage(panel,state.media,design,state.dark,clock,model,Modifier.fillMaxWidth().aspectRatio(1920f/720f),state.preferences.clusterAccent,colorMode=state.preferences.clusterColorMode,navigationMode=state.preferences.clusterNavigationMode)
                if(!expanded)ClusterOutputControls(model,design)
            }
        }
        Panel(Modifier.fillMaxWidth().testTag("cluster-configuration-card")) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Оформление",color=c.text,fontSize=Jeland.type.title)
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    ClusterDesignChoices(design,state.dark,state.preferences.clusterAccentFor(ClusterDriveMode.read(state.car,SystemClock.elapsedRealtime())),state.media,state.preferences.clusterNavigationMode){model.dependencies.preferences.change(model.dependencies.preferences.state.value.copy(clusterDesign=it))}
                }
                if(design!=ClusterDesign.ORIGINAL) {
                    Box(Modifier.fillMaxWidth().padding(vertical=4.dp).height(1.dp).background(c.outline.copy(alpha=.45f)))
                    ClusterNavigationSettings(state.preferences.clusterNavigationMode){mode->
                        model.dependencies.preferences.change(model.dependencies.preferences.state.value.copy(clusterNavigationMode=mode))
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical=4.dp).height(1.dp).background(c.outline.copy(alpha=.45f)))
                    ClusterColorSettings(state.preferences,ClusterDriveMode.read(state.car,SystemClock.elapsedRealtime())){model.dependencies.preferences.change(it)}
                    Box(Modifier.fillMaxWidth().padding(vertical=4.dp).height(1.dp).background(c.outline.copy(alpha=.45f)))
                    ClusterStatisticsSettings(state.preferences.clusterTripPeriod){period->
                        model.dependencies.preferences.change(model.dependencies.preferences.state.value.copy(clusterTripPeriod=period))
                    }
                }
            }
        }
    }
    if(expanded)Dialog(onDismissRequest={expanded=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(c.background).testTag("cluster-preview-expanded")) {
            val availableHeight=(maxHeight-180.dp).coerceAtLeast(1.dp)
            val width=minOf(maxWidth,availableHeight*(1920f/720f))
            ClusterStage(panel,state.media,design,state.dark,clock,model,Modifier.width(width).aspectRatio(1920f/720f).align(Alignment.Center),state.preferences.clusterAccent,colorMode=state.preferences.clusterColorMode,navigationMode=state.preferences.clusterNavigationMode)
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(design.title,Modifier.weight(1f),color=c.text,fontSize=Jeland.type.title)
                RoundControl(JIcons.Close,"Закрыть предпросмотр",Modifier.testTag("close-cluster-preview")){expanded=false}
            }
            ClusterOutputControls(model,design,Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp))
        }
    }
}

@Composable private fun ClusterOutputControls(model:MainViewModel,design:ClusterDesign,modifier:Modifier=Modifier) {
    val projection=model.dependencies.clusterProjection
    val status by projection.status.collectAsStateWithLifecycle()
    val applied by projection.applied.collectAsStateWithLifecycle()
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(design!=ClusterDesign.ORIGINAL)Control("Показать на приборке",Modifier.weight(1f).testTag("show-cluster-output"),selected=true) {
                projection.show()
            }
            Control("Вернуть штатную",Modifier.weight(1f).testTag("restore-stock-cluster")){projection.restoreStock()}
        }
        Text(if(status=="Приборная панель включена")applied?.let {localized("На приборке")+": "+it.design.title}?:status else status,
            color=Jeland.colors.muted,fontSize=Jeland.type.footnote)
    }
}

@Composable internal fun ClusterStage(panel:ClusterPanelState,media:MediaState,design:ClusterDesign,dark:Boolean,clock:String,model:MainViewModel,modifier:Modifier,accent:ClusterAccent,projection:Boolean=false,colorMode:ClusterColorMode=ClusterColorMode.MANUAL,navigationMode:ClusterNavigationMode=ClusterNavigationMode.MAP) {
    val roadAlert=rememberStrelkaRoadAlert(model.dependencies.navigation.roadAlerts)
    val resolvedAccent=if(colorMode==ClusterColorMode.DRIVE_MODE)panel.driveMode?.accent?:accent else accent
    BoxWithConstraints(modifier.clip(RoundedCornerShape(8.dp)).background(Jeland.colors.background).testTag("cluster-stage")) {
        if(design==ClusterDesign.NAVIGATION&&navigationMode==ClusterNavigationMode.MAP) {
            CoMapsClusterSurface(model.dependencies.navigation,dark,
                Modifier.offset(x=maxWidth*(25f/2048f),y=maxHeight*(77f/768f))
                    .width(maxWidth*(1998f/2048f)).height(maxHeight*(673f/768f)),
                panel.activeNavigation!=null,mapFirst=true,projection=projection)
        }
        ClusterArtwork(panel,design,dark,clock,Modifier.matchParentSize(),accent=resolvedAccent,
            startupAt=if(projection)model.dependencies.clusterProjection.openedAtMillis else null,roadAlert=roadAlert,navigationMode=navigationMode,
            onRoadAlertShown=model.dependencies.navigation.roadAlerts::alertShown) {
            clusterMapBounds(design,panel.activeNavigation!=null)?.takeIf {navigationMode==ClusterNavigationMode.MAP}?.let {bounds->
                Box(Modifier.offset(x=maxWidth*((bounds.left+2f)/2048f),y=maxHeight*(bounds.top/768f))
                        .width(maxWidth*((bounds.width-4f)/2048f)).height(maxHeight*((bounds.height-2f)/768f))
                        .clip(RoundedCornerShape(bottomStart=maxWidth*(16f/2048f),bottomEnd=maxWidth*(16f/2048f)))
                        .testTag("cluster-minimap")) {
                    CoMapsClusterSurface(model.dependencies.navigation,dark,Modifier.matchParentSize(),
                        panel.activeNavigation!=null,projection=projection,compact=true)
                }
            }
            if(!clusterMusicCovered(panel,design))ClusterMusicOverlay(media,design,dark,resolvedAccent)
        }
    }
}

@Composable internal fun ClusterArtwork(panel:ClusterPanelState,design:ClusterDesign,dark:Boolean,clock:String,modifier:Modifier=Modifier,miniature:Boolean=false,accent:ClusterAccent=ClusterAccent.CYAN,startupAt:Long?=null,roadAlert:RoadAlert?=null,navigationMode:ClusterNavigationMode=ClusterNavigationMode.MAP,onRoadAlertShown:(RoadAlert)->Unit={},content:@Composable BoxWithConstraintsScope.()->Unit={}) {
    val resources=LocalContext.current.resources
    val renderer=remember(resources) {ClusterRenderer(resources)}
    DisposableEffect(renderer){onDispose {renderer.close()}}
    val speed by animateFloatAsState(panel.speed?.toFloat()?:0f,tween(130),label="Cluster speed scale")
    val rpm by animateFloatAsState(panel.rpm?.toFloat()?:0f,androidx.compose.animation.core.spring(dampingRatio=.9f,stiffness=110f),label="Cluster rpm scale")
    val startupElapsed by produceState<Long?>(null,startupAt) {
        if(startupAt!=null) {
            while(true) {
                val age=SystemClock.elapsedRealtime()-startupAt
                if(age>=ClusterStartupSweep.DURATION)break
                value=age
                withFrameNanos { }
            }
        }
        value=null
    }
    val blinkBelt=ClusterJ7Indicators.passengerWarning(panel.indicators.orEmpty())&&!miniature
    val beltVisible by produceState(true,blinkBelt) {
        value=true
        while(blinkBelt){delay(500);value=!value}
    }
    val labels=ClusterLabels(localized("км/ч"),localized("×1000 об/мин"),localized("км"),localized("м"),localized("л"),
        localized("л/100 км"),localized(panel.tripPeriod.title),localized("Нет маршрута"),localized("Штатная приборная панель автомобиля"),localized("ч"),
        warnings=mapOf(ClusterLamp.BRAKE to localized("Тормозная система"),ClusterLamp.OIL_PRESSURE to localized("Низкое давление масла"),
            ClusterLamp.COOLANT_HOT to localized("Перегрев двигателя"),ClusterLamp.DOOR to localized("Открыта дверь"),ClusterLamp.RESERVE to localized("Мало топлива")))
    val unknown=localized("Нет данных")
    val speedLabel=panel.speed?.let {"${it.toInt()} ${labels.kmh}"}?:unknown
    val rpmLabel=panel.rpm?.toInt()?.toString()?:unknown
    val description=localized("Приборная панель")+": ${design.title}; "+localized("Скорость")+": $speedLabel; "+localized("Обороты")+": $rpmLabel"
    BoxWithConstraints(modifier) {
    Canvas(Modifier.matchParentSize().testTag(if(miniature)"cluster-thumbnail-${design.name.lowercase()}"else"cluster-artwork")
        .semantics {contentDescription=description}) {
        drawIntoCanvas {canvas->
            val native=canvas.nativeCanvas
            native.save();native.scale(size.width/1920f,size.height/720f)
            renderer.draw(native,panel,design,dark,clock,labels,if(panel.speed==null)null else speed,if(panel.rpm==null)null else rpm,miniature,accent,beltVisible,
                startupElapsed?.let {ClusterStartupSweep.sample(it,speed,rpm)},includeMessage=false,roadAlert=if(miniature)null else roadAlert,navigationMode=navigationMode)
            native.restore()
        }
    }
        if(design!=ClusterDesign.ORIGINAL) {
            val cameraLimit=remember(roadAlert?.title,miniature){if(miniature)null else roadAlert?.title?.let {RoadAlertSymbol.cameraSpeedLimit(it)}}
            val cameraLimitDescription=localized("Ограничение скорости камеры")+": $cameraLimit ${labels.kmh}"
            if(cameraLimit!=null)clusterSpeedLimitCircle(design)?.let {sign->
                Box(Modifier.offset(x=maxWidth*((sign.x-sign.radius)/2048f),y=maxHeight*((sign.y-sign.radius)/768f))
                    .width(maxWidth*(sign.radius*2/2048f)).height(maxHeight*(sign.radius*2/768f))
                    .testTag("cluster-camera-speed-limit").semantics {contentDescription=cameraLimitDescription})
            }
            panel.activeNavigation?.let {nav->
                Box(Modifier.matchParentSize().testTag("cluster-route-guidance")
                    .semantics {contentDescription="${nav.distanceToManeuver} ${labels.metres}; ${nav.street}"})
            }
            content()
            Canvas(Modifier.matchParentSize().testTag("cluster-warning-overlay")) {
                drawIntoCanvas {canvas->
                    val native=canvas.nativeCanvas;native.save();native.scale(size.width/1920f,size.height/720f)
                    renderer.drawWarningOverlay(native,panel,design,dark,labels);native.restore()
                }
            }
            ClusterIslandOverlay(panel,dark,clock,labels,accent,renderer,if(miniature)null else roadAlert,onRoadAlertShown)
        }
    }
}

/** The capsule expands over the existing scene; neither instruments nor permanent lamps reflow. */
@Composable private fun BoxWithConstraintsScope.ClusterIslandOverlay(panel:ClusterPanelState,dark:Boolean,clock:String,
    labels:ClusterLabels,accent:ClusterAccent,renderer:ClusterRenderer,roadAlert:RoadAlert?,onRoadAlertShown:(RoadAlert)->Unit) {
    val notice=clusterIslandNotice(panel,labels,roadAlert)
    if(notice?.road==true&&roadAlert!=null) {
        LifecycleResumeEffect(roadAlert) {onRoadAlertShown(roadAlert);onPauseOrDispose {}}
    }
    var previous by remember {mutableStateOf<ClusterIslandNotice?>(notice)}
    if(notice!=null)SideEffect {previous=notice}
    val retained=notice?:previous
    val target=remember(notice){renderer.islandTarget(notice)}
    val progress by animateFloatAsState(if(notice==null)0f else 1f,tween(280),label="Cluster island visibility")
    val width by animateFloatAsState(target.first,tween(280),label="Cluster island width")
    val height by animateFloatAsState(target.second,tween(280),label="Cluster island height")
    LaunchedEffect(notice,progress){if(notice==null&&progress==0f)previous=null}
    Canvas(Modifier.matchParentSize().testTag(if(notice?.road==true)"cluster-road-alert"else"cluster-message-overlay")
        .semantics {if(notice?.road==true)contentDescription=listOfNotNull(notice.text,notice.detail).joinToString(". ")}) {
        drawIntoCanvas {canvas->
            val native=canvas.nativeCanvas;native.save();native.scale(size.width/1920f,size.height/720f)
            renderer.drawIsland(native,panel,dark,clock,labels,accent,width,height,progress,retained);native.restore()
        }
    }
    ClusterHeaderOverlay(panel,dark,clock)
}

/** Compose text stays independent of the cached native fascia across design changes. */
@Composable private fun BoxWithConstraintsScope.ClusterHeaderOverlay(panel:ClusterPanelState,dark:Boolean,clock:String) {
    val density=LocalDensity.current
    val scale=maxWidth.value/2048f
    val color=Color(if(dark)0xFFF5FAFF else 0xFF0D1D2A)
    val outside=panel.outside?.let {
        String.format(java.util.Locale.getDefault(),if(it%1.0==0.0)"%.0f °C"else"%.1f °C",it)
    }?:"— °C"
    for((index,item) in listOf(Triple(818f,clock,37f),Triple(1024f,panel.gear?:"—",52f),Triple(1230f,outside,33f)).withIndex()) {
        Box(Modifier.offset(x=maxWidth*((item.first-80f)/2048f),y=maxHeight*(ClusterHeaderLayout.TOP/768f))
            .width(maxWidth*(160f/2048f)).height(maxHeight*((ClusterHeaderLayout.BOTTOM-ClusterHeaderLayout.TOP)/768f)),contentAlignment=Alignment.Center) {
            CompositionLocalProvider(LocalDensity provides Density(density.density*scale,fontScale=1f)) {
                Text(item.second,color=color,fontSize=item.third.sp,maxLines=1,
                    fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Normal,
                    modifier=Modifier.testTag("cluster-header-${listOf("clock","gear","outside")[index]}").layout {measurable,constraints->
                        val placeable=measurable.measure(constraints)
                        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            textSize=item.third.sp.toPx();typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL)
                        }
                        val bounds=android.graphics.Rect()
                        paint.getTextBounds(item.second,0,item.second.length,bounds)
                        // Give the parent the visible glyph height instead of font leading.
                        layout(placeable.width,bounds.height().coerceAtLeast(1)) {
                            placeable.place(0,-placeable[FirstBaseline]-bounds.top)
                        }
                    })
            }
        }
    }
}
