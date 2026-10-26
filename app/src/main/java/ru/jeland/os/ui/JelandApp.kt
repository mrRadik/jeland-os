package ru.jeland.os.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.jeland.os.ui.automation.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.*

enum class Popup(val wheel:Wheel?=null) {
    DRIVER,PASSENGER,CLIMATE,SHADE,WEATHER,APPS,DEBUG,FUEL,BATTERY,VEHICLE,DRIVER_TEMPERATURE,PASSENGER_TEMPERATURE,
    TIRE_FRONT_LEFT(Wheel.FRONT_LEFT),TIRE_FRONT_RIGHT(Wheel.FRONT_RIGHT),TIRE_REAR_LEFT(Wheel.REAR_LEFT),TIRE_REAR_RIGHT(Wheel.REAR_RIGHT);
    companion object {fun tire(wheel:Wheel)=entries.first {it.wheel==wheel}}
}
private data class Destination(val route:String,val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
private val destinations=listOf(
    Destination("home","Главная",JIcons.Home),Destination("music","Музыка",JIcons.MusicNote),Destination("navigation","Навигация",JIcons.Navigation),
    Destination("car","Автомобиль",JIcons.DirectionsCar),Destination("automations","Автоматизации",JIcons.Automations),
    Destination("apps","Приложения",JIcons.Apps),Destination("settings","Настройки",JIcons.Settings))

@Composable fun JelandApp(model:MainViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,model) {
        val observer=LifecycleEventObserver {_,event->when(event){Lifecycle.Event.ON_RESUME->model.resume();Lifecycle.Event.ON_PAUSE->model.pause();else->Unit}}
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))model.resume()
        onDispose {owner.lifecycle.removeObserver(observer);model.pause()}
    }
    val original=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale=(maxWidth.value/1000f).coerceIn(.65f,1.8f)
        CompositionLocalProvider(LocalDensity provides Density(original.density*scale,original.fontScale)) {
            JelandTheme(state.dark,state.preferences.style) {JelandScaffold(state,model)}
        }
    }
}
@Composable fun JelandScaffold(state:UiState,model:MainViewModel) {
    val nav=rememberNavController();val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route?:"home"
    var popupName by rememberSaveable {mutableStateOf<String?>(null)}
    val popup=popupName?.let(Popup::valueOf)
    val show:(Popup)->Unit={popupName=if(popupName==it.name)null else it.name}
    val navigate:(String)->Unit={to->
        popupName=null
        if(route.startsWith("automation-edit/")&&to!="car")nav.popBackStack("automations",false)
        if(to=="car")model.openVehicleSettings()
        else nav.navigate(to){popUpTo("home"){saveState=true};launchSingleTop=true;restoreState=true}
    }
    val c=Jeland.colors
    val home=route=="home"
    val classicHome=home&&state.preferences.style==DashboardStyle.CLASSIC
    // Keep the chrome in one coordinate space. Previously moving the header between
    // Column and overlay instantly resized both outgoing and incoming destinations.
    val pageMotion=if(route=="navigation")snap<androidx.compose.ui.unit.Dp>()else tween(320,easing=FastOutSlowInEasing)
    val pageTop by animateDpAsState(if(classicHome)0.dp else Jeland.headerHeight,pageMotion,label="Page header inset")
    val pageStart by animateDpAsState(if(home)0.dp else 12.dp,pageMotion,label="Page leading inset")
    val pageEnd by animateDpAsState(if(home)0.dp else 16.dp,pageMotion,label="Page trailing inset")
    val message by model.message.collectAsStateWithLifecycle()
    val vehicleNotices by model.vehicleNotices.collectAsStateWithLifecycle()
    LaunchedEffect(message) {if(message!=null){delay(4500);model.clearMessage()}}
    Column(Modifier.fillMaxSize().imePadding().background(c.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Row(Modifier.fillMaxSize()) {
                JelandSidebar(if(popup==Popup.APPS)"apps"else if(route.startsWith("automation-edit/"))"automations"else route,navigate,underHeader=true)
                NavHost(nav,"home",Modifier.weight(1f).fillMaxHeight().padding(top=pageTop,start=pageStart,end=pageEnd,bottom=10.dp).testTag("screen-content-frame"),
                    enterTransition={
                        if(initialState.destination.route=="navigation"||targetState.destination.route=="navigation")EnterTransition.None
                        else if(setOf(initialState.destination.route,targetState.destination.route)==setOf("home","music"))fadeIn(tween(320,delayMillis=40,easing=FastOutSlowInEasing))
                        else fadeIn(tween(Jeland.animation.quick))
                    },
                    exitTransition={
                        if(initialState.destination.route=="navigation"||targetState.destination.route=="navigation")ExitTransition.None
                        else if(setOf(initialState.destination.route,targetState.destination.route)==setOf("home","music"))fadeOut(tween(180,easing=FastOutSlowInEasing))
                        else fadeOut(tween(Jeland.animation.quick))
                    }) {
                    composable("home"){HomeScreen(state,model,show,navigate)}
                    composable("music"){MusicScreen(state,model)}
                    composable("navigation"){NavigationScreen(state,model,mapVisible=popup==null&&message==null&&!vehicleNotices.visible){navigate("settings")}}
                    composable("car"){VehicleScreen(state,model,show)}
                    composable("apps"){AppsScreen(state,model)}
                    composable("automations") {
                        val automationModel:AutomationViewModel=viewModel(factory=remember {AutomationViewModel.Factory(model.dependencies)})
                        AutomationsScreen(automationModel){id->nav.navigate("automation-edit/${id?:"new"}"){launchSingleTop=true}}
                    }
                    composable("automation-edit/{id}") {backStack->
                        val id=checkNotNull(backStack.arguments?.getString("id"))
                        val editor:AutomationEditorViewModel=viewModel(factory=remember(id){AutomationEditorViewModel.Factory(id,model.dependencies.automations)})
                        AutomationEditorScreen(editor){nav.popBackStack()}
                    }
                    composable("settings"){SettingsScreen(state,model,show){
                        nav.navigate("car"){popUpTo("home");launchSingleTop=true}
                    }}
                }
            }
            JelandHeader(state.connectivity,state.car,overHero=classicHome,showTime=!home||state.preferences.style==DashboardStyle.CLASSIC)
            if(popup!=null) {
                val inactivity=if(popup.isTransientControl)controlPanelInactivity(popup){popupName=null}else Modifier
                BackHandler {popupName=null}
                Box(Modifier.matchParentSize().background(c.scrim).clickable {popupName=null})
                Panel(Modifier.align(Alignment.BottomCenter).padding(start=Jeland.railWidth+12.dp,end=16.dp,bottom=12.dp)
                    .then(if(popup==Popup.SHADE)Modifier.widthIn(max=560.dp).fillMaxWidth()else Modifier.fillMaxWidth())
                    .heightIn(max=if(popup==Popup.CLIMATE)840.dp else 720.dp).then(inactivity).testTag("popup-${popup.name.lowercase()}")) {
                    if(popup==Popup.DRIVER_TEMPERATURE||popup==Popup.PASSENGER_TEMPERATURE) {
                        TemperatureSliderPanel(popup==Popup.DRIVER_TEMPERATURE,state,model.dependencies.climate){popupName=null}
                    } else {
                    Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(when(popup){Popup.DRIVER->SeatSide.DRIVER.title;Popup.PASSENGER->SeatSide.PASSENGER.title
                            Popup.CLIMATE->"Климат";Popup.SHADE->"Панорамная шторка";Popup.WEATHER->"Погода"
                            Popup.APPS->"Приложения и избранное";Popup.DEBUG->if(state.car.demo)"Симулятор автомобиля"else"Подключение автомобиля";Popup.FUEL->if(state.car.demo)"Топливо · DEMO"else"Топливо · предварительный расчёт"
                            Popup.BATTERY->"Аккумулятор"
                            Popup.VEHICLE->if(state.car.demo)"Состояние автомобиля · DEMO"else"Состояние автомобиля · ГУ"
                            Popup.DRIVER_TEMPERATURE,Popup.PASSENGER_TEMPERATURE->"Температура"
                            Popup.TIRE_FRONT_LEFT,Popup.TIRE_FRONT_RIGHT,Popup.TIRE_REAR_LEFT,Popup.TIRE_REAR_RIGHT->popup.wheel!!.title},color=c.text,style=Jeland.heading,modifier=Modifier.weight(1f))
                        RoundControl(JIcons.Close,"Закрыть панель",Modifier.testTag("close-popup")){popupName=null}
                    }
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(start=22.dp,end=22.dp,bottom=22.dp)) {PopupContent(popup,state,model,show)}
                    }
                }
            }
            Column(Modifier.align(Alignment.TopCenter).padding(start=Jeland.railWidth+24.dp,end=24.dp,top=Jeland.headerHeight+14.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                VehicleEventNotifications(vehicleNotices,!state.car.demo)
                if(message!=null)Panel(Modifier.clickable {model.clearMessage()}.testTag("ui-message")) {
                    Label(message!!,Modifier.padding(18.dp),small=true)
                }
            }
        }
        ClimateBottomBar(state,model,show)
    }
}

@Composable fun JelandHeader(connection:ConnectivityState,car:VehicleLinkState=VehicleLinkState(),overHero:Boolean=false,showTime:Boolean=true) {
    val c=Jeland.colors
    var now by remember {mutableStateOf(LocalDateTime.now())}
    LaunchedEffect(Unit) {while(true){now=LocalDateTime.now();delay(1000)}}
    Row(Modifier.fillMaxWidth().height(Jeland.headerHeight).padding(end=20.dp).testTag("jeland-header"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.width(Jeland.railWidth),contentAlignment=Alignment.Center) {HeroOutlinedText("JELAND",fontSize=Jeland.type.clockSmall,letterSpacing=Jeland.type.trackTight,outlined=overHero)}
        Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(6.dp)){drawCircle(c.accent)}
            HeroOutlinedText(if(car.demo)"DEMO · БЕЗ ПОДКЛЮЧЕНИЯ К АВТО"else if(!car.connected)"ГУ · НЕТ СВЯЗИ"else if(car.writesEnabled)"ГУ · УПРАВЛЕНИЕ РАЗРЕШЕНО"else"ГУ · ТОЛЬКО ЧТЕНИЕ",color=if(overHero)c.text else c.muted,fontSize=Jeland.type.footnote,fontWeight=FontWeight.Medium,outlined=overHero)
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(25.dp).semantics {contentDescription=if(connection.mobileLevel!=null)"Мобильный сигнал ${connection.mobileLevel} из 4"else"Мобильный сигнал недоступен"}) {
                for(i in 0..3) {
                    val height=size.height*(.25f+i*.23f);val x=size.width*i/4
                    val top=Offset(x,size.height-height);val bar=Size(size.width*.13f,height)
                    if(connection.mobileLevel!=null&&i<connection.mobileLevel)drawRect(c.heroText,top,bar)
                    else drawRect(radioIndicatorColor(c.heroText,false),top,bar,style=Stroke(1.dp.toPx()))
                }
            }
            HeroOutlinedText(connection.networkType?:if(connection.cellular)"Моб."else"—",color=radioIndicatorColor(c.heroText,connection.cellular),fontSize=Jeland.type.footnote,modifier=Modifier.testTag("mobile-network-type"),outlined=overHero)
        }
        WifiStatusIcon(connection)
        Icon(JIcons.Bluetooth,contentDescription=when {connection.bluetoothEnabled==null->"Bluetooth: нет данных";connection.bluetoothEnabled==false->"Bluetooth выключен";connection.bluetoothConnected==true->"Bluetooth: телефон подключён";connection.bluetoothConnected==false->"Bluetooth: нет подключения";else->"Bluetooth включён, подключение неизвестно"},
            modifier=Modifier.size(24.dp).testTag("bluetooth-indicator"),tint=when {connection.bluetoothConnected==true->c.heroText;connection.bluetoothEnabled==true->c.heroText.copy(alpha=.6f);else->radioIndicatorColor(c.heroText,false)})
        if(showTime)HeroOutlinedText(now.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=Jeland.type.clockSmall,outlined=overHero)
    }
}
@Composable fun JelandSidebar(selected:String,onNavigate:(String)->Unit,underHeader:Boolean=false) {
    val c=Jeland.colors
    val minimal=Jeland.style==DashboardStyle.MINIMAL
    val itemShape=androidx.compose.foundation.shape.RoundedCornerShape(if(minimal)0.dp else 14.dp)
    Box(Modifier.width(Jeland.railWidth).fillMaxHeight().background(Brush.verticalGradient(listOf(c.rail,c.background)))) {
        val top=if(underHeader)Jeland.headerHeight else 0.dp
        Column(Modifier.fillMaxSize().padding(start=12.dp,end=12.dp,top=top+8.dp,bottom=8.dp)
            .verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            destinations.forEach {item->
                val active=item.route==selected
                Column(Modifier.fillMaxWidth().height(108.dp).clip(itemShape).background(if(active)c.deep.copy(alpha=.22f)else c.rail.copy(alpha=0f))
                    .then(if(active&&minimal)Modifier.drawBehind {drawRect(c.accent,size=Size(3.dp.toPx(),size.height))}else if(active)Modifier.border(1.dp,c.accent,itemShape)else Modifier)
                    .clickable {onNavigate(item.route)}.testTag("nav-${item.route}"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Glyph(item.icon,item.title,Modifier.size(30.dp),active)
                    Text(item.title,color=if(active&&minimal)c.text else if(active)c.accent else c.muted,fontSize=if(item.route=="automations")Jeland.type.footnote else Jeland.type.navigation,
                        textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=if(item.route=="automations")1 else 2,modifier=Modifier.padding(top=10.dp))
                }
            }
        }
    }
}
@Composable fun ClimateBottomBar(s:UiState,model:MainViewModel,show:(Popup)->Unit) {
    val c=Jeland.colors;val r=model.dependencies.climate;val climateOn=s.climate.controlsEnabled
    val shape=androidx.compose.foundation.shape.RoundedCornerShape(if(s.preferences.style==DashboardStyle.MINIMAL)32.dp else 22.dp)
    Row(Modifier.fillMaxWidth().height(Jeland.climateHeight).padding(start=12.dp,end=12.dp,top=2.dp,bottom=14.dp).clip(shape).background(Brush.verticalGradient(listOf(c.rail,c.background))).border(1.dp,c.outline,shape).padding(horizontal=12.dp,vertical=10.dp).testTag("climate-bar"),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        SeatClimateButton(SeatSide.DRIVER,s.seats.driver,heatKnown=s.seats.known(95),ventilationKnown=s.seats.known(93)){show(Popup.DRIVER)}
        Temperature(true,model){show(Popup.DRIVER_TEMPERATURE)}
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                RoundControl(JIcons.Fan,"Расширенный климат",Modifier.size(58.dp).testTag("expand-climate"),climateOn&&s.climate.known(12)&&s.climate.fan>0,spin=climateOn&&s.climate.known(12)&&s.climate.fan>0,spinSpeed=s.climate.fan){show(Popup.CLIMATE)}
                ClimateBarButton(text="AUTO",description="Автоматический климат",active=s.climate.known(4)&&s.climate.auto,enabled=climateOn&&s.climate.known(4),modifier=Modifier.testTag("bar-auto"),onClick=r::toggleAuto)
                ClimateBarButton(text="A/C",description="Кондиционер",active=s.climate.known(1)&&s.climate.ac,enabled=climateOn&&s.climate.known(1),modifier=Modifier.testTag("bar-ac"),onClick=r::toggleAc)
                ClimateBarButton(icon=JIcons.Loop,description="Рециркуляция",active=s.climate.known(8)&&s.climate.recirculation,enabled=climateOn&&s.climate.known(8),modifier=Modifier.testTag("bar-recirculation"),onClick=r::toggleRecirculation)
                ClimateBarButton(icon=JIcons.Waves,description="Обдув лобового",active=s.climate.known(24)&&s.climate.defrost,enabled=climateOn&&s.climate.known(24),modifier=Modifier.testTag("bar-front-defrost"),onClick=r::toggleDefrost)
                ClimateBarButton(icon=JIcons.RearDefrost,description="Обогрев заднего стекла",active=s.climate.known(80)&&s.climate.rearDefrost,enabled=climateOn&&s.climate.known(80),modifier=Modifier.testTag("bar-rear-defrost"),onClick=r::toggleRearDefrost)
                ClimateBarButton(icon=JIcons.WindshieldHeat,description="Обогрев лобового стекла",active=s.climate.known(65)&&s.climate.windshieldHeat,enabled=climateOn&&s.climate.known(65),modifier=Modifier.testTag("bar-windshield-heat"),onClick=r::toggleWindshieldHeat)
            }
        Temperature(false,model){show(Popup.PASSENGER_TEMPERATURE)}
        SeatClimateButton(SeatSide.PASSENGER,s.seats.passenger,heatKnown=s.seats.known(96),ventilationKnown=s.seats.known(94)){show(Popup.PASSENGER)}
    }
}
@Composable private fun WifiStatusIcon(connection:ConnectivityState) {
    val c=Jeland.colors
    Canvas(Modifier.size(27.dp).testTag("wifi-indicator").semantics {contentDescription=when{!connection.available->"Состояние Wi-Fi недоступно";connection.wifi->"Wi-Fi подключён, уровень ${connection.wifiLevel?:"неизвестен"}";connection.wifiEnabled==false->"Wi-Fi выключен";else->"Wi-Fi не подключён"}}) {
        val base=Offset(size.width/2,size.height*.87f)
        for(i in 1..3) {
            val radius=size.width*(.18f+i*.16f)
            val color=radioIndicatorColor(c.heroText,connection.wifi&&connection.wifiLevel!=null&&connection.wifiLevel>=i)
            drawArc(color,-135f,90f,false,Offset(base.x-radius,base.y-radius),Size(radius*2,radius*2),style=Stroke(1.55.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round))
        }
        drawCircle(radioIndicatorColor(c.heroText,connection.wifi),1.5.dp.toPx(),base)
        if(connection.available&&!connection.wifi)drawLine(radioIndicatorColor(c.heroText,false),Offset(size.width*.1f,size.height*.1f),Offset(size.width*.9f,size.height*.9f),1.5.dp.toPx())
    }
}
@Composable private fun Temperature(driver:Boolean,model:MainViewModel,onClick:()->Unit) {
    val tag=if(driver)"driver"else"passenger"
    val climate=model.state.collectAsStateWithLifecycle().value.climate
    val enabled=climate.controlsEnabled&&climate.known(if(driver)10 else 13)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        RoundControl(JIcons.Remove,"Уменьшить температуру ${if(driver)"водителя"else"пассажира"}",Modifier.size(52.dp).testTag("$tag-minus"),enabled=enabled){model.dependencies.climate.adjustTemperature(driver,-.5)}
        val text=climateDockTemperature(climate,driver)
        Text(text,color=if(enabled)Jeland.colors.text else Jeland.colors.unknown,fontSize=Jeland.type.title,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1,modifier=Modifier.width(44.dp).heightIn(min=48.dp).wrapContentHeight(Alignment.CenterVertically).testTag("$tag-temp")
            .clickable(enabled=enabled,role=androidx.compose.ui.semantics.Role.Button,onClick=onClick))
        RoundControl(JIcons.Add,"Увеличить температуру ${if(driver)"водителя"else"пассажира"}",Modifier.size(52.dp).testTag("$tag-plus"),enabled=enabled){model.dependencies.climate.adjustTemperature(driver,.5)}
    }
}
