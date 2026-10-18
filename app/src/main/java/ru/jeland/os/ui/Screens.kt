package ru.jeland.os.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val weights=listOf(.34f,.20f,if(s.preferences.showTires).20f else 0f,if(s.preferences.showFavorites).14f else 0f,.12f)
        val total=weights.sum();val count=weights.count {it>0};val available=maxHeight-((count-1)*10).dp
        fun section(index:Int,min:androidx.compose.ui.unit.Dp)=(available*(weights[index]/total)).coerceAtLeast(min)
        // Leave a little breathing room instead of redistributing the saved height to other cards.
        val heroHeight=(section(0,280.dp)-24.dp).coerceAtLeast(240.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("home-scroll"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            JelandHero(s,heroHeight,{if(s.weather.demo)show(Popup.WEATHER)else model.openWeather()},{show(Popup.FUEL)})
            Row(Modifier.fillMaxWidth().height(section(1,210.dp)),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MediaWidget(s.media,model,Modifier.weight(1f))
                NavigationWidget(s.navigation,Modifier.weight(1.06f)) { navigate("navigation") }
            }
            if(s.preferences.showTires)VehicleStatusCard(s.vehicle,Modifier.fillMaxWidth().height(section(2,190.dp)),{show(Popup.VEHICLE)},{show(Popup.tire(it))}){show(Popup.DEBUG)}
            if(s.preferences.showFavorites)FavoriteAppsWidget(s.apps,model,Modifier.fillMaxWidth().height(section(3,130.dp))){show(Popup.APPS)}
            QuickActions(s,model,Modifier.fillMaxWidth().height(section(4,112.dp)),show,navigate)
        }
    }
}
@Composable fun JelandHero(s:UiState,height:androidx.compose.ui.unit.Dp,onWeather:()->Unit,onFuel:()->Unit) {
    val c=Jeland.colors
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {while(true) {now=LocalDateTime.now();delay(1000)}}
    Box(Modifier.fillMaxWidth().height(height).clip(Jeland.card).border(1.dp,c.outline,Jeland.card)) {
        Image(painterResource(R.drawable.hero_jeland),"Чёрный SUV в горах — иллюстрация",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.artScrim.copy(alpha=.7f),c.artScrim.copy(alpha=.02f),c.artScrim.copy(alpha=.25f)))))
        Column(Modifier.align(Alignment.TopStart).padding(Jeland.space.hero)) {
            Text(now.format(DateTimeFormatter.ofPattern("d MMMM, EEE",Locale.forLanguageTag("ru"))),color=c.artText,fontSize=Jeland.type.body,modifier=Modifier.testTag("hero-date"))
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")),color=c.artText,fontSize=Jeland.type.clock,fontWeight=FontWeight.Light,letterSpacing=Jeland.type.trackTight,modifier=Modifier.testTag("hero-clock"))
        }
        WeatherWidget(s.weather,Modifier.align(Alignment.TopEnd).padding(18.dp),onWeather)
        if(s.preferences.showFuel)FuelWidget(s.vehicle.fuel,Modifier.align(Alignment.BottomEnd).padding(end=18.dp,bottom=30.dp),onFuel)
        Text("JELAND J7 EDITION",color=c.artMuted,fontSize=Jeland.type.footnote,letterSpacing=Jeland.type.trackNormal,modifier=Modifier.align(Alignment.BottomStart).padding(20.dp))
    }
}
@Composable fun QuickActions(s:UiState,model:MainViewModel,modifier:Modifier,show:(Popup)->Unit,navigate:(String)->Unit) {
    Row(modifier,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        QuickTile("Подогрев\nруля",s.vehicle.steeringSummary,JIcons.Steering,Modifier.weight(1f).testTag("quick-steering"),s.vehicle.steeringHeat,model.dependencies.vehicle::toggleSteeringHeat)
        QuickTile("Панорамная\nшторка",s.vehicle.shadeUiSummary,JIcons.Shade,Modifier.weight(1f).testTag("quick-shade"),false){show(Popup.SHADE)}
        QuickTile("Камеры\n360°","Демонстрация",JIcons.Cameraswitch,Modifier.weight(1f).testTag("quick-camera"),false){show(Popup.CAMERA)}
        QuickTile("Настройки\nавтомобиля","Штатные настройки",JIcons.DirectionsCar,Modifier.weight(1f).testTag("quick-settings"),false){navigate("car")}
    }
}
@Composable private fun QuickTile(title:String,detail:String,icon:ImageVector,modifier:Modifier,active:Boolean,onClick:()->Unit) {
    Panel(modifier.fillMaxHeight().clickable(onClick=onClick)) {
        Column(Modifier.fillMaxSize().padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Glyph(icon,null,Modifier.size(30.dp),active)
            Text(title,color=Jeland.colors.text,fontSize=Jeland.type.secondary,lineHeight=Jeland.type.title,textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.padding(top=8.dp))
            Text(detail,color=if(active)Jeland.colors.accent else Jeland.colors.muted,fontSize=Jeland.type.footnote,lineHeight=Jeland.type.caption,modifier=Modifier.padding(top=4.dp))
        }
    }
}
@Composable fun MediaWidget(s:MediaState,model:MainViewModel,modifier:Modifier=Modifier,expanded:Boolean=false) {
    val r=model.dependencies.media
    Panel(modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
                Box(Modifier.size(if(expanded)148.dp else 66.dp).clip(Jeland.control).background(Jeland.colors.deep)) {
                    Image(painterResource(R.drawable.hero_jeland),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Icon(JIcons.MusicNote,null,tint=Jeland.colors.artText,modifier=Modifier.align(Alignment.Center).size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(s.track.title,color=Jeland.colors.text,fontSize=Jeland.type.title,maxLines=1,modifier=Modifier.testTag("media-title"))
                    Label(s.track.artist,small=true,muted=true,modifier=Modifier.padding(top=Jeland.space.compact))
                    Label("DEMO · без аудио",small=true,muted=true,modifier=Modifier.padding(top=Jeland.space.iconGap))
                }
                Box(Modifier.size(48.dp).clickable(onClick=r::toggleLike),contentAlignment=Alignment.Center) {
                    Glyph(if(s.liked)JIcons.Favorite else JIcons.FavoriteBorder,if(s.liked)"Удалить композицию из избранного"else"Добавить композицию в избранное",active=s.liked)
                }
            }
            Column {
                JelandSlider(s.position.toFloat(),{r.seek(it.toInt())},valueRange=0f..s.track.duration.toFloat(),modifier=Modifier.height(48.dp).testTag("media-seek"))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Label(timeString(s.position),small=true,muted=true);Label(timeString(s.track.duration),small=true,muted=true)}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                RoundControl(JIcons.SkipPrevious,"Предыдущая композиция",Modifier.testTag("media-previous"),onClick=r::previous)
                RoundControl(if(s.playing)JIcons.Pause else JIcons.PlayArrow,if(s.playing)"Пауза демо"else"Воспроизвести демо",Modifier.then(if(expanded)Modifier.size(72.dp)else Modifier).testTag("media-play"),active=s.playing,onClick=r::togglePlay)
                RoundControl(JIcons.SkipNext,"Следующая композиция",Modifier.testTag("media-next"),onClick=r::next)
            }
        }
    }
}
@Composable fun NavigationWidget(s:NavigationState,modifier:Modifier,onClick:()->Unit) {
    val c=Jeland.colors
    Box(modifier.fillMaxHeight().clip(Jeland.card).border(1.dp,c.outline,Jeland.card).clickable(onClick=onClick)) {
        MockMap(s,Modifier.fillMaxSize())
        Text("МОК-КАРТА",fontSize=Jeland.type.badge,color=c.artMuted,letterSpacing=Jeland.type.trackNormal,modifier=Modifier.padding(Jeland.space.roomy))
        Row(Modifier.align(Alignment.TopEnd).padding(Jeland.space.regular).background(c.map.copy(alpha=.85f),Jeland.control).padding(Jeland.space.controlGap),verticalAlignment=Alignment.CenterVertically) {
            Icon(when(s.maneuver){Maneuver.RIGHT->JIcons.TurnRight;Maneuver.LEFT->JIcons.TurnLeft;Maneuver.STRAIGHT->JIcons.Straight},"Манёвр демо",tint=c.artText)
            Text("${s.distanceToManeuver} м",fontSize=Jeland.type.body,color=c.artText,modifier=Modifier.padding(start=Jeland.space.iconGap))
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.map.copy(alpha=.92f)).padding(Jeland.space.card),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(s.active)s.destination else "Маршрут остановлен",fontSize=Jeland.type.title,color=c.artText)
                Text("${s.minutes} мин · ${s.kilometers} км · ${s.arrivalTime}",fontSize=Jeland.type.secondary,color=c.artMuted,modifier=Modifier.padding(top=Jeland.space.small))
            }
            Icon(JIcons.ChevronRight,null,tint=c.artMuted)
        }
    }
}
@Composable fun MusicScreen(s:UiState,model:MainViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.roomy)) {
        SectionTitle("Музыка","Ваша демо-коллекция · ${demoTracks.size} композиции · без воспроизведения звука")
        MediaWidget(s.media,model,Modifier.fillMaxWidth().height(350.dp),expanded=true)
        Panel(Modifier.fillMaxWidth().padding(top=Jeland.space.tiny)) {
            Column(Modifier.padding(Jeland.space.page),verticalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {
                SectionTitle("Источник аудио","Выбор демонстрационный. Bluetooth, USB и радио не подключаются.")
                Row(horizontalArrangement=Arrangement.spacedBy(Jeland.space.controlGap)) {
                    listOf("Bluetooth","USB","Радио").forEach {source->Control(source,Modifier.weight(1f).testTag("media-source-$source"),selected=s.media.source==source){model.dependencies.media.setSource(source)}}
                }
                Label("Громкость демо: ${s.media.volume}%")
                JelandSlider(s.media.volume.toFloat(),{model.dependencies.media.setVolume(it.toInt())},valueRange=0f..100f,modifier=Modifier.testTag("media-volume"))
            }
        }
        Label("Демо-коллекция",muted=true)
        demoTracks.forEachIndexed { i,track->
            Panel(Modifier.fillMaxWidth().testTag("media-track-$i").clickable {model.dependencies.media.selectTrack(i)}) {
                Row(Modifier.padding(Jeland.space.page),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Jeland.space.roomy)) {
                    Glyph(JIcons.MusicNote,active=s.media.index==i)
                    Column(Modifier.weight(1f)) {Label(track.title);Label(track.artist,small=true,muted=true)}
                    Label(timeString(track.duration),small=true,muted=true)
                    Glyph(if(s.media.index==i&&s.media.playing)JIcons.Pause else JIcons.PlayArrow,active=s.media.index==i)
                }
            }
        }
    }
}
@Composable fun NavigationScreen(s:UiState,model:MainViewModel) {
    val n=s.navigation; val r=model.dependencies.navigation
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
        SectionTitle("Навигация","Локальная карта-иллюстрация. Не маршрут для реального движения.")
        Panel(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(430.dp)) {
                MockMap(n,Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopEnd).padding(Jeland.space.roomy),verticalArrangement=Arrangement.spacedBy(Jeland.space.controlGap)) {
                    RoundControl(JIcons.Add,"Приблизить карту") {r.zoom(.2f)}
                    RoundControl(JIcons.Remove,"Отдалить карту") {r.zoom(-.2f)}
                }
                Text("DEMO  /  ${n.zoom.toString().take(3)}×",fontSize=Jeland.type.caption,color=Jeland.colors.artMuted,modifier=Modifier.padding(Jeland.space.card))
            }
            Row(Modifier.padding(Jeland.space.page),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Jeland.space.page)) {
                Glyph(when(n.maneuver){Maneuver.RIGHT->JIcons.TurnRight;Maneuver.LEFT->JIcons.TurnLeft;Maneuver.STRAIGHT->JIcons.Straight},"Манёвр демо",Modifier.size(50.dp),true)
                Column(Modifier.weight(1f)) {SectionTitle("${n.distanceToManeuver} м",n.street);Label("${n.destination} · ${n.minutes} мин · ${n.kilometers} км");Label("Прибытие ${n.arrivalTime} · демо",small=true,muted=true)}
                Control(if(n.active)"Остановить"else"Построить",selected=n.active,onClick=r::toggleRoute)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {
            Control("Домой",selected=n.destination=="Домой") {r.setDestination("Домой")}
            Control("На работу",selected=n.destination=="На работу") {r.setDestination("На работу")}
            Control("Следующий манёвр",onClick=r::nextManeuver)
        }
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(Jeland.space.page)) {
            SectionTitle("Единое состояние маршрута","MockClusterBridge получает те же данные, что и экран. На приборку ничего не отправляется.")
            Label("Приборка: не подключена · Binder / Kanzi / display 2 не используются",small=true,muted=true)
        }}
    }
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
                QuickTile("Подогрев руля",s.vehicle.steeringSummary,JIcons.Steering,Modifier.weight(1f).testTag("car-steering"),s.vehicle.steeringHeat,r::toggleSteeringHeat)
                QuickTile("Шторка",s.vehicle.shadeUiSummary,JIcons.Shade,Modifier.weight(1f),false){show(Popup.SHADE)}
                QuickTile("Зеркала",if(!s.car.demo)"Не подключены"else if(s.vehicle.mirrorsFolded)"Сложены · DEMO"else"Разложены",JIcons.SwapHoriz,Modifier.weight(1f).testTag("car-mirrors"),s.vehicle.mirrorsFolded,r::toggleMirrors)
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
        Control(if(s.car.demo)"Открыть симулятор кузова и шин"else"Подключение автомобиля",Modifier.fillMaxWidth().testTag("car-debug")){show(Popup.DEBUG)}
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings-scroll").padding(bottom=Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.roomy)) {
        SectionTitle("Настройки","Оформление и персонализация Jeland OS")
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(22.dp)){VehicleConnectionPanel(s,model)}}
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(22.dp)){IndicatorPermissions(model)}}
        if(s.car.demo)Control("DEMO-панель автомобиля",Modifier.testTag("open-demo-car"),onClick=onDemoVehicle)
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
            SectionTitle("Оформление")
            Row(horizontalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {
                ThemeMode.entries.forEach {mode->Control(mode.title,Modifier.weight(1f).testTag("theme-${mode.name.lowercase()}"),s.theme==mode){model.dependencies.theme.setMode(mode)}}
            }
            Label("Авто использует тестовый DayNightProvider, не сигнал автомобиля.",small=true,muted=true)
            Control(if(s.night)"Демо: ночь → переключить на день"else"Демо: день → переключить на ночь",Modifier.testTag("toggle-night"),onClick=model.dependencies.dayNight::toggle)
        }}
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(24.dp)) {
            SectionTitle("Главный экран","Изменения сохраняются между запусками")
            SettingsToggle("Топливо в hero","Литры и объём до полного бака 55 л",s.preferences.showFuel,Modifier.testTag("setting-fuel")){preferences.change(s.preferences.copy(showFuel=!s.preferences.showFuel))}
            SettingsToggle("Состояние автомобиля","Кузов и давление около каждого колеса",s.preferences.showTires,Modifier.testTag("setting-tires")){preferences.change(s.preferences.copy(showTires=!s.preferences.showTires))}
            SettingsToggle("Избранные приложения","Четыре ярлыка на главной",s.preferences.showFavorites,Modifier.testTag("setting-favorites")){preferences.change(s.preferences.copy(showFavorites=!s.preferences.showFavorites))}
            SettingsToggle("Панель разработчика","Быстрый доступ к локальным тестовым событиям",s.preferences.developerPanel,Modifier.testTag("setting-developer")){preferences.change(s.preferences.copy(developerPanel=!s.preferences.developerPanel))}
            if(s.preferences.developerPanel)Control("Симулятор автомобиля",Modifier.testTag("settings-debug")){show(Popup.DEBUG)}
        }}
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {
            SectionTitle("Сеть и остальные функции",if(s.car.demo)"Режим DEMO · автомобиль отключён"else"Автомобиль: ${s.car.detail}")
            Label(when {s.connectivity.wifi->"Wi-Fi подключён";s.connectivity.cellular->"Мобильная сеть подключена";s.connectivity.available->"Нет подключения к сети";else->"Сведения о сети недоступны"},small=true,muted=true)
            Label("Музыка — демо без звука\nНавигация — локальная иллюстрация\n"+
                (if(s.weather.demo)"Погода — DEMO"else"Погода — штатное приложение, без отдельной загрузки")+
                "\nКамеры и приборка — не подключены",small=true,muted=true)
            Label("Обычное OEM-разрешение ivi.vds.permission.SERVICE. Без root, priv-app, CAN-команд, фонового сервиса и замены штатной оболочки. Probe остаётся отдельным приложением.",small=true,muted=true)
            Label("Jeland OS · ${BuildConfig.VERSION_NAME}\nОтдельное Android-приложение, не системная прошивка",small=true,muted=true)
        }}
    }
}
