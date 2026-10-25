package ru.jeland.os.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.*

@Composable fun WeatherWidget(state:WeatherState,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=Jeland.colors
    Box(modifier.testTag("weather-widget").clip(Jeland.control).clickable(onClick=onClick).padding(16.dp)) {
        HeroOutlinedText(state.temperatureLabel,fontSize=Jeland.type.weather*1.25f,fontWeight=FontWeight.Medium,modifier=Modifier.testTag("outside-temperature"))
    }
}
@Composable fun FuelWidget(state:FuelState,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=Jeland.colors
    Column(modifier.width(184.dp).clip(Jeland.control).background(c.artScrim.copy(alpha=.88f)).border(1.dp,c.outline,Jeland.control)
        .clickable(onClick=onClick).padding(14.dp).testTag("fuel-widget")) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(JIcons.Fuel,null,tint=c.artMuted,modifier=Modifier.size(26.dp))
            Text(state.displayLiters?.let {"${if(state.estimated)"≈ "else""}$it л"}?:"Нет данных",color=c.artText,fontSize=if(state.displayLiters==null)Jeland.type.body else Jeland.type.heading)
            Spacer(Modifier.weight(1f));Icon(JIcons.ChevronRight,null,tint=c.artMuted,modifier=Modifier.size(18.dp))
        }
        Text(state.litersToFull?.let {"До полного $it л"}?:"Бак 55 л",color=c.artMuted,fontSize=Jeland.type.caption,modifier=Modifier.padding(top=7.dp))
        if(state.estimated)Text(if(state.experimental)"Экспериментальный расчёт"else"Оценочный объём · DEMO",color=c.artMuted,fontSize=Jeland.type.micro)
    }
}
@Composable fun FuelPopup(state:FuelState,model:MainViewModel) {
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Label("${state.displayLiters?.let {"Остаток $it л"}?:"Нет данных об остатке"} · бак 55 л")
        Label(state.litersToFull?.let {"До полного $it л"}?:"Ожидаем данные",muted=true)
        Label(if(state.estimated)"Объём оценочный, округлён до целого литра. Это не точный замер."else"Демонстрационные данные, не показания автомобиля.",small=true,muted=true)
        if(model.state.collectAsStateWithLifecycle().value.car.demo)Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Control("31 л"){model.dependencies.vehicle.simulateFuel(FuelState(31.0))}
            Control("Полный"){model.dependencies.vehicle.simulateFuel(FuelState(55.0))}
            Control("Оценка"){model.dependencies.vehicle.simulateFuel(FuelState.fromRelative(.56))}
            Control("Нет данных"){model.dependencies.vehicle.simulateFuel(FuelState())}
        }
    }
}
@Composable fun AppIcon(app:LauncherApp?,model:MainViewModel,modifier:Modifier=Modifier) {
    val icons by model.appIcons.collectAsStateWithLifecycle()
    val bitmap=app?.let {icons[it.id]}
    val c=Jeland.colors
    Box(modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
        .background(Brush.verticalGradient(listOf(c.raised.copy(alpha=.9f),c.panel)))
        .border(1.dp,c.outline.copy(alpha=.55f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {
        if(bitmap!=null)Image(bitmap.asImageBitmap(),null,Modifier.fillMaxSize().padding(2.dp),contentScale=ContentScale.Fit,filterQuality=FilterQuality.High)
        else Icon(JIcons.Apps,null,tint=c.muted,modifier=Modifier.fillMaxSize(.55f))
    }
}
@Composable fun FavoriteAppsWidget(state:AppsState,model:MainViewModel,modifier:Modifier=Modifier,compact:Boolean=false,onConfigure:()->Unit) {
    val c=Jeland.colors
    Panel(modifier.testTag("favorite-apps-widget")) {
        Row(Modifier.fillMaxWidth().height(if(compact)28.dp else 38.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Избранные приложения",color=Jeland.colors.text,fontSize=if(compact)Jeland.type.caption else Jeland.type.body,modifier=Modifier.weight(1f))
            Row(Modifier.height(38.dp).clickable(onClick=onConfigure).testTag("configure-favorites"),verticalAlignment=Alignment.CenterVertically) {
                Text("Настроить",color=Jeland.colors.muted,fontSize=Jeland.type.caption);Glyph(JIcons.ChevronRight)
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(start=12.dp,end=12.dp,bottom=10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            val actual=state.favoriteApps
            repeat(if(compact)5 else 4) {index->
                val app=actual.getOrNull(index)
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().testTag("favorite-home-card-$index")
                    .clip(Jeland.control).background(Brush.verticalGradient(listOf(c.raised.copy(alpha=.75f),c.panel)))
                    .border(1.dp,c.outline.copy(alpha=if(app!=null).85f else .5f),Jeland.control)
                    .clickable(onClickLabel=if(app!=null)"Открыть ${app.label}"else"Выбрать приложение") {
                        if(app!=null)model.launchApp(app.id) else onConfigure()
                    }.padding(if(compact)6.dp else 12.dp)) {
                    // Extra height must not change the composition on taller head-unit screens.
                    // Only use a vertical fallback when there really is no room beside the icon.
                    val stacked=compact||maxWidth<156.dp
                    val iconSize=if(compact)44.dp else 56.dp
                    val label=app?.label?:"Добавить"
                    val subtitle=if(app!=null)"Открыть"else if(state.loading)"Проверяем…"else"Выбрать ярлык"
                    val icon: @Composable ()->Unit = {
                        if(app!=null)AppIcon(app,model,Modifier.size(iconSize).testTag("favorite-home-icon-$index"))
                        else Box(Modifier.size(iconSize).testTag("favorite-home-add-$index").clip(Jeland.control)
                            .background(c.accent.copy(alpha=.06f)).border(1.dp,c.outline.copy(alpha=.6f),Jeland.control),contentAlignment=Alignment.Center) {
                            Icon(JIcons.Add,null,Modifier.size(28.dp),tint=c.muted)
                        }
                    }
                    val title: @Composable ()->Unit = {
                        Text(label,color=c.text,fontSize=if(compact)Jeland.type.footnote else Jeland.type.secondary,fontWeight=FontWeight.Medium,
                            maxLines=if(stacked)1 else 2,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("favorite-home-title-$index"))
                    }
                    val action: @Composable ()->Unit = {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(subtitle,color=if(app!=null)c.accent else c.muted,fontSize=Jeland.type.footnote,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false))
                            Icon(JIcons.ChevronRight,null,Modifier.size(14.dp),tint=if(app!=null)c.accent else c.muted)
                        }
                    }
                    if(stacked)Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)) {
                        icon();title();if(!compact)action()
                    } else Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        icon()
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {title();action()}
                    }
                }
            }
        }
    }
}
@Composable fun AppsPicker(model:MainViewModel,state:AppsState) {
    var query by rememberSaveable {mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        AppsSearch(query,{query=it},model::refreshApps)
        Label("Избранное: ${state.favoriteApps.size}. Звезда добавляет ярлык, стрелки меняют порядок. Значки берутся с устройства.",small=true,muted=true)
        if(state.loading)Label("Читаем доступные приложения…",small=true)
        state.error?.let {Label(it,small=true)}
        val apps=state.installed.filter {it.label.contains(query,true)||it.packageName.contains(query,true)}
        if(apps.isEmpty()&&!state.loading)Label("Нет доступных приложений по этому запросу.",small=true,muted=true)
        AppsGrid(apps,state,model,Modifier.fillMaxWidth().height(330.dp))
        FavoriteOrderEditor(state,model)
    }
}
@Composable fun AppsSearch(query:String,onQuery:(String)->Unit,onRefresh:()->Unit) {
    val c=Jeland.colors
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).height(56.dp).clip(Jeland.control).background(c.raised).border(1.dp,c.outline,Jeland.control).padding(horizontal=14.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Glyph(JIcons.Search)
            BasicTextField(query,onQuery,Modifier.weight(1f).testTag("app-search"),singleLine=true,
                textStyle=TextStyle(color=c.text,fontSize=Jeland.type.body),cursorBrush=SolidColor(c.accent),
                decorationBox={inner->if(query.isEmpty())Text("Поиск приложений",color=c.muted,fontSize=Jeland.type.secondary);inner()})
            if(query.isNotEmpty())RoundControl(JIcons.Close,"Очистить поиск",Modifier.testTag("clear-app-search")){onQuery("")}
        }
        RoundControl(JIcons.Refresh,"Обновить приложения",onClick=onRefresh)
    }
}
@Composable fun FavoriteOrderEditor(state:AppsState,model:MainViewModel) {
    if(state.favoriteApps.isEmpty())return
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Label("Порядок избранного",small=true,muted=true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            state.favoriteApps.forEachIndexed {index,app->
                Row(Modifier.width(245.dp).clip(Jeland.control).background(Jeland.colors.raised.copy(alpha=.5f)).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("${index+1}. ${app.label}",color=Jeland.colors.text,fontSize=Jeland.type.caption,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                    RoundControl(JIcons.ChevronLeft,"Подвинуть ${app.label} раньше",Modifier.testTag("favorite-earlier-${app.packageName}")){model.dependencies.apps.moveFavorite(app.id,-1)}
                    RoundControl(JIcons.ChevronRight,"Подвинуть ${app.label} позже",Modifier.testTag("favorite-later-${app.packageName}")){model.dependencies.apps.moveFavorite(app.id,1)}
                }
            }
        }
    }
}
@Composable fun AppsGrid(apps:List<LauncherApp>,state:AppsState,model:MainViewModel,modifier:Modifier=Modifier) {
    LazyVerticalGrid(GridCells.Adaptive(180.dp),modifier.testTag("apps-grid"),contentPadding=PaddingValues(bottom=8.dp),
        horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        items(apps,key={it.id}) {app->AppLaunchCard(app,state,model)}
    }
}

@Composable private fun AppLaunchCard(app:LauncherApp,state:AppsState,model:MainViewModel) {
    val c=Jeland.colors
    val favorite=app.id in state.favorites
    Panel(Modifier.fillMaxWidth().height(202.dp).testTag("app-card-${app.id}").clickable {model.launchApp(app.id)}) {
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.SpaceBetween) {
                AppIcon(app,model,Modifier.size(68.dp).testTag("app-icon-${app.id}"))
                RoundControl(JIcons.Star,"Избранное ${app.label}",Modifier.size(44.dp).testTag("favorite-${app.packageName}")
                    .semantics {stateDescription=if(favorite)"В избранном"else"Не в избранном"},favorite){model.dependencies.apps.toggleFavorite(app.id)}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(app.label,color=c.text,fontSize=Jeland.type.secondary,fontWeight=FontWeight.Medium,maxLines=2,
                    overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth())
                Text(app.packageName,color=c.muted.copy(alpha=.8f),fontSize=Jeland.type.micro,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if(favorite)"В избранном"else"Открыть приложение",color=if(favorite)c.accent else c.muted,fontSize=Jeland.type.footnote)
                Icon(JIcons.ChevronRight,null,Modifier.size(18.dp),tint=c.accent)
            }
        }
    }
}
