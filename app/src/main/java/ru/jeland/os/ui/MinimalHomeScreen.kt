package ru.jeland.os.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.Jeland

/** Alternative arrangement of existing dashboard components. No second vehicle,
 * media, weather, navigation or command implementation lives here.
 */
@Composable fun MinimalHomeScreen(s:UiState,model:MainViewModel,show:(Popup)->Unit,navigate:(String)->Unit) {
    val c=Jeland.colors
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()
        .background(Brush.verticalGradient(listOf(c.background,c.rail,c.background)))
        .drawBehind {
            drawRect(Brush.radialGradient(listOf(c.deep.copy(alpha=if(s.dark).34f else .14f),Color.Transparent),
                center=Offset(size.width*.5f,size.height*.50f),radius=size.width*.85f))
            drawRect(Brush.radialGradient(listOf(c.warm.copy(alpha=if(s.dark).10f else .12f),Color.Transparent),
                center=Offset(size.width*.94f,size.height*.1f),radius=size.width*.65f))
            drawRect(Brush.radialGradient(listOf(c.accent.copy(alpha=if(s.dark).12f else .05f),Color.Transparent),
                center=Offset(size.width*.5f,size.height*.51f),radius=size.width*.55f))
            repeat(17) {i->
                drawLine(c.accent.copy(alpha=if(s.dark).025f else .018f),
                    Offset(size.width*.5f,size.height*.36f),Offset(size.width*(i-4)/8f,size.height*.85f),.7.dp.toPx())
            }
        }.padding(horizontal=18.dp,vertical=12.dp).testTag("minimal-home")) {
        val height=maxHeight.coerceAtLeast(900.dp)
        val header=(height*.22f).coerceIn(334.dp,380.dp)
        val actions=(height*.11f).coerceIn(104.dp,136.dp)
        val lower=(height*.10f).coerceIn(128.dp,150.dp)
        CompositionLocalProvider(LocalFramelessDashboard provides true) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("home-scroll")) {
            Column(Modifier.fillMaxWidth().height(height),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth().height(header).testTag("minimal-main-layout"),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.weight(7f).fillMaxHeight().testTag("minimal-primary-content")) {
                        Box(Modifier.fillMaxSize().testTag("minimal-navigation-widget")) {
                            ru.jeland.os.navigation.OfflineNavigationWidget(model.dependencies.navigation,s.dark,Modifier.fillMaxSize(),framed=false) {navigate("navigation")}
                        }
                    }
                    Column(Modifier.weight(3f).fillMaxHeight().testTag("minimal-info-stack"),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                        DashboardClock(Modifier.fillMaxWidth().height(112.dp).testTag("clock-weather-panel"),compact=true,outlined=false,
                            temperature=s.weather,onWeather=model::openWeather)
                        MediaWidget(s.media,model,Modifier.fillMaxWidth().weight(1f),compact=true)
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).testTag("vehicle-status-card")) {
                    VehicleTopView(s.vehicle,Modifier.fillMaxSize().padding(horizontal=8.dp),showTires=s.preferences.showTires,
                        onVehicle={show(Popup.VEHICLE)},onTire={show(Popup.tire(it))},portrait=true)
                    if(s.preferences.showFuel)VehicleFuelIndicator(s.vehicle.fuel,Modifier.align(Alignment.CenterStart),gauge=true){show(Popup.FUEL)}
                    BatteryIndicator(s.vehicle.battery,Modifier.align(Alignment.CenterEnd),gauge=true){show(Popup.BATTERY)}
                }
                QuickActions(s,model,Modifier.fillMaxWidth().height(actions).testTag("quick-actions-widget"),show,navigate)
                if(s.preferences.showFavorites)FavoriteAppsWidget(s.apps,model,Modifier.fillMaxWidth().height(lower),compact=true){show(Popup.APPS)}
            }
        }
        }
    }
}

/** Shared with the classic hero; the image is not part of the clock component. */
@Composable fun DashboardClock(modifier:Modifier=Modifier,compact:Boolean=false,outlined:Boolean=true,centered:Boolean=false,
    temperature:WeatherState?=null,onWeather:()->Unit={}) {
    var now by remember {mutableStateOf(LocalDateTime.now())}
    LaunchedEffect(Unit){while(true){now=LocalDateTime.now();delay(1000)}}
    Column(modifier,verticalArrangement=Arrangement.Center,horizontalAlignment=if(centered)Alignment.CenterHorizontally else Alignment.Start) {
        val time: @Composable (Modifier)->Unit={timeModifier->
            FittedHeroText(now.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=Jeland.type.clock*(if(compact).8f else 1f),
                fontWeight=FontWeight.Light,letterSpacing=Jeland.type.trackTight,outlined=outlined,modifier=timeModifier.testTag("hero-clock"))
        }
        if(temperature!=null)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            time(Modifier.weight(1f))
            WeatherWidget(temperature,Modifier.width(84.dp).height(64.dp),onWeather)
        }else time(Modifier)
        FittedHeroText(now.format(DateTimeFormatter.ofPattern("d MMMM, EEE",Locale.forLanguageTag("ru"))),
            fontSize=Jeland.type.body,fontWeight=FontWeight.Medium,outlined=outlined,modifier=Modifier.fillMaxWidth().testTag("hero-date"),
            alignment=if(centered)Alignment.Center else Alignment.CenterStart)
    }
}
