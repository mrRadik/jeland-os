package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedIcon as Icon

import ru.jeland.os.localization.LocalizedText as Text

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import ru.jeland.os.R
import ru.jeland.os.BuildConfig
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.JIcons
import ru.jeland.os.ui.theme.Jeland

@Composable fun HomeScreen(s:UiState,model:MainViewModel,show:(Popup)->Unit,navigate:(String)->Unit) {
    if(s.preferences.style==DashboardStyle.MINIMAL){MinimalHomeScreen(s,model,show,navigate);return}
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Proportions follow the approved portrait reference, without oversized empty card slots.
        val weights=listOf(.29f,.22f,if(s.preferences.showTires).22f else 0f,.13f,if(s.preferences.showFavorites).14f else 0f)
        val available=maxHeight-Jeland.headerHeight-((weights.count {it>0}-1)*10).dp
        fun section(index:Int)=available*(weights[index]/weights.sum())
        Column(Modifier.fillMaxSize().testTag("home-scroll"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            JelandHero(s,section(0)+Jeland.headerHeight,{model.openWeather()},{show(Popup.FUEL)},Jeland.headerHeight)
            Row(Modifier.padding(start=10.dp,end=16.dp).fillMaxWidth().heightIn(max=section(1)).height(IntrinsicSize.Min).testTag("home-media-row"),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ru.jeland.os.navigation.OfflineNavigationWidget(model.dependencies.navigation,s.dark,Modifier.weight(1.06f)) { navigate("navigation") }
                MediaWidget(s.media,model,Modifier.weight(1f).fillMaxHeight())
            }
            // The compact media row may be shorter than its allowance. Give all
            // remaining height to vehicle status instead of leaving a bottom gap.
            if(s.preferences.showTires)VehicleStatusWithFuel(s.vehicle,Modifier.padding(start=10.dp,end=16.dp).fillMaxWidth().weight(1f),{show(Popup.VEHICLE)},{show(Popup.tire(it))},showFuel=s.preferences.showFuel,onFuel={show(Popup.FUEL)},onBattery={show(Popup.BATTERY)}){show(Popup.DEBUG)}
            Panel(Modifier.padding(start=10.dp,end=16.dp).fillMaxWidth().height(section(3)).testTag("quick-actions-widget")) {
                Text("Быстрые действия",color=Jeland.colors.text,fontSize=Jeland.type.caption,modifier=Modifier.padding(horizontal=14.dp,vertical=6.dp))
                QuickActions(s,model,Modifier.fillMaxWidth().weight(1f).padding(start=10.dp,end=10.dp,bottom=10.dp),show,navigate)
            }
            if(s.preferences.showFavorites)FavoriteAppsWidget(s.apps,model,Modifier.padding(start=10.dp,end=16.dp).fillMaxWidth().height(section(4)),compact=true){show(Popup.APPS)}
        }
    }
}
@Composable fun JelandHero(s:UiState,height:androidx.compose.ui.unit.Dp,onWeather:()->Unit,onFuel:()->Unit,topInset:androidx.compose.ui.unit.Dp=0.dp) {
    val c=Jeland.colors
    Box(Modifier.fillMaxWidth().height(height).testTag("hero-banner")) {
        Image(painterResource(heroBackgroundResource(s.dark)),"Jeland J7 в горах — ${if(s.dark)"ночь"else"светлый день"}",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        if(s.dark) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to c.artScrim.copy(alpha=.26f),.35f to c.artScrim.copy(alpha=0f),1f to c.artScrim.copy(alpha=.08f))))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to c.background.copy(alpha=.45f),.12f to c.background.copy(alpha=0f),.94f to c.background.copy(alpha=0f),1f to c.background.copy(alpha=.3f))))
        }
        // No side haze in daylight; the pale image only meets the app background at its lower edge.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to c.background.copy(alpha=0f),(if(s.dark).78f else .92f) to c.background.copy(alpha=0f),1f to c.background)))
        DashboardClock(Modifier.align(Alignment.TopStart).padding(start=24.dp,end=24.dp,top=topInset+8.dp))
        // Weather fits text to its slot; keep that slot local to the top-right corner.
        WeatherWidget(s.weather,Modifier.align(Alignment.TopEnd).padding(start=18.dp,end=18.dp,top=topInset+14.dp)
            .size(width=184.dp,height=96.dp),onWeather)
    }
}
@Composable fun QuickActions(s:UiState,model:MainViewModel,modifier:Modifier,show:(Popup)->Unit,navigate:(String)->Unit) {
    Row(modifier,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        QuickTile("Подогрев\nруля",s.vehicle.steeringSummary,JIcons.Steering,Modifier.weight(1f).testTag("quick-steering"),s.vehicle.steeringHeat,onClick=model.dependencies.vehicle::toggleSteeringHeat)
        QuickTile("Панорамная\nшторка",s.vehicle.shadeUiSummary,JIcons.Shade,Modifier.weight(1f).testTag("quick-shade"),false){show(Popup.SHADE)}
        QuickTile("Камеры\n360°","Штатные камеры",JIcons.Cameraswitch,Modifier.weight(1f).testTag("quick-cameras"),false,onClick=model::openCameras)
        QuickTile("Инженерное\nменю","Диагностика ГУ",JIcons.Settings,Modifier.weight(1f).testTag("quick-engineering"),false,onClick=model::openEngineeringMenu)
    }
}
@Composable private fun QuickTile(title:String,detail:String,icon:ImageVector,modifier:Modifier,active:Boolean,tintOverride:androidx.compose.ui.graphics.Color?=null,onClick:()->Unit) {
    Panel(modifier.fillMaxHeight().clickable(onClick=onClick)) {
        Row(Modifier.fillMaxSize().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            val tint=tintOverride?:if(active)Jeland.colors.warm else Jeland.colors.accent
            Box(Modifier.size(56.dp).clip(Jeland.control)
                .background(Brush.radialGradient(listOf(tint.copy(alpha=if(active).24f else .13f),Jeland.colors.raised.copy(alpha=.4f))))
                .border(1.dp,tint.copy(alpha=if(active).55f else .22f),Jeland.control),contentAlignment=Alignment.Center) {
                Icon(icon,null,Modifier.size(40.dp),tint=tintOverride?:if(active)Jeland.colors.warm else if(Jeland.style==DashboardStyle.MINIMAL)Jeland.colors.accent else Jeland.colors.artText)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
              Text(title,color=Jeland.colors.text,fontSize=Jeland.type.caption,lineHeight=Jeland.type.secondary,maxLines=2)
              Text(detail,color=if(active)Jeland.colors.accent else Jeland.colors.muted,fontSize=Jeland.type.micro,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable fun MediaWidget(s:MediaState,model:MainViewModel,modifier:Modifier=Modifier,expanded:Boolean=false,repository:MediaRepository=model.dependencies.media,compact:Boolean=false) {
    val r=repository
    if(!expanded&&!compact) {
        Panel(modifier.testTag("music-widget")) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    AlbumArtwork(s,Modifier.size(64.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        MusicMarqueeText(musicDisplayTitle(s),Jeland.colors.text,Jeland.type.title,Modifier.testTag("media-title"))
                        MusicMarqueeText(s.track.artist,Jeland.colors.muted,Jeland.type.secondary,Modifier.testTag("media-artist"))
                        Text(s.source,color=Jeland.colors.muted,fontSize=Jeland.type.footnote,maxLines=1,modifier=Modifier.testTag("media-status"))
                    }
                    if(!s.live)Box(Modifier.size(42.dp).clickable(onClick=r::toggleLike),contentAlignment=Alignment.Center) {
                        Glyph(if(s.liked)JIcons.Favorite else JIcons.FavoriteBorder,if(s.liked)"Удалить композицию из избранного"else"Добавить композицию в избранное",active=s.liked)
                    }
                }
                MusicProgress(s,r,Modifier.fillMaxWidth())
                MusicTransport(s,r,Modifier.fillMaxWidth(),compact=true)
            }
        }
        return
    }
    if(compact){
        if(Jeland.style==DashboardStyle.MINIMAL) {
            Panel(modifier.fillMaxHeight().testTag("music-widget")) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    if(maxWidth<320.dp)Column(Modifier.fillMaxSize().padding(horizontal=6.dp,vertical=6.dp),
                        verticalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterVertically),horizontalAlignment=Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            AlbumArtwork(s,Modifier.size(64.dp),square=true)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                MusicMarqueeText(musicDisplayTitle(s),Jeland.colors.text,Jeland.type.secondary,Modifier.testTag("media-title"))
                                MusicMarqueeText(s.track.artist,Jeland.colors.muted,Jeland.type.caption,Modifier.testTag("media-artist"))
                            }
                        }
                        MinimalMusicProgress(s,Modifier.fillMaxWidth())
                        MusicTransport(s,r,Modifier.fillMaxWidth(),compact=true,borderless=true)
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            Icon(if(s.source.contains("Bluetooth",true))JIcons.Bluetooth else JIcons.MusicNote,null,Modifier.size(18.dp),tint=Jeland.colors.accent)
                            Text(s.source,color=Jeland.colors.muted,fontSize=Jeland.type.footnote,maxLines=1,modifier=Modifier.testTag("media-status"))
                        }
                    } else Row(Modifier.fillMaxSize().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        AlbumArtwork(s,Modifier.size(82.dp),square=true)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            MusicMarqueeText(musicDisplayTitle(s),Jeland.colors.text,Jeland.type.clockSmall,Modifier.testTag("media-title"))
                            MusicMarqueeText(s.track.artist,Jeland.colors.muted,Jeland.type.caption,Modifier.testTag("media-artist"))
                            MinimalMusicProgress(s,Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                MusicTransport(s,r,Modifier.weight(1f),compact=true,borderless=true)
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Icon(if(s.source.contains("Bluetooth",true))JIcons.Bluetooth else JIcons.MusicNote,null,Modifier.size(18.dp),tint=Jeland.colors.accent)
                                    Text(s.source,color=Jeland.colors.muted,fontSize=Jeland.type.micro,maxLines=1,modifier=Modifier.widthIn(max=64.dp).testTag("media-status"))
                                }
                            }
                        }
                    }
                }
            }
            return
        }
        Panel(modifier.fillMaxHeight()) {
            Column(Modifier.fillMaxSize().padding(8.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AlbumArtwork(s,Modifier.size(38.dp))
                    Column(Modifier.weight(1f)) {
                        MusicMarqueeText(musicDisplayTitle(s),Jeland.colors.text,Jeland.type.secondary,Modifier.testTag("media-title"))
                        MusicMarqueeText(s.track.artist,Jeland.colors.muted,Jeland.type.footnote,Modifier.testTag("media-artist"))
                    }
                    Text(s.source,color=Jeland.colors.muted,fontSize=Jeland.type.micro,maxLines=1,modifier=Modifier.widthIn(max=64.dp).testTag("media-status"))
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    RoundControl(JIcons.SkipPrevious,"Предыдущая композиция",Modifier.size(38.dp).testTag("media-previous"),enabled=s.controlsEnabled,onClick=r::previous)
                    RoundControl(if(s.playing)JIcons.Pause else JIcons.PlayArrow,"Воспроизведение / пауза",Modifier.size(42.dp).testTag("media-play"),active=s.playing,enabled=s.controlsEnabled,onClick=r::togglePlay)
                    RoundControl(JIcons.SkipNext,"Следующая композиция",Modifier.size(38.dp).testTag("media-next"),enabled=s.controlsEnabled,onClick=r::next)
                    JelandSlider(s.position.toFloat(),{r.seek(it.toInt())},valueRange=0f..s.track.duration.coerceAtLeast(1).toFloat(),enabled=!s.live,modifier=Modifier.weight(1f).height(30.dp).testTag("media-seek"))
                }
            }
        };return
    }
    Panel(modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
                AlbumArtwork(s,Modifier.size(if(expanded)148.dp else 66.dp))
                Column(Modifier.weight(1f)) {
                    MusicMarqueeText(musicDisplayTitle(s),Jeland.colors.text,Jeland.type.title,Modifier.testTag("media-title"))
                    MusicMarqueeText(s.track.artist,Jeland.colors.muted,Jeland.type.secondary,Modifier.padding(top=Jeland.space.compact).testTag("media-artist"))
                    Label(s.source,small=true,muted=true,modifier=Modifier.padding(top=Jeland.space.iconGap).testTag("media-status"))
                }
                if(!s.live)Box(Modifier.size(48.dp).clickable(onClick=r::toggleLike),contentAlignment=Alignment.Center) {
                    Glyph(if(s.liked)JIcons.Favorite else JIcons.FavoriteBorder,if(s.liked)"Удалить композицию из избранного"else"Добавить композицию в избранное",active=s.liked)
                }
            }
            Column {
                JelandSlider(s.position.toFloat(),{r.seek(it.toInt())},valueRange=0f..s.track.duration.coerceAtLeast(1).toFloat(),enabled=!s.live,modifier=Modifier.height(48.dp).testTag("media-seek"))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Label(if(s.live&&!s.positionKnown)"—:—"else timeString(s.position),small=true,muted=true);Label(if(s.live&&s.track.duration<=0)"—:—"else timeString(s.track.duration),small=true,muted=true)}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                RoundControl(JIcons.SkipPrevious,"Предыдущая композиция",Modifier.testTag("media-previous"),enabled=s.controlsEnabled,onClick=r::previous)
                RoundControl(if(s.playing)JIcons.Pause else JIcons.PlayArrow,if(s.playing)if(s.live)"Пауза"else"Пауза демо"else if(s.live)"Воспроизвести"else"Воспроизвести демо",Modifier.then(if(expanded)Modifier.size(72.dp)else Modifier).testTag("media-play"),active=s.playing,enabled=s.controlsEnabled,onClick=r::togglePlay)
                RoundControl(JIcons.SkipNext,"Следующая композиция",Modifier.testTag("media-next"),enabled=s.controlsEnabled,onClick=r::next)
            }
        }
    }
}
@Composable fun NavigationWidget(s:NavigationState,modifier:Modifier,onClick:()->Unit) {
    val c=Jeland.colors
    Box(modifier.fillMaxHeight().clip(Jeland.card).border(1.dp,c.outline,Jeland.card).clickable(onClick=onClick).testTag("navigation-widget")) {
        MockMap(s,Modifier.fillMaxSize())
        Text("НАВИГАЦИЯ · DEMO",fontSize=Jeland.type.micro,color=c.artMuted,modifier=Modifier.padding(10.dp))
        Row(Modifier.align(Alignment.TopEnd).padding(Jeland.space.regular).background(c.map.copy(alpha=.85f),Jeland.control).padding(Jeland.space.controlGap),verticalAlignment=Alignment.CenterVertically) {
            Icon(when(s.maneuver){Maneuver.RIGHT->JIcons.TurnRight;Maneuver.LEFT->JIcons.TurnLeft;Maneuver.STRAIGHT->JIcons.Straight},"Манёвр демо",tint=c.artText)
            Text("${s.distanceToManeuver} м",fontSize=Jeland.type.body,color=c.artText,modifier=Modifier.padding(start=Jeland.space.iconGap))
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.map.copy(alpha=.92f)).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(s.active)s.destination else "Маршрут остановлен",fontSize=Jeland.type.secondary,color=c.artText,maxLines=1)
                Text("${s.minutes} мин · ${s.kilometers} км",fontSize=Jeland.type.footnote,color=c.artMuted)
            }
            Icon(JIcons.ChevronRight,null,tint=c.artMuted)
        }
    }
}
@Composable fun MusicScreen(s:UiState,model:MainViewModel) { FullMusicPlayer(s.media,model.dependencies.media) }
@Composable fun NavigationScreen(s:UiState,model:MainViewModel,mapVisible:Boolean=true,onSettings:()->Unit={}) {
    ru.jeland.os.navigation.FullNavigationScreen(model.dependencies.navigation,s.dark)
}
@Composable fun VehicleScreen(s:UiState,model:MainViewModel,show:(Popup)->Unit) {
    val r=model.dependencies.vehicle
    var sectionName by rememberSaveable {mutableStateOf(VehicleSection.QUICK.name)}
    val section=VehicleSection.valueOf(sectionName)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("car-scroll").padding(bottom=Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
        SectionTitle("Автомобиль",if(s.car.demo)"Jeland J7 Prestige AWD · настройки макета, не команды автомобилю"else"Jeland J7 · подтверждённые функции через CarInfoService")
        Panel(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                Image(painterResource(R.drawable.hero_jeland),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Jeland.colors.artScrim.copy(alpha=.05f),Jeland.colors.artScrim.copy(alpha=.8f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                    Text("J7  /  PRESTIGE AWD",color=Jeland.colors.artText,fontSize=Jeland.type.heading)
                    Text(if(s.car.demo)"ЛОКАЛЬНЫЙ ДЕМО-РЕЖИМ"else s.car.detail,color=Jeland.colors.artMuted,fontSize=Jeland.type.footnote,letterSpacing=Jeland.type.trackNormal)
                }
            }
        }
        VehicleSection.entries.chunked(3).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            row.forEach {tab->Control(tab.title,Modifier.weight(1f).testTag("car-tab-${tab.name.lowercase()}"),section==tab){sectionName=tab.name}}
        }}
        if(section==VehicleSection.QUICK) {
            Row(Modifier.height(140.dp),horizontalArrangement=Arrangement.spacedBy(Jeland.space.inset)) {
                QuickTile("Подогрев руля",s.vehicle.steeringSummary,JIcons.Steering,Modifier.weight(1f).testTag("car-steering"),s.vehicle.steeringHeat,onClick=r::toggleSteeringHeat)
                QuickTile("Шторка",s.vehicle.shadeUiSummary,JIcons.Shade,Modifier.weight(1f),false){show(Popup.SHADE)}
                QuickTile("Зеркала",if(!s.car.demo)"Не подключены"else if(s.vehicle.mirrorsFolded)"Сложены · DEMO"else"Разложены",JIcons.SwapHoriz,Modifier.weight(1f).testTag("car-mirrors"),s.vehicle.mirrorsFolded,onClick=r::toggleMirrors)
            }
            VehicleStatusCard(s.vehicle,Modifier.fillMaxWidth().height(250.dp),{show(Popup.VEHICLE)},{show(Popup.tire(it))}){show(Popup.DEBUG)}
        } else {
            Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(22.dp)) {
                SectionTitle(section.title,if(s.car.demo)"Параметры сохраняются только в этом приложении. OEM API не подключён."else"Эти настройки ещё не подтверждены и не отправляются автомобилю.")
                VehicleOption.entries.filter {s.car.demo && it.section==section}.forEach {option->
                    SettingsToggle(option.title,vehicleOptionDetail(option),s.vehicle.options[option]==true,Modifier.testTag("car-option-${option.name.lowercase()}")){r.toggleOption(option)}
                }
            }}
            if(section==VehicleSection.LOCKS)VehicleStatusCard(s.vehicle,Modifier.fillMaxWidth().height(250.dp),{show(Popup.VEHICLE)},{show(Popup.tire(it))}){show(Popup.DEBUG)}
            if(section==VehicleSection.COMFORT)Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Control("Сиденья",Modifier.weight(1f)){show(Popup.DRIVER)}
                Control("Климат",Modifier.weight(1f)){show(Popup.CLIMATE)}
                Control("Шторка",Modifier.weight(1f)){show(Popup.SHADE)}
            }
        }
        if(BuildConfig.DEMO_FEATURES)Control(if(s.car.demo)"Открыть симулятор кузова и шин"else"Подключение автомобиля",Modifier.fillMaxWidth().testTag("car-debug")){show(Popup.DEBUG)}
    }
}
private fun vehicleOptionDetail(option:VehicleOption)=when(option) {
    VehicleOption.AUTO_LIGHT->"Демонстрация автоматического включения света"
    VehicleOption.FOLLOW_HOME->"Подсветка пути после выхода · DEMO"
    VehicleOption.WELCOME_LIGHT->"Сценарий приветственного освещения · DEMO"
    VehicleOption.AUTO_LOCK->"Автозапирание не выполняется · настройка макета"
    VehicleOption.UNLOCK_PARK->"Отпирание в режиме P не выполняется"
    VehicleOption.FOLD_LOCK->"Демо-предпочтение, без управления зеркалами"
    VehicleOption.SEAT_WELCOME->"Макет функции удобной посадки, без движения сиденья"
    VehicleOption.CABIN_LIGHT->"Локальный выбор, без управления подсветкой"
    VehicleOption.LANE_DEMO->"Только предпочтение демонстрационной индикации"
    VehicleOption.ASSIST_DEMO->"Не включает и не отключает штатные ассистенты"
    VehicleOption.WELCOME_SOUND->"Только сохранение выбора, без звукового потока"
}
@Composable fun SettingsScreen(s:UiState,model:MainViewModel,show:(Popup)->Unit,onDemoVehicle:()->Unit={}) {
    val preferences=model.dependencies.preferences
    val startup by model.startup.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings-scroll").padding(bottom=Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.roomy)) {
        SectionTitle("Настройки","Сделайте Jeland OS удобным для себя")
        LanguageSettings(model)
        SystemStartupSettings(startup,model::setSystemStartup,model::openStartupPermission)
        AppUpdateSettings(model.dependencies.updates)
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(22.dp)){PermissionSettings(model)}}
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
            SectionTitle("Оформление")
            AppearancePicker(s,model)
        }}
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(24.dp)) {
            SectionTitle("Главный экран")
            SettingsToggle("Топливо","Уровень и объём до полного бака",s.preferences.showFuel,Modifier.testTag("setting-fuel")){preferences.change(s.preferences.copy(showFuel=!s.preferences.showFuel))}
            SettingsToggle("Состояние автомобиля","Кузов и давление в шинах",s.preferences.showTires,Modifier.testTag("setting-tires")){preferences.change(s.preferences.copy(showTires=!s.preferences.showTires))}
            SettingsToggle("Избранные приложения","Четыре ярлыка на главной",s.preferences.showFavorites,Modifier.testTag("setting-favorites")){preferences.change(s.preferences.copy(showFavorites=!s.preferences.showFavorites))}
        }}
        if(BuildConfig.DEMO_FEATURES)Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(22.dp)) {
            SectionTitle("Разработка")
            VehicleConnectionPanel(s,model)
            Control("Симулятор автомобиля",Modifier.testTag("settings-debug")){show(Popup.DEBUG)}
            if(s.car.demo)Control("DEMO-панель автомобиля",Modifier.testTag("open-demo-car"),onClick=onDemoVehicle)
        }}
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SectionTitle("Jeland OS",BuildConfig.VERSION_NAME)
            Control("Повторить начальную настройку",Modifier.fillMaxWidth().testTag("repeat-setup")){model.dependencies.firstRun.reopen()}
        }}
    }
}
