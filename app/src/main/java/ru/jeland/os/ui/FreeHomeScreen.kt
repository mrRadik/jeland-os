package ru.jeland.os.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.jeland.os.core.*
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.localization.localized
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The editor stores placement only. Every tile delegates to an existing application widget. */
@Composable fun FreeHomeScreen(s:UiState,model:MainViewModel,show:(Popup)->Unit,navigate:(String)->Unit) {
    var editing by rememberSaveable {mutableStateOf(false)}
    var draft by rememberSaveable {mutableStateOf(s.preferences.freeLayout.encode())}
    var selection by rememberSaveable {mutableStateOf<String?>(null)}
    var catalog by remember {mutableStateOf(false)}
    var hint by remember {mutableStateOf("")}
    var dragging by remember {mutableStateOf<DashboardWidgetKind?>(null)}
    var resizeCorner by remember {mutableStateOf<WidgetCorner?>(null)}
    var dragOrigin by remember {mutableStateOf<DashboardWidget?>(null)}
    var dragDelta by remember {mutableStateOf(Offset.Zero)}
    var dragScrollStart by remember {mutableIntStateOf(0)}
    var autoScroll by remember {mutableIntStateOf(0)}
    val layout=remember(editing,draft,s.preferences.freeLayout){if(editing)FreeDashboardLayout.decode(draft)else s.preferences.freeLayout}
    val latestLayout by rememberUpdatedState(layout)
    val selected=layout.widgets.find {it.kind.name==selection}
    val scroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    val c=Jeland.colors
    fun edit(){draft=s.preferences.freeLayout.encode();selection=null;hint="";editing=true}
    fun cancel(){editing=false;selection=null;dragging=null;resizeCorner=null;dragOrigin=null;autoScroll=0;catalog=false}
    fun replace(next:FreeDashboardLayout?) {
        if(next==null){hint="Не удалось изменить виджет.";return}
        draft=next.encode();hint=""
    }
    BackHandler(editing){cancel()}
    LaunchedEffect(dragging,autoScroll) {
        if(dragging!=null&&autoScroll!=0)while(true) {
            scroll.scrollBy(autoScroll*12f);delay(16)
        }
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.background,c.rail,c.background)))
        .padding(horizontal=14.dp).testTag("free-home")) {
        Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(editing) {
                Control("Добавить",Modifier.weight(1f).height(48.dp).testTag("free-add")){catalog=true}
                Control("Сбросить",Modifier.weight(1f).height(48.dp).testTag("free-reset")){
                    draft=FreeDashboardLayout.default().encode();selection=null;hint="";scope.launch {scroll.scrollTo(0)}
                }
                Control("Отмена",Modifier.weight(1f).height(48.dp).testTag("free-cancel")){cancel()}
                Control("Сохранить",Modifier.weight(1.2f).height(48.dp).testTag("free-save"),selected=true){
                    model.dependencies.preferences.change(s.preferences.copy(freeLayout=layout));cancel()
                }
            }else {
                Text("Мой экран",Modifier.weight(1f),color=c.text,fontSize=Jeland.type.body)
                Control("Изменить",Modifier.height(48.dp).testTag("free-edit"),onClick=::edit)
            }
        }
        if(editing)Text(hint.ifBlank {"Перетаскивайте виджеты. Тяните за углы, чтобы изменить размер."},
            color=if(hint.isBlank())c.muted else c.warm,fontSize=Jeland.type.footnote,maxLines=2,modifier=Modifier.padding(vertical=6.dp).testTag("free-editor-hint"))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("free-viewport")) {
            val cell=(maxWidth/FreeDashboardLayout.COLUMNS).coerceAtLeast(1.dp)
            val cellPx=with(LocalDensity.current){cell.toPx()}
            val viewportPx=with(LocalDensity.current){maxHeight.toPx()}
            val scrollDelta=if(dragging!=null)scroll.value-dragScrollStart else 0
            val moving=dragOrigin
            val visualDelta=dragDelta+Offset(0f,scrollDelta.toFloat())
            val candidate=if(resizeCorner!=null)moving?.resized(resizeCorner!!,(visualDelta.x/cellPx).roundToInt(),(visualDelta.y/cellPx).roundToInt())else moving?.copy(
                x=(moving.x+visualDelta.x/cellPx).roundToInt().coerceIn(0,FreeDashboardLayout.COLUMNS-moving.footprint.columns),
                y=(moving.y+visualDelta.y/cellPx).roundToInt().coerceIn(0,FreeDashboardLayout.ROWS-moving.footprint.rows))
            fun startGesture(widget:DashboardWidget,corner:WidgetCorner?=null) {
                selection=widget.kind.name;dragging=widget.kind;dragOrigin=widget;resizeCorner=corner
                dragDelta=Offset.Zero;dragScrollStart=scroll.value;hint=""
                draft=latestLayout.raise(widget.kind).encode()
            }
            fun endGesture(apply:Boolean) {
                if(apply)dragOrigin?.let {old->
                    val delta=dragDelta+Offset(0f,(scroll.value-dragScrollStart).toFloat())
                    val corner=resizeCorner
                    replace(if(corner!=null)latestLayout.place(old.resized(corner,(delta.x/cellPx).roundToInt(),(delta.y/cellPx).roundToInt()))
                        else latestLayout.move(old.kind,(old.x+delta.x/cellPx).roundToInt(),(old.y+delta.y/cellPx).roundToInt()))
                }
                dragging=null;resizeCorner=null;dragOrigin=null;dragDelta=Offset.Zero;autoScroll=0
            }
            val rows=maxOf(ceil(viewportPx/cellPx).toInt(),layout.bottom+(if(editing)6 else 0),(candidate?.bottom?:0)+(if(editing)4 else 0))
                .coerceAtMost(FreeDashboardLayout.ROWS)
            LaunchedEffect(draft,editing) {
                if(editing&&dragging==null&&selected!=null) {
                    withFrameNanos {}
                    val top=selected.y*cellPx
                    val bottom=selected.bottom*cellPx
                    val target=when {top<scroll.value->top;bottom>scroll.value+viewportPx->bottom-viewportPx;else->scroll.value.toFloat()}
                    scroll.animateScrollTo(target.roundToInt().coerceIn(0,scroll.maxValue))
                }
            }
            Box(Modifier.fillMaxSize().verticalScroll(scroll,enabled=dragging==null).testTag("free-scroll")) {
                Box(Modifier.fillMaxWidth().height(cell*rows).testTag("free-canvas")) {
                    if(editing)Canvas(Modifier.matchParentSize()) {
                        for(x in 0..FreeDashboardLayout.COLUMNS)drawLine(c.outline.copy(alpha=.22f),Offset(x*cellPx,0f),Offset(x*cellPx,size.height),1.dp.toPx())
                        for(y in 0..rows)drawLine(c.outline.copy(alpha=.22f),Offset(0f,y*cellPx),Offset(size.width,y*cellPx),1.dp.toPx())
                    }
                    if(candidate!=null)Box(Modifier.offset{IntOffset((candidate.x*cellPx).roundToInt(),(candidate.y*cellPx).roundToInt())}
                        .size(cell*candidate.footprint.columns,cell*candidate.footprint.rows).padding(5.dp)
                        .background((if(layout.accepts(candidate))c.success else c.critical).copy(alpha=.18f),Jeland.card))
                    CompositionLocalProvider(LocalFramelessDashboard provides true) {
                        layout.widgets.forEach {widget->key(widget.kind) {
                            val currentWidget by rememberUpdatedState(widget)
                            val active=widget.kind==dragging
                            val shown=if(active&&resizeCorner!=null)candidate?:widget else widget
                            val positionDescription=localized("Колонка ${widget.x+1}, строка ${widget.y+1}")
                            val name=localized(widget.kind.title)
                            val resizeDescription=localized("Изменить размер")
                            Box(Modifier.offset {IntOffset((shown.x*cellPx+if(active&&resizeCorner==null)visualDelta.x else 0f).roundToInt(),
                                (shown.y*cellPx+if(active&&resizeCorner==null)visualDelta.y else 0f).roundToInt())}
                                .size(cell*shown.footprint.columns,cell*shown.footprint.rows).padding(6.dp)
                                .zIndex(if(active||editing&&selection==widget.kind.name)1f else 0f)
                                // TextureView's external texture must not be rendered through a
                                // rounded hardware clip (unsupported by some head-unit/desktop GL drivers).
                                .then(if(widget.kind==DashboardWidgetKind.NAVIGATION)Modifier.clipToBounds()else Modifier.clip(Jeland.card))
                                .background(c.panel.copy(alpha=.18f)).testTag("free-widget-${widget.kind.name}")) {
                                Box(Modifier.fillMaxSize().then(if(editing)Modifier.clearAndSetSemantics {}else Modifier)) {
                                    FreeWidgetContent(shown,s,model,show,navigate)
                                }
                                if(editing)Box(Modifier.matchParentSize().background(c.accent.copy(alpha=if(selection==widget.kind.name).07f else .015f))
                                    .border(if(selection==widget.kind.name)2.dp else 1.dp,if(selection==widget.kind.name)c.accent else c.outline,Jeland.card)
                                    .testTag("free-select-${widget.kind.name}")
                                    .semantics {contentDescription=name;stateDescription=positionDescription;this.selected=selection==widget.kind.name}
                                    .clickable {selection=widget.kind.name;draft=latestLayout.raise(widget.kind).encode();hint=""}
                                    .pointerInput(widget.kind,cellPx) {
                                        detectDragGestures(onDragStart={
                                            startGesture(currentWidget)
                                        },onDragCancel={endGesture(false)},onDragEnd={endGesture(true)}) {change,amount->
                                            change.consume();dragDelta+=amount
                                            val fingerY=currentWidget.y*cellPx+dragDelta.y-dragScrollStart+change.position.y
                                            autoScroll=when {fingerY<60->-1;fingerY>viewportPx-60->1;else->0}
                                        }
                                    }) {
                                    Text(widget.kind.title,color=c.text,fontSize=Jeland.type.micro,
                                        modifier=Modifier.align(Alignment.TopCenter).background(c.panel.copy(alpha=.94f),Jeland.control).padding(horizontal=10.dp,vertical=5.dp))
                                    if(selection==widget.kind.name)WidgetCorner.entries.forEach {corner->
                                        val alignment=when(corner) {
                                            WidgetCorner.TOP_LEFT->Alignment.TopStart;WidgetCorner.TOP_RIGHT->Alignment.TopEnd
                                            WidgetCorner.BOTTOM_LEFT->Alignment.BottomStart;WidgetCorner.BOTTOM_RIGHT->Alignment.BottomEnd
                                        }
                                        Box(Modifier.align(alignment).size(44.dp).testTag("free-resize-${widget.kind.name}-${corner.name}")
                                            .semantics {contentDescription=resizeDescription}
                                            .pointerInput(widget.kind,corner,cellPx) {
                                                detectDragGestures(onDragStart={startGesture(currentWidget,corner)},
                                                    onDragCancel={endGesture(false)},onDragEnd={endGesture(true)}) {change,amount->
                                                    change.consume();dragDelta+=amount
                                                    val old=dragOrigin?:currentWidget
                                                    val fingerY=(if(corner.top)old.y else old.bottom)*cellPx+dragDelta.y-dragScrollStart
                                                    autoScroll=when {fingerY<60->-1;fingerY>viewportPx-60->1;else->0}
                                                }
                                            }.padding(10.dp).background(c.panel,Jeland.control).border(2.dp,c.accent,Jeland.control))
                                    }
                                }
                            }
                        }}
                    }
                    if(layout.widgets.isEmpty())Column(Modifier.align(Alignment.TopCenter).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Label("Добавьте виджеты на свой экран",muted=true)
                        Control("Добавить виджеты",Modifier.padding(top=14.dp).testTag("free-empty-add")){if(!editing)edit();catalog=true}
                    }
                }
            }
        }
        if(editing&&selected!=null)Column(Modifier.fillMaxWidth().background(c.panel,Jeland.control).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(selected.kind.title,Modifier.weight(1f),color=c.text,fontSize=Jeland.type.caption,maxLines=1)
                Control("Убрать",Modifier.testTag("free-remove")){replace(layout.remove(selected.kind));selection=null}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DashboardWidgetSize.entries.forEach {size->Control(size.title,Modifier.weight(1f).height(44.dp).testTag("free-size-${size.name}"),selected=selected.columns==0&&selected.size==size){replace(layout.resize(selected.kind,size))}}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(selected.kind==DashboardWidgetKind.ACTIONS||selected.kind==DashboardWidgetKind.FAVORITES)
                    Control("Квадрат",Modifier.weight(1f).height(44.dp).testTag("free-square")){replace(layout.square(selected.kind))}
                Control("На передний план",Modifier.weight(1f).height(44.dp).testTag("free-raise")){replace(layout.raise(selected.kind))}
                Control("На задний план",Modifier.weight(1f).height(44.dp).testTag("free-lower")){replace(layout.lower(selected.kind));selection=null}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Label("Переместить",small=true,muted=true,modifier=Modifier.weight(1f))
                listOf(Triple("←",-1,0),Triple("↑",0,-1),Triple("↓",0,1),Triple("→",1,0)).forEachIndexed {index,(title,dx,dy)->
                    Control(title,Modifier.width(60.dp).height(44.dp).testTag("free-move-$index")){replace(layout.move(selected.kind,selected.x+dx,selected.y+dy))}
                }
            }
        }
    }
    if(catalog)Dialog(onDismissRequest={catalog=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Panel(Modifier.widthIn(max=680.dp).fillMaxWidth().padding(20.dp)) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Label("Виджеты",modifier=Modifier.weight(1f));RoundControl(JIcons.Close,"Закрыть список виджетов",Modifier.testTag("free-catalog-close")){catalog=false}
                }
                LazyColumn(Modifier.heightIn(max=540.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(DashboardWidgetKind.entries,key={it.name}){kind->
                        val present=layout.widgets.any {it.kind==kind}
                        Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(kind.title,color=c.text,fontSize=Jeland.type.caption,modifier=Modifier.weight(1f))
                            Control(if(present)"Выбрать"else"Добавить",Modifier.testTag("free-add-${kind.name}")){
                                val next=if(present)layout.raise(kind)else layout.add(kind)
                                replace(next)
                                if(next!=null){selection=kind.name;catalog=false}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun FreeWidgetContent(widget:DashboardWidget,s:UiState,model:MainViewModel,show:(Popup)->Unit,navigate:(String)->Unit) {
    val f=widget.footprint
    val compact=if(widget.columns==0)widget.size==DashboardWidgetSize.COMPACT else f.rows<=3||f.columns<=4
    val large=if(widget.columns==0)widget.size==DashboardWidgetSize.LARGE else f.columns>=7&&f.rows>=6
    val tileColumns=if(f.columns<8&&f.rows>=4)2 else 4
    when(widget.kind) {
        DashboardWidgetKind.CLOCK->DashboardClock(Modifier.fillMaxSize().padding(12.dp),compact=compact,outlined=false)
        DashboardWidgetKind.WEATHER->WeatherWidget(s.weather,Modifier.fillMaxSize(),model::openWeather)
        DashboardWidgetKind.MUSIC->MediaWidget(s.media,model,Modifier.fillMaxSize(),compact=!large,expanded=large)
        DashboardWidgetKind.NAVIGATION->ru.jeland.os.navigation.OfflineNavigationWidget(model.dependencies.navigation,s.dark,Modifier.fillMaxSize(),framed=false){navigate("navigation")}
        DashboardWidgetKind.VEHICLE->VehicleTopView(s.vehicle,Modifier.fillMaxSize().padding(6.dp),showTires=true,portrait=f.rows>f.columns,onVehicle={show(Popup.VEHICLE)},onTire={show(Popup.tire(it))})
        DashboardWidgetKind.FUEL->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){VehicleFuelIndicator(s.vehicle.fuel,if(compact)Modifier else Modifier.fillMaxSize().padding(12.dp),gauge=!compact){show(Popup.FUEL)}}
        DashboardWidgetKind.BATTERY->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){BatteryIndicator(s.vehicle.battery,if(compact)Modifier else Modifier.fillMaxSize().padding(12.dp),gauge=!compact){show(Popup.BATTERY)}}
        DashboardWidgetKind.ACTIONS->QuickActions(s,model,Modifier.fillMaxSize(),show,navigate,columns=tileColumns)
        DashboardWidgetKind.FAVORITES->FavoriteAppsWidget(s.apps,model,Modifier.fillMaxSize(),compact=!large,columns=tileColumns){show(Popup.APPS)}
        DashboardWidgetKind.HERO->BoxWithConstraints(Modifier.fillMaxSize()){JelandHero(s,maxHeight,{model.openWeather()},{show(Popup.FUEL)})}
    }
}
